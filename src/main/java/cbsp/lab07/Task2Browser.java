package cbsp.lab07;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Pattern;

final class Task2Browser {
    private static final Pattern WINDOW_QUERY = Pattern.compile("variant=([0-9]{1,2})&window=(initial|reload)&downloaded=([0-9,]*)");

    private Task2Browser() { }

    private record WindowRequest(int variant, String snapshot, Set<Long> downloaded) { }

    static void run() throws IOException, InterruptedException {
        try (var server = new HlsServer();
             var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            var variants = Task2.variants(client, server.masterUri());
            LocalLabBrowser.run("task2", Map.of(
                    "/hls.css", LocalLabBrowser.resource("task2/hls.css", "text/css"),
                    "/api/variants", exchange -> LocalLabBrowser.sendText(exchange, 200, "application/json", variantsJson(variants)),
                    "/api/window", exchange -> sendWindow(exchange, client, server, variants)));
        }
    }

    private static void sendWindow(HttpExchange exchange, HttpClient client, HlsServer server,
                                   List<HlsPlaylists.Variant> variants) throws IOException {
        WindowRequest request;
        try {
            request = readRequest(exchange.getRequestURI().getQuery(), variants.size());
        } catch (IllegalArgumentException ex) {
            LocalLabBrowser.sendText(exchange, 400, "text/plain", "Invalid variant, playlist window or download history.");
            return;
        }

        var variant = variants.get(request.variant());
        String response;
        try {
            Task2.Window window;
            synchronized (server) {
                server.publishWindow(request.snapshot().equals("reload"));
                window = Task2.downloadWindow(client, variant.playlistUri(), request.snapshot(), request.downloaded());
            }
            response = windowJson(window);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("The HLS request was interrupted", ex);
        } catch (IOException ex) {
            LocalLabBrowser.sendText(exchange, 502, "text/plain", ex.getMessage());
            return;
        }
        LocalLabBrowser.sendText(exchange, 200, "application/json", response);
    }

    private static WindowRequest readRequest(String query, int variantCount) {
        var match = WINDOW_QUERY.matcher(query == null ? "" : query);
        if (!match.matches()) {
            throw new IllegalArgumentException();
        }
        int variant = Integer.parseInt(match.group(1));
        if (variant >= variantCount) {
            throw new IllegalArgumentException();
        }
        Set<Long> downloaded = new HashSet<>();
        String previous = match.group(3);
        if (!previous.isEmpty()) {
            for (String item : previous.split(",", -1)) {
                downloaded.add(Long.parseLong(item));
            }
        }
        return new WindowRequest(variant, match.group(2), downloaded);
    }

    private static String variantsJson(List<HlsPlaylists.Variant> variants) {
        var rows = new StringJoiner(",", "[", "]");
        for (var variant : variants) {
            rows.add("{\"bandwidthBps\":" + variant.bandwidthBps() + ",\"path\":\"" + variant.playlistUri().getRawPath() + "\"}");
        }
        return rows.toString();
    }

    private static String windowJson(Task2.Window window) throws IOException {
        var segments = new StringJoiner(",", "[", "]");
        for (var segment : window.segments()) {
            segments.add("{\"sequence\":" + segment.sequence() + ",\"durationSeconds\":" + segment.durationSeconds()
                    + ",\"path\":\"" + segment.uri().getRawPath() + "\"}");
        }
        var downloads = new StringJoiner(",", "[", "]");
        var encoder = Base64.getEncoder();
        for (var download : window.downloads()) {
            String pcm = encoder.encodeToString(Task2Preview.pcm(download.bytes()));
            downloads.add("{\"sequence\":" + download.segment().sequence() + ",\"bytes\":" + download.bytes().length
                    + ",\"pcm\":\"" + pcm + "\"}");
        }
        return "{\"segments\":" + segments + ",\"downloads\":" + downloads
                + ",\"previewSampleRate\":" + Task2Preview.SAMPLE_RATE + "}";
    }
}
