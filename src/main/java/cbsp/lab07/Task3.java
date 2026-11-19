package cbsp.lab07;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class Task3 {
    static final int SEGMENT_COUNT = 4;
    static final int DEFAULT_THROUGHPUT_KBPS = 96;
    static final double SEGMENT_SECONDS = 2.0;
    static final double INITIAL_BUFFER_SECONDS = 3.0;
    static final int HIGH_KBPS = 128;
    static final int LOW_KBPS = 64;
    private static final int HIGH_BYTES = 32_000;
    private static final int LOW_BYTES = 16_000;

    private Task3() { }

    record Step(int throughputKbps, int encodingKbps, int bytes, double startSeconds, double bufferBeforeSeconds,
                AdaptiveBuffer.Transfer transfer) {
        double finishSeconds() {
            return startSeconds + transfer.downloadSeconds();
        }
    }

    record Run(List<Step> steps, List<Plots.Point> points) { }

    static void run(Path output, TaskParameters parameters) throws IOException {
        var csv = new StringBuilder(Csv.row("profile", "policy", "segment", "throughput_kbps", "encoding_kbps",
                "segment_bytes", "start_seconds", "download_seconds", "finish_seconds", "buffer_before_seconds",
                "buffer_after_seconds", "stall_seconds"));
        int[] fixedThroughput = new int[SEGMENT_COUNT];
        Arrays.fill(fixedThroughput, DEFAULT_THROUGHPUT_KBPS);
        plot(output.resolve("task3-buffer.png"), "fixed", fixedThroughput, csv);
        plot(output.resolve("task3-assigned-buffer.png"), "assigned", parameters.throughputKbps(), csv);
        Path report = output.resolve("task3-buffer.csv");
        Files.writeString(report, csv);
        System.out.println("Wrote " + report + " and buffer plots.");
    }

    private static void plot(Path path, String profile, int[] throughput, StringBuilder csv) throws IOException {
        var adaptiveRun = simulate(true, throughput);
        var fixedRun = simulate(false, throughput);
        appendRows(csv, profile, "adaptive", adaptiveRun.steps());
        appendRows(csv, profile, "always-high", fixedRun.steps());

        var adaptive = adaptiveRun.points();
        var fixed = fixedRun.points();
        double xMax = Math.ceil(Math.max(adaptive.getLast().x(), fixed.getLast().x()));
        double yMax = Math.ceil(Math.max(maxBuffer(adaptive), maxBuffer(fixed)));
        Plots.write(path, "Audio buffer · " + profile, "Time (s)", "Buffered audio (s)", xMax, yMax, (int) yMax,
                List.of(new Plots.Series("Adaptive", adaptive, true), new Plots.Series("Always 128 kbit/s", fixed, true)));
    }

    static Run simulate(boolean adaptive, int[] throughput) {
        double time = 0;
        double buffer = INITIAL_BUFFER_SECONDS;
        List<Step> steps = new ArrayList<>();
        List<Plots.Point> points = new ArrayList<>();
        points.add(new Plots.Point(time, buffer));
        for (int i = 0; i < throughput.length; i++) {
            boolean high = i == 0 || !adaptive || AdaptiveBuffer.useHigh(buffer, HIGH_BYTES, throughput[i]);
            int size = high ? HIGH_BYTES : LOW_BYTES;
            var transfer = AdaptiveBuffer.transfer(buffer, size, throughput[i], SEGMENT_SECONDS);
            var step = new Step(throughput[i], high ? HIGH_KBPS : LOW_KBPS, size, time, buffer, transfer);
            steps.add(step);
            double finish = step.finishSeconds();
            if (transfer.stallSeconds() > 0) {
                points.add(new Plots.Point(time + buffer, 0));
            }
            points.add(new Plots.Point(finish, Math.max(0, buffer - transfer.downloadSeconds())));
            points.add(new Plots.Point(finish, transfer.bufferAfterSeconds()));
            time = finish;
            buffer = transfer.bufferAfterSeconds();
        }
        return new Run(List.copyOf(steps), List.copyOf(points));
    }

    private static void appendRows(StringBuilder csv, String profile, String policy, List<Step> steps) {
        for (int i = 0; i < steps.size(); i++) {
            var step = steps.get(i);
            var transfer = step.transfer();
            csv.append(Csv.row(profile, policy, i + 1, step.throughputKbps(), step.encodingKbps(), step.bytes(),
                    step.startSeconds(), transfer.downloadSeconds(), step.finishSeconds(), step.bufferBeforeSeconds(),
                    transfer.bufferAfterSeconds(), transfer.stallSeconds()));
        }
    }

    private static double maxBuffer(List<Plots.Point> points) {
        return points.stream().mapToDouble(Plots.Point::y).max().orElseThrow();
    }
}
