package cbsp.lab07;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class Task5 {
    static final long DEFAULT_REQUEST_MS = 300;
    private static final long PREPARE_PLAYOUT_MS = 300;

    private Task5() { }

    record Timeline(String scenario, long capturedMs, long publishedMs, long requestedMs, long playedMs) {
        Latency.Measurements measurements() {
            return Latency.measure(capturedMs, requestedMs, playedMs);
        }
    }

    static void run(Path output) throws IOException {
        var example = new Timeline("slide-example", 0, 2_800, 2_600, 3_000);
        var full = publication(false, DEFAULT_REQUEST_MS);
        var part = publication(true, DEFAULT_REQUEST_MS);
        var csv = new StringBuilder(Csv.row("scenario", "capture_ms", "publication_ms", "request_ms", "playout_ms",
                "startup_ms", "capture_to_playout_ms"));
        for (var row : List.of(example, full, part)) {
            var measurements = row.measurements();
            csv.append(Csv.row(row.scenario(), row.capturedMs(), row.publishedMs(), row.requestedMs(), row.playedMs(),
                    measurements.startupMs(), measurements.captureToPlayoutMs()));
        }
        Path report = output.resolve("task5-latency.csv");
        Files.writeString(report, csv);
        Plots.write(output.resolve("task5-timeline.png"), "Capture, publication, request and playout", "Time (ms)",
                "Scenario number", 3_200, 3, 3, List.of(
                        marks("Capture", example.capturedMs(), full.capturedMs(), part.capturedMs()),
                        marks("Request", example.requestedMs(), full.requestedMs(), part.requestedMs()),
                        marks("Publication", example.publishedMs(), full.publishedMs(), part.publishedMs()),
                        marks("Playout", example.playedMs(), full.playedMs(), part.playedMs())));
        System.out.println("Wrote " + report + " and latency timeline.");
    }

    static Timeline publication(boolean partial, long requestedMs) {
        long publishedMs = partial ? 500 : 2_000;
        long playedMs = Math.max(requestedMs, publishedMs) + PREPARE_PLAYOUT_MS;
        return new Timeline(partial ? "partial-segment" : "full-segment", 0, publishedMs, requestedMs, playedMs);
    }

    private static Plots.Series marks(String label, long first, long second, long third) {
        return new Plots.Series(label, List.of(new Plots.Point(first, 1), new Plots.Point(second, 2),
                new Plots.Point(third, 3)), false);
    }
}
