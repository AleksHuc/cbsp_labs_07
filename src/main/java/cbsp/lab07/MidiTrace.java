package cbsp.lab07;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Observed loopback events on a shared monotonic time axis. */
final class MidiTrace {
    enum Action { SENT, ARRIVED, SCHEDULED, QUEUED, PLAYED, OMITTED, CLEANUP }

    record Entry(Action action, MidiNetwork.Event event, double timeMs, Double deadlineMs, String payloadHex) { }

    private final long start = System.nanoTime();
    private final List<Entry> entries = new ArrayList<>();

    void record(Action action, MidiNetwork.Event event, long time) {
        record(action, event, time, null, null);
    }

    synchronized void record(Action action, MidiNetwork.Event event, long time, Long deadline, String payloadHex) {
        entries.add(new Entry(action, event, elapsed(time), deadline == null ? null : elapsed(deadline), payloadHex));
    }

    synchronized List<Entry> events() {
        return entries.stream().sorted(Comparator.comparingDouble(Entry::timeMs)).toList();
    }

    private double elapsed(long time) {
        return (time - start) / 1_000_000.0;
    }
}
