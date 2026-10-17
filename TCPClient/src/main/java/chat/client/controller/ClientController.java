package chat.client.controller;

import chat.client.model.ChatClient;
import chat.client.model.ClientConfig;
import chat.client.view.ChatLine;
import chat.client.view.ClientView;
import javafx.application.Platform;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The glue between the model and the view. It reacts to the buttons, calls the model off the UI
 * thread, and turns the model's events (which arrive on a background thread) into view updates
 * on the JavaFX thread. Neither the model nor the view knows the other exists.
 */
public final class ClientController implements ChatClient.Listener {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ClientConfig config;
    private final ClientView view;
    private final String address;
    private volatile ChatClient client;

    public ClientController(ClientConfig config, ClientView view) {
        this.config = config;
        this.view = view;
        this.address = config.host() + ":" + config.port();

        view.login().setOnJoin(this::join);
        view.chat().setOnSend(text -> {
            ChatClient c = client;
            if (c != null) {
                c.submit(text);
            }
        });
        view.chat().setOnLeave(this::leaveClicked);
    }

    // ---- joining -----------------------------------------------------------------------------

    private void join(String username) {
        var problem = ChatClient.validateUsername(username);
        if (problem.isPresent()) {
            view.login().showError(problem.get());
            return;
        }
        view.login().clearNotice();
        view.login().setBusy(true);

        ChatClient connection = new ChatClient(this);
        client = connection;
        Thread.ofVirtual().name("chat-connect").start(() -> {
            try {
                ChatClient.JoinResult result = connection.connect(config.host(), config.port(), username);
                Platform.runLater(() -> joinAnswered(connection, result));
            } catch (IOException e) {
                Platform.runLater(() -> joinFailed(describe(e)));
            }
        });
    }

    private void joinAnswered(ChatClient connection, ChatClient.JoinResult result) {
        if (result.outcome() == ChatClient.Outcome.REJECTED) {
            joinFailed(result.message());
            return;
        }
        boolean readOnly = result.outcome() == ChatClient.Outcome.READ_ONLY;

        var chat = view.chat();
        chat.clearMessages();
        chat.setIdentity(result.name(), address, readOnly);
        if (readOnly) {
            chat.addMessage(ChatLine.system(now(), "You joined as " + result.name()
                    + ". You can read everything, but sending is disabled."));
        } else {
            chat.addMessage(ChatLine.system(now(), "Welcome, " + result.name() + "! Type allUsers to see who is online."));
        }
        view.login().setBusy(false);
        view.showChat();
        connection.startListening();
    }

    private void joinFailed(String message) {
        client = null;
        view.login().setBusy(false);
        view.login().showError(message);
        view.login().focusUsername();
    }

    // ---- leaving -----------------------------------------------------------------------------

    private void leaveClicked() {
        ChatClient c = client;
        if (c == null) {
            view.showLogin(); // already disconnected: "Back to login"
        } else {
            c.leave();
        }
    }

    /** Called when the window closes. */
    public void shutdown() {
        ChatClient c = client;
        if (c != null) {
            c.leave();
        }
    }

    // ---- ChatClient.Listener: called from a background thread --------------------------------

    @Override
    public void onChatMessage(String time, String sender, String text) {
        ChatClient c = client;
        boolean mine = c != null && sender.equalsIgnoreCase(c.username());
        ChatLine line = new ChatLine(mine ? ChatLine.Kind.OWN : ChatLine.Kind.OTHER, sender, time, text);
        Platform.runLater(() -> view.chat().addMessage(line));
    }

    @Override
    public void onInfo(String time, String text) {
        Platform.runLater(() -> view.chat().addMessage(ChatLine.system(time, text)));
    }

    @Override
    public void onUserList(List<String> users) {
        String text = "Online users (" + users.size() + "): " + String.join(", ", users);
        Platform.runLater(() -> view.chat().addMessage(ChatLine.system(now(), text)));
    }

    @Override
    public void onServerError(String code, String message) {
        Platform.runLater(() -> view.chat().addMessage(ChatLine.error(message)));
    }

    @Override
    public void onDisconnected(boolean requestedByUser, String message) {
        Platform.runLater(() -> {
            client = null;
            if (requestedByUser) {
                view.login().showInfo(message);
                view.showLogin();
            } else {
                var chat = view.chat();
                chat.setOnline(false);
                chat.setInputEnabled(false);
                chat.setLeaveText("BACK TO LOGIN");
                chat.addMessage(ChatLine.error(message));
            }
        });
    }

    // ---- helpers -----------------------------------------------------------------------------

    private static String now() {
        return LocalTime.now().format(TIME);
    }

    /** Turns a network exception into something a person can act on. */
    private String describe(IOException e) {
        if (e instanceof ConnectException) {
            return "Can't connect to " + address + ". Is the server running?";
        }
        if (e instanceof UnknownHostException) {
            return "Unknown server address: " + config.host();
        }
        if (e instanceof SocketTimeoutException) {
            return "The server at " + address + " did not answer in time.";
        }
        return "Connection problem: " + e.getMessage();
    }
}
