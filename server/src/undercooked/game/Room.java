package undercooked.game;

import java.util.ArrayList;
import java.util.List;
import undercooked.concurrency.BoundedBuffer;
import undercooked.concurrency.LamportClock;
import undercooked.concurrency.ReadersWritersLock;
import undercooked.concurrency.ResourceLocker;
import undercooked.net.ClientConnection;
import undercooked.net.MiniJson;

/**
 * Authoritative multiplayer kitchen room orchestrating all CMPE 436 concurrency primitives:
 * - RoomStateMonitor (Java Monitor: lobby wait/notifyAll)
 * - ReadersWritersLock (writer-fair snapshot vs structural mutation guard)
 * - Per-Player & Per-Station BakeryLock + ResourceLocker ascending lock ordering
 * - OrderBoard (Producer-Consumer Bounded Buffer + atomic completion/expiry)
 * - PlatePool (CountingSemaphore + DishwasherThread event ordering)
 * - BroadcastQueue (BoundedBuffer MPSC fan-out to RoomBroadcasterThread)
 * - LamportClock (monotonic total ordering of room broadcasts)
 */
public class Room {

    public static final int MAX_PLAYERS = 4;
    public static final int TICK_THREAD_SLOT = 4;
    public static final int DISHWASHER_THREAD_SLOT = 5;
    public static final int MAX_THREAD_SLOTS = 6; // 0..3 players, 4 tick, 5 dishwasher

    private final String roomCode;
    private final RoomStateMonitor stateMonitor = new RoomStateMonitor();
    private final ReadersWritersLock rwLock = new ReadersWritersLock();
    private final LamportClock lamportClock = new LamportClock();

    private final KitchenMap map = new KitchenMap(MAX_THREAD_SLOTS);
    private final OrderBoard orderBoard = new OrderBoard();
    private final PlatePool platePool = new PlatePool();
    private final BoundedBuffer<String> broadcastQueue = new BoundedBuffer<>(64);

    private final Player[] seats = new Player[MAX_PLAYERS];
    private String hostPlayerId;
    private int durationSec;
    private long shiftEndMs;

    private final GameTickThread tickThread;
    private final OrderGeneratorThread orderGeneratorThread;
    private final DishwasherThread dishwasherThread;
    private final RoomBroadcasterThread broadcasterThread;

    public Room(String roomCode, int durationSec) {
        this.roomCode = roomCode;
        this.durationSec = (durationSec == 90 || durationSec == 180) ? durationSec : 180;

        this.tickThread = new GameTickThread(this);
        this.orderGeneratorThread = new OrderGeneratorThread(this);
        this.dishwasherThread = new DishwasherThread(this);
        this.broadcasterThread = new RoomBroadcasterThread(this);

        this.tickThread.start();
        this.orderGeneratorThread.start();
        this.dishwasherThread.start();
        this.broadcasterThread.start();
    }

    // =========================================================================
    // Lobby & Membership (guarded by ReadersWritersLock write lock)
    // =========================================================================

    public Player addPlayer(ClientConnection conn) {
        Player joined = null;
        rwLock.startWrite();
        try {
            if (stateMonitor.getState() != RoomStateMonitor.State.WAITING) {
                return null;
            }
            int freeSlot = -1;
            for (int i = 0; i < MAX_PLAYERS; i++) {
                if (seats[i] == null) {
                    freeSlot = i;
                    break;
                }
            }
            if (freeSlot == -1) {
                return null;
            }
            Player p = new Player(conn.getPlayerId(), freeSlot, conn.getPlayerName(), conn, MAX_THREAD_SLOTS);
            double[] spawn = map.getSpawn(freeSlot);
            p.setPosition(spawn[0], spawn[1]);
            seats[freeSlot] = p;
            if (hostPlayerId == null) {
                hostPlayerId = p.getPlayerId();
            }
            conn.setCurrentRoom(this);
            joined = p;
        } finally {
            rwLock.endWrite();
        }

        if (joined != null) {
            broadcastLobbyState();
        }
        return joined;
    }

