package chat.client;

import chat.client.model.ClientConfig;
import javafx.application.Application;

import java.io.IOException;

/**
 * Entry point: {@code java -jar TCPClient.jar <ServerIPAddress> <PortNumber>}, for example
 * {@code java -jar TCPClient.jar localhost 3000}. Without arguments the address is read from
 * client.properties.
 *
 * <p>This class is deliberately not a JavaFX Application itself. A runnable fat JAR needs a plain
 * main class, otherwise the JVM looks for JavaFX as a module and refuses to start.
 */
public final class TCPClient {
    private TCPClient() {
    }

    public static void main(String[] args) {
        // Check the arguments here so a typo gives a clear message in the terminal, not a stack trace.
        try {
            ClientConfig.resolve(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(2);
        } catch (IOException e) {
            System.err.println("Could not read the settings: " + e.getMessage());
            System.exit(1);
        }
        Application.launch(ClientApp.class, args);
    }
}
