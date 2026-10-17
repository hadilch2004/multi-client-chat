package chat.server.model;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolAndConfigTest {

    @Test
    void escapeKeepsTextOnOneLineAndRoundTrips() {
        String original = "a\\b\nline two\tTabbed \\n not a newline";
        String escaped = Protocol.escape(original);
        assertFalse(escaped.contains("\n"));
        assertFalse(escaped.contains("\t"));
        assertEquals(original, Protocol.unescape(escaped));
    }

    @Test
    void lineJoinsFieldsWithTabs() {
        assertEquals("MSG\t12:00:00\talice\thi", Protocol.line(Protocol.MSG, "12:00:00", "alice", "hi"));
        assertEquals("BYE", Protocol.line(Protocol.BYE));
    }

    @Test
    void usernameRules() {
        assertTrue(ChatServer.isValidUsername("hadil"));
        assertTrue(ChatServer.isValidUsername("Zoé_2.0-x"));
        assertFalse(ChatServer.isValidUsername(""));
        assertFalse(ChatServer.isValidUsername("two words"));
        assertFalse(ChatServer.isValidUsername("tab\there"));
        assertFalse(ChatServer.isValidUsername("a".repeat(21)));
    }

    @Test
    void configReadsHostAndPort() {
        Properties p = new Properties();
        p.setProperty("server.host", "192.168.1.20");
        p.setProperty("server.port", "4500");
        ServerConfig config = ServerConfig.from(p, "test");
        assertEquals("192.168.1.20", config.host());
        assertEquals(4500, config.port());
    }

    @Test
    void configFallsBackToDefaults() {
        ServerConfig config = ServerConfig.from(new Properties(), "test");
        assertEquals("0.0.0.0", config.host());
        assertEquals(3000, config.port());
    }

    @Test
    void configRejectsBadPorts() {
        for (String bad : new String[]{"abc", "-1", "70000", ""}) {
            Properties p = new Properties();
            p.setProperty("server.port", bad);
            assertThrows(IllegalArgumentException.class, () -> ServerConfig.from(p, "test"), "port '" + bad + "'");
        }
    }

    @Test
    void configIsLoadedFromAnExternalFile() throws IOException {
        Path file = Files.createTempFile("server", ".properties");
        try {
            Files.writeString(file, "server.host=localhost\nserver.port=4242\n");
            System.setProperty(ServerConfig.PATH_PROPERTY, file.toString());
            ServerConfig config = ServerConfig.load();
            assertEquals("localhost", config.host());
            assertEquals(4242, config.port());
            assertEquals(file.toAbsolutePath().toString(), config.source());
        } finally {
            System.clearProperty(ServerConfig.PATH_PROPERTY);
            Files.deleteIfExists(file);
        }
    }

    @Test
    void bundledConfigIsUsedWhenNoFileExists() throws IOException {
        System.clearProperty(ServerConfig.PATH_PROPERTY);
        ServerConfig config = ServerConfig.load();
        assertEquals(3000, config.port());
    }
}