    public boolean removePlayer(String playerId) {
        boolean emptyAfter = false;
        boolean removed = false;
        rwLock.startWrite();
        try {
            for (int i = 0; i < MAX_PLAYERS; i++) {
                if (seats[i] != null && seats[i].getPlayerId().equals(playerId)) {
                    // If player was carrying a plate, recycle it so plate conservation holds
                    Item held = seats[i].getHeldItem();
                    if (held != null && held.isPlate()) {
                        platePool.returnCleanPlateImmediate();
                    }
                    seats[i].getConnection().setCurrentRoom(null);
                    seats[i] = null;
                    removed = true;
                    break;
                }
            }
            if (removed) {
                int remaining = 0;
                String newHost = null;
                for (int i = 0; i < MAX_PLAYERS; i++) {
                    if (seats[i] != null) {
                        remaining++;
                        if (newHost == null) {
                            newHost = seats[i].getPlayerId();
                        }
                    }
                }
                if (remaining == 0) {
                    emptyAfter = true;
                } else if (playerId.equals(hostPlayerId)) {
                    hostPlayerId = newHost;
                }
            }
        } finally {
            rwLock.endWrite();
        }

        if (removed && !emptyAfter && stateMonitor.getState() == RoomStateMonitor.State.WAITING) {
            broadcastLobbyState();
        }
        return emptyAfter;
    }

    public void setPlayerReady(String playerId, boolean ready) {
        boolean updated = false;
        rwLock.startWrite();
        try {
            Player p = findPlayerUnlocked(playerId);
            if (p != null && stateMonitor.getState() == RoomStateMonitor.State.WAITING) {
                p.setReady(ready);
                updated = true;
            }
        } finally {
            rwLock.endWrite();
        }
        if (updated) {
            broadcastLobbyState();
        }
    }

    public String startGame(String requesterId) {
        String error = null;
        String gameStartJson = null;

        rwLock.startWrite();
        try {
            RoomStateMonitor.State st = stateMonitor.getState();
            if (st != RoomStateMonitor.State.WAITING && st != RoomStateMonitor.State.FINISHED) {
                error = "Room is already in progress";
            } else if (!requesterId.equals(hostPlayerId)) {
                error = "Only the room host can start the shift";
            } else {
                int count = 0;
                for (Player p : seats) {
                    if (p != null) {
                        count++;
                        if (count > 1 && !p.isReady() && st == RoomStateMonitor.State.WAITING) {
                            // Check all non-host players or all players ready when >1 player
                        }
                    }
                }
                if (count > 1 && st == RoomStateMonitor.State.WAITING) {
                    for (Player p : seats) {
                        if (p != null && !p.isReady()) {
                            error = "All chefs must be Ready before starting";
                            break;
                        }
                    }
                }
                if (error == null) {
                    resetKitchenUnlocked();
                    shiftEndMs = System.currentTimeMillis() + (durationSec * 1000L);
                    stateMonitor.transitionTo(RoomStateMonitor.State.PLAYING);
                    gameStartJson = new MiniJson.Obj()
                            .put("type", "GAME_START")
                            .put("seq", lamportClock.tick())
                            .put("roomCode", roomCode)
                            .put("gridW", KitchenMap.WIDTH)
                            .put("gridH", KitchenMap.HEIGHT)
                            .put("durationSec", durationSec)
                            .putRaw("stations", map.stationsStaticJson())
                            .toString();
                }
            }
        } finally {
            rwLock.endWrite();
        }

        if (gameStartJson != null) {
            enqueueBroadcast(gameStartJson);
        }
        return error;
    }

    public String returnToLobby(String requesterId) {
        String error = null;
        rwLock.startWrite();
        try {
            if (!requesterId.equals(hostPlayerId)) {
                error = "Only the host can return to lobby";
            } else if (stateMonitor.getState() == RoomStateMonitor.State.FINISHED) {
                for (Player p : seats) {
                    if (p != null) {
                        p.setReady(false);
                    }
                }
                stateMonitor.transitionTo(RoomStateMonitor.State.WAITING);
            }
        } finally {
            rwLock.endWrite();
        }
        if (error == null) {
            broadcastLobbyState();
        }
        return error;
    }

