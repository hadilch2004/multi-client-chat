package chat.client;

import chat.client.controller.ClientController;
import chat.client.model.ClientConfig;
import chat.client.view.ClientView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.io.IOException;

/** The JavaFX application: works out the server address, builds the window, wires it up. */
public class ClientApp extends Application {
    private ClientController controller;

    @Override
    public void start(Stage stage) {
        ClientConfig config;
        try {
            config = ClientConfig.resolve(getParameters().getRaw().toArray(String[]::new));
        } catch (IOException | IllegalArgumentException e) {
            new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait();
            Platform.exit();
            return;
        }

        ClientView view = new ClientView(config.host() + ":" + config.port());
        controller = new ClientController(config, view);

        Scene scene = new Scene(view.getRoot(), 760, 660);
        scene.getStylesheets().add(getClass().getResource("/client.css").toExternalForm());

        stage.setTitle("Chat Client");
        stage.setMinWidth(560);
        stage.setMinHeight(480);
        stage.setScene(scene);
        stage.show();
        view.login().focusUsername();
    }

    /** Called by JavaFX when the window closes: say goodbye to the server. */
    @Override
    public void stop() {
        if (controller != null) {
            controller.shutdown();
        }
    }
}
