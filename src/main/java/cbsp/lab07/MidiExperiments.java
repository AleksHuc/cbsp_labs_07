package cbsp.lab07;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Synthesizer;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class MidiExperiments {
    private static final int BUFFER_MS = 70;
    private static final int IDLE_MS = 900;
    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();

    private MidiExperiments() { }

    private record SendStep(int sendAtMs, MidiNetwork.Event event, boolean omitted) {
        private SendStep(int sendAtMs, MidiNetwork.Event event) {
            this(sendAtMs, event, false);
        }
    }

    private record Payload(int bytes, String hex) { }

    static void runSend(Path output, TaskParameters parameters) throws IOException {
        var rows = new StringBuilder(Csv.row("sequence", "media_ms", "event", "channel", "note", "velocity",
                "payload_bytes", "payload_hex"));
        try (var input = loopbackSocket(); var sender = loopbackSocket()) {
            input.setSoTimeout(2_000);
            var destination = new InetSocketAddress(LOOPBACK, input.getLocalPort());
            for (var step : notePair(parameters.midiNote())) {
                var event = step.event();
                MidiNetwork.send(sender, destination, event);
                var payload = readPayload(input);
                rows.append(Csv.row(event.sequence(), event.mediaTimeMs(), eventName(event), event.channel(),
                        event.note(), event.velocity(), payload.bytes(), payload.hex()));
            }
        }
        Path report = output.resolve("task6-midi-send.csv");
        Files.writeString(report, rows);
        System.out.println("Wrote " + report);
    }

    static void runReceive(Path output, TaskParameters parameters) throws IOException {
        fixedClock(output.resolve("task7-midi-timing.csv"), notePair(parameters.midiNote()));
    }

    static void runPlayback(Path output, TaskParameters parameters, boolean sound) throws Exception {
        var steps = notePair(parameters.midiNote());
        withPlayback(sound, receiver -> loopback(output.resolve("task8-midi-playout.csv"), steps, receiver, false, null));
    }

    static void runSession(Path output, TaskParameters parameters, boolean sound) throws Exception {
        runSend(output, parameters);
        runReceive(output, parameters);
        var steps = notePair(parameters.midiNote());
        withPlayback(sound, receiver -> {
            loopback(output.resolve("task8-midi-playout.csv"), steps, receiver, true, null);
            loopback(output.resolve("task9-midi-loss.csv"), withoutNoteOff(steps), receiver, true, null);
        });
    }

    static List<MidiTrace.Entry> observe(int task, TaskParameters parameters) throws Exception {
        var steps = notePair(parameters.midiNote());
        var trace = new MidiTrace();
        switch (task) {
            case 6, 7 -> observeReception(steps, trace, task == 7);
            case 8 -> loopback(null, queuedPhrase(parameters.midiNote()), null, false, trace);
            case 9 -> loopback(null, withoutNoteOff(steps), null, true, trace);
            default -> throw new IllegalArgumentException("Choose MIDI task 6, 7, 8 or 9.");
        }
        return trace.events();
    }

    private static void observeReception(List<SendStep> steps, MidiTrace trace, boolean decode) throws Exception {
        try (var socket = loopbackSocket(); var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            socket.setSoTimeout(2_000);
            Future<Void> sender = startSender(threads, socket, steps, trace);
            var clock = new MidiNetwork.PlayoutClock(System::nanoTime, BUFFER_MS);
            for (var step : steps) {
                if (decode) {
                    if (!MidiNetwork.receive(socket, event -> {
                        trace.record(MidiTrace.Action.ARRIVED, event.event(), event.arrivalNanos());
                        trace.record(MidiTrace.Action.SCHEDULED, event.event(), System.nanoTime(), event.deadlineNanos(), null);
                    }, clock)) {
                        throw new IOException("The MIDI receiver discarded a demo event");
                    }
                } else {
                    var payload = readPayload(socket);
                    trace.record(MidiTrace.Action.ARRIVED, step.event(), System.nanoTime(), null, payload.hex());
                }
            }
            completed(sender);
        }
    }

    private static Payload readPayload(DatagramSocket socket) throws IOException {
        byte[] buffer = new byte[64];
        var packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        int length = packet.getLength();
        return new Payload(length, HexFormat.of().formatHex(buffer, 0, length));
    }

    static void sendTo(String host, int port, TaskParameters parameters) throws Exception {
        var destination = new InetSocketAddress(InetAddress.getByName(host), port);
        int root = parameters.midiNote();
        var phrase = List.of(
                new SendStep(30, note(1, 0, true, root)), new SendStep(580, note(2, 500, false, root)),
                new SendStep(680, note(3, 650, true, root + 4)), new SendStep(945, note(4, 900, false, root + 4)),
                new SendStep(1_110, note(5, 1_050, true, root + 7)), new SendStep(1_410, note(6, 1_350, false, root + 7)));
        sendPlan(destination, phrase, null);
        System.out.println("Sent " + phrase.size() + " MIDI events to " + destination);
    }

    static void listen(Path output, int port, boolean sound) throws Exception {
        Path report = output.resolve("task9-midi-receiver.csv");
        withPlayback(sound, receiver -> {
            try (var socket = new DatagramSocket(port)) {
                System.out.println("Listening for MIDI UDP on port " + socket.getLocalPort());
                capture(socket, report, receiver, 20_000, true, null);
            }
        });
    }

    private static List<SendStep> notePair(int note) {
        return List.of(new SendStep(30, note(1, 0, true, note)), new SendStep(580, note(2, 500, false, note)));
    }

    private static List<SendStep> queuedPhrase(int root) {
        return List.of(new SendStep(30, note(1, 0, true, root)), new SendStep(55, note(2, 250, true, root + 4)),
                new SendStep(580, note(3, 500, false, root)), new SendStep(675, note(4, 700, false, root + 4)));
    }

    private static List<SendStep> withoutNoteOff(List<SendStep> steps) {
        var off = steps.getLast();
        return List.of(steps.getFirst(), new SendStep(off.sendAtMs(), off.event(), true));
    }

    private static MidiNetwork.Event note(int sequence, int mediaTime, boolean on, int pitch) {
        return new MidiNetwork.Event(sequence, mediaTime, on, 0, pitch, on ? 84 : 64);
    }

    private static void fixedClock(Path path, List<SendStep> steps) throws IOException {
        var times = steps.iterator();
        var clock = new MidiNetwork.PlayoutClock(() -> TimeUnit.MILLISECONDS.toNanos(times.next().sendAtMs()), BUFFER_MS);
        var received = new ArrayList<MidiNetwork.Scheduled>();
        try (var input = loopbackSocket(); var sender = loopbackSocket()) {
            input.setSoTimeout(2_000);
            var destination = new InetSocketAddress(LOOPBACK, input.getLocalPort());
            for (var step : steps) {
                MidiNetwork.send(sender, destination, step.event());
                if (!MidiNetwork.receive(input, received::add, clock)) {
                    throw new IOException("The MIDI receiver discarded a demo event");
                }
            }
        }

        var rows = new StringBuilder(Csv.row("policy", "sequence", "event", "note", "media_ms", "arrival_ms",
                "playout_ms", "held_ms"));
        long firstArrival = steps.getFirst().sendAtMs();
        long firstDeadline = firstArrival + BUFFER_MS;
        for (var event : received) {
            long arrival = TimeUnit.NANOSECONDS.toMillis(event.arrivalNanos());
            long deadline = TimeUnit.NANOSECONDS.toMillis(event.deadlineNanos());
            var note = event.event();
            rows.append(Csv.row("arrival", note.sequence(), eventName(note), note.note(), note.mediaTimeMs(), arrival,
                    arrival, note.noteOn() ? null : arrival - firstArrival));
            rows.append(Csv.row("scheduled", note.sequence(), eventName(note), note.note(), note.mediaTimeMs(), arrival,
                    deadline, note.noteOn() ? null : deadline - firstDeadline));
        }
        Files.writeString(path, rows);
        System.out.println("Wrote " + path);
    }

    private static void loopback(Path path, List<SendStep> steps, Receiver receiver, boolean cleanup, MidiTrace trace)
            throws Exception {
        try (var socket = loopbackSocket(); var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Void> sender = startSender(threads, socket, steps, trace);
            capture(socket, path, receiver, 2_000, cleanup, trace);
            completed(sender);
        }
    }

    private static Future<Void> startSender(ExecutorService threads, DatagramSocket input, List<SendStep> steps,
                                           MidiTrace trace) {
        var destination = new InetSocketAddress(LOOPBACK, input.getLocalPort());
        return threads.submit(() -> {
            sendPlan(destination, steps, trace);
            return null;
        });
    }

    private static DatagramSocket loopbackSocket() throws IOException {
        return new DatagramSocket(new InetSocketAddress(LOOPBACK, 0));
    }

    private static void sendPlan(InetSocketAddress destination, List<SendStep> steps, MidiTrace trace) throws Exception {
        try (var sender = new DatagramSocket()) {
            long start = System.nanoTime();
            for (var step : steps) {
                long remaining = start + TimeUnit.MILLISECONDS.toNanos(step.sendAtMs()) - System.nanoTime();
                if (remaining > 0) {
                    TimeUnit.NANOSECONDS.sleep(remaining);
                }
                long sent = System.nanoTime();
                if (!step.omitted()) {
                    MidiNetwork.send(sender, destination, step.event());
                }
                if (trace != null) {
                    trace.record(step.omitted() ? MidiTrace.Action.OMITTED : MidiTrace.Action.SENT, step.event(), sent);
                }
            }
        }
    }

    private static void capture(DatagramSocket socket, Path path, Receiver receiver, int firstWaitMs, boolean cleanup,
                                MidiTrace trace) throws Exception {
        long start = System.nanoTime();
        var queue = new DelayQueue<MidiNetwork.Scheduled>();
        var clock = new MidiNetwork.PlayoutClock(System::nanoTime, BUFFER_MS);
        var rows = new StringBuilder(Csv.row("action", "sequence", "event", "channel", "note", "media_ms",
                "arrival_ms", "deadline_ms", "playout_ms", "held_ms"));
        Map<Integer, Long> activeNotes = new LinkedHashMap<>();
        Consumer<MidiNetwork.Scheduled> onEvent = queue::add;
        if (trace != null) {
            onEvent = event -> {
                trace.record(MidiTrace.Action.ARRIVED, event.event(), event.arrivalNanos());
                trace.record(MidiTrace.Action.QUEUED, event.event(), System.nanoTime(), event.deadlineNanos(), null);
                queue.add(event);
            };
        }

        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Void> receiving = startReceiver(threads, socket, onEvent, clock, firstWaitMs);
            try {
                while (!receiving.isDone() || !queue.isEmpty()) {
                    var scheduled = queue.poll(40, TimeUnit.MILLISECONDS);
                    if (scheduled != null) {
                        playScheduled(rows, receiver, activeNotes, start, scheduled, trace);
                    }
                }
                completed(receiving);
            } finally {
                socket.close();
                if (cleanup && !activeNotes.isEmpty()) {
                    cleanupNotes(rows, receiver, start, activeNotes, trace);
                }
                if (path != null) {
                    Files.writeString(path, rows);
                }
            }
        }
        if (path != null) {
            System.out.println("Wrote " + path);
        }
    }

    private static Future<Void> startReceiver(ExecutorService threads, DatagramSocket socket,
                                             Consumer<MidiNetwork.Scheduled> onEvent,
                                             MidiNetwork.PlayoutClock clock, int firstWaitMs) {
        // TODO (Task 8): start the receive loop in the executor and connect the onEvent callback.
        throw new UnsupportedOperationException("TODO (Task 8): receive thread");
    }

    private static void readUntilIdle(DatagramSocket socket, Consumer<MidiNetwork.Scheduled> onEvent,
                                      MidiNetwork.PlayoutClock clock, int firstWaitMs) throws IOException {
        socket.setSoTimeout(firstWaitMs);
        boolean received = false;
        try {
            while (true) {
                if (MidiNetwork.receive(socket, onEvent, clock) && !received) {
                    received = true;
                    socket.setSoTimeout(IDLE_MS);
                }
            }
        } catch (SocketTimeoutException ex) {
            if (!received) {
                throw new IOException("No MIDI events arrived within " + firstWaitMs + " ms", ex);
            }
        }
    }

    private static void playScheduled(StringBuilder rows, Receiver receiver, Map<Integer, Long> activeNotes, long start,
                                      MidiNetwork.Scheduled scheduled, MidiTrace trace) throws Exception {
        var event = scheduled.event();
        long played = System.nanoTime();
        emit(receiver, event.noteOn(), event.channel(), event.note(), event.velocity());
        if (trace != null) {
            trace.record(MidiTrace.Action.PLAYED, event, played, scheduled.deadlineNanos(), null);
        }
        int key = event.channel() * 128 + event.note();
        Long previous = event.noteOn() ? activeNotes.put(key, played) : activeNotes.remove(key);
        rows.append(Csv.row("playout", event.sequence(), eventName(event), event.channel() + 1, event.note(),
                event.mediaTimeMs(), ms(scheduled.arrivalNanos(), start), ms(scheduled.deadlineNanos(), start),
                ms(played, start), event.noteOn() || previous == null ? null : ms(played, previous)));
    }

    private static void release(StringBuilder rows, Receiver receiver, long start, int key, long started, MidiTrace trace)
            throws Exception {
        long played = System.nanoTime();
        int channel = key / 128;
        int note = key % 128;
        emit(receiver, false, channel, note, 64);
        if (trace != null) {
            trace.record(MidiTrace.Action.CLEANUP, new MidiNetwork.Event(-1, -1, false, channel, note, 64), played);
        }
        rows.append(Csv.row("cleanup", null, "note-off", channel + 1, note, null, null, null,
                ms(played, start), ms(played, started)));
    }

    private static void cleanupNotes(StringBuilder rows, Receiver receiver, long start, Map<Integer, Long> activeNotes,
                                     MidiTrace trace) throws Exception {
        // TODO (Task 9): release each note still active when the receive session ends.
        throw new UnsupportedOperationException("TODO (Task 9): active-note cleanup");
    }

    private static void emit(Receiver receiver, boolean on, int channel, int note, int velocity) throws Exception {
        if (receiver != null) {
            receiver.send(new ShortMessage(on ? ShortMessage.NOTE_ON : ShortMessage.NOTE_OFF, channel, note, velocity), -1);
        }
    }

    private static String eventName(MidiNetwork.Event event) {
        return event.noteOn() ? "note-on" : "note-off";
    }

    private static double ms(long later, long earlier) {
        return (later - earlier) / 1_000_000.0;
    }

    private static void completed(Future<?> running) throws Exception {
        try {
            running.get();
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw ex;
        }
    }

    @FunctionalInterface
    private interface PlaybackExperiment {
        void run(Receiver receiver) throws Exception;
    }

    private static void withPlayback(boolean sound, PlaybackExperiment experiment) throws Exception {
        if (!sound) {
            experiment.run(null);
            return;
        }
        try (Synthesizer synthesizer = MidiSystem.getSynthesizer()) {
            synthesizer.open();
            try (Receiver receiver = synthesizer.getReceiver()) {
                experiment.run(receiver);
            }
        }
    }
}
