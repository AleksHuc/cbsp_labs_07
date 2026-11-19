package cbsp.lab07;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;

final class Task1 {
    private Task1() { }

    static void run(Path output, boolean browser) throws IOException, InterruptedException {
        byte[] audio = LabResources.read("episode.mp3");
        Path report = output.resolve("task1-http.csv");
        try (AudioServer server = new AudioServer(audio, null);
             HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            StringBuilder rows = new StringBuilder(Csv.row("mode", "range", "status", "content_range",
                    "content_length", "body_bytes"));
            for (String path : List.of(AudioServer.RANGE_PATH, AudioServer.FULL_PATH)) {
                compare(rows, server, client, path, null);
                compare(rows, server, client, path, "bytes=48000-95999");
                compare(rows, server, client, path, "bytes=48000-");
            }
            Files.writeString(report, rows);
        }
        System.out.println("Wrote " + report);
        if (browser) {
            browse(audio, output.resolve("task1-browser-http.csv"));
        }
    }

    private static void compare(StringBuilder rows, AudioServer server, HttpClient client, String path,
                                String range) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(server.url(path)).timeout(Duration.ofSeconds(5));
        if (range != null) {
            request.header("Range", range);
        }
        var response = client.send(request.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() == 501) {
            throw new UnsupportedOperationException(new String(response.body(), StandardCharsets.UTF_8));
        }
        rows.append(Csv.row(path.equals(AudioServer.RANGE_PATH) ? "range" : "full", range,
                response.statusCode(), response.headers().firstValue("Content-Range").orElse(""),
                response.headers().firstValue("Content-Length").orElse(""), response.body().length));
    }

    private static void browse(byte[] audio, Path log) throws IOException, InterruptedException {
        try (AudioServer server = new AudioServer(audio, log)) {
            Runtime runtime = Runtime.getRuntime();
            Thread shutdown = new Thread(server::close, "lab07-http-shutdown");
            runtime.addShutdownHook(shutdown);
            try {
                System.out.println("Open " + server.url("/") + " in your browser.");
                System.out.println("HTTP log: " + log);
                System.out.println("Press Ctrl+C to stop the server.");
                new CountDownLatch(1).await();
            } finally {
                runtime.removeShutdownHook(shutdown);
            }
        }
    }
}
