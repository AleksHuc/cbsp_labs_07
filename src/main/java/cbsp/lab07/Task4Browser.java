package cbsp.lab07;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Map;
import java.util.StringJoiner;

final class Task4Browser {
    private static final int MIN_DELAY_MS = 20;
    private static final int MAX_DELAY_MS = 80;
    private static final int DEFAULT_DELAY_MS = 40;

    private Task4Browser() { }

    static void run() throws IOException, InterruptedException {
        LocalLabBrowser.run("task4", Map.of("/jitter.css", LocalLabBrowser.resource("task4/jitter.css", "text/css"),
                "/api/jitter", exchange -> simulate(exchange, false), "/preview.wav", exchange -> simulate(exchange, true)));
    }

    private static void simulate(HttpExchange exchange, boolean audio) throws IOException {
        int delay;
        try {
            delay = readDelay(exchange.getRequestURI().getQuery());
        } catch (IllegalArgumentException ex) {
            LocalLabBrowser.sendText(exchange, 400, "text/plain", ex.getMessage());
            return;
        }

        var trace = Task4.fixedTrace(delay);
        if (audio) {
            LocalLabBrowser.send(exchange, 200, "audio/wav", Task4.wave(trace));
            return;
        }
        var packets = new StringJoiner(",", "[", "]");
        for (var packet : trace) {
            var decision = packet.decision();
            packets.add("{\"number\":" + packet.number() + ",\"arrivalMs\":" + packet.arrivalMs()
                    + ",\"deadlineMs\":" + decision.deadlineMs() + ",\"outcome\":\"" + decision.outcome() + "\"}");
        }
        String model = "{\"delayMs\":" + delay + ",\"minDelayMs\":" + MIN_DELAY_MS
                + ",\"maxDelayMs\":" + MAX_DELAY_MS + ",\"packets\":" + packets + "}";
        LocalLabBrowser.sendText(exchange, 200, "application/json", model);
    }

    private static int readDelay(String query) {
        if (query == null) {
            return DEFAULT_DELAY_MS;
        }
        if (query.matches("delay=[0-9]{1,2}")) {
            int delay = Integer.parseInt(query.substring("delay=".length()));
            if (delay >= MIN_DELAY_MS && delay <= MAX_DELAY_MS) {
                return delay;
            }
        }
        throw new IllegalArgumentException("Delay must be between " + MIN_DELAY_MS + " and " + MAX_DELAY_MS + " ms.");
    }
}
