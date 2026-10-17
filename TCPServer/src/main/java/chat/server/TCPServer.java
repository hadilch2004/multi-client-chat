package chat.server;

import javafx.application.Application;

/**
 * Entry point: {@code java -jar TCPServer.jar}. No arguments; the IP address and port come from
 * server.properties.
 *
 * <p>This class is deliberately not a JavaFX Application itself. A runnable fat JAR needs a plain
 * main class, otherwise the JVM looks for JavaFX as a module and refuses to start.
 */
public final class TCPServer {
    private TCPServer() {
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            System.err.println("TCPServer takes no arguments. Edit server.properties to change the port.");
        }
        Application.launch(ServerApp.class, args);
    }
}
