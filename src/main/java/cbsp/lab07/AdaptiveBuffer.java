package cbsp.lab07;

final class AdaptiveBuffer {
    private AdaptiveBuffer() { }

    record Transfer(double downloadSeconds, double bufferAfterSeconds, double stallSeconds) { }

    static Transfer transfer(double bufferedSeconds, int sizeBytes, int throughputKbps, double segmentSeconds) {
        // TODO (Task 3): compute transfer time, remaining audio and time spent stalled.
        throw new UnsupportedOperationException("TODO (Task 3): transfer and buffer update");
    }

    static boolean useHigh(double bufferedSeconds, int highSizeBytes, int throughputKbps) {
        // TODO (Task 3): choose high quality when the higher-rate segment can finish before playback stalls.
        throw new UnsupportedOperationException("TODO (Task 3): bitrate selection");
    }
}
