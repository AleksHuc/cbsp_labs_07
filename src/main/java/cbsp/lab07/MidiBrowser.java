package cbsp.lab07;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Map;
import java.util.StringJoiner;

final class MidiBrowser {
    private MidiBrowser() { }

    static void run(int task, TaskParameters parameters) throws IOException, InterruptedException {
        LocalLabBrowser.run("midi", Map.of(
                "/midi.css", LocalLabBrowser.resource("midi/midi.css", "text/css"),
                "/midi-sound.js", LocalLabBrowser.resource("midi/midi-sound.js", "text/javascript"),
                "/api/trace", exchange -> simulate(exchange, task, parameters)));
    }

    private static void simulate(HttpExchange exchange, int defaultTask, TaskParameters parameters) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        if (query != null && !query.matches("task=[6-9]")) {
            LocalLabBrowser.sendText(exchange, 400, "text/plain", "Choose task 6, 7, 8 or 9.");
            return;
        }
        int task = query == null ? defaultTask : query.charAt("task=".length()) - '0';
        var rows = new StringJoiner(",", "[", "]");
        try {
            for (var entry : MidiExperiments.observe(task, parameters)) {
                var event = entry.event();
                String payload = entry.payloadHex() == null ? "null" : "\"" + entry.payloadHex() + "\"";
                rows.add("{\"action\":\"" + entry.action() + "\",\"timeMs\":" + entry.timeMs()
                        + ",\"deadlineMs\":" + entry.deadlineMs() + ",\"payloadHex\":" + payload
                        + ",\"sequence\":" + event.sequence() + ",\"mediaMs\":" + event.mediaTimeMs()
                        + ",\"noteOn\":" + event.noteOn() + ",\"channel\":" + (event.channel() + 1)
                        + ",\"note\":" + event.note() + ",\"velocity\":" + event.velocity() + "}");
            }
        } catch (UnsupportedOperationException ex) {
            LocalLabBrowser.sendText(exchange, 501, "text/plain", "Complete the TODO methods for this task and try again.");
            return;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("MIDI capture interrupted", ex);
        } catch (Exception ex) {
            LocalLabBrowser.sendText(exchange, 500, "text/plain", "UDP recording failed. " + ex.getMessage());
            return;
        }
        LocalLabBrowser.sendText(exchange, 200, "application/json", "{\"task\":" + task + ",\"events\":" + rows + "}");
    }
}