    private void resetKitchenUnlocked() {
        map.resetAllStations();
        orderBoard.reset();
        platePool.reset();
        for (int i = 0; i < MAX_PLAYERS; i++) {
            if (seats[i] != null) {
                double[] spawn = map.getSpawn(i);
                seats[i].resetForNewShift(spawn[0], spawn[1]);
            }
        }
    }

    // =========================================================================
    // Player Gameplay Commands (ClientHandlerThread)
    // =========================================================================

    public void handleMove(String playerId, double dx, double dy, String facing) {
        if (!stateMonitor.isPlaying()) {
            return;
        }
        rwLock.startRead();
        try {
            Player p = findPlayerUnlocked(playerId);
            if (p != null) {
                p.setMoveInput(dx, dy, facing);
            }
        } finally {
            rwLock.endRead();
        }
    }

    public void handleChop(String playerId, boolean active) {
        if (!stateMonitor.isPlaying()) {
            return;
        }
        rwLock.startRead();
        try {
            Player p = findPlayerUnlocked(playerId);
            if (p != null) {
                p.setChopping(active);
            }
        } finally {
            rwLock.endRead();
        }
    }

    /**
     * Handles primary GRAB / DROP / PLATE / SERVE action using:
     * - Level 1: ReadersWritersLock.startRead()
     * - Level 2: ResourceLocker.withOrderedLocks(slot, player, station) (BakeryLock, ascending resourceId)
     * - Level 3: OrderBoard atomic transaction (when serving)
     * - Level 4: PlatePool (when grabbing/returning/submitting plates)
     * - Level 5: BroadcastQueue (outside all gameplay locks!)
     */
    public void handleInteract(String playerId) {
        if (!stateMonitor.isPlaying()) {
            return;
        }
        List<String> pendingEvents = new ArrayList<>();

        rwLock.startRead();
        try {
            Player player = findPlayerUnlocked(playerId);
            if (player == null) {
                return;
            }
            int pid = player.getSlot();
            Station station = map.getFacingStation(player.getX(), player.getY(), player.getFacing());
            if (station == null) {
                return;
            }

            ConcurrencyLogger.log("BAKERY",
                    "Slot " + pid + " requesting ordered locks Player#" + player.getResourceId()
                            + " -> Station#" + station.getResourceId() + " (" + station.getType() + ")");

            ResourceLocker.withOrderedLocks(pid, player, station, () -> {
                performInteractionLocked(player, station, pendingEvents);
            });
        } finally {
            rwLock.endRead();
        }

        for (String evt : pendingEvents) {
            enqueueBroadcast(evt);
        }
    }

