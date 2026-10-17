package chat.client.model;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * The chat client's model: one connection to the server. It has no JavaFX in it; the UI (or a
 * test) hands it a {@link Listener} and calls {@link #connect}, {@link #submit} and {@link #leave}.
 *
 * <p>A ChatClient is used once: connect, chat, leave. For another session create a new one.
 */
public final class ChatClient implements AutoCloseable {
    private static final Pattern USERNAME = Pattern.compile("[\\p{L}\\p{N}_.-]{1,20}");
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int JOIN_TIMEOUT_MS = 10_000;
    private static final int LEAVE_GRACE_MS = 1_500;

    /** Receives what happens on the connection. Called from a background thread, never the JavaFX one. */
    public interface Listener {
        void onChatMessage(String time, String sender, String text);

        void onInfo(String time, String text);

        void onUserList(List<String> users);

        void onServerError(String code, String message);

        /** The connection is over (reported exactly once). {@code requestedByUser} is true after leave()/bye/end. */
        void onDisconnected(boolean requestedByUser, String message);
    }

    public enum Outcome {JOINED, READ_ONLY, REJECTED}

    /**
     * The server's answer to JOIN.
     *
     * @param name    the username, or the guest name when read-only (null when rejected)
     * @param message why the server said no (only when rejected)
     */
    public record JoinResult(Outcome outcome, String name, String message) {
    }

    private final Listener listener;
    private final ReentrantLock writeLock = new ReentrantLock();
    private final AtomicBoolean disconnectReported = new AtomicBoolean();

    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;

    private volatile boolean leaving;
    private volatile boolean readOnly;
    private volatile String username;

    public ChatClient(Listener listener) {
        this.listener = listener;
    }

    /** Returns an error message when the username is not allowed; an empty username (read-only) is fine. */
    public static Optional<String> validateUsername(String username) {
        String name = username == null ? "" : username.trim();
        if (name.isEmpty() || USERNAME.matcher(name).matches()) {
            return Optional.empty();
        }
        return Optional.of("Use 1-20 letters, digits, '_', '.' or '-' (no spaces).");
    }

    /**
     * Connects and asks to join. Blocks until the server answers, so call it off the UI thread.
     * Nothing is delivered to the listener until {@link #startListening()} is called, so the UI
     * can get ready first.
     *
     * @param requestedUsername empty or null means "join read-only"
     * @throws IOException when the server cannot be reached or misbehaves
     */
    public JoinResult connect(String host, int port, String requestedUsername) throws IOException {
        String name = requestedUsername == null ? "" : requestedUsername.trim();
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            s.setSoTimeout(JOIN_TIMEOUT_MS);
            socket = s;
            in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));

            sendOrThrow(Protocol.line(Protocol.JOIN, name));
            String reply = in.readLine();
            if (reply == null) {
                throw new IOException("The server closed the connection.");
            }
            String[] f = reply.split("\t", -1);
            switch (f[0]) {
                case Protocol.WELCOME -> {
                    s.setSoTimeout(0);
                    username = f[1];
                    return new JoinResult(Outcome.JOINED, f[1], null);
                }
                case Protocol.READ_ONLY -> {
                    s.setSoTimeout(0);
                    readOnly = true;
                    return new JoinResult(Outcome.READ_ONLY, f[1], null);
                }
                case Protocol.ERROR -> {
                    closeSocket();
                    return new JoinResult(Outcome.REJECTED, null, f.length > 2 ? f[2] : "The server refused the connection.");
                }
                default -> throw new IOException("Unexpected answer from the server: " + reply);
            }
        } catch (IOException e) {
            closeSocket();
            throw e;
        }
    }

    /** Starts delivering the server's messages to the listener. Call once, after a successful connect. */
    public void startListening() {
        Thread.ofVirtual().name("chat-reader").start(this::readLoop);
    }

    public String username() {
        return username;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    /** Handles whatever the user typed: a command ("allUsers", "end", "bye") or a chat message. */
    public void submit(String input) {
        switch (InputCommand.parse(input)) {
            case EMPTY -> {
                // nothing to do
            }
            case ALL_USERS -> send(Protocol.line(Protocol.ALL_USERS));
            case LEAVE -> leave();
            case MESSAGE -> {
                if (readOnly) {
                    listener.onServerError(Protocol.ERR_READ_ONLY, "READ-ONLY MODE: you cannot send messages.");
                } else {
                    send(Protocol.line(Protocol.MSG, Protocol.escape(input)));
                }
            }
        }
    }

    /** Tells the server we are leaving and closes the connection. */
    public void leave() {
        if (leaving) {
            return;
        }
        leaving = true;
        send(Protocol.line(Protocol.LEAVE));
        // The server answers BYE and the read loop closes the socket. If it never does, close it ourselves.
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(LEAVE_GRACE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            closeSocket();
        });
    }

    /** Drops the connection without a goodbye. Prefer {@link #leave()}. */
    @Override
    public void close() {
        leaving = true;
        closeSocket();
    }

    // ---- reading -----------------------------------------------------------------------------

    private void readLoop() {
        String reason = "The server closed the connection.";
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (!dispatch(line)) {
                    break;
                }
            }
        } catch (IOException e) {
            if (!leaving) {
                reason = "Connection lost: " + e.getMessage();
            }
        } finally {
            closeSocket();
            if (disconnectReported.compareAndSet(false, true)) {
                listener.onDisconnected(leaving, leaving ? "You left the chat." : reason);
            }
        }
    }

    /** Passes one server line to the listener. Returns false when the connection is over. */
    private boolean dispatch(String line) {
        String[] f = line.split("\t", -1);
        switch (f[0]) {
            case Protocol.MSG -> {
                if (f.length >= 4) {
                    listener.onChatMessage(f[1], f[2], Protocol.unescape(f[3]));
                }
            }
            case Protocol.INFO -> {
                if (f.length >= 3) {
                    listener.onInfo(f[1], Protocol.unescape(f[2]));
                }
            }
            case Protocol.USERS -> listener.onUserList(Arrays.asList(f).subList(1, f.length));
            case Protocol.ERROR -> listener.onServerError(f.length > 1 ? f[1] : "", f.length > 2 ? f[2] : "");
            case Protocol.BYE -> {
                return false;
            }
            default -> {
                // an unknown line: ignore it so a newer server can add features
            }
        }
        return true;
    }

    // ---- writing -----------------------------------------------------------------------------

    private void sendOrThrow(String line) throws IOException {
        writeLock.lock();
        try {
            out.write(line);
            out.write('\n');
            out.flush();
        } finally {
            writeLock.unlock();
        }
    }

    /** A failed write closes the socket; the read loop then reports the disconnect. */
    private void send(String line) {
        try {
            sendOrThrow(line);
        } catch (IOException e) {
            closeSocket();
        }
    }

    private void closeSocket() {
        Socket s = socket;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // already closed
            }
        }
    }
}
