package cbsp.lab07;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class Task2 {
    private Task2() { }

    record Download(String snapshot, HlsPlaylists.Segment segment, byte[] bytes) { }

    record Window(List<HlsPlaylists.Segment> segments, List<Download> downloads) { }

    static void run(Path output, boolean saveMedia) throws IOException, InterruptedException {
        List<HlsPlaylists.Variant> variants;
        HlsPlaylists.Variant selected;
        List<Download> downloads = new ArrayList<>();
        Set<Long> downloaded = new HashSet<>();

        try (HlsServer server = new HlsServer();
             HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            variants = variants(client, server.masterUri());
            selected = variants.stream().min(Comparator.comparingLong(HlsPlaylists.Variant::bandwidthBps))
                    .orElseThrow(() -> new IOException("The master playlist has no variants"));

            downloads.addAll(downloadWindow(client, selected.playlistUri(), "initial", downloaded).downloads());
            server.publishWindow(true);
            downloads.addAll(downloadWindow(client, selected.playlistUri(), "reload", downloaded).downloads());
        }

        writeCsv(output, variants, selected, downloads);
        if (saveMedia) {
            saveMedia(output.resolve("task2-media"), downloads);
        }
    }

    static List<HlsPlaylists.Variant> variants(HttpClient client, URI masterUri) throws IOException, InterruptedException {
        return HlsPlaylists.variants(masterUri, playlist(client, masterUri));
    }

    static Window downloadWindow(HttpClient client, URI mediaUri, String snapshot, Set<Long> downloaded)
            throws IOException, InterruptedException {
        var window = HlsPlaylists.readMedia(mediaUri, playlist(client, mediaUri));
        var segments = HlsPlaylists.segments(window);
        List<Download> downloads = new ArrayList<>();
        for (var segment : HlsPlaylists.pending(segments, downloaded)) {
            byte[] bytes = get(client, segment.uri());
            downloads.add(new Download(snapshot, segment, bytes));
            downloaded.add(segment.sequence());
        }
        return new Window(segments, List.copyOf(downloads));
    }

    private static String playlist(HttpClient client, URI uri) throws IOException, InterruptedException {
        return new String(get(client, uri), StandardCharsets.UTF_8);
    }

    private static byte[] get(HttpClient client, URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("GET " + uri.getPath() + " returned " + response.statusCode());
        }
        return response.body();
    }

    private static void writeCsv(Path output, List<HlsPlaylists.Variant> variants, HlsPlaylists.Variant selected,
                                 List<Download> downloads) throws IOException {
        StringBuilder variantRows = new StringBuilder(Csv.row("playlist_uri", "bandwidth_bps", "selected"));
        for (var variant : variants) {
            variantRows.append(Csv.row(variant.playlistUri().getPath(), variant.bandwidthBps(), variant.equals(selected)));
        }

        StringBuilder segmentRows = new StringBuilder(Csv.row("snapshot", "playlist_uri", "media_sequence",
                "segment_uri", "duration_seconds", "body_bytes"));
        for (var download : downloads) {
            var segment = download.segment();
            segmentRows.append(Csv.row(download.snapshot(), selected.playlistUri().getPath(), segment.sequence(),
                    segment.uri().getPath(), segment.durationSeconds(), download.bytes().length));
        }

        Path variantsCsv = output.resolve("task2-variants.csv");
        Path segmentsCsv = output.resolve("task2-segments.csv");
        Files.writeString(variantsCsv, variantRows);
        Files.writeString(segmentsCsv, segmentRows);
        System.out.println("Wrote " + variantsCsv + " and " + segmentsCsv);
    }

    private static void saveMedia(Path folder, List<Download> downloads) throws IOException {
        if (downloads.isEmpty()) {
            throw new IOException("No downloaded segments to save");
        }
        Files.createDirectories(folder);
        long targetDuration = downloads.stream().mapToLong(row -> Math.round(row.segment().durationSeconds()))
                .max().orElseThrow();
        StringBuilder index = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-PLAYLIST-TYPE:VOD\n");
        index.append("#EXT-X-TARGETDURATION:").append(targetDuration).append('\n');
        index.append("#EXT-X-MEDIA-SEQUENCE:").append(downloads.getFirst().segment().sequence()).append('\n');
        for (var download : downloads) {
            var segment = download.segment();
            String name = "segment-" + segment.sequence() + ".ts";
            Files.write(folder.resolve(name), download.bytes());
            index.append("#EXTINF:").append(segment.durationSeconds()).append(",\n").append(name).append('\n');
        }
        Path playlist = folder.resolve("index.m3u8");
        Files.writeString(playlist, index.append("#EXT-X-ENDLIST\n"));
        System.out.println("Wrote " + playlist);
    }
}
