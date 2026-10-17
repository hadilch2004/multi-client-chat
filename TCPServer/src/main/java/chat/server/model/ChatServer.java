package chat.server.model;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * The chat server's model: accepts TCP connections, keeps track of who is online and
 * distributes messages. It has no JavaFX in it; anything that wants to follow along
 * (the UI, a test) registers a {@link ServerListener}.
 *
 * <p>Every client gets its own virtual thread, so thousands of idle connections cost almost nothing.
 */
public final class ChatServer implements AutoCloseable {
    private static final Pattern USERNAME = Pattern.compile("[\\p{L}\\p{N}_.-]{1,20}");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ServerConfig config;
    private final ServerListener listener;

    private final List<ClientSession> sessions = new CopyOnWriteArrayList<>();           // everyone connected
    private final Map<String, ClientSession> usersByName = new ConcurrentHashMap<>();    // lower-case name -> owner
    private final ExecutorService sessionThreads = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger guestCounter = new AtomicInteger();

    private volatile ServerSocket serverSocket;
    private volatile boolean running;

    public ChatServer(ServerConfig config, ServerListener listener) {
        this.config = config;
        this.listener = listener;
    }

    /** Opens the port and starts accepting clients in the background. */
    public void start() throws IOException {
        ServerSocket ss = new ServerSocket();
        ss.setReuseAddress(true);
        ss.bind(new InetSocketAddress(InetAddress.getByName(config.host()), config.port()));
        serverSocket = ss;
        running = true;
        log("Server Started on " + config.host() + ":" + ss.getLocalPort());
        Thread.ofVirtual().name("acceptor").start(this::acceptLoop);
    }

    /** The port actually in use (differs from the config when the config says 0). */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public boolean isRunning() {
        return running;
    }

    private void acceptLoop() {
        ServerSocket ss = serverSocket;
        while (running) {
            log("Waiting for Client...");
            try {
                Socket socket = ss.accept();
                ClientSession session = new ClientSession(this, socket);
                sessions.add(session);
                log("Client connected from " + session.remoteAddress());
                sessionThreads.submit(session);
            } catch (IOException e) {
                if (!running || ss.isClosed()) {
                    break;
                }
                log("Could not accept a client: " + e.getMessage());
            }
        }
    }

    // ---- called by ClientSession ------------------------------------------------------------

    static boolean isValidUsername(String name) {
        return USERNAME.matcher(name).matches();
    }

    /** Reserves a username. Returns false when someone already has it (case-insensitive). */
    boolean admitUser(ClientSession session, String username) {
        if (usersByName.putIfAbsent(username.toLowerCase(Locale.ROOT), session) != null) {
            return false;
        }
        session.identify(username, username);
        return true;
    }

    /** Gives a client that joined without a username a guest name; guests can only read. */
    String admitGuest(ClientSession session) {
        String name = "Guest-" + guestCounter.incrementAndGet() + " (read-only)";
        session.identify(null, name);
        return name;
    }

    /** Makes the session visible to everyone: the welcome line, the user list and the others' notice. */
    void announceJoin(ClientSession session) {
        session.activate();
        String name = session.displayName();
        log("Welcome " + name);
        listener.onUserJoined(name);
        broadcastExcept(session, infoLine(session.isReadOnly()
                ? name + " is watching the chat"
                : name + " joined the chat"));
    }

    void sessionEnded(ClientSession session) {
        sessions.remove(session);
        if (session.username() != null) {
            usersByName.remove(session.username().toLowerCase(Locale.ROOT), session);
        }
        if (session.isJoined()) {
            session.deactivate();
            String name = session.displayName();
            log("Goodbye " + name);
            listener.onUserLeft(name);
            broadcast(infoLine(name + " left the chat"));
        } else if (!session.wasRejected()) {
            log("Client " + session.remoteAddress() + " disconnected before joining");
        }
        session.close();
    }

    /** Stamps a chat message with the sender and the time, then sends it to every active connection. */
    void broadcastMessage(ClientSession sender, String text) {
        String time = now();
        log("Message from " + sender.username() + ": " + text.replace("\n", " / "));
        broadcast(Protocol.line(Protocol.MSG, time, sender.username(), Protocol.escape(text)));
    }

    List<String> userDisplayNames() {
        return sessions.stream()
                .filter(ClientSession::isJoined)
                .map(ClientSession::displayName)
                .toList();
    }

    void log(String message) {
        listener.onLog(message);
    }

    // ---- sending -----------------------------------------------------------------------------

    private void broadcast(String line) {
        for (ClientSession s : sessions) {
            if (s.isJoined()) {
                s.send(line);
            }
        }
    }

    private void broadcastExcept(ClientSession except, String line) {
        for (ClientSession s : sessions) {
            if (s != except && s.isJoined()) {
                s.send(line);
            }
        }
    }

    private static String infoLine(String text) {
        return Protocol.line(Protocol.INFO, now(), Protocol.escape(text));
    }

    private static String now() {
        return LocalTime.now().format(TIME);
    }

    /** Stops accepting, tells the clients, and closes every connection. */
    @Override
    public void close() {
        if (!running) {
            return;
        }
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // already closed
        }
        String goodbye = infoLine("The server is shutting down");
        for (ClientSession s : sessions) {
            if (s.isJoined()) {
                s.send(goodbye);
                s.send(Protocol.line(Protocol.BYE));
            }
            s.close();
        }
        sessionThreads.shutdownNow();
        log("Server Stopped");
    }
}
