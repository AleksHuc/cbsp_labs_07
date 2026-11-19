package cbsp.lab07;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.regex.Pattern;

/** Serves two prepared windows at the same live playlist URI. */
final class HlsServer implements AutoCloseable {
    private static final String MASTER_PATH = "/hls/master.m3u8";
    private static final Pattern VARIANT_PATH = Pattern.compile("/hls/(audio-(?:64|128))/(live\\.m3u8|segment-1[0-3]\\.ts)");

    private final HttpServer server;
    private volatile boolean reloaded;

    HlsServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hls/", this::handle);
        server.start();
    }

    URI masterUri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + MASTER_PATH);
    }

    void publishWindow(boolean reloaded) {
        this.reloaded = reloaded;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!exchange.getRequestMethod().equals("GET")) {
                exchange.getResponseHeaders().set("Allow", "GET");
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String resource = resourceFor(exchange.getRequestURI().getPath());
            if (resource == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            byte[] body = LabResources.read("task2/" + resource);
            exchange.getResponseHeaders().set("Content-Type", resource.endsWith(".m3u8")
                    ? "application/vnd.apple.mpegurl" : "video/mp2t");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        }
    }

    private String resourceFor(String path) {
        if (path.equals(MASTER_PATH)) {
            return "master.m3u8";
        }
        var match = VARIANT_PATH.matcher(path);
        if (!match.matches()) {
            return null;
        }
        String file = match.group(2);
        if (file.equals("live.m3u8")) {
            file = reloaded ? "reload.m3u8" : "initial.m3u8";
        }
        return match.group(1) + "/" + file;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
