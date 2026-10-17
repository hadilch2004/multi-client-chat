package chat.server.view;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * The server window: an activity log on the left, a live list of connected users on the right.
 * Pure presentation. It knows nothing about sockets; the controller feeds it text.
 * All methods must be called on the JavaFX thread.
 */
public final class ServerView {
    private static final int MAX_LOG_LINES = 1000;

    private final GridPane root = new GridPane();
    private final Circle statusDot = new Circle(6);
    private final Label statusLabel = new Label("Starting...");
    private final Label usersTitle = new Label();
    private final ListView<String> logList = new ListView<>();
    private final ListView<String> userList = new ListView<>();

    private final Map<String, String> colorByUser = new HashMap<>();
    private double nextHue = new Random().nextDouble() * 360;

    public ServerView() {
        build();
        updateUserCount();
    }

    public Parent getRoot() {
        return root;
    }

    private void build() {
        root.getStyleClass().add("server-root");
        root.setPadding(new Insets(18));
        root.setHgap(16);
        root.setVgap(10);

        ColumnConstraints logColumn = new ColumnConstraints();
        logColumn.setPercentWidth(62);
        ColumnConstraints usersColumn = new ColumnConstraints();
        usersColumn.setPercentWidth(38);
        root.getColumnConstraints().addAll(logColumn, usersColumn);

        RowConstraints fixed = new RowConstraints();
        RowConstraints fixed2 = new RowConstraints();
        RowConstraints grow = new RowConstraints();
        grow.setVgrow(Priority.ALWAYS);
        root.getRowConstraints().addAll(fixed, fixed2, grow);

        // header: title on the left, status indicator on the right
        Label title = new Label("Chat Server");
        title.getStyleClass().add("title");
        statusDot.getStyleClass().addAll("status-dot", "offline");
        statusLabel.getStyleClass().add("status-text");
        HBox status = new HBox(8, statusDot, statusLabel);
        status.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(status, Priority.ALWAYS);
        HBox header = new HBox(title, status);
        header.setAlignment(Pos.CENTER_LEFT);
        root.add(header, 0, 0, 2, 1);

        // section titles
        Label logTitle = new Label("Activity log");
        logTitle.getStyleClass().add("section-title");
        usersTitle.getStyleClass().add("section-title");
        root.add(logTitle, 0, 1);
        root.add(usersTitle, 1, 1);

        // the two lists
        logList.getStyleClass().add("log-list");
        logList.setFocusTraversable(false);
        logList.setCellFactory(lv -> {
            ListCell<String> cell = new ListCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                }
            };
            cell.setWrapText(true);
            cell.prefWidthProperty().bind(lv.widthProperty().subtract(24));
            return cell;
        });
        root.add(logList, 0, 2);

        userList.getStyleClass().add("user-list");
        userList.setFocusTraversable(false);
        userList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty || name == null) {
                    setText(null);
                    setStyle(null);
                } else {
                    setText(name);
                    setStyle("-fx-background-color: " + colorFor(name) + ";");
                }
            }
        });
        root.add(userList, 1, 2);
        GridPane.setHalignment(usersTitle, HPos.LEFT);
    }

    /** Adds a line to the log and keeps it scrolled to the newest entry. */
    public void appendLog(String line) {
        var items = logList.getItems();
        items.add(line);
        if (items.size() > MAX_LOG_LINES) {
            items.remove(0, items.size() - MAX_LOG_LINES);
        }
        logList.scrollTo(items.size() - 1);
    }

    public void addUser(String name) {
        if (!userList.getItems().contains(name)) {
            userList.getItems().add(name);
        }
        updateUserCount();
    }

    public void removeUser(String name) {
        userList.getItems().remove(name);
        updateUserCount();
    }

    public void setStatus(String text, boolean online) {
        statusLabel.setText(text);
        statusDot.getStyleClass().setAll("status-dot", online ? "online" : "offline");
    }

    private void updateUserCount() {
        usersTitle.setText("Connected users (" + userList.getItems().size() + ")");
    }

    /**
     * Every user gets their own background colour, picked at random the first time they appear
     * and kept afterwards. Hues are spaced by the golden angle so two users never look alike.
     */
    private String colorFor(String name) {
        return colorByUser.computeIfAbsent(name, n -> {
            nextHue = (nextHue + 137.508) % 360;
            Color c = Color.hsb(nextHue, 0.30, 0.98);
            return String.format("#%02x%02x%02x",
                    Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255));
        });
    }
}
