package cbsp.lab07;

import java.math.BigInteger;
import java.util.regex.Pattern;

/** The single byte-range policy for the lab's audio resource. */
final class ByteRanges {
    private static final Pattern SINGLE_RANGE = Pattern.compile("bytes=([0-9]+)-([0-9]*)",
            Pattern.CASE_INSENSITIVE);
    private static final BigInteger MAX_POSITION = BigInteger.valueOf(Integer.MAX_VALUE);

    private ByteRanges() { }

    // The bounds are inclusive. A null last byte means that the range is open-ended.
    record RequestedRange(int first, Integer last) { }

    record Selection(int status, int first, int length) {
        static Selection full(int size) {
            return new Selection(200, 0, size);
        }

        int last() {
            return first + length - 1;
        }
    }

    static Selection select(RequestedRange range, int size) {
        if (range != null && range.first() >= size) {
            return new Selection(416, 0, 0);
        }

        // TODO (Task 1): select the whole file or compute the start and length of the partial response.
        throw new UnsupportedOperationException("TODO (Task 1): byte selection");
    }

    static Selection forRequest(String header, int size) {
        var match = SINGLE_RANGE.matcher(header == null ? "" : header.strip());
        if (!match.matches()) {
            return select(null, size);
        }

        BigInteger first = new BigInteger(match.group(1));
        String lastField = match.group(2);
        BigInteger last = lastField.isEmpty() ? null : new BigInteger(lastField);
        if (last != null && last.compareTo(first) < 0) {
            return select(null, size);
        }
        // An index above the int limit still lies beyond the last byte of any supported file.
        int requestedFirst = first.min(MAX_POSITION).intValueExact();
        Integer requestedLast = last == null ? null : last.min(MAX_POSITION).intValueExact();
        return select(new RequestedRange(requestedFirst, requestedLast), size);
    }

    static String contentRange(Selection selection, int size) {
        return switch (selection.status()) {
            case 206 -> "bytes " + selection.first() + "-" + selection.last() + "/" + size;
            case 416 -> "bytes */" + size;
            default -> null;
        };
    }
}
