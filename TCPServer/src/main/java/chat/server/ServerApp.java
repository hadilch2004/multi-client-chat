package chat.server;

import chat.server.controller.ServerController;
import chat.server.view.ServerView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** The JavaFX application: builds the window and starts the server when it opens. */
public class ServerApp extends Application {
    private ServerController controller;

    @Override
    public void start(Stage stage) {
        ServerView view = new ServerView();
        controller = new ServerController(view);

        Scene scene = new Scene(view.getRoot(), 880, 580);
        scene.getStylesheets().add(getClass().getResource("/server.css").toExternalForm());

        stage.setTitle("Chat Server");
        stage.setMinWidth(640);
        stage.setMinHeight(420);
        stage.setScene(scene);
        stage.show();

        controller.start();
    }

    /** Called by JavaFX when the window closes: shut the server down cleanly. */
    @Override
    public void stop() {
        if (controller != null) {
            controller.stop();
        }
    }
}
