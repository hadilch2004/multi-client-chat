package chat.client.view;

import javafx.scene.Parent;
import javafx.scene.layout.StackPane;

/** The client window's content: shows either the login screen or the chat screen. */
public final class ClientView {
    private final StackPane root = new StackPane();
    private final LoginPane loginPane;
    private final ChatPane chatPane = new ChatPane();

    public ClientView(String serverAddress) {
        loginPane = new LoginPane(serverAddress);
        root.getChildren().setAll(loginPane);
    }

    public Parent getRoot() {
        return root;
    }

    public LoginPane login() {
        return loginPane;
    }

    public ChatPane chat() {
        return chatPane;
    }

    public void showLogin() {
        root.getChildren().setAll(loginPane);
        loginPane.focusUsername();
    }

    public void showChat() {
        root.getChildren().setAll(chatPane);
        chatPane.focusInput();
    }
}
