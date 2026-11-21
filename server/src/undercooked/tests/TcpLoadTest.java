package undercooked.tests;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import undercooked.net.MiniJson;
import undercooked.net.UndercookedServer;

/**
 * End-to-end raw TCP ServerSocket / Socket integration test (Scenario C1 & lecture4-2.pdf).
 * Starts UndercookedServer on an ephemeral port, connects 8 real TCP Socket clients
 * across 2 multiplayer rooms, runs lobby -> start -> move/interact/chop -> snapshots,
 * verifies Lamport clock monotonicity, and asserts clean disconnect teardown.
 */
public final class TcpLoadTest {

    private static final class RawTcpClient implements AutoCloseable {
        final Socket socket;
        final BufferedReader in;
        final BufferedWriter out;

        RawTcpClient(int port) throws Exception {
            this.socket = new Socket("127.0.0.1", port);
            this.socket.setSoTimeout(5000);
            this.in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.out = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        void send(String json) throws Exception {
            out.write(json);
            out.write('\n');
            out.flush();
        }

        Map<String, Object> readUntilType(String expectedType) throws Exception {
            for (int i = 0; i < 60; i++) {
                String line = in.readLine();
                if (line == null) {
                    throw new AssertionError("Unexpected EOF waiting for " + expectedType);
                }
                Map<String, Object> msg = MiniJson.parseObject(line);
                if (expectedType.equals(MiniJson.getString(msg, "type", ""))) {
                    return msg;
                }
            }
            throw new AssertionError("Did not receive message type: " + expectedType);
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }

    public static void runAll() throws Exception {
        UndercookedServer server = new UndercookedServer(0);
        server.bind();
        int port = server.getBoundPort();

        Thread serverThread = new Thread(() -> {
            try {
                server.serveForever();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "TestTcpServer");
        serverThread.start();

        try {
            Thread room1 = new Thread(() -> runFourPlayerRoom(port, "RoomA"), "TcpRoomTest-A");
            Thread room2 = new Thread(() -> runFourPlayerRoom(port, "RoomB"), "TcpRoomTest-B");
            room1.start();
            room2.start();
            room1.join();
            room2.join();

            // Wait briefly for disconnect cleanup on ClientHandlerThreads
            long deadline = System.currentTimeMillis() + 3000;
            while (server.getActiveClientCount() > 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            if (server.getActiveClientCount() != 0) {
                throw new AssertionError("Leaked client connections: " + server.getActiveClientCount());
            }
            System.out.println("  [PASS] TcpLoadTest -> 8 concurrent raw TCP Sockets across 2 rooms, "
                    + "monotonic snapshots, 0 leaked connections");
        } finally {
            server.stop();
            serverThread.join(2000);
        }
    }

    private static void runFourPlayerRoom(int port, String label) {
        RawTcpClient[] clients = new RawTcpClient[4];
        try {
            for (int i = 0; i < 4; i++) {
                clients[i] = new RawTcpClient(port);
                clients[i].readUntilType("WELCOME");
                clients[i].send(new MiniJson.Obj()
                        .put("type", "HELLO")
                        .put("name", label + "-Chef" + i)
                        .toString());
                clients[i].readUntilType("WELCOME");
            }

            // Host creates room
            clients[0].send(new MiniJson.Obj()
                    .put("type", "CREATE_ROOM")
                    .put("durationSec", 90)
                    .toString());
            Map<String, Object> lobby = clients[0].readUntilType("LOBBY_STATE");
            String roomCode = MiniJson.getString(lobby, "roomCode", "");
            if (roomCode.length() != 4) {
                throw new AssertionError("Invalid room code: " + roomCode);
            }

            // Players 1..3 join room concurrently
            for (int i = 1; i < 4; i++) {
                clients[i].send(new MiniJson.Obj()
                        .put("type", "JOIN_ROOM")
                        .put("roomCode", roomCode)
                        .toString());
                clients[i].readUntilType("LOBBY_STATE");
            }

            // All 4 players toggle READY
            for (int i = 0; i < 4; i++) {
                clients[i].send(new MiniJson.Obj()
                        .put("type", "SET_READY")
                        .put("ready", true)
                        .toString());
            }
            Thread.sleep(80);

            // Host starts game
            clients[0].send(new MiniJson.Obj().put("type", "START_GAME").toString());
            for (int i = 0; i < 4; i++) {
                clients[i].readUntilType("GAME_START");
            }

            // Send concurrent gameplay commands
            for (int i = 0; i < 4; i++) {
                clients[i].send(new MiniJson.Obj()
                        .put("type", "MOVE")
                        .put("dx", 0.5)
                        .put("dy", -0.5)
                        .put("facing", "N")
                        .toString());
                clients[i].send(new MiniJson.Obj().put("type", "INTERACT").toString());
                clients[i].send(new MiniJson.Obj().put("type", "CHOP").put("active", true).toString());
            }

            // Verify every client receives >= 3 monotonically ordered STATE_SNAPSHOTs
            for (int i = 0; i < 4; i++) {
                long prevSeq = 0;
                for (int s = 0; s < 3; s++) {
                    Map<String, Object> snap = clients[i].readUntilType("STATE_SNAPSHOT");
                    long seq = ((Number) snap.get("seq")).longValue();
                    if (seq <= prevSeq) {
                        throw new AssertionError("Non-monotonic snapshot seq: " + seq + " <= " + prevSeq);
                    }
                    prevSeq = seq;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("TcpLoadTest failed in " + label, e);
        } finally {
            for (RawTcpClient c : clients) {
                if (c != null) {
                    try {
                        c.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }
}
