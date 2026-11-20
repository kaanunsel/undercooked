package undercooked.net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import undercooked.concurrency.BinarySemaphore;
import undercooked.game.Room;

/**
 * Wraps a raw TCP client Socket (CMPE 436 lecture4-2.pdf).
 * Uses a per-connection BinarySemaphore (writeMutex) so concurrent writes from
 * ClientHandlerThread (direct replies) and RoomBroadcasterThread (room snapshots)
 * never interleave bytes on the TCP OutputStream.
 */
public class ClientConnection {

    private final String playerId;
    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final BinarySemaphore writeMutex = new BinarySemaphore(true);

    private volatile String playerName;
    private volatile Room currentRoom;
    private volatile boolean open = true;

    public ClientConnection(String playerId, Socket socket) throws IOException {
        this.playerId = playerId;
        this.playerName = "Chef-" + playerId;
        this.socket = socket;
        this.socket.setTcpNoDelay(true); // Low-latency gameplay packets
        this.reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    public String readLine() throws IOException {
        return reader.readLine();
    }

    public boolean sendLine(String jsonLine) {
        if (!open) {
            return false;
        }
        writeMutex.P();
        try {
            if (!open) {
                return false;
            }
            writer.write(jsonLine);
            writer.write('\n');
            writer.flush();
            return true;
        } catch (IOException e) {
            close();
            return false;
        } finally {
            writeMutex.V();
        }
    }

    public void sendError(String code, String message) {
        String payload = new MiniJson.Obj()
                .put("type", "ERROR")
                .put("code", code)
                .put("message", message)
                .toString();
        sendLine(payload);
    }

    public void close() {
        if (!open) {
            return;
        }
        open = false;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public Room getCurrentRoom() {
        return currentRoom;
    }

    public void setCurrentRoom(Room currentRoom) {
        this.currentRoom = currentRoom;
    }

    public boolean isOpen() {
        return open && !socket.isClosed();
    }

    public String getRemoteAddress() {
        return socket.getRemoteSocketAddress() != null
                ? socket.getRemoteSocketAddress().toString()
                : "unknown";
    }
}
