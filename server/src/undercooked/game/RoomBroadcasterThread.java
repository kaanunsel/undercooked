package undercooked.game;

import java.util.List;
import undercooked.net.ClientConnection;

/**
 * Explicit Consumer worker thread draining the room's BoundedBuffer<String> BroadcastQueue
 * (Scenario C7) so slow TCP writes never stall kitchen simulation or station locks.
 */
public class RoomBroadcasterThread extends Thread {

    private final Room room;

    public RoomBroadcasterThread(Room room) {
        super("Broadcaster-" + room.getRoomCode());
        this.room = room;
    }

    @Override
    public void run() {
        while (!room.getStateMonitor().isClosed()) {
            try {
                String jsonLine = room.getBroadcastQueue().fetchInterruptible();
                List<ClientConnection> connections = room.snapshotConnections();
                for (ClientConnection conn : connections) {
                    if (conn.isOpen()) {
                        conn.sendLine(jsonLine);
                    }
                }
            } catch (InterruptedException e) {
                if (room.getStateMonitor().isClosed()) {
                    break;
                }
            }
        }
    }
}
