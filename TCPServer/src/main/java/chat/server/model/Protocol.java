package chat.server.model;

/**
 * The small text protocol spoken between TCPClient and TCPServer.
 * One command per line; the fields of a line are separated by a TAB character.
 *
 * <pre>
 * client -> server                       server -> client
 * JOIN  [username]    (empty = read-only) WELCOME  username
 * MSG   text                              READONLY guestName
 * ALLUSERS                                ERROR    code  message
 * LEAVE                                   MSG      time  sender  text
 *                                         INFO     time  text
 *                                         USERS    name1 name2 ...
 *                                         BYE
 * </pre>
 *
 * Free text is escaped so it always fits on one line (see {@link #escape}).
 * The client has its own copy of this class so the two projects stay independent.
 */
public final class Protocol {
    private Protocol() {
    }

    public static final char SEP = '\t';

    // client -> server
    public static final String JOIN = "JOIN";
    public static final String MSG = "MSG";
    public static final String ALL_USERS = "ALLUSERS";
    public static final String LEAVE = "LEAVE";

    // server -> client
    public static final String WELCOME = "WELCOME";
    public static final String READ_ONLY = "READONLY";
    public static final String ERROR = "ERROR";
    public static final String USERS = "USERS";
    public static final String INFO = "INFO";
    public static final String BYE = "BYE";

    // error codes carried by ERROR lines
    public static final String ERR_USERNAME_TAKEN = "USERNAME_TAKEN";
    public static final String ERR_USERNAME_INVALID = "USERNAME_INVALID";
    public static final String ERR_READ_ONLY = "READ_ONLY";
    public static final String ERR_TOO_LONG = "TOO_LONG";
    public static final String ERR_PROTOCOL = "PROTOCOL";

    /** Builds one protocol line (without the trailing newline). */
    public static String line(String command, String... fields) {
        StringBuilder sb = new StringBuilder(command);
        for (String field : fields) {
            sb.append(SEP).append(field);
        }
        return sb.toString();
    }

    /** Makes free text safe for a single line: backslash, newline and tab are escaped. */
    public static String escape(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                case '\r' -> {
                    // dropped: only \n marks a line break in the protocol
                }
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Reverses {@link #escape}. */
    public static String unescape(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(++i);
                switch (next) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case '\\' -> sb.append('\\');
                    default -> sb.append(c).append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
