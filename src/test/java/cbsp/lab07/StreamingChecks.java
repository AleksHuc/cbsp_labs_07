package cbsp.lab07;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class StreamingChecks {
    private StreamingChecks() { }

    public static void main(String[] args) throws Exception {
        verifyBuffer();
        verifyJitterAndLatency();
        verifyReceiveCallback();
        verifyMidi();
        System.out.println("Lab 07 buffer, timing and MIDI checks passed.");
    }

    private static void verifyBuffer() {
        var first = AdaptiveBuffer.transfer(3, 32_000, 96, 2);
        near(8.0 / 3, first.downloadSeconds(), "high-rate download time");
        near(7.0 / 3, first.bufferAfterSeconds(), "buffer after first segment");
        near(0, first.stallSeconds(), "first segment stays on time");
        var high = AdaptiveBuffer.transfer(first.bufferAfterSeconds(), 32_000, 96, 2);
        var low = AdaptiveBuffer.transfer(first.bufferAfterSeconds(), 16_000, 96, 2);
        near(1.0 / 3, high.stallSeconds(), "stall for the second high-rate segment");
        near(4.0 / 3, low.downloadSeconds(), "low-rate download time");
        near(3, low.bufferAfterSeconds(), "lower bitrate refills the buffer");
        equal(false, AdaptiveBuffer.useHigh(first.bufferAfterSeconds(), 32_000, 96), "lower-rate choice");
        equal(true, AdaptiveBuffer.useHigh(2, 32_000, 128), "completion at the buffer limit");
    }

    private static void verifyJitterAndLatency() {
        equal(new JitterBuffer.Decision(100, JitterBuffer.Outcome.LATE),
                JitterBuffer.decide(60, 40, 103L), "late packet");
        equal(new JitterBuffer.Decision(110, JitterBuffer.Outcome.ON_TIME),
                JitterBuffer.decide(60, 50, 103L), "jitter absorbed by a longer buffer");
        equal(JitterBuffer.Outcome.ON_TIME, JitterBuffer.decide(60, 50, 110L).outcome(), "arrival at deadline");
        equal(JitterBuffer.Outcome.LOST, JitterBuffer.decide(80, 50, null).outcome(), "missing packet");
        equal(new Latency.Measurements(400, 3_000), Latency.measure(0, 2_600, 3_000), "slide example");
        equal(new Latency.Measurements(300, 750), Latency.measure(50, 500, 800), "nonzero capture timestamp");
    }

    private static void verifyMidi() throws Exception {
        var earlier = new MidiNetwork.Event(1, 0, true, 0, 60, 84);
        var later = new MidiNetwork.Event(2, 0, false, 0, 60, 64);
        if (new MidiNetwork.Scheduled(later, 0, 200).compareTo(new MidiNetwork.Scheduled(earlier, 2, 200)) <= 0) {
            throw new AssertionError("MIDI events at the same deadline must be ordered by sequence");
        }

        Path output = Files.createTempDirectory("lab07-midi-check-");
        try {
            MidiExperiments.runSession(output, new TaskParameters("00123456"), false);
            List<String> sent = Files.readAllLines(output.resolve("task6-midi-send.csv"));
            equal(3, sent.size(), "one wire datagram per MIDI event");
            equal("1,0,note-on,0,65,84,12,000000010000000001004154", sent.get(1), "Note On wire format");
            equal("2,500,note-off,0,65,64,12,00000002000001f400004140", sent.get(2), "Note Off wire format");

            List<String> fixed = Files.readAllLines(output.resolve("task7-midi-timing.csv"));
            equal(5, fixed.size(), "two events in the two timing policies");
            equal("arrival,2,note-off,65,500,580,580,550", fixed.get(3), "immediate note duration");
            equal("scheduled,2,note-off,65,500,580,600,500", fixed.get(4), "scheduled note duration");

            List<String> live = Files.readAllLines(output.resolve("task8-midi-playout.csv"));
            equal(3, live.size(), "two events arrived over UDP");
            String[] on = live.get(1).split(",");
            String[] off = live.get(2).split(",");
            equal("note-on", on[2], "live attack");
            equal("note-off", off[2], "live release");
            near(500, Double.parseDouble(off[7]) - Double.parseDouble(on[7]), "due times stay 500 ms apart");
            double offArrival = Double.parseDouble(off[6]);
            double offPlayback = Double.parseDouble(off[8]);
            if (offPlayback < Double.parseDouble(off[7]) || offPlayback - offArrival >= 750) {
                throw new AssertionError("Note Off should play at its deadline while the receiver waits for UDP");
            }

            List<String> loss = Files.readAllLines(output.resolve("task9-midi-loss.csv"));
            equal(3, loss.size(), "one played event and one cleanup event");
            if (!loss.getLast().startsWith("cleanup,,note-off,1,65,")) {
                throw new AssertionError("The missing Note Off must be replaced by cleanup: " + loss.getLast());
            }
        } finally {
            try (var paths = Files.walk(output)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    private static void verifyReceiveCallback() throws Exception {
        var loopback = InetAddress.getLoopbackAddress();
        var arrivals = new ArrayList<MidiNetwork.Scheduled>();
        var now = new AtomicLong(TimeUnit.MILLISECONDS.toNanos(5));
        var clock = new MidiNetwork.PlayoutClock(now::get, 70);
        var hex = HexFormat.of();
        byte[] valid = hex.parseHex("000000110000001901024a63");
        byte[] badChannel = hex.parseHex("000000110000001901104a63");

        try (var input = new DatagramSocket(new InetSocketAddress(loopback, 0));
             var sender = new DatagramSocket(new InetSocketAddress(loopback, 0))) {
            input.setSoTimeout(2_000);
            var destination = new InetSocketAddress(loopback, input.getLocalPort());
            for (byte[] malformed : List.of(Arrays.copyOf(valid, 11), Arrays.copyOf(valid, 13), badChannel)) {
                sender.send(new DatagramPacket(malformed, malformed.length, destination));
                equal(false, MidiNetwork.receive(input, arrivals::add, clock), "reject malformed MIDI payload");
            }
            equal(List.of(), arrivals, "malformed packets never call the callback");

            now.set(TimeUnit.MILLISECONDS.toNanos(30));
            sender.send(new DatagramPacket(valid, valid.length, destination));
            equal(true, MidiNetwork.receive(input, arrivals::add, clock), "valid packet reaches the callback");
            var event = new MidiNetwork.Event(17, 25, true, 2, 74, 99);
            equal(List.of(new MidiNetwork.Scheduled(event, now.get(), TimeUnit.MILLISECONDS.toNanos(125))), arrivals,
                    "decoded fields and deadline are delivered exactly once");
        }
    }

    private static void equal(Object expected, Object actual, String experiment) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(experiment + ": expected " + expected + ", got " + actual);
        }
    }

    private static void near(double expected, double actual, String experiment) {
        if (!(Math.abs(expected - actual) < 1e-8)) {
            throw new AssertionError(experiment + ": expected " + expected + ", got " + actual);
        }
    }
}
