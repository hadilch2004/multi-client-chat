package chat.client.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.util.function.Consumer;

/**
 * The chat screen: header with the Online indicator, the conversation, and the message box
 * with SEND and LEAVE. Pure presentation; the controller decides what the buttons do.
 */
public final class ChatPane extends GridPane {
    private final Label identityLabel = new Label();
    private final Label serverLabel = new Label();
    private final Circle statusDot = new Circle(6);
    private final Label statusLabel = new Label("Online");
    private final Label readOnlyBanner = new Label();
    private final ListView<ChatLine> messages = new ListView<>();
    private final TextArea input = new TextArea();
    private final Button sendButton = new Button("SEND");
    private final Button leaveButton = new Button("LEAVE");

    private Consumer<String> onSend = text -> {
    };
    private Runnable onLeave = () -> {
    };

    public ChatPane() {
        getStyleClass().add("chat-root");
        ColumnConstraints column = new ColumnConstraints();
        column.setHgrow(Priority.ALWAYS);
        getColumnConstraints().add(column);

        RowConstraints header = new RowConstraints();
        RowConstraints banner = new RowConstraints();
        RowConstraints conversation = new RowConstraints();
        conversation.setVgrow(Priority.ALWAYS);
        RowConstraints composer = new RowConstraints();
        getRowConstraints().addAll(header, banner, conversation, composer);

        add(buildHeader(), 0, 0);
        add(buildBanner(), 0, 1);
        add(buildConversation(), 0, 2);
        add(buildComposer(), 0, 3);
    }

    private GridPane buildHeader() {
        GridPane bar = new GridPane();
        bar.getStyleClass().add("chat-header");
        bar.setHgap(12);
        ColumnConstraints left = new ColumnConstraints();
        left.setHgrow(Priority.ALWAYS);
        bar.getColumnConstraints().addAll(left, new ColumnConstraints());

        identityLabel.getStyleClass().add("identity");
        serverLabel.getStyleClass().add("muted");
        VBox who = new VBox(2, identityLabel, serverLabel);

        statusDot.getStyleClass().addAll("status-dot", "online");
        statusLabel.getStyleClass().add("status-text");
        HBox status = new HBox(8, statusDot, statusLabel);
        status.setAlignment(Pos.CENTER_RIGHT);

        bar.add(who, 0, 0);
        bar.add(status, 1, 0);
        return bar;
    }

    private Label buildBanner() {
        readOnlyBanner.getStyleClass().add("readonly-banner");
        readOnlyBanner.setText("READ-ONLY MODE  -  you can read but not send. LEAVE and rejoin with a username to chat.");
        readOnlyBanner.setWrapText(true);
        readOnlyBanner.setMinHeight(Region.USE_PREF_SIZE); // never cut the text off with "..."
        readOnlyBanner.setMaxWidth(Double.MAX_VALUE);
        readOnlyBanner.setVisible(false);
        readOnlyBanner.setManaged(false);
        return readOnlyBanner;
    }

    private ListView<ChatLine> buildConversation() {
        messages.getStyleClass().add("message-list");
        messages.setFocusTraversable(false);
        messages.setSelectionModel(null);
        messages.setCellFactory(lv -> new MessageCell(lv));
        return messages;
    }

