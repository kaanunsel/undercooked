package undercooked.game;

import undercooked.concurrency.BakeryLock;
import undercooked.concurrency.Lock;
import undercooked.concurrency.ResourceLocker;
import undercooked.net.ClientConnection;
import undercooked.net.MiniJson;

/**
 * Represents a connected chef inside a kitchen room.
 * Implements LockableResource with resourceId = slot (0..3), strictly less than
 * Station resourceIds (10..49) to enforce deadlock-free lock ordering (Scenario C6).
 */
public class Player implements ResourceLocker.LockableResource {

    public static final double MOVE_SPEED_TILES_PER_SEC = 4.6;

    private final String playerId;
    private final int slot; // 0..3 (also used as BakeryLock pid & resourceId)
    private final String name;
    private final ClientConnection connection;
    private final BakeryLock lock;

    private volatile boolean ready = false;
    private double x;
    private double y;
    private String facing = "S";
    private volatile double inputDx = 0.0;
    private volatile double inputDy = 0.0;
    private volatile boolean chopping = false;

    private Item heldItem;

    // Shift stats
    private int ordersServed = 0;
    private int itemsChopped = 0;
    private int pattiesCooked = 0;

    public Player(String playerId, int slot, String name, ClientConnection connection, int maxThreadSlots) {
        this.playerId = playerId;
        this.slot = slot;
        this.name = name;
        this.connection = connection;
        this.lock = new BakeryLock(maxThreadSlots);
    }

    @Override
    public int getResourceId() {
        return slot;
    }

    @Override
    public Lock getLock() {
        return lock;
    }

    public String getPlayerId() {
        return playerId;
    }

    public int getSlot() {
        return slot;
    }

    public String getName() {
        return name;
    }

    public ClientConnection getConnection() {
        return connection;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public String getFacing() {
        return facing;
    }

    public void setFacing(String facing) {
        if ("N".equals(facing) || "S".equals(facing) || "E".equals(facing) || "W".equals(facing)) {
            this.facing = facing;
        }
    }

    public double getInputDx() {
        return inputDx;
    }

    public double getInputDy() {
        return inputDy;
    }

    public void setMoveInput(double dx, double dy, String facing) {
        double mag = Math.hypot(dx, dy);
        if (mag > 1.0) {
            dx /= mag;
            dy /= mag;
        }
        this.inputDx = dx;
        this.inputDy = dy;
        if (facing != null && !facing.isEmpty()) {
            setFacing(facing);
        } else if (mag > 0.15) {
            if (Math.abs(dx) > Math.abs(dy)) {
                this.facing = dx > 0 ? "E" : "W";
            } else {
                this.facing = dy > 0 ? "S" : "N";
            }
        }
    }

    public boolean isChopping() {
        return chopping;
    }

    public void setChopping(boolean chopping) {
        this.chopping = chopping;
    }

    public Item getHeldItem() {
        return heldItem;
    }

    public void setHeldItem(Item heldItem) {
        this.heldItem = heldItem;
    }

    public int getOrdersServed() {
        return ordersServed;
    }

    public void incrementOrdersServed() {
        this.ordersServed++;
    }

    public int getItemsChopped() {
        return itemsChopped;
    }

    public void incrementItemsChopped() {
        this.itemsChopped++;
    }

    public int getPattiesCooked() {
        return pattiesCooked;
    }

    public void incrementPattiesCooked() {
        this.pattiesCooked++;
    }

    public void resetForNewShift(double spawnX, double spawnY) {
        this.x = spawnX;
        this.y = spawnY;
        this.facing = "S";
        this.inputDx = 0.0;
        this.inputDy = 0.0;
        this.chopping = false;
        this.heldItem = null;
        this.ordersServed = 0;
        this.itemsChopped = 0;
        this.pattiesCooked = 0;
    }

    public String toLobbyJson() {
        return new MiniJson.Obj()
                .put("id", playerId)
                .put("slot", slot)
                .put("name", name)
                .put("ready", ready)
                .toString();
    }

    public String toSnapshotJson() {
        MiniJson.Obj obj = new MiniJson.Obj()
                .put("id", playerId)
                .put("slot", slot)
                .put("name", name)
                .put("x", x)
                .put("y", y)
                .put("facing", facing)
                .put("chopping", chopping);
        if (heldItem != null) {
            obj.putRaw("heldItem", heldItem.toJson());
        } else {
            obj.putRaw("heldItem", "null");
        }
        return obj.toString();
    }

    public String toStatsJson() {
        return new MiniJson.Obj()
                .put("id", playerId)
                .put("slot", slot)
                .put("name", name)
                .put("ordersServed", ordersServed)
                .put("itemsChopped", itemsChopped)
                .put("pattiesCooked", pattiesCooked)
                .toString();
    }
}
