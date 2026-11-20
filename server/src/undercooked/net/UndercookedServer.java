package undercooked.net;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import undercooked.concurrency.BinarySemaphore;
import undercooked.concurrency.HWMutex;
import undercooked.game.RoomRegistry;

/**
 * Plain Java Multithreaded Raw TCP Server directly following CMPE 436 lecture4-2.pdf.
 * Listens on a java.net.ServerSocket and explicitly spawns a new ClientHandlerThread
 * for every accepted TCP client socket (no thread pools, no frameworks).
 */
public class UndercookedServer {

    private final int requestedPort;
    private final RoomRegistry roomRegistry = new RoomRegistry();

    private final HWMutex idMutex = new HWMutex();
    private int nextClientId = 1;

    private final BinarySemaphore clientsMutex = new BinarySemaphore(true);
    private final List<ClientConnection> activeClients = new ArrayList<>();

    private ServerSocket welcomeSocket;
    private volatile boolean running = false;

    public UndercookedServer(int port) {
        this.requestedPort = port;
    }

    public void bind() throws IOException {
        this.welcomeSocket = new ServerSocket(requestedPort);
    }

    public int getBoundPort() {
        return welcomeSocket != null ? welcomeSocket.getLocalPort() : requestedPort;
    }

    public void serveForever() throws IOException {
        if (welcomeSocket == null) {
            bind();
        }
        running = true;
        System.out.println("Undercooked TCP Server listening on port " + getBoundPort()
                + " (plain Java ServerSocket, zero frameworks)");

        while (running) {
            try {
                Socket connectionSocket = welcomeSocket.accept();
                String playerId = nextPlayerId();
                ClientConnection conn = new ClientConnection(playerId, connectionSocket);

                clientsMutex.P();
                try {
                    activeClients.add(conn);
                } finally {
                    clientsMutex.V();
                }

                // Explicit thread-per-client connection (CMPE 436 requirement)
                ClientHandlerThread handler = new ClientHandlerThread(conn, this);
                handler.start();
            } catch (SocketException se) {
                if (!running) {
                    break; // Clean shutdown via stop()
                }
                System.err.println("Accept socket error: " + se.getMessage());
            } catch (IOException ioe) {
                if (running) {
                    System.err.println("Client accept I/O error: " + ioe.getMessage());
                }
            }
        }
    }

    public void stop() {
        running = false;
        if (welcomeSocket != null && !welcomeSocket.isClosed()) {
            try {
                welcomeSocket.close();
            } catch (IOException ignored) {
            }
        }
        List<ClientConnection> copy;
        clientsMutex.P();
        try {
            copy = new ArrayList<>(activeClients);
            activeClients.clear();
        } finally {
            clientsMutex.V();
        }
        for (ClientConnection c : copy) {
            c.close();
        }
        roomRegistry.shutdownAll();
    }

    public void onClientDisconnected(ClientConnection conn) {
        clientsMutex.P();
        try {
            activeClients.remove(conn);
        } finally {
            clientsMutex.V();
        }
    }

    public int getActiveClientCount() {
        clientsMutex.P();
        try {
            return activeClients.size();
        } finally {
            clientsMutex.V();
        }
    }

    public RoomRegistry getRoomRegistry() {
        return roomRegistry;
    }

    private String nextPlayerId() {
        idMutex.requestCS(0);
        try {
            return "p" + (nextClientId++);
        } finally {
            idMutex.releaseCS(0);
        }
    }
}
