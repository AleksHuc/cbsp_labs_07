package cbsp.lab07;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

final class Task5Browser {
    private static final int MAX_REQUEST_MS = 3_000;
    private static final int TIMELINE_END_MS = 4_000;
    private static final Pattern TIMELINE_QUERY = Pattern.compile("publication=(full|partial)&request=([0-9]{1,4})");

    private Task5Browser() { }

    static void run() throws IOException, InterruptedException {
        byte[] excerpt = LabResources.read("task5/excerpt.wav");
        LocalLabBrowser.run("task5", Map.of(
                "/latency.css", LocalLabBrowser.resource("task5/latency.css", "text/css"),
                "/excerpt.wav", exchange -> LocalLabBrowser.send(exchange, 200, "audio/wav", excerpt),
                "/api/latency", Task5Browser::simulate));
    }

    private static void simulate(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        boolean partial = false;
        long requestMs = Task5.DEFAULT_REQUEST_MS;
        if (query != null) {
            var match = TIMELINE_QUERY.matcher(query);
            if (!match.matches() || Long.parseLong(match.group(2)) > MAX_REQUEST_MS) {
                LocalLabBrowser.sendText(exchange, 400, "text/plain",
                        "Choose a publication type and a request time from 0 to " + MAX_REQUEST_MS + " ms.");
                return;
            }
            partial = match.group(1).equals("partial");
            requestMs = Long.parseLong(match.group(2));
        }

        var timeline = Task5.publication(partial, requestMs);
        var measurements = timeline.measurements();
        String model = "{\"publication\":\"" + (partial ? "partial" : "full") + "\""
                + ",\"captureMs\":" + timeline.capturedMs() + ",\"requestMs\":" + timeline.requestedMs()
                + ",\"publicationMs\":" + timeline.publishedMs() + ",\"playoutMs\":" + timeline.playedMs()
                + ",\"startupMs\":" + measurements.startupMs()
                + ",\"captureToPlayoutMs\":" + measurements.captureToPlayoutMs()
                + ",\"maxRequestMs\":" + MAX_REQUEST_MS + ",\"timelineEndMs\":" + TIMELINE_END_MS + "}";
        LocalLabBrowser.sendText(exchange, 200, "application/json", model);
    }
}