    private void performInteractionLocked(Player player, Station station, List<String> pendingEvents) {
        Item held = player.getHeldItem();
        Station.Type st = station.getType();

        // 1. Infinite Ingredient Crates
        if (station.isCrate()) {
            if (held == null) {
                player.setHeldItem(station.spawnCrateItem());
                pendingEvents.add(buildEventJson("PICKUP", player.getPlayerId(), station.getType().name(), 0));
            } else if (held.isPlate()) {
                Item crateItem = station.spawnCrateItem();
                if (crateItem != null && held.canAddToPlate(crateItem.getType())) {
                    held.addToPlate(crateItem.getType());
                    pendingEvents.add(buildEventJson("PLATED", player.getPlayerId(), crateItem.getType().name(), 0));
                }
            }
            return;
        }

        // 2. Plate Dispenser (Finite PlatePool CountingSemaphore)
        if (st == Station.Type.PLATE_DISPENSER) {
            if (held == null) {
                if (platePool.tryTakeCleanPlate()) {
                    player.setHeldItem(Item.plate());
                    pendingEvents.add(buildEventJson("PICKUP", player.getPlayerId(), "PLATE", 0));
                } else {
                    pendingEvents.add(buildEventJson("TOAST", player.getPlayerId(),
                            "No clean plates — dishwasher running!", 0));
                }
            } else if (held.isEmptyPlate()) {
                player.setHeldItem(null);
                platePool.returnCleanPlateImmediate();
            } else if (Item.isPlateableIngredient(held.getType())) {
                if (platePool.tryTakeCleanPlate()) {
                    Item plate = Item.plate();
                    plate.addToPlate(held.getType());
                    player.setHeldItem(plate);
                    pendingEvents.add(buildEventJson("PLATED", player.getPlayerId(), held.getType().name(), 0));
                } else {
                    pendingEvents.add(buildEventJson("TOAST", player.getPlayerId(),
                            "No clean plates — dishwasher running!", 0));
                }
            }
            return;
        }

        // 3. Trash Bin
        if (st == Station.Type.TRASH_BIN) {
            if (held != null) {
                if (held.isPlate()) {
                    held.clearPlate();
                } else {
                    player.setHeldItem(null);
                }
                pendingEvents.add(buildEventJson("TRASHED", player.getPlayerId(), "Item discarded", 0));
            }
            return;
        }

        // 4. Serving Window (Atomic OrderBoard Completion + Dishwasher Signal)
        if (st == Station.Type.SERVING_WINDOW) {
            if (held != null && held.isPlate() && !held.isEmptyPlate()) {
                Item dish = held;
                player.setHeldItem(null);
                OrderBoard.ServeResult res = orderBoard.tryCompleteOrder(dish, System.currentTimeMillis());
                platePool.submitDirtyPlate();

                if (res.matched) {
                    player.incrementOrdersServed();
                    pendingEvents.add(buildEventJson("ORDER_COMPLETE", player.getPlayerId(),
                            res.recipe.getDisplayName() + " (#" + res.orderId + ")", res.pointsDelta));
                } else {
                    pendingEvents.add(buildEventJson("WRONG_DISH", player.getPlayerId(),
                            "No matching active order!", res.pointsDelta));
                }
            } else if (held != null) {
                pendingEvents.add(buildEventJson("TOAST", player.getPlayerId(),
                        "Serve finished dishes on a plate!", 0));
            }
            return;
        }

        // 5. Counters, Chopping Boards, and Stoves
        if (station.canHoldItems()) {
            Item stationItem = station.getItem();

            // Pick up from station
            if (held == null && stationItem != null) {
                player.setHeldItem(stationItem);
                station.setItem(null);
                pendingEvents.add(buildEventJson("PICKUP", player.getPlayerId(), stationItem.getType().name(), 0));
                return;
            }

            // Drop onto empty station
            if (held != null && stationItem == null) {
                if (st == Station.Type.STOVE && !held.isCookable()) {
                    pendingEvents.add(buildEventJson("TOAST", player.getPlayerId(),
                            "Only raw patties go on the stove!", 0));
                    return;
                }
                station.setItem(held);
                player.setHeldItem(null);
                if (st == Station.Type.STOVE) {
                    pendingEvents.add(buildEventJson("COOK_START", player.getPlayerId(), "Sizzling!", 0));
                } else {
                    pendingEvents.add(buildEventJson("DROP", player.getPlayerId(), held.getType().name(), 0));
                }
                return;
            }

            // Combine / Plate (both directions supported)
            if (held != null && stationItem != null) {
                if (held.isPlate() && Item.isPlateableIngredient(stationItem.getType())
                        && held.canAddToPlate(stationItem.getType())) {
                    held.addToPlate(stationItem.getType());
                    station.setItem(null);
                    pendingEvents.add(buildEventJson("PLATED", player.getPlayerId(),
                            stationItem.getType().name(), 0));
                    return;
                }
                if (stationItem.isPlate() && Item.isPlateableIngredient(held.getType())
                        && stationItem.canAddToPlate(held.getType())) {
                    stationItem.addToPlate(held.getType());
                    player.setHeldItem(null);
                    pendingEvents.add(buildEventJson("PLATED", player.getPlayerId(), held.getType().name(), 0));
                    return;
                }
                pendingEvents.add(buildEventJson("TOAST", player.getPlayerId(),
                        "Cannot combine those items", 0));
            }
        }
    }

