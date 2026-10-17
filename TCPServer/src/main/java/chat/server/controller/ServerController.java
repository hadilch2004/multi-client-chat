package chat.server.controller;

import chat.server.model.ChatServer;
import chat.server.model.ServerConfig;
import chat.server.model.ServerListener;
import chat.server.view.ServerView;
import javafx.application.Platform;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * The glue between the model and the view. It listens to the model (on server threads),
 * hops over to the JavaFX thread, and updates the view. Neither side knows about the other.
 */
public final class ServerController implements ServerListener {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ServerView view;
    private ChatServer server;

    public ServerController(ServerView view) {
        this.view = view;
    }

    /** Reads the settings file and starts the server. Problems go to the log and the status label. */
    public void start() {
        try {
            ServerConfig config = ServerConfig.load();
            onLog("Settings loaded from " + config.source());
            server = new ChatServer(config, this);
            server.start();
            view.setStatus("Online - port " + server.getPort(), true);
        } catch (IOException | IllegalArgumentException e) {
            onLog("Could not start the server: " + e.getMessage());
            view.setStatus("Not running", false);
        }
    }

    public void stop() {
        if (server != null) {
            server.close();
        }
    }

    // ---- ServerListener: called from server threads ------------------------------------------

    @Override
    public void onLog(String message) {
        String line = "[" + LocalTime.now().format(TIME) + "] " + message;
        Platform.runLater(() -> view.appendLog(line));
    }

    @Override
    public void onUserJoined(String displayName) {
        Platform.runLater(() -> view.addUser(displayName));
    }

    @Override
    public void onUserLeft(String displayName) {
        Platform.runLater(() -> view.removeUser(displayName));
    }
}
