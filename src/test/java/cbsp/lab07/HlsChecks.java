package cbsp.lab07;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class HlsChecks {
    private HlsChecks() { }

    public static void main(String[] args) throws Exception {
        verifyPlaylists();
        verifyDownloads();
        verifyAudioPreview("audio-64/segment-10.ts", "871cdb8de5128a6a4b11bb5d6a2d177374b57b2a5458120feaaa29abf34fea4c");
        verifyAudioPreview("audio-128/segment-13.ts", "b7487b64759521e8534fd18dd4d85d4f25a2bb795947f2fa94e3846b742ea6a8");
        verifyPcmPreviews();
        System.out.println("Lab 07 HLS checks passed.");
    }

    private static void verifyPlaylists() {
        URI master = URI.create("http://localhost/hls/master.m3u8");
        URI narrow = master.resolve("narrow/live.m3u8");
        URI wide = master.resolve("wide/live.m3u8");
        var variants = HlsPlaylists.variants(master, """
                #EXTM3U
                #EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=64000,CODECS="mp4a.40.2,ac-3",BANDWIDTH=76000
                narrow/live.m3u8

                #EXT-X-STREAM-INF:BANDWIDTH=150000
                wide/live.m3u8
                """);
        equal(List.of(new HlsPlaylists.Variant(76_000, narrow), new HlsPlaylists.Variant(150_000, wide)),
                variants, "variant order, advertised bandwidth and resolved URI");

        var window = HlsPlaylists.readMedia(narrow, """
                #EXTM3U
                #EXT-X-MEDIA-SEQUENCE:71
                #EXTINF:1.5,A title, with a comma
                z.ts
                #EXTINF:2.25,
                a.ts
                #EXTINF:2.0,
                ../shared/chime.ts
                """);
        var segments = HlsPlaylists.segments(window);
        equal(List.of(new HlsPlaylists.Segment(71, 1.5, narrow.resolve("z.ts")),
                      new HlsPlaylists.Segment(72, 2.25, narrow.resolve("a.ts")),
                      new HlsPlaylists.Segment(73, 2.0, narrow.resolve("../shared/chime.ts"))),
                segments, "numbered segments, durations and resolved URIs in playlist order");
        equal(List.of(segments.getFirst(), segments.getLast()), HlsPlaylists.pending(segments, Set.of(72L)),
                "pending segments when download history has a gap");
        equal(List.of(), HlsPlaylists.pending(segments, Set.of(71L, 72L, 73L)), "unchanged window");

        var implicitWindow = HlsPlaylists.readMedia(narrow, "#EXTM3U\r\n#EXTINF:2.0,\r\nfirst.ts\r\n");
        equal(0L, HlsPlaylists.segments(implicitWindow).getFirst().sequence(), "default first sequence number");

        URI queryVariant = HlsPlaylists.variants(master, "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=76000\n?variant=a\n")
                .getFirst().playlistUri();
        equal(URI.create("http://localhost/hls/master.m3u8?variant=a"), queryVariant, "query-only variant URI");

        URI encoded = URI.create("http://localhost/hls/a%2Fb.m3u8?old=1#clip");
        URI querySegment = HlsPlaylists.readMedia(encoded, "#EXTM3U\n#EXTINF:2.0,\n?segment=71\n")
                .entries().getFirst().uri();
        equal(URI.create("http://localhost/hls/a%2Fb.m3u8?segment=71"), querySegment, "query-only segment URI");

        try {
            HlsPlaylists.readMedia(narrow, """
                    #EXTM3U
                    #EXT-X-MEDIA-SEQUENCE:70
                    #EXT-X-MEDIA-SEQUENCE:71
                    #EXTINF:2.0,
                    first.ts
                    """);
            throw new AssertionError("A media playlist must reject repeated sequence tags");
        } catch (IllegalArgumentException expected) {
            // A repeated sequence tag leaves the first segment number ambiguous.
        }
    }

    private static void verifyDownloads() throws Exception {
        Path output = Files.createTempDirectory("lab07-hls-check-");
        try {
            Task2.run(output, true);
            equal(List.of("playlist_uri,bandwidth_bps,selected", "/hls/audio-64/live.m3u8,75751,true",
                          "/hls/audio-128/live.m3u8,148501,false"),
                    Files.readAllLines(output.resolve("task2-variants.csv")), "fixture variants and selection");

            List<String> rows = Files.readAllLines(output.resolve("task2-segments.csv"));
            equal(5, rows.size(), "one CSV header and four downloads");
            Path media = output.resolve("task2-media");
            for (int sequence = 10; sequence <= 13; sequence++) {
                String filename = "segment-" + sequence + ".ts";
                String snapshot = sequence == 13 ? "reload" : "initial";
                String duration = sequence == 13 ? "1.984" : "2.005333";
                byte[] expected = LabResources.read("task2/audio-64/" + filename);
                String[] columns = rows.get(sequence - 9).split(",");
                equal(List.of(snapshot, "/hls/audio-64/live.m3u8", Integer.toString(sequence),
                              "/hls/audio-64/" + filename, duration, Integer.toString(expected.length)),
                        Arrays.asList(columns), "download row for " + filename);
                if (!Arrays.equals(expected, Files.readAllBytes(media.resolve(filename)))) {
                    throw new AssertionError("Saved segment differs from fixture: " + filename);
                }
            }

            Path vod = media.resolve("index.m3u8");
            String text = Files.readString(vod);
            if (!text.contains("#EXT-X-ENDLIST\n")) {
                throw new AssertionError("Exported media playlist must be finite");
            }
            var window = HlsPlaylists.readMedia(vod.toUri(), text);
            equal(List.of(10L, 11L, 12L, 13L), HlsPlaylists.segments(window).stream()
                    .map(HlsPlaylists.Segment::sequence).toList(), "exported media playlist order");
        } finally {
            try (var paths = Files.walk(output)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    private static void equal(Object expected, Object actual, String context) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(context + ": expected " + expected + ", got " + actual);
        }
    }

    private static void verifyAudioPreview(String segment, String expectedHash) throws Exception {
        byte[] audio = TsAac.extract(LabResources.read("task2/" + segment));
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(audio);
        equal(expectedHash, HexFormat.of().formatHex(digest), "ADTS audio extracted from " + segment);
    }

    private static void verifyPcmPreviews() throws Exception {
        for (String variant : List.of("audio-64", "audio-128")) {
            for (int sequence = 10; sequence <= 13; sequence++) {
                String resource = "task2/" + variant + "/segment-" + sequence + ".ts";
                byte[] pcm = Task2Preview.pcm(LabResources.read(resource));
                int sampleFrames = (sequence == 13 ? 93 : 94) * 1_024;
                equal(sampleFrames * 2, pcm.length, "PCM sample count for " + resource);
            }
        }
    }
}
