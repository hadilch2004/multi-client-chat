package chat.client.model;

/** What a line typed in the message box means. */
public enum InputCommand {
    /** Nothing but spaces: ignored. */
    EMPTY,
    /** "allUsers": ask the server who is online. */
    ALL_USERS,
    /** "end" or "bye": disconnect. */
    LEAVE,
    /** Anything else is a chat message. */
    MESSAGE;

    public static InputCommand parse(String input) {
        String text = input == null ? "" : input.trim();
        if (text.isEmpty()) {
            return EMPTY;
        }
        if (text.equalsIgnoreCase("allUsers")) {
            return ALL_USERS;
        }
        if (text.equalsIgnoreCase("end") || text.equalsIgnoreCase("bye")) {
            return LEAVE;
        }
        return MESSAGE;
    }
}
