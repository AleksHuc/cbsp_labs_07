package cbsp.lab07;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/** Recent audio responses for the live browser view. The CSV is written separately. */
final class BrowserAudioRequests {
    private static final Pattern VIEW = Pattern.compile("view=([0-9a-f]{32})");
    private static final int HISTORY_SIZE = 128;
    private final Deque<Transfer> recent = new ArrayDeque<>();

    static String view(String query) {
        if (query == null) {
            return null;
        }
        var match = VIEW.matcher(query);
        return match.matches() ? match.group(1) : null;
    }

    synchronized Transfer start(String view, int id, String method, String range, int status, String contentRange) {
        var transfer = new Transfer(view, id, method, range, status, contentRange);
        recent.addFirst(transfer);
        if (recent.size() > HISTORY_SIZE) {
            recent.removeLast();
        }
        return transfer;
    }

    synchronized String json(String view) {
        var rows = new StringJoiner(",", "[", "]");
        for (var transfer : recent) {
            if (transfer.view.equals(view)) {
                rows.add(transfer.json());
            }
        }
        return rows.toString();
    }

    static final class Transfer {
        private final String view;
        private final int id;
        private final String method;
        private final String range;
        private final int status;
        private final String contentRange;
        private volatile String state = "Receiving";

        private Transfer(String view, int id, String method, String range, int status, String contentRange) {
            this.view = view;
            this.id = id;
            this.method = method;
            this.range = range;
            this.status = status;
            this.contentRange = contentRange;
        }

        void finish(boolean complete) {
            state = complete ? "Complete" : "Ended early";
        }

        private String json() {
            return "{\"id\":" + id + ",\"method\":" + quoted(method) + ",\"range\":" + quoted(range)
                    + ",\"status\":" + status + ",\"contentRange\":" + quoted(contentRange)
                    + ",\"state\":" + quoted(state) + "}";
        }
    }

    private static String quoted(String value) {
        if (value == null) {
            return "null";
        }
        var json = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < ' ') {
                json.append(String.format("\\u%04x", (int) c));
            } else {
                if (c == '"' || c == '\\') {
                    json.append('\\');
                }
                json.append(c);
            }
        }
        return json.append('"').toString();
    }
}
