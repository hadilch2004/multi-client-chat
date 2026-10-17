package chat.client.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Where to connect. Command-line arguments win; with no arguments the address comes from
 * client.properties, so it can change without recompiling.
 *
 * @param host   server IP address or host name
 * @param port   server TCP port
 * @param source where the values came from, for messages
 */
public record ClientConfig(String host, int port, String source) {

    public static final String FILE_NAME = "client.properties";
    /** Optional JVM property that points to a config file: -Dchat.config=/path/client.properties */
    public static final String PATH_PROPERTY = "chat.config";

    public static final String USAGE =
            "Usage: java -jar TCPClient.jar <ServerIPAddress> <PortNumber>   (e.g. localhost 3000)";

    /**
     * Works out the server address.
     *
     * @throws IllegalArgumentException when the arguments or the settings are wrong
     * @throws IOException              when a settings file cannot be read
     */
    public static ClientConfig resolve(String[] args) throws IOException {
        if (args.length == 0) {
            return load();
        }
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected 2 arguments but got " + args.length + ".\n" + USAGE);
        }
        return new ClientConfig(requireHost(args[0]), parsePort(args[1]), "command line");
    }

    /** Loads the settings: -Dchat.config path, else ./client.properties, else the copy inside the JAR. */
    public static ClientConfig load() throws IOException {
        Properties props = new Properties();
        String source = "built-in defaults";

        String override = System.getProperty(PATH_PROPERTY);
        Path external = Path.of(override != null ? override : FILE_NAME);

        if (Files.isRegularFile(external)) {
            try (InputStream in = Files.newInputStream(external)) {
                props.load(in);
            }
            source = external.toAbsolutePath().toString();
        } else if (override != null) {
            throw new IOException("Config file not found: " + external.toAbsolutePath());
        } else {
            try (InputStream in = ClientConfig.class.getResourceAsStream("/" + FILE_NAME)) {
                if (in != null) {
                    props.load(in);
                    source = FILE_NAME + " (bundled in the JAR)";
                }
            }
        }
        return from(props, source);
    }

    public static ClientConfig from(Properties props, String source) {
        return new ClientConfig(
                requireHost(props.getProperty("server.host", "localhost")),
                parsePort(props.getProperty("server.port", "3000")),
                source);
    }

    private static String requireHost(String host) {
        String trimmed = host == null ? "" : host.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("The server address must not be empty.\n" + USAGE);
        }
        return trimmed;
    }

    private static int parsePort(String raw) {
        int port;
        try {
            port = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("The port must be a number, but was '" + raw + "'.\n" + USAGE);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("The port must be between 1 and 65535, but was " + port + ".");
        }
        return port;
    }
}
