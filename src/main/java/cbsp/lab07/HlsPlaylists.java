package cbsp.lab07;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class HlsPlaylists {
    private static final String STREAM_INF = "#EXT-X-STREAM-INF:";
    private static final String MEDIA_SEQUENCE = "#EXT-X-MEDIA-SEQUENCE:";
    private static final String EXTINF = "#EXTINF:";
    private static final Pattern ATTRIBUTE = Pattern.compile("([A-Z0-9-]+)=(\"[^\"]*\"|[^,]+)(?:,|$)");

    private HlsPlaylists() { }

    record Variant(long bandwidthBps, URI playlistUri) { }

    record MediaEntry(double durationSeconds, URI uri) { }

    record MediaWindow(long firstSequence, List<MediaEntry> entries) { }

    record Segment(long sequence, double durationSeconds, URI uri) { }

    static List<Variant> variants(URI playlistUri, String text) {
        List<Variant> variants = new ArrayList<>();
        Long bandwidth = null;
        for (String line : playlistLines(text)) {
            if (line.startsWith(STREAM_INF)) {
                if (bandwidth != null) {
                    throw new IllegalArgumentException("Variant tag is missing its playlist URI");
                }
                String value = attribute(line.substring(STREAM_INF.length()), "BANDWIDTH");
                bandwidth = Long.parseLong(value);
                if (bandwidth <= 0) {
                    throw new IllegalArgumentException("BANDWIDTH must be positive");
                }
            } else if (!line.startsWith("#")) {
                if (bandwidth == null) {
                    throw new IllegalArgumentException("Playlist URI is missing its variant tag: " + line);
                }
                variants.add(new Variant(bandwidth, resolveUri(playlistUri, line)));
                bandwidth = null;
            }
        }
        if (bandwidth != null || variants.isEmpty()) {
            throw new IllegalArgumentException("Expected a complete variant and playlist URI");
        }
        return List.copyOf(variants);
    }

    static MediaWindow readMedia(URI playlistUri, String text) {
        List<MediaEntry> entries = new ArrayList<>();
        long firstSequence = 0;
        boolean sequencePresent = false;
        Double duration = null;
        for (String line : playlistLines(text)) {
            if (line.startsWith(MEDIA_SEQUENCE)) {
                firstSequence = Long.parseLong(line.substring(MEDIA_SEQUENCE.length()));
                if (sequencePresent || firstSequence < 0 || !entries.isEmpty() || duration != null) {
                    throw new IllegalArgumentException("Invalid first media sequence number");
                }
                sequencePresent = true;
            } else if (line.startsWith(EXTINF)) {
                int comma = line.indexOf(',', EXTINF.length());
                if (duration != null || comma < 0) {
                    throw new IllegalArgumentException("Expected an EXTINF duration followed by a segment URI");
                }
                duration = Double.parseDouble(line.substring(EXTINF.length(), comma));
                if (!Double.isFinite(duration) || duration <= 0) {
                    throw new IllegalArgumentException("EXTINF duration must be positive and finite");
                }
            } else if (!line.startsWith("#")) {
                if (duration == null) {
                    throw new IllegalArgumentException("Segment URI is missing its EXTINF duration: " + line);
                }
                entries.add(new MediaEntry(duration, resolveUri(playlistUri, line)));
                duration = null;
            }
        }
        if (duration != null) {
            throw new IllegalArgumentException("EXTINF is missing its segment URI");
        }
        return new MediaWindow(firstSequence, List.copyOf(entries));
    }

    static List<Segment> segments(MediaWindow window) {
        // TODO (Task 2): assign consecutive sequence numbers to the prepared media entries.
        throw new UnsupportedOperationException("TODO (Task 2): media sequence numbering");
    }

    static List<Segment> pending(List<Segment> playlist, Set<Long> downloaded) {
        // TODO (Task 2): retain segments that have not been downloaded, in playlist order.
        throw new UnsupportedOperationException("TODO (Task 2): pending segment selection");
    }

    private static URI resolveUri(URI playlistUri, String reference) {
        if (reference.startsWith("?")) {
            // Query-only references keep the full base path, including any escaped characters.
            String base = playlistUri.toString().split("[?#]", 2)[0];
            return URI.create(base + reference);
        }
        return playlistUri.resolve(reference);
    }

    private static List<String> playlistLines(String text) {
        List<String> lines = text.lines().filter(line -> !line.isBlank()).toList();
        if (lines.isEmpty() || !lines.getFirst().equals("#EXTM3U")) {
            throw new IllegalArgumentException("Expected an #EXTM3U header");
        }
        return lines.subList(1, lines.size());
    }

    private static String attribute(String attributes, String name) {
        var matcher = ATTRIBUTE.matcher(attributes);
        while (matcher.find()) {
            if (matcher.group(1).equals(name)) {
                return matcher.group(2);
            }
        }
        throw new IllegalArgumentException("Missing " + name + " attribute");
    }
}
