package undercooked.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import undercooked.concurrency.BinarySemaphore;
import undercooked.net.ClientConnection;
import undercooked.net.MiniJson;

/**
 * Global server room directory protected by a custom BinarySemaphore (Scenario C10).
 * Strict Level 0 lock: never acquires Room/Station/Order locks while holding registryMutex
 * except creating/looking up Room references cleanly.
 */
public class RoomRegistry {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final BinarySemaphore registryMutex = new BinarySemaphore(true);
    private final Map<String, Room> rooms = new LinkedHashMap<>();
    private final Random random = new Random();

    public Room createRoom(ClientConnection creator, int durationSec) {
        Room room;
        registryMutex.P();
        try {
            String code = generateUniqueCodeUnlocked();
            room = new Room(code, durationSec);
            rooms.put(code, room);
        } finally {
            registryMutex.V();
        }

        // Release Level 0 registryMutex BEFORE acquiring Level 1 Room lock!
        room.addPlayer(creator);
        return room;
    }

    public Room joinRoom(String roomCode, ClientConnection client) {
        if (roomCode == null) {
            return null;
        }
        String normalized = roomCode.trim().toUpperCase();
        Room room;
        registryMutex.P();
        try {
            room = rooms.get(normalized);
        } finally {
            registryMutex.V();
        }
        if (room == null) {
            return null;
        }
        Player joined = room.addPlayer(client);
        return (joined != null) ? room : null;
    }

    public void leaveRoom(ClientConnection client) {
        Room room = client.getCurrentRoom();
        if (room == null) {
            return;
        }
        boolean empty = room.removePlayer(client.getPlayerId());
        if (empty) {
            boolean removed = false;
            registryMutex.P();
            try {
                if (rooms.get(room.getRoomCode()) == room) {
                    rooms.remove(room.getRoomCode());
                    removed = true;
                }
            } finally {
                registryMutex.V();
            }
            if (removed) {
                room.shutdown();
            }
        }
    }

    public String listRoomsJson() {
        List<Room> snapshot;
        registryMutex.P();
        try {
            snapshot = new ArrayList<>(rooms.values());
        } finally {
            registryMutex.V();
        }

        MiniJson.Arr arr = new MiniJson.Arr();
        for (Room r : snapshot) {
            if (r.getStateMonitor().getState() == RoomStateMonitor.State.WAITING) {
                arr.addRaw(r.toSummaryJson());
            }
        }
        return new MiniJson.Obj()
                .put("type", "ROOM_LIST")
                .putRaw("rooms", arr.toString())
                .toString();
    }

    public void shutdownAll() {
        List<Room> snapshot;
        registryMutex.P();
        try {
            snapshot = new ArrayList<>(rooms.values());
            rooms.clear();
        } finally {
            registryMutex.V();
        }
        for (Room r : snapshot) {
            r.shutdown();
        }
    }

    private String generateUniqueCodeUnlocked() {
        for (int attempt = 0; attempt < 100; attempt++) {
            StringBuilder sb = new StringBuilder(4);
            for (int i = 0; i < 4; i++) {
                sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            String code = sb.toString();
            if (!rooms.containsKey(code)) {
                return code;
            }
        }
        return "R" + (System.nanoTime() % 9999);
    }
}
