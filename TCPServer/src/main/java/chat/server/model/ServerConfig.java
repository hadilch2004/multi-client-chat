package chat.server.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Network settings read at runtime from a .properties file, so the IP address and port can
 * change without recompiling.
 *
 * @param host   address to bind to ("0.0.0.0" = all network interfaces)
 * @param port   TCP port (0 lets the OS pick a free one, handy in tests)
 * @param source where the values came from, for the log
 */
public record ServerConfig(String host, int port, String source) {

    public static final String FILE_NAME = "server.properties";
    /** Optional JVM property that points to a config file: -Dchat.config=/path/server.properties */
    public static final String PATH_PROPERTY = "chat.config";

    public static final String DEFAULT_HOST = "0.0.0.0";
    public static final int DEFAULT_PORT = 3000;

    /** Loads the config: -Dchat.config path, else ./server.properties, else the copy inside the JAR. */
    public static ServerConfig load() throws IOException {
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
            try (InputStream in = ServerConfig.class.getResourceAsStream("/" + FILE_NAME)) {
                if (in != null) {
                    props.load(in);
                    source = FILE_NAME + " (bundled in the JAR)";
                }
            }
        }
        return from(props, source);
    }

    public static ServerConfig from(Properties props, String source) {
        String host = props.getProperty("server.host", DEFAULT_HOST).trim();
        if (host.isEmpty()) {
            host = DEFAULT_HOST;
        }
        String rawPort = props.getProperty("server.port", String.valueOf(DEFAULT_PORT)).trim();
        int port;
        try {
            port = Integer.parseInt(rawPort);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("server.port must be a number, but was '" + rawPort + "'");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("server.port must be between 0 and 65535, but was " + port);
        }
        return new ServerConfig(host, port, source);
    }
}
