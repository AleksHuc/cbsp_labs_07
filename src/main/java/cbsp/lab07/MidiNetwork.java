package cbsp.lab07;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

final class MidiNetwork {
    private static final int MESSAGE_BYTES = 12;

    private MidiNetwork() { }

    record Event(int sequence, int mediaTimeMs, boolean noteOn, int channel, int note, int velocity) { }

    record Scheduled(Event event, long arrivalNanos, long deadlineNanos) implements Delayed {
        @Override
        public long getDelay(TimeUnit unit) {
            // TODO (Task 8): convert the remaining time before this event can be played.
            throw new UnsupportedOperationException("TODO (Task 8): playout delay");
        }

        @Override
        public int compareTo(Delayed other) {
            var eventTime = (Scheduled) other;
            int byTime = Long.compare(deadlineNanos - eventTime.deadlineNanos, 0);
            return byTime == 0 ? Integer.compare(event.sequence(), eventTime.event.sequence()) : byTime;
        }
    }

    static final class PlayoutClock {
        private final LongSupplier now;
        private final long bufferNanos;
        private Long originNanos;

        PlayoutClock(LongSupplier now, long bufferMs) {
            this.now = now;
            this.bufferNanos = TimeUnit.MILLISECONDS.toNanos(bufferMs);
        }

        long nowNanos() {
            return now.getAsLong();
        }

        long originNanos(long firstOrLaterArrival) {
            if (originNanos == null) {
                originNanos = firstOrLaterArrival + bufferNanos;
            }
            return originNanos;
        }
    }

    static void send(DatagramSocket socket, InetSocketAddress destination, Event event) throws IOException {
        // TODO (Task 6): send one UDP datagram containing the encoded event.
        throw new UnsupportedOperationException("TODO (Task 6): datagram send");
    }

    static boolean receive(DatagramSocket socket, Consumer<Scheduled> onEvent, PlayoutClock clock) throws IOException {
        // TODO (Task 7): receive one event, calculate its local deadline and pass it to the callback.
        throw new UnsupportedOperationException("TODO (Task 7): receive callback and deadline");
    }

    private static byte[] encode(Event event) {
        // TODO (Task 6): write the event fields to the 12-byte wire format.
        throw new UnsupportedOperationException("TODO (Task 6): event encoding");
    }

    private static DatagramPacket receiveBuffer() {
        byte[] body = new byte[MESSAGE_BYTES + 1];
        return new DatagramPacket(body, body.length);
    }

    private static Event decode(DatagramPacket packet) {
        // TODO (Task 7): read the 12-byte wire format and pass the fields to validatedEvent.
        throw new UnsupportedOperationException("TODO (Task 7): event decoding");
    }

    private static Event validatedEvent(int sequence, int mediaTime, int type, int channel, int note, int velocity) {
        if (sequence < 0 || mediaTime < 0 || mediaTime > 10_000 || type > 1 || channel > 15 || note > 127
                || velocity > 127 || type == 1 && velocity == 0) {
            return null;
        }
        return new Event(sequence, mediaTime, type == 1, channel, note, velocity);
    }
}