    // =========================================================================
    // 20 Hz Simulation Tick (GameTickThread, slot = 4)
    // =========================================================================

    public void stepSimulation(long dtMs, long nowMs) {
        double dtSec = dtMs / 1000.0;
        List<String> pendingEvents = new ArrayList<>();
        String snapshotJson = null;
        boolean shiftOver = false;

        rwLock.startRead();
        try {
            if (!stateMonitor.isPlaying()) {
                return;
            }

            // 1. Update player positions & chopping progress
            for (int i = 0; i < MAX_PLAYERS; i++) {
                Player p = seats[i];
                if (p == null) {
                    continue;
                }
                double dx = p.getInputDx();
                double dy = p.getInputDy();
                if (Math.abs(dx) > 0.02 || Math.abs(dy) > 0.02) {
                    p.getLock().requestCS(TICK_THREAD_SLOT);
                    try {
                        double nextX = p.getX() + dx * Player.MOVE_SPEED_TILES_PER_SEC * dtSec;
                        double nextY = p.getY() + dy * Player.MOVE_SPEED_TILES_PER_SEC * dtSec;
                        if (map.isWalkable(nextX, p.getY())) {
                            p.setPosition(nextX, p.getY());
                        }
                        if (map.isWalkable(p.getX(), nextY)) {
                            p.setPosition(p.getX(), nextY);
                        }
                    } finally {
                        p.getLock().releaseCS(TICK_THREAD_SLOT);
                    }
                }

                // Advance chopping if player is holding CHOP facing a Chopping Board
                if (p.isChopping()) {
                    Station target = map.getFacingStation(p.getX(), p.getY(), p.getFacing());
                    if (target != null && target.getType() == Station.Type.CHOPPING_BOARD) {
                        ResourceLocker.withOrderedLocks(TICK_THREAD_SLOT, p, target, () -> {
                            Item item = target.getItem();
                            if (item != null && item.isChoppable()) {
                                double nextProg = target.getChopProgressMs() + dtMs;
                                if (nextProg >= Station.CHOP_TIME_MS) {
                                    item.setType(item.choppedResult());
                                    target.setChopProgressMs(0.0);
                                    p.incrementItemsChopped();
                                    pendingEvents.add(buildEventJson("CHOP_DONE", p.getPlayerId(),
                                            item.getType().name(), 0));
                                } else {
                                    target.setChopProgressMs(nextProg);
                                }
                            }
                        });
                    }
                }
            }

            // 2. Advance stove cooking & burning timers (BakeryLock slot = 4)
            for (Station stove : map.getStoves()) {
                stove.getLock().requestCS(TICK_THREAD_SLOT);
                try {
                    Item item = stove.getItem();
                    if (item != null && (item.getType() == Item.Type.RAW_PATTY
                            || item.getType() == Item.Type.COOKED_PATTY)) {
                        double prev = stove.getCookProgressMs();
                        double next = prev + dtMs;
                        stove.setCookProgressMs(next);

                        if (item.getType() == Item.Type.RAW_PATTY && next >= Station.COOK_TIME_MS) {
                            item.setType(Item.Type.COOKED_PATTY);
                            pendingEvents.add(buildEventJson("PATTY_COOKED", null,
                                    "Stove@" + stove.getTileX() + "," + stove.getTileY(), 0));
                        } else if (item.getType() == Item.Type.COOKED_PATTY && next >= Station.BURN_TIME_MS) {
                            item.setType(Item.Type.BURNT_PATTY);
                            pendingEvents.add(buildEventJson("PATTY_BURNT", null,
                                    "Stove@" + stove.getTileX() + "," + stove.getTileY(), 0));
                        }
                    }
                } finally {
                    stove.getLock().releaseCS(TICK_THREAD_SLOT);
                }
            }

            // 3. Atomic expiry check on OrderBoard
            List<Order> expired = orderBoard.expireOverdueOrders(nowMs);
            for (Order o : expired) {
                pendingEvents.add(buildEventJson("ORDER_EXPIRED", null,
                        o.getRecipe().getDisplayName() + " expired!", -OrderBoard.EXPIRED_ORDER_PENALTY));
            }

            // 4. Check shift countdown & build consistent snapshot
            long timeLeftMs = Math.max(0L, shiftEndMs - nowMs);
            if (timeLeftMs == 0L) {
                shiftOver = true;
            }
            snapshotJson = buildSnapshotJsonUnlocked(nowMs, timeLeftMs);
        } finally {
            rwLock.endRead();
        }

        for (String evt : pendingEvents) {
            enqueueBroadcast(evt);
        }
        if (snapshotJson != null) {
            enqueueBroadcast(snapshotJson);
        }
        if (shiftOver) {
            finishShift();
        }
    }

