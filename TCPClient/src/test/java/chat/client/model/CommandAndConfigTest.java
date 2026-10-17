package chat.client.model;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandAndConfigTest {

    @Test
    void inputIsClassified() {
        assertEquals(InputCommand.EMPTY, InputCommand.parse(null));
        assertEquals(InputCommand.EMPTY, InputCommand.parse("   "));
        assertEquals(InputCommand.ALL_USERS, InputCommand.parse("allUsers"));
        assertEquals(InputCommand.ALL_USERS, InputCommand.parse("  ALLUSERS "));
        assertEquals(InputCommand.LEAVE, InputCommand.parse("bye"));
        assertEquals(InputCommand.LEAVE, InputCommand.parse("End"));
        assertEquals(InputCommand.MESSAGE, InputCommand.parse("bye bye"));
        assertEquals(InputCommand.MESSAGE, InputCommand.parse("hello"));
    }

    @Test
    void usernameRules() {
        assertTrue(ChatClient.validateUsername("hadil").isEmpty());
        assertTrue(ChatClient.validateUsername("").isEmpty(), "empty means read-only, which is allowed");
        assertTrue(ChatClient.validateUsername(null).isEmpty());
        assertFalse(ChatClient.validateUsername("two words").isEmpty());
        assertFalse(ChatClient.validateUsername("a".repeat(21)).isEmpty());
    }

    @Test
    void commandLineArgumentsGiveTheAddress() throws IOException {
        ClientConfig config = ClientConfig.resolve(new String[]{"192.168.1.10", "4000"});
        assertEquals("192.168.1.10", config.host());
        assertEquals(4000, config.port());
        assertEquals("command line", config.source());
    }

    @Test
    void wrongArgumentsAreRejectedWithUsage() {
        assertThrows(IllegalArgumentException.class, () -> ClientConfig.resolve(new String[]{"localhost"}));
        assertThrows(IllegalArgumentException.class, () -> ClientConfig.resolve(new String[]{"a", "b", "c"}));
        assertThrows(IllegalArgumentException.class, () -> ClientConfig.resolve(new String[]{"localhost", "abc"}));
        assertThrows(IllegalArgumentException.class, () -> ClientConfig.resolve(new String[]{"localhost", "0"}));
        assertThrows(IllegalArgumentException.class, () -> ClientConfig.resolve(new String[]{"localhost", "70000"}));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ClientConfig.resolve(new String[]{"localhost"}));
        assertTrue(e.getMessage().contains("Usage:"));
    }

    @Test
    void noArgumentsReadsTheBundledFile() throws IOException {
        System.clearProperty(ClientConfig.PATH_PROPERTY);
        ClientConfig config = ClientConfig.resolve(new String[0]);
        assertEquals("localhost", config.host());
        assertEquals(3000, config.port());
    }

    @Test
    void settingsFileCanBeOverriddenAtRuntime() throws IOException {
        Path file = Files.createTempFile("client", ".properties");
        try {
            Files.writeString(file, "server.host=10.0.0.5\nserver.port=4242\n");
            System.setProperty(ClientConfig.PATH_PROPERTY, file.toString());
            ClientConfig config = ClientConfig.resolve(new String[0]);
            assertEquals("10.0.0.5", config.host());
            assertEquals(4242, config.port());
        } finally {
            System.clearProperty(ClientConfig.PATH_PROPERTY);
            Files.deleteIfExists(file);
        }
    }

    @Test
    void propertiesFallBackToDefaults() {
        ClientConfig config = ClientConfig.from(new Properties(), "test");
        assertEquals("localhost", config.host());
        assertEquals(3000, config.port());
    }
}
