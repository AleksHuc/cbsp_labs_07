package cbsp.lab07;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.Map;
import java.util.StringJoiner;

/** Prepared loopback view for the Task 3 simulation. */
final class Task3Browser {
    private static final int MIN_THROUGHPUT_KBPS = 48;
    private static final int MAX_THROUGHPUT_KBPS = 192;

    private Task3Browser() { }

    static void run() throws IOException, InterruptedException {
        var audio = new Task3Audio();
        HttpHandler simulation = exchange -> serveSimulation(exchange, audio);
        LocalLabBrowser.run("task3", Map.of("/api/simulation", simulation,
                "/adaptive.wav", simulation, "/fixed.wav", simulation));
    }

    private static void serveSimulation(HttpExchange exchange, Task3Audio audio) throws IOException {
        int[] throughput;
        try {
            throughput = readThroughput(exchange.getRequestURI().getQuery());
        } catch (IllegalArgumentException ex) {
            LocalLabBrowser.sendText(exchange, 400, "text/plain", ex.getMessage());
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/api/simulation")) {
            LocalLabBrowser.sendText(exchange, 200, "application/json", model(throughput));
        } else {
            var run = Task3.simulate(path.equals("/adaptive.wav"), throughput);
            LocalLabBrowser.send(exchange, 200, "audio/wav", audio.wave(run));
        }
    }

    private static String model(int[] throughput) {
        return "{\"segmentCount\":" + Task3.SEGMENT_COUNT
                + ",\"minThroughputKbps\":" + MIN_THROUGHPUT_KBPS
                + ",\"maxThroughputKbps\":" + MAX_THROUGHPUT_KBPS
                + ",\"defaultThroughputKbps\":" + Task3.DEFAULT_THROUGHPUT_KBPS
                + ",\"adaptive\":" + toJson(Task3.simulate(true, throughput))
                + ",\"fixed\":" + toJson(Task3.simulate(false, throughput)) + "}";
    }

    private static int[] readThroughput(String query) {
        if (query == null || query.equals("throughput=")) {
            return new int[0];
        }
        if (!query.startsWith("throughput=")) {
            throw new IllegalArgumentException("Expected a throughput value for each simulated segment.");
        }
        String[] values = query.substring("throughput=".length()).split(",", -1);
        if (values.length > Task3.SEGMENT_COUNT) {
            throw new IllegalArgumentException("Reset the experiment to start another run.");
        }
        int[] throughput = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            throughput[i] = Integer.parseInt(values[i]);
            if (throughput[i] < MIN_THROUGHPUT_KBPS || throughput[i] > MAX_THROUGHPUT_KBPS) {
                throw new IllegalArgumentException("Throughput must be between " + MIN_THROUGHPUT_KBPS + " and "
                        + MAX_THROUGHPUT_KBPS + " kbit/s.");
            }
        }
        return throughput;
    }

    private static String toJson(Task3.Run run) {
        var segments = new StringJoiner(",", "[", "]");
        for (var step : run.steps()) {
            segments.add("{\"encodingKbps\":" + step.encodingKbps() + ",\"downloadSeconds\":"
                    + step.transfer().downloadSeconds() + ",\"stallSeconds\":" + step.transfer().stallSeconds() + "}");
        }
        var points = new StringJoiner(",", "[", "]");
        for (var point : run.points()) {
            points.add("[" + point.x() + "," + point.y() + "]");
        }
        return "{\"segments\":" + segments + ",\"points\":" + points + "}";
    }
}