    private void finishShift() {
        String gameOverJson = null;
        rwLock.startWrite();
        try {
            if (!stateMonitor.isPlaying()) {
                return;
            }
            stateMonitor.transitionTo(RoomStateMonitor.State.FINISHED);
            int score = orderBoard.getScore();
            int stars = computeStars(score, durationSec);

            MiniJson.Arr statsArr = new MiniJson.Arr();
            for (Player p : seats) {
                if (p != null) {
                    statsArr.addRaw(p.toStatsJson());
                }
            }
            gameOverJson = new MiniJson.Obj()
                    .put("type", "GAME_OVER")
                    .put("seq", lamportClock.tick())
                    .put("score", score)
                    .put("stars", stars)
                    .put("ordersCompleted", orderBoard.getOrdersCompleted())
                    .put("ordersExpired", orderBoard.getOrdersExpired())
                    .putRaw("playerStats", statsArr.toString())
                    .toString();
        } finally {
            rwLock.endWrite();
        }

        // Wake OrderGenerator & Dishwasher out of blocking semaphores so they return to waitUntilPlaying()
        orderGeneratorThread.interrupt();
        dishwasherThread.interrupt();

        if (gameOverJson != null) {
            enqueueBroadcast(gameOverJson);
        }
    }

    private static int computeStars(int score, int durationSec) {
        double scale = (durationSec <= 90) ? 0.5 : 1.0;
        if (score >= (int) (500 * scale)) {
            return 3;
        }
        if (score >= (int) (320 * scale)) {
            return 2;
        }
        if (score >= (int) (150 * scale)) {
            return 1;
        }
        return 0;
    }

    // =========================================================================
    // Serialization & Broadcasting
    // =========================================================================

    private String buildSnapshotJsonUnlocked(long nowMs, long timeLeftMs) {
        MiniJson.Arr playersArr = new MiniJson.Arr();
        for (Player p : seats) {
            if (p != null) {
                p.getLock().requestCS(TICK_THREAD_SLOT);
                try {
                    playersArr.addRaw(p.toSnapshotJson());
                } finally {
                    p.getLock().releaseCS(TICK_THREAD_SLOT);
                }
            }
        }

        MiniJson.Arr stationItemsArr = new MiniJson.Arr();
        for (Station s : map.getUniqueStations()) {
            if (!s.canHoldItems()) {
                continue;
            }
            s.getLock().requestCS(TICK_THREAD_SLOT);
            try {
                String dyn = s.toDynamicJson();
                if (dyn != null) {
                    stationItemsArr.addRaw(dyn);
                }
            } finally {
                s.getLock().releaseCS(TICK_THREAD_SLOT);
            }
        }

        return new MiniJson.Obj()
                .put("type", "STATE_SNAPSHOT")
                .put("seq", lamportClock.tick())
                .put("timeLeftMs", timeLeftMs)
                .put("score", orderBoard.getScore())
                .put("combo", orderBoard.getCombo())
                .put("cleanPlates", platePool.getCleanPlates())
                .put("washingPlates", platePool.getWashingPlates())
                .putRaw("players", playersArr.toString())
                .putRaw("stationItems", stationItemsArr.toString())
                .putRaw("orders", orderBoard.activeOrdersJson(nowMs))
                .toString();
    }

