package chat.server.model;

/**
 * How the model tells the outside world (the UI, a test, a console logger) what is happening.
 * Methods are called from server threads, never from the JavaFX thread, so a UI must hop
 * threads itself. The model does not know or care who is listening.
 */
public interface ServerListener {

    /** A line for the activity log, e.g. "Server Started" or "Welcome hadil". */
    default void onLog(String message) {
    }

    /** A user (or read-only guest) finished joining. {@code displayName} is what lists show. */
    default void onUserJoined(String displayName) {
    }

    /** A user (or guest) is gone. */
    default void onUserLeft(String displayName) {
    }
}
