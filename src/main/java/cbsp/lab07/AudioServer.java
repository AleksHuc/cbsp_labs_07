package cbsp.lab07;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Prepared loopback server for the audio comparison. */
final class AudioServer implements AutoCloseable {
    static final String RANGE_PATH = "/range/episode.mp3";
    static final String FULL_PATH = "/full/episode.mp3";
    private static final int CHUNK_BYTES = 4_096;
    private static final int BROWSER_BYTES_PER_SECOND = 24_000;

    private final HttpServer server;
    private final ExecutorService handlers;
    private final byte[] audio;
    private final byte[] page;
    private final byte[] sharedStyles;
    private final byte[] seekStyles;
    private final byte[] script;
    private final Path browserLog;
    private final BrowserAudioRequests browserRequests = new BrowserAudioRequests();
    private final AtomicInteger requestNumber = new AtomicInteger();

    AudioServer(byte[] audio, Path browserLog) throws IOException {
        this.audio = audio;
        this.page = LabResources.read("task1/index.html");
        this.sharedStyles = LabResources.read("browser/styles.css");
        this.seekStyles = LabResources.read("task1/seeking.css");
        this.script = LabResources.read("task1/seeking.js");
        this.browserLog = browserLog;
        if (browserLog != null) {
            Files.writeString(browserLog, Csv.row("request", "mode", "method", "range", "status",
                    "content_range", "content_length", "bytes_written", "complete", "elapsed_ms"));
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        handlers = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(handlers);
        server.createContext("/", this::handle);
        server.start();
    }

    URI url(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String method = exchange.getRequestMethod();
            boolean head = method.equals("HEAD");
            if (!head && !method.equals("GET")) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            switch (exchange.getRequestURI().getPath()) {
                case "/" -> sendAsset(exchange, "text/html", page, head);
                case "/styles.css" -> sendAsset(exchange, "text/css", sharedStyles, head);
                case "/seeking.css" -> sendAsset(exchange, "text/css", seekStyles, head);
                case "/seeking.js" -> sendAsset(exchange, "text/javascript", script, head);
                case "/api/requests" -> sendRequests(exchange, head);
                case RANGE_PATH -> sendAudio(exchange, true, head);
                case FULL_PATH -> sendAudio(exchange, false, head);
                default -> exchange.sendResponseHeaders(404, -1);
            }
        }
    }

    private void sendAudio(HttpExchange exchange, boolean supportsRange, boolean head) throws IOException {
        int number = requestNumber.incrementAndGet();
        long started = System.nanoTime();
        var ranges = exchange.getRequestHeaders().get("Range");
        String range = ranges == null ? null : String.join(", ", ranges);
        ByteRanges.Selection selection;
        String contentRange;
        try {
            selection = supportsRange && !head && !exchange.getRequestHeaders().containsKey("If-Range")
                    ? ByteRanges.forRequest(range, audio.length) : ByteRanges.Selection.full(audio.length);
            contentRange = ByteRanges.contentRange(selection, audio.length);
        } catch (UnsupportedOperationException ex) {
            byte[] message = Objects.toString(ex.getMessage(), "Task 1 is incomplete.")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            sendHeaders(exchange, 501, message.length, head);
            if (!head) {
                exchange.getResponseBody().write(message);
            }
            return;
        }
        int length = selection.length();

        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "audio/mpeg");
        headers.set("Accept-Ranges", supportsRange ? "bytes" : "none");
        if (contentRange != null) {
            headers.set("Content-Range", contentRange);
        }

        String view = BrowserAudioRequests.view(exchange.getRequestURI().getRawQuery());
        BrowserAudioRequests.Transfer transfer = null;
        int written = 0;
        boolean complete = false;
        try {
            sendHeaders(exchange, selection.status(), length, head);
            if (browserLog != null && view != null) {
                transfer = browserRequests.start(view, number, exchange.getRequestMethod(), range,
                        selection.status(), contentRange);
            }
            if (!head && length > 0) {
                try (OutputStream body = exchange.getResponseBody()) {
                    long sendingSince = System.nanoTime();
                    while (written < length) {
                        if (browserLog != null) {
                            waitForNextChunk(sendingSince, written);
                        }
                        int chunk = Math.min(CHUNK_BYTES, length - written);
                        body.write(audio, selection.first() + written, chunk);
                        written += chunk;
                    }
                }
            }
            complete = true;
        } catch (InterruptedException ex) {
            throw new InterruptedIOException("Audio response interrupted");
        } finally {
            if (transfer != null) {
                transfer.finish(complete);
            }
            if (browserLog != null) {
                appendLog(Csv.row(number, supportsRange ? "range" : "full", exchange.getRequestMethod(),
                        range, selection.status(), contentRange, length, written, complete,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)));
            }
        }
    }

    private void sendRequests(HttpExchange exchange, boolean head) throws IOException {
        String view = BrowserAudioRequests.view(exchange.getRequestURI().getRawQuery());
        if (view == null) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }
        byte[] json = browserRequests.json(view).getBytes(StandardCharsets.UTF_8);
        sendAsset(exchange, "application/json", json, head);
    }

    private static void sendAsset(HttpExchange exchange, String contentType, byte[] body, boolean head)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        sendHeaders(exchange, 200, body.length, head);
        if (!head) {
            exchange.getResponseBody().write(body);
        }
    }

    private static void sendHeaders(HttpExchange exchange, int status, int length, boolean head)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Length", Integer.toString(length));
        exchange.sendResponseHeaders(status, head || length == 0 ? -1 : length);
    }

    private static void waitForNextChunk(long started, int written) throws InterruptedException {
        long due = started + TimeUnit.SECONDS.toNanos(written) / BROWSER_BYTES_PER_SECOND;
        long remaining = due - System.nanoTime();
        if (remaining > 0) {
            TimeUnit.NANOSECONDS.sleep(remaining);
        }
    }

    private synchronized void appendLog(String row) throws IOException {
        Files.writeString(browserLog, row, StandardOpenOption.APPEND);
    }

    @Override
    public void close() {
        server.stop(0);
        handlers.shutdownNow();
        try {
            handlers.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