    private GridPane buildComposer() {
        GridPane box = new GridPane();
        box.getStyleClass().add("composer");
        box.setHgap(12);
        box.setVgap(8);
        ColumnConstraints grow = new ColumnConstraints();
        grow.setHgrow(Priority.ALWAYS);
        box.getColumnConstraints().addAll(grow, new ColumnConstraints());

        input.setPromptText("Type a message... Enter sends, Shift+Enter adds a line");
        input.setPrefRowCount(3);
        input.setWrapText(true);
        // Plain Enter sends; Shift+Enter falls through to the default behaviour (a new line).
        input.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER && !e.isShiftDown()) {
                e.consume();
                send();
            }
        });

        sendButton.getStyleClass().add("primary-button");
        sendButton.setMaxWidth(Double.MAX_VALUE);
        sendButton.setOnAction(e -> send());
        leaveButton.getStyleClass().add("danger-button");
        leaveButton.setMaxWidth(Double.MAX_VALUE);
        leaveButton.setOnAction(e -> onLeave.run());
        VBox buttons = new VBox(8, sendButton, leaveButton);
        buttons.setMinWidth(110);

        Label commands = new Label("Commands:  allUsers  lists who is online   |   bye  or  end  leaves the chat");
        commands.getStyleClass().add("muted");

        box.add(input, 0, 0);
        box.add(buttons, 1, 0);
        box.add(commands, 0, 1, 2, 1);
        return box;
    }

    // ---- what the controller can do ------------------------------------------------------------

    public void setOnSend(Consumer<String> onSend) {
        this.onSend = onSend;
    }

    public void setOnLeave(Runnable onLeave) {
        this.onLeave = onLeave;
    }

    /** Sets up the header and the input for a new session. */
    public void setIdentity(String username, String serverAddress, boolean readOnly) {
        identityLabel.setText(readOnly ? username : username + " (you)");
        serverLabel.setText("Connected to " + serverAddress);
        readOnlyBanner.setVisible(readOnly);
        readOnlyBanner.setManaged(readOnly);
        input.setPromptText(readOnly
                ? "READ-ONLY MODE: sending is disabled"
                : "Type a message... Enter sends, Shift+Enter adds a line");
        setInputEnabled(!readOnly);
        leaveButton.setText("LEAVE");
        setOnline(true);
    }

    public void setOnline(boolean online) {
        statusLabel.setText(online ? "Online" : "Offline");
        statusDot.getStyleClass().setAll("status-dot", online ? "online" : "offline");
    }

    public void setInputEnabled(boolean enabled) {
        input.setDisable(!enabled);
        sendButton.setDisable(!enabled);
        if (enabled) {
            input.requestFocus();
        }
    }

    public void setLeaveText(String text) {
        leaveButton.setText(text);
    }

    public void addMessage(ChatLine line) {
        messages.getItems().add(line);
        messages.scrollTo(messages.getItems().size() - 1);
    }

    public void clearMessages() {
        messages.getItems().clear();
        input.clear();
    }

    public void focusInput() {
        if (!input.isDisabled()) {
            input.requestFocus();
        }
    }

    private void send() {
        String text = input.getText();
        if (text.isBlank()) {
            return;
        }
        input.clear();
        onSend.accept(text.stripTrailing());
    }

    /** Draws one row as a chat bubble: yours on the right, others on the left, notices centred. */
    private static final class MessageCell extends ListCell<ChatLine> {
        private final Label header = new Label();
        private final Label body = new Label();
        private final VBox bubble = new VBox(3, header, body);
        private final HBox row = new HBox(bubble);

        MessageCell(ListView<ChatLine> list) {
            header.getStyleClass().add("msg-header");
            body.getStyleClass().add("msg-body");
            body.setWrapText(true);
            bubble.setMaxWidth(460);
            // Wrap long messages to the width of the list instead of stretching it sideways.
            row.prefWidthProperty().bind(list.widthProperty().subtract(36));
            row.setPadding(new Insets(0));
            setText(null);
        }

        @Override
        protected void updateItem(ChatLine line, boolean empty) {
            super.updateItem(line, empty);
            if (empty || line == null) {
                setGraphic(null);
                return;
            }
            String kind = line.kind().name().toLowerCase();
            bubble.getStyleClass().setAll("bubble", kind);

            boolean notice = line.kind() == ChatLine.Kind.SYSTEM || line.kind() == ChatLine.Kind.ERROR;
            header.setVisible(!notice);
            header.setManaged(!notice);

            if (notice) {
                row.setAlignment(Pos.CENTER);
                body.setText(line.time().isEmpty() ? line.text() : line.time() + "   " + line.text());
            } else {
                row.setAlignment(line.kind() == ChatLine.Kind.OWN ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                header.setText((line.kind() == ChatLine.Kind.OWN ? "You" : line.sender()) + "  -  " + line.time());
                body.setText(line.text());
            }
            setGraphic(row);
        }
    }
}
