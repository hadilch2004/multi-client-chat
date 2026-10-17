package chat.server.model;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.ReentrantLock;

/**
 * One connected client. {@link #run()} is the loop that lives on that client's own virtual
 * thread: it reads the client's lines and reacts to them until the client leaves.
 */
final class ClientSession implements Runnable {
    private static final int HANDSHAKE_TIMEOUT_MS = 10_000;
    static final int MAX_MESSAGE_LENGTH = 2000;

    private final ChatServer server;
    private final Socket socket;
    private final ReentrantLock writeLock = new ReentrantLock(); // many threads may write to this client
    private BufferedReader in;
    private BufferedWriter out;

    private volatile String username;     // null for a read-only guest
    private volatile String displayName;  // what lists and logs show
    private volatile boolean joined;
    private volatile boolean rejected;    // turned away at the door (already logged)

    ClientSession(ChatServer server, Socket socket) {
        this.server = server;
        this.socket = socket;
    }

    String username() {
        return username;
    }

    String displayName() {
        return displayName;
    }

    boolean isJoined() {
        return joined;
    }

    boolean wasRejected() {
        return rejected;
    }

    boolean isReadOnly() {
        return username == null;
    }

    /** Records who this client is. It only becomes visible to others after {@link #activate()}. */
    void identify(String username, String displayName) {
        this.username = username;
        this.displayName = displayName;
    }

    /** Called after WELCOME/READONLY went out, so broadcasts can never overtake the greeting. */
    void activate() {
        this.joined = true;
    }

    void deactivate() {
        this.joined = false;
    }

    String remoteAddress() {
        return String.valueOf(socket.getRemoteSocketAddress());
    }

    @Override
    public void run() {
        try (socket) {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            socket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
            if (!handshake()) {
                return;
            }
            socket.setSoTimeout(0);

            String line;
            while ((line = in.readLine()) != null) {
                if (!handle(line)) {
                    break;
                }
            }
        } catch (SocketTimeoutException e) {
            server.log("Dropped " + remoteAddress() + ": it did not say who it was in time");
        } catch (IOException e) {
            // the client vanished (window closed, network cut); cleanup happens in finally
        } finally {
            server.sessionEnded(this);
        }
    }

    /** First line must be JOIN [username]. Returns false if the client was turned away. */
    private boolean handshake() throws IOException {
        String line = in.readLine();
        if (line == null) {
            return false;
        }
        String[] parts = line.split("\t", 2);
        if (!Protocol.JOIN.equals(parts[0])) {
            send(Protocol.line(Protocol.ERROR, Protocol.ERR_PROTOCOL, "Say JOIN first"));
            return false;
        }
        String requested = parts.length > 1 ? parts[1].trim() : "";

        if (requested.isEmpty()) {
            String guest = server.admitGuest(this);
            send(Protocol.line(Protocol.READ_ONLY, guest));
            server.announceJoin(this);
            return true;
        }
        if (!ChatServer.isValidUsername(requested)) {
            reject("invalid username", Protocol.ERR_USERNAME_INVALID,
                    "Use 1-20 letters, digits, '_', '.' or '-' (no spaces)");
            return false;
        }
        if (!server.admitUser(this, requested)) {
            reject("username '" + requested + "' is already in use", Protocol.ERR_USERNAME_TAKEN,
                    "The username '" + requested + "' is already in use");
            return false;
        }
        send(Protocol.line(Protocol.WELCOME, requested));
        server.announceJoin(this);
        return true;
    }

    private void reject(String logReason, String code, String messageForClient) {
        rejected = true;
        server.log("Rejected " + remoteAddress() + ": " + logReason);
        send(Protocol.line(Protocol.ERROR, code, messageForClient));
    }

    /** Handles one line from the client. Returns false when the client wants to leave. */
    private boolean handle(String line) {
        String[] parts = line.split("\t", 2);
        switch (parts[0]) {
            case Protocol.MSG -> {
                return handleText(Protocol.unescape(parts.length > 1 ? parts[1] : ""));
            }
            case Protocol.ALL_USERS -> {
                sendUserList();
                return true;
            }
            case Protocol.LEAVE -> {
                send(Protocol.line(Protocol.BYE));
                return false;
            }
            default -> {
                send(Protocol.line(Protocol.ERROR, Protocol.ERR_PROTOCOL, "Unknown command: " + parts[0]));
                return true;
            }
        }
    }

    /**
     * Chat text. The typed commands work here too (so a plain telnet/nc client behaves like the
     * real one): "allUsers" lists who is online, "end" and "bye" disconnect.
     */
    private boolean handleText(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        if (trimmed.equalsIgnoreCase("allUsers")) {
            sendUserList();
            return true;
        }
        if (trimmed.equalsIgnoreCase("end") || trimmed.equalsIgnoreCase("bye")) {
            send(Protocol.line(Protocol.BYE));
            return false;
        }
        if (isReadOnly()) {
            send(Protocol.line(Protocol.ERROR, Protocol.ERR_READ_ONLY,
                    "You are in READ-ONLY MODE and cannot send messages"));
            return true;
        }
        if (text.length() > MAX_MESSAGE_LENGTH) {
            send(Protocol.line(Protocol.ERROR, Protocol.ERR_TOO_LONG,
                    "Messages are limited to " + MAX_MESSAGE_LENGTH + " characters"));
            return true;
        }
        server.broadcastMessage(this, text);
        return true;
    }

    private void sendUserList() {
        String[] names = server.userDisplayNames().toArray(String[]::new);
        send(Protocol.line(Protocol.USERS, names));
    }

    /** Sends one line to this client. A failed write closes the connection; the read loop then cleans up. */
    void send(String line) {
        writeLock.lock();
        try {
            out.write(line);
            out.write('\n');
            out.flush();
        } catch (IOException e) {
            close();
        } finally {
            writeLock.unlock();
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
