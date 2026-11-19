package cbsp.lab07;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class Task4 {
    private static final int PACKET_MS = 20;
    private static final int SAMPLE_RATE = 16_000;
    private static final Long[] FIXED_ARRIVALS = {12L, 55L, 47L, 103L, null};

    private Task4() { }

    record Packet(int number, long mediaTimeMs, Long arrivalMs, JitterBuffer.Decision decision) { }

    static void run(Path output, TaskParameters parameters, boolean audio) throws IOException {
        var csv = new StringBuilder(Csv.row("trace", "delay_ms", "packet", "media_ms", "arrival_ms", "deadline_ms",
                                            "outcome"));
        Long[] assigned = {12L, 55L, 47L, parameters.packet4ArrivalMs(), null};
        var fixed40 = fixedTrace(40);
        var fixed50 = fixedTrace(50);
        appendRows(csv, "fixed", 40, fixed40);
        appendRows(csv, "fixed", 50, fixed50);
        appendRows(csv, "assigned", 40, simulate(assigned, 40));
        appendRows(csv, "assigned", 50, simulate(assigned, 50));
        Path report = output.resolve("task4-packets.csv");
        Files.writeString(report, csv);

        List<Plots.Point> arrivals = new ArrayList<>();
        for (var packet : fixed40) {
            if (packet.arrivalMs() != null) {
                arrivals.add(new Plots.Point(packet.arrivalMs(), packet.number()));
            }
        }
        Plots.write(output.resolve("task4-playout.png"), "Packet arrivals and playout deadlines", "Time (ms)",
                "Packet number", 140, 5, 5, List.of(new Plots.Series("Arrival", arrivals, false),
                        new Plots.Series("Deadline with 40 ms delay", deadlines(fixed40), false),
                        new Plots.Series("Deadline with 50 ms delay", deadlines(fixed50), false)));
        if (audio) {
            Files.write(output.resolve("task4-delay40.wav"), wave(fixed40));
            Files.write(output.resolve("task4-delay50.wav"), wave(fixed50));
        }
        System.out.println("Wrote " + report + " and playout plot" + (audio ? " and WAV files." : "."));
    }

    static List<Packet> fixedTrace(long delay) {
        return simulate(FIXED_ARRIVALS, delay);
    }

    private static List<Packet> simulate(Long[] arrivals, long delay) {
        List<Packet> packets = new ArrayList<>();
        for (int i = 0; i < arrivals.length; i++) {
            long mediaTime = (long) i * PACKET_MS;
            var decision = JitterBuffer.decide(mediaTime, delay, arrivals[i]);
            packets.add(new Packet(i + 1, mediaTime, arrivals[i], decision));
        }
        return List.copyOf(packets);
    }

    private static void appendRows(StringBuilder csv, String trace, long delay, List<Packet> packets) {
        for (var packet : packets) {
            var decision = packet.decision();
            csv.append(Csv.row(trace, delay, packet.number(), packet.mediaTimeMs(), packet.arrivalMs(),
                    decision.deadlineMs(), decision.outcome()));
        }
    }

    private static List<Plots.Point> deadlines(List<Packet> packets) {
        return packets.stream().map(packet -> new Plots.Point(packet.decision().deadlineMs(), packet.number())).toList();
    }

    static byte[] wave(List<Packet> packets) throws IOException {
        int packetSamples = SAMPLE_RATE * PACKET_MS / 1_000;
        int clipSamples = packetSamples * packets.size();
        int repeatSamples = 2 * clipSamples;
        byte[] pcm = new byte[12 * repeatSamples * 2];
        for (int repeat = 0; repeat < 12; repeat++) {
            for (int i = 0; i < clipSamples; i++) {
                if (packets.get(i / packetSamples).decision().outcome() == JitterBuffer.Outcome.ON_TIME) {
                    int sample = (int) (6_000 * Math.sin(2 * Math.PI * 440 * i / SAMPLE_RATE));
                    int position = 2 * (repeat * repeatSamples + i);
                    pcm[position] = (byte) sample;
                    pcm[position + 1] = (byte) (sample >> 8);
                }
            }
        }
        var format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        var output = new ByteArrayOutputStream();
        try (var input = new AudioInputStream(new ByteArrayInputStream(pcm), format, pcm.length / 2)) {
            AudioSystem.write(input, AudioFileFormat.Type.WAVE, output);
        }
        return output.toByteArray();
    }
}
