package cbsp.lab07;

final class Latency {
    private Latency() { }

    record Measurements(long startupMs, long captureToPlayoutMs) { }

    static Measurements measure(long capturedMs, long requestedMs, long playedMs) {
        // TODO (Task 5): measure startup and capture-to-playout latency for the same audio sample.
        throw new UnsupportedOperationException("TODO (Task 5): latency measurements");
    }
}
