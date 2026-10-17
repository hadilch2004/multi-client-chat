package chat.server.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Talks to a real ChatServer over real sockets, exactly like TCPClient (or telnet) would. */
@Timeout(20)
class ChatServerTest {

    private ChatServer server;
    private final List<String> logs = new CopyOnWriteArrayList<>();
    private final List<String> joined = new CopyOnWriteArrayList<>();
    private final List<String> left = new CopyOnWriteArrayList<>();
    private final List<RawClient> clients = new ArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = new ChatServer(new ServerConfig("127.0.0.1", 0, "test"), new ServerListener() {
            @Override
            public void onLog(String message) {
                logs.add(message);
            }

            @Override
            public void onUserJoined(String displayName) {
                joined.add(displayName);
            }

            @Override
            public void onUserLeft(String displayName) {
                left.add(displayName);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        clients.forEach(RawClient::close);
        server.close();
    }

    // ---- joining ------------------------------------------------------------------------------

    @Test
    void namedUserIsWelcomedAndLogged() throws IOException {
        RawClient alice = connect("alice");
        assertEquals("WELCOME\talice", alice.readLine());
        eventually(() -> logs.contains("Welcome alice"));
        eventually(() -> joined.contains("alice"));
    }

    @Test
    void serverLogsStartupAndWaiting() {
        assertTrue(logs.stream().anyMatch(l -> l.startsWith("Server Started")));
        eventually(() -> logs.contains("Waiting for Client..."));
    }

    @Test
    void usernameAlreadyTakenIsRejectedIgnoringCase() throws IOException {
        connect("alice").readLine();
        RawClient second = connect("ALICE");
        assertTrue(second.readLine().startsWith("ERROR\tUSERNAME_TAKEN"));
        assertNull(second.readLine(), "server should hang up on a rejected client");
        eventually(() -> logs.stream().anyMatch(l -> l.startsWith("Rejected") && l.contains("already in use")));
    }

    @Test
    void invalidUsernameIsRejected() throws IOException {
        RawClient c = connect("bad name!");
        assertTrue(c.readLine().startsWith("ERROR\tUSERNAME_INVALID"));
    }

    @Test
    void firstLineMustBeJoin() throws IOException {
        RawClient c = open();
        c.send("MSG\thello");
        assertTrue(c.readLine().startsWith("ERROR\tPROTOCOL"));
    }

    // ---- messaging ---------------------------------------------------------------------------

    @Test
    void messageIsStampedWithSenderAndTimeAndReachesEveryone() throws IOException {
        RawClient alice = joinAs("alice");
        RawClient bob = joinAs("bob");

        alice.send("MSG\thello bob");

        for (RawClient c : List.of(alice, bob)) {
            String[] f = c.readUntil("MSG").split("\t");
            assertEquals(4, f.length);
            assertTrue(f[1].matches("\\d\\d:\\d\\d:\\d\\d"), "time stamp, was " + f[1]);
            assertEquals("alice", f[2]);
            assertEquals("hello bob", f[3]);
        }
    }

    @Test
    void multiLineMessagesSurviveTheTrip() throws IOException {
        RawClient alice = joinAs("alice");
        alice.send("MSG\tline one\\nline two");
        String[] f = alice.readUntil("MSG").split("\t");
        assertEquals("line one\nline two", Protocol.unescape(f[3]));
    }

    @Test
    void othersAreToldWhenSomeoneJoins() throws IOException {
        RawClient alice = joinAs("alice");
        joinAs("bob");
        assertTrue(alice.readUntil("INFO").endsWith("bob joined the chat"));
    }

    // ---- commands ----------------------------------------------------------------------------

    @Test
    void allUsersAnswersOnlyTheAsker() throws IOException {
        RawClient alice = joinAs("alice");
        RawClient bob = joinAs("bob");
        alice.readUntil("INFO"); // "bob joined"

        alice.send("ALLUSERS");
        assertEquals("USERS\talice\tbob", alice.readLine());
        bob.assertNothingPending();
    }

    @Test
    void typedAllUsersWorksOnAPlainConnectionToo() throws IOException {
        RawClient alice = joinAs("alice");
        alice.send("MSG\tallUsers");
        assertEquals("USERS\talice", alice.readLine());
    }

    @Test
    void leaveClosesTheConnectionAndNotifiesOthers() throws IOException {
        RawClient alice = joinAs("alice");
        RawClient bob = joinAs("bob");

        bob.send("LEAVE");
        assertEquals("BYE", bob.readLine());
        assertNull(bob.readLine());

        alice.readUntil("INFO"); // "bob joined the chat"
        assertTrue(alice.readUntil("INFO").endsWith("bob left the chat"));
        eventually(() -> left.contains("bob"));
        eventually(() -> logs.contains("Goodbye bob"));
    }

    @Test
    void typedEndAndByeDisconnect() throws IOException {
        List<String> words = List.of("end", "bye", "BYE");
        for (int i = 0; i < words.size(); i++) {
            RawClient c = joinAs("user" + i);
            c.send("MSG\t" + words.get(i));
            assertEquals("BYE", c.readLine());
            assertNull(c.readLine());
        }
    }

    @Test
    void droppedConnectionIsCleanedUp() throws IOException {
        RawClient alice = joinAs("alice");
        RawClient bob = joinAs("bob");
        bob.close();

        eventually(() -> left.contains("bob"));
        alice.readUntil("INFO"); // bob joined
        assertTrue(alice.readUntil("INFO").endsWith("bob left the chat"));

        // the name is free again
        assertEquals("WELCOME\tbob", connect("bob").readLine());
    }

    // ---- read-only mode ----------------------------------------------------------------------

    @Test
    void clientWithoutUsernameIsReadOnly() throws IOException {
        RawClient guest = connect("");
        assertEquals("READONLY\tGuest-1 (read-only)", guest.readLine());

        guest.send("MSG\tcan I talk?");
        assertTrue(guest.readLine().startsWith("ERROR\tREAD_ONLY"));
    }

    @Test
    void guestsSeeMessagesAndAreListedButCannotSend() throws IOException {
        RawClient guest = connect("");
        guest.readLine();
        RawClient alice = joinAs("alice");

        alice.send("MSG\thi everyone");
        assertTrue(guest.readUntil("MSG").endsWith("alice\thi everyone"));

        alice.send("ALLUSERS");
        assertEquals("USERS\tGuest-1 (read-only)\talice", alice.readUntil("USERS"));
    }

    // ---- lots of clients ---------------------------------------------------------------------

    @Test
    void manyClientsAtOnce() throws Exception {
        int count = 60;
        List<RawClient> all = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            all.add(joinAs("user" + i));
        }
        all.get(0).send("ALLUSERS");
        assertEquals(count, all.get(0).readUntil("USERS").split("\t").length - 1);

        all.get(count - 1).send("MSG\tto all of you");
        for (RawClient c : all) {
            assertTrue(c.readUntil("MSG").endsWith("user" + (count - 1) + "\tto all of you"));
        }
    }

    @Test
    void shutdownTellsClientsAndClosesConnections() throws IOException {
        RawClient alice = joinAs("alice");
        server.close();
        assertTrue(alice.readUntil("INFO").endsWith("The server is shutting down"));
        assertEquals("BYE", alice.readLine());
        assertNull(alice.readLine());
    }

    // ---- helpers -----------------------------------------------------------------------------

    private RawClient open() throws IOException {
        RawClient c = new RawClient(new Socket("127.0.0.1", server.getPort()));
        clients.add(c);
        return c;
    }

    /** Opens a connection and sends JOIN (empty name = read-only). The reply is left unread. */
    private RawClient connect(String name) throws IOException {
        RawClient c = open();
        c.send("JOIN\t" + name);
        return c;
    }

    /** Connects with a username and consumes the WELCOME line. */
    private RawClient joinAs(String name) throws IOException {
        RawClient c = connect(name);
        assertEquals("WELCOME\t" + name, c.readLine());
        return c;
    }

    private static void eventually(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("condition not met within 5 seconds");
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** A bare-bones client that speaks the protocol line by line. */
    private static final class RawClient {
        private final Socket socket;
        private final BufferedReader in;
        private final PrintWriter out;

        RawClient(Socket socket) throws IOException {
            this.socket = socket;
            this.socket.setSoTimeout(5000);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        }

        void send(String line) {
            out.print(line + "\n");
            out.flush();
        }

        /** Next line, or null when the server closed the connection. */
        String readLine() throws IOException {
            return in.readLine();
        }

        /** Skips lines until one starts with the given command, and returns it. */
        String readUntil(String command) throws IOException {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.equals(command) || line.startsWith(command + "\t")) {
                    return line;
                }
            }
            throw new AssertionError("connection closed while waiting for " + command);
        }

        void assertNothingPending() throws IOException {
            socket.setSoTimeout(300);
            try {
                String line = in.readLine();
                assertFalse(line != null, "did not expect a line, but got: " + line);
            } catch (SocketTimeoutException expected) {
                // good: nothing arrived
            } finally {
                socket.setSoTimeout(5000);
            }
        }

        void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // already closed
            }
        }
    }
}
