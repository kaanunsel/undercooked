package undercooked.net;

import java.io.IOException;
import java.util.Map;
import undercooked.game.Room;

/**
 * Explicit java.lang.Thread spawned for every accepted TCP client socket
 * (CMPE 436 lecture4-2.pdf; Scenario C1).
 * Reads newline-delimited JSON commands and dispatches them into Room / RoomRegistry.
 */
public class ClientHandlerThread extends Thread {

    private final ClientConnection conn;
    private final UndercookedServer server;

    public ClientHandlerThread(ClientConnection conn, UndercookedServer server) {
        super("ClientHandler-" + conn.getPlayerId());
        this.conn = conn;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            // Send immediate WELCOME upon TCP connection
            conn.sendLine(new MiniJson.Obj()
                    .put("type", "WELCOME")
                    .put("playerId", conn.getPlayerId())
                    .put("serverVersion", "1.0-cmpe436")
                    .toString());

            String line;
            while (conn.isOpen() && (line = conn.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                handleMessage(line);
            }
        } catch (IOException ignored) {
            // Client disconnected or socket closed
        } finally {
            server.getRoomRegistry().leaveRoom(conn);
            conn.close();
            server.onClientDisconnected(conn);
        }
    }

    private void handleMessage(String rawJson) {
        Map<String, Object> msg;
        try {
            msg = MiniJson.parseObject(rawJson);
        } catch (Exception e) {
            conn.sendError("BAD_JSON", "Malformed JSON line: " + e.getMessage());
            return;
        }

        String type = MiniJson.getString(msg, "type", "");
        switch (type) {
            case "HELLO" -> {
                String name = MiniJson.getString(msg, "name", "").trim();
                if (!name.isEmpty()) {
                    if (name.length() > 16) {
                        name = name.substring(0, 16);
                    }
                    conn.setPlayerName(name);
                }
                conn.sendLine(new MiniJson.Obj()
                        .put("type", "WELCOME")
                        .put("playerId", conn.getPlayerId())
                        .put("name", conn.getPlayerName())
                        .put("serverVersion", "1.0-cmpe436")
                        .toString());
            }
            case "LIST_ROOMS" -> conn.sendLine(server.getRoomRegistry().listRoomsJson());
            case "CREATE_ROOM" -> {
                if (conn.getCurrentRoom() != null) {
                    server.getRoomRegistry().leaveRoom(conn);
                }
                int duration = MiniJson.getInt(msg, "durationSec", 180);
                server.getRoomRegistry().createRoom(conn, duration);
            }
            case "JOIN_ROOM" -> {
                if (conn.getCurrentRoom() != null) {
                    server.getRoomRegistry().leaveRoom(conn);
                }
                String code = MiniJson.getString(msg, "roomCode", "");
                Room joined = server.getRoomRegistry().joinRoom(code, conn);
                if (joined == null) {
                    conn.sendError("JOIN_FAILED", "Room '" + code + "' not found, full, or already playing");
                }
            }
            case "SET_READY" -> {
                Room room = conn.getCurrentRoom();
                if (room != null) {
                    room.setPlayerReady(conn.getPlayerId(), MiniJson.getBool(msg, "ready", true));
                }
            }
            case "START_GAME" -> {
                Room room = conn.getCurrentRoom();
                if (room != null) {
                    String err = room.startGame(conn.getPlayerId());
                    if (err != null) {
                        conn.sendError("START_FAILED", err);
                    }
                }
            }
            case "RETURN_LOBBY" -> {
                Room room = conn.getCurrentRoom();
                if (room != null) {
                    String err = room.returnToLobby(conn.getPlayerId());
                    if (err != null) {
                        conn.sendError("LOBBY_FAILED", err);
                    }
                }
            }
            case "MOVE" -> {
                Room room = conn.getCurrentRoom();
                if (room != null) {
                    double dx = MiniJson.getDouble(msg, "dx", 0.0);
                    double dy = MiniJson.getDouble(msg, "dy", 0.0);
                    String facing = MiniJson.getString(msg, "facing", "");
                    room.handleMove(conn.getPlayerId(), dx, dy, facing);
                }
            }
            case "INTERACT" -> {
                Room room = conn.getCurrentRoom();
                if (room != null) {
                    room.handleInteract(conn.getPlayerId());
                }
            }
            case "CHOP" -> {
                Room room = conn.getCurrentRoom();
                if (room != null) {
                    room.handleChop(conn.getPlayerId(), MiniJson.getBool(msg, "active", false));
                }
            }
            case "LEAVE_ROOM" -> {
                server.getRoomRegistry().leaveRoom(conn);
                conn.sendLine(new MiniJson.Obj().put("type", "LEFT_ROOM").toString());
            }
            case "PING" -> conn.sendLine(new MiniJson.Obj()
                    .put("type", "PONG")
                    .put("t", System.currentTimeMillis())
                    .toString());
            default -> conn.sendError("UNKNOWN_TYPE", "Unsupported message type: " + type);
        }
    }
}
