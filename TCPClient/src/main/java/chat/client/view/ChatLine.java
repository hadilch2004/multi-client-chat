package chat.client.view;

/**
 * One row in the conversation, ready to display.
 *
 * @param kind   decides how the row looks
 * @param sender who wrote it (empty for system rows)
 * @param time   HH:mm:ss as stamped by the server (may be empty)
 * @param text   the message itself
 */
public record ChatLine(Kind kind, String sender, String time, String text) {

    public enum Kind {
        /** A message you sent. */
        OWN,
        /** A message from someone else. */
        OTHER,
        /** Notices such as "bob joined the chat". */
        SYSTEM,
        /** Problems, such as "READ-ONLY MODE: you cannot send messages". */
        ERROR
    }

    public static ChatLine system(String time, String text) {
        return new ChatLine(Kind.SYSTEM, "", time, text);
    }

    public static ChatLine error(String text) {
        return new ChatLine(Kind.ERROR, "", "", text);
    }
}
