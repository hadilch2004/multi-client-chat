package chat.client.view;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

import java.util.function.Consumer;

/**
 * The first screen: pick a username. Leaving it empty joins in READ-ONLY MODE.
 * Pure presentation; the controller decides what "Join" does.
 */
public final class LoginPane extends StackPane {
    private final TextField usernameField = new TextField();
    private final Button joinButton = new Button();
    private final Label noticeLabel = new Label();
    private Consumer<String> onJoin = name -> {
    };
    private boolean busy;

    public LoginPane(String serverAddress) {
        getStyleClass().add("login-root");

        GridPane card = new GridPane();
        card.getStyleClass().add("card");
        card.setVgap(10);
        card.setMaxWidth(420);
        card.setMaxHeight(USE_PREF_SIZE);
        ColumnConstraints column = new ColumnConstraints();
        column.setHgrow(Priority.ALWAYS);
        card.getColumnConstraints().add(column);

        Label title = new Label("Join the chat");
        title.getStyleClass().add("card-title");
        Label server = new Label("Server: " + serverAddress);
        server.getStyleClass().add("muted");

        Label fieldLabel = new Label("Username");
        fieldLabel.getStyleClass().add("field-label");
        usernameField.setPromptText("e.g. hadil");
        usernameField.setOnAction(e -> submit());

        Label hint = new Label("Leave it empty to join in READ-ONLY MODE: you will see the conversation but cannot send messages.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);

        joinButton.getStyleClass().add("primary-button");
        joinButton.setMaxWidth(Double.MAX_VALUE);
        joinButton.setDefaultButton(true);
        joinButton.setOnAction(e -> submit());
        usernameField.textProperty().addListener((obs, old, now) -> updateButtonText());
        updateButtonText();

        noticeLabel.setWrapText(true);
        noticeLabel.setVisible(false);
        noticeLabel.setManaged(false);

        card.add(title, 0, 0);
        card.add(server, 0, 1);
        card.add(fieldLabel, 0, 2);
        card.add(usernameField, 0, 3);
        card.add(hint, 0, 4);
        card.add(joinButton, 0, 5);
        card.add(noticeLabel, 0, 6);
        GridPane.setMargin(fieldLabel, new Insets(14, 0, 0, 0));
        GridPane.setMargin(joinButton, new Insets(8, 0, 0, 0));
        GridPane.setHalignment(title, HPos.LEFT);

        StackPane.setAlignment(card, Pos.CENTER);
        getChildren().add(card);
        setPadding(new Insets(24));
    }

    public void setOnJoin(Consumer<String> onJoin) {
        this.onJoin = onJoin;
    }

    /** Shows a red problem message under the button. */
    public void showError(String message) {
        showNotice(message, "error");
    }

    /** Shows a neutral message under the button (for example "You left the chat."). */
    public void showInfo(String message) {
        showNotice(message, "info");
    }

    public void clearNotice() {
        noticeLabel.setVisible(false);
        noticeLabel.setManaged(false);
    }

    /** While connecting: lock the form so the button cannot be pressed twice. */
    public void setBusy(boolean busy) {
        this.busy = busy;
        usernameField.setDisable(busy);
        joinButton.setDisable(busy);
        updateButtonText();
    }

    public void focusUsername() {
        usernameField.requestFocus();
    }

    private void submit() {
        if (!busy) {
            onJoin.accept(usernameField.getText().trim());
        }
    }

    private void updateButtonText() {
        if (busy) {
            joinButton.setText("Connecting...");
        } else if (usernameField.getText().isBlank()) {
            joinButton.setText("Join read-only");
        } else {
            joinButton.setText("Join chat");
        }
    }

    private void showNotice(String message, String styleClass) {
        noticeLabel.getStyleClass().setAll("notice", styleClass);
        noticeLabel.setText(message);
        noticeLabel.setVisible(true);
        noticeLabel.setManaged(true);
    }
}