    public void broadcastLobbyState() {
        String lobbyJson;
        rwLock.startRead();
        try {
            MiniJson.Arr playersArr = new MiniJson.Arr();
            for (Player p : seats) {
                if (p != null) {
                    playersArr.addRaw(p.toLobbyJson());
                }
            }
            lobbyJson = new MiniJson.Obj()
                    .put("type", "LOBBY_STATE")
                    .put("seq", lamportClock.tick())
                    .put("roomCode", roomCode)
                    .put("hostId", hostPlayerId == null ? "" : hostPlayerId)
                    .put("state", stateMonitor.getState().name())
                    .put("durationSec", durationSec)
                    .putRaw("players", playersArr.toString())
                    .toString();
        } finally {
            rwLock.endRead();
        }
        enqueueBroadcast(lobbyJson);
    }

    public void emitEvent(String eventName, String actorId, String detail, int pointsDelta) {
        enqueueBroadcast(buildEventJson(eventName, actorId, detail, pointsDelta));
    }

    private String buildEventJson(String eventName, String actorId, String detail, int pointsDelta) {
        MiniJson.Obj obj = new MiniJson.Obj()
                .put("type", "GAME_EVENT")
                .put("seq", lamportClock.tick())
                .put("event", eventName)
                .put("detail", detail == null ? "" : detail)
                .put("pointsDelta", pointsDelta);
        if (actorId != null) {
            obj.put("actorId", actorId);
        }
        return obj.toString();
    }

    public void enqueueBroadcast(String jsonLine) {
        // Non-blocking tryDeposit prevents a stalled consumer from ever blocking gameplay threads
        broadcastQueue.tryDeposit(jsonLine);
    }

    public List<ClientConnection> snapshotConnections() {
        List<ClientConnection> list = new ArrayList<>(MAX_PLAYERS);
        rwLock.startRead();
        try {
            for (Player p : seats) {
                if (p != null) {
                    list.add(p.getConnection());
                }
            }
        } finally {
            rwLock.endRead();
        }
        return list;
    }

    public String toSummaryJson() {
        rwLock.startRead();
        try {
            int count = 0;
            for (Player p : seats) {
                if (p != null) {
                    count++;
                }
            }
            return new MiniJson.Obj()
                    .put("code", roomCode)
                    .put("players", count)
                    .put("maxPlayers", MAX_PLAYERS)
                    .put("durationSec", durationSec)
                    .put("state", stateMonitor.getState().name())
                    .toString();
        } finally {
            rwLock.endRead();
        }
    }

    private Player findPlayerUnlocked(String playerId) {
        for (Player p : seats) {
            if (p != null && p.getPlayerId().equals(playerId)) {
                return p;
            }
        }
        return null;
    }

    public void shutdown() {
        stateMonitor.transitionTo(RoomStateMonitor.State.CLOSED);
        tickThread.interrupt();
        orderGeneratorThread.interrupt();
        dishwasherThread.interrupt();
        broadcasterThread.interrupt();
        try {
            tickThread.join(1500);
            orderGeneratorThread.join(1500);
            dishwasherThread.join(1500);
            broadcasterThread.join(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Accessors
    public String getRoomCode() {
        return roomCode;
    }

    public RoomStateMonitor getStateMonitor() {
        return stateMonitor;
    }

    public OrderBoard getOrderBoard() {
        return orderBoard;
    }

    public PlatePool getPlatePool() {
        return platePool;
    }

    public BoundedBuffer<String> getBroadcastQueue() {
        return broadcastQueue;
    }

    public KitchenMap getMap() {
        return map;
    }
}
