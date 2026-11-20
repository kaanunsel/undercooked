package undercooked;

import undercooked.game.ConcurrencyLogger;
import undercooked.net.UndercookedServer;

/**
 * Entry point for the Undercooked plain-Java multithreaded TCP game server.
 * Usage: java -jar undercooked-server.jar [port] [--log-concurrency]
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        int port = 6789;
        for (String arg : args) {
            if ("--log-concurrency".equals(arg)) {
                ConcurrencyLogger.setEnabled(true);
            } else {
                try {
                    port = Integer.parseInt(arg);
                } catch (NumberFormatException ignored) {
                    System.err.println("Ignoring unrecognized argument: " + arg);
                }
            }
        }

        UndercookedServer server = new UndercookedServer(port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down Undercooked TCP server...");
            server.stop();
        }, "ShutdownHook"));

        server.serveForever();
    }
}
