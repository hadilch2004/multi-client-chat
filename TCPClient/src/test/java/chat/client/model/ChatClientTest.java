package chat.client.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the client model against a tiny fake server, so no JavaFX and no real server is needed. */
@Timeout(20)
class ChatClientTest {

    private FakeServer server;
    private RecordingListener events;
    private ChatClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new FakeServer();
        events = new RecordingListener();
        client = new ChatClient(events);
    }

    @AfterEach
    void tearDown() {
        client.close();
        server.close();
    }

    // ---- joining ------------------------------------------------------------------------------

    @Test
    void joinWithUsernameIsWelcomed() throws Exception {
        server.replyToJoin("WELCOME\talice");
        ChatClient.JoinResult result = client.connect("127.0.0.1", server.port(), "alice");

        assertEquals(ChatClient.Outcome.JOINED, result.outcome());
        assertEquals("alice", result.name());
        assertEquals("alice", client.username());
        assertEquals("JOIN\talice", server.nextLineFromClient());
    }

    @Test
    void joinWithoutUsernameIsReadOnly() throws Exception {
        server.replyToJoin("READONLY\tGuest-1 (read-only)");
        ChatClient.JoinResult result = client.connect("127.0.0.1", server.port(), "  ");

        assertEquals(ChatClient.Outcome.READ_ONLY, result.outcome());
        assertEquals("Guest-1 (read-only)", result.name());
        assertTrue(client.isReadOnly());
        assertEquals("JOIN\t", server.nextLineFromClient());
    }

    @Test
    void rejectedUsernameReportsTheServersReason() throws Exception {
        server.replyToJoin("ERROR\tUSERNAME_TAKEN\tThe username 'alice' is already in use");
        ChatClient.JoinResult result = client.connect("127.0.0.1", server.port(), "alice");

        assertEquals(ChatClient.Outcome.REJECTED, result.outcome());
        assertEquals("The username 'alice' is already in use", result.message());
    }

    @Test
    void connectingToNothingFails() throws IOException {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        assertThrows(ConnectException.class, () -> client.connect("127.0.0.1", closedPort, "alice"));
    }

    // ---- receiving ---------------------------------------------------------------------------

    @Test
    void serverLinesBecomeListenerEvents() throws Exception {
        joinAs("alice");

        server.send("MSG\t12:00:01\tbob\thello\\nsecond line");
        server.send("INFO\t12:00:02\tbob left the chat");
        server.send("USERS\talice\tbob\tcarol");
        server.send("ERROR\tREAD_ONLY\tnope");

        assertEquals("MSG|12:00:01|bob|hello\nsecond line", events.next());
        assertEquals("INFO|12:00:02|bob left the chat", events.next());
        assertEquals("USERS|alice,bob,carol", events.next());
        assertEquals("ERROR|READ_ONLY|nope", events.next());
    }

    // ---- sending -----------------------------------------------------------------------------

    @Test
    void chatTextIsSentAsAMessage() throws Exception {
        joinAs("alice");
        client.submit("hello everyone");
        client.submit("two\nlines");

        assertEquals("MSG\thello everyone", server.nextLineFromClient());
        assertEquals("MSG\ttwo\\nlines", server.nextLineFromClient());
    }

    @Test
    void allUsersIsSentAsACommandNotAMessage() throws Exception {
        joinAs("alice");
        client.submit("allUsers");
        client.submit("ALLUSERS");

        assertEquals("ALLUSERS", server.nextLineFromClient());
        assertEquals("ALLUSERS", server.nextLineFromClient());
    }

    @Test
    void blankInputSendsNothing() throws Exception {
        joinAs("alice");
        client.submit("   ");
        client.submit("hi");
        assertEquals("MSG\thi", server.nextLineFromClient());
    }

    @Test
    void readOnlyClientCannotSend() throws Exception {
        server.replyToJoin("READONLY\tGuest-1 (read-only)");
        client.connect("127.0.0.1", server.port(), "");
        server.nextLineFromClient(); // JOIN
        client.startListening();

        client.submit("can I talk?");
        assertTrue(events.next().startsWith("ERROR|READ_ONLY|"));

        client.submit("allUsers"); // commands still work
        assertEquals("ALLUSERS", server.nextLineFromClient());
    }

    // ---- leaving -----------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"bye", "end", "BYE", "End"})
    void byeAndEndLeaveTheChat(String word) throws Exception {
        joinAs("alice");
        client.submit(word);

        assertEquals("LEAVE", server.nextLineFromClient());
        server.send("BYE");
        assertEquals("DISCONNECTED|byUser=true|You left the chat.", events.next());
    }

    @Test
    void leaveButtonTellsTheServerAndClosesTheConnection() throws Exception {
        joinAs("alice");
        client.leave();

        assertEquals("LEAVE", server.nextLineFromClient());
        server.send("BYE");
        assertEquals("DISCONNECTED|byUser=true|You left the chat.", events.next());
    }

    @Test
    void leaveStillClosesWhenTheServerNeverAnswers() throws Exception {
        joinAs("alice");
        client.leave();
        assertEquals("LEAVE", server.nextLineFromClient());
        // the fake server stays silent; the client gives up waiting after a moment
        assertEquals("DISCONNECTED|byUser=true|You left the chat.", events.next());
    }

    @Test
    void serverGoingAwayIsReportedOnce() throws Exception {
        joinAs("alice");
        server.dropClient();

        String event = events.next();
        assertTrue(event.startsWith("DISCONNECTED|byUser=false|"), event);
        assertNull(events.nextOrNull(300), "disconnect must be reported only once");
    }

    // ---- helpers -----------------------------------------------------------------------------

    private void joinAs(String name) throws Exception {
        server.replyToJoin("WELCOME\t" + name);
        client.connect("127.0.0.1", server.port(), name);
        assertEquals("JOIN\t" + name, server.nextLineFromClient());
        client.startListening();
    }

    private static final class RecordingListener implements ChatClient.Listener {
        private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

        @Override
        public void onChatMessage(String time, String sender, String text) {
            queue.add("MSG|" + time + "|" + sender + "|" + text);
        }

        @Override
        public void onInfo(String time, String text) {
            queue.add("INFO|" + time + "|" + text);
        }

        @Override
        public void onUserList(List<String> users) {
            queue.add("USERS|" + String.join(",", users));
        }

        @Override
        public void onServerError(String code, String message) {
            queue.add("ERROR|" + code + "|" + message);
        }

        @Override
        public void onDisconnected(boolean requestedByUser, String message) {
            queue.add("DISCONNECTED|byUser=" + requestedByUser + "|" + message);
        }

        String next() throws InterruptedException {
            String event = nextOrNull(5000);
            if (event == null) {
                throw new AssertionError("no event arrived within 5 seconds");
            }
            return event;
        }

        String nextOrNull(long millis) throws InterruptedException {
            return queue.poll(millis, TimeUnit.MILLISECONDS);
        }
    }

    /** A stand-in for the real server: accepts one client, records its lines, sends what the test says. */
    private static final class FakeServer {
        private final ServerSocket serverSocket;
        private final BlockingQueue<String> fromClient = new LinkedBlockingQueue<>();
        private volatile PrintWriter toClient;
        private volatile Socket clientSocket;
        private volatile String joinReply;

        FakeServer() throws IOException {
            serverSocket = new ServerSocket(0, 5, java.net.InetAddress.getLoopbackAddress());
            Thread.ofVirtual().start(this::serve);
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        /** What to answer when the client sends JOIN. */
        void replyToJoin(String reply) {
            joinReply = reply;
        }

        private void serve() {
            try {
                clientSocket = serverSocket.accept();
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
                toClient = new PrintWriter(clientSocket.getOutputStream(), true, StandardCharsets.UTF_8);
                String line;
                while ((line = in.readLine()) != null) {
                    fromClient.add(line);
                    if (line.startsWith("JOIN")) {
                        send(joinReply);
                    }
                }
            } catch (IOException ignored) {
                // test finished
            }
        }

        void send(String line) {
            toClient.print(line + "\n");
            toClient.flush();
        }

        String nextLineFromClient() throws InterruptedException {
            String line = fromClient.poll(5, TimeUnit.SECONDS);
            if (line == null) {
                throw new AssertionError("the client sent nothing within 5 seconds");
            }
            return line;
        }

        void dropClient() throws IOException {
            clientSocket.close();
        }

        void close() {
            try {
                serverSocket.close();
                if (clientSocket != null) {
                    clientSocket.close();
                }
            } catch (IOException ignored) {
                // already closed
            }
        }
    }
}
