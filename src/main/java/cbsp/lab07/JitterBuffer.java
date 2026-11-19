package cbsp.lab07;

final class JitterBuffer {
    private JitterBuffer() { }

    enum Outcome { ON_TIME, LATE, LOST }

    record Decision(long deadlineMs, Outcome outcome) { }

    static Decision decide(long mediaTimeMs, long delayMs, Long arrivalMs) {
        // TODO (Task 4): compute the deadline and classify the expected packet.
        throw new UnsupportedOperationException("TODO (Task 4): packet deadline and outcome");
    }
}
