package cbsp.lab07;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;

public final class HttpChecks {
    private HttpChecks() { }

    public static void main(String[] args) throws Exception {
        equal("00123456", new TaskParameters("00123456").studentId(), "leading zeroes");
        verifyRanges();
        verifyHttp();
        System.out.println("Lab 07 HTTP checks passed.");
    }

    private static void verifyRanges() {
        selection(null, 200, 0, 99, null);
        selection("bytes=10-19", 206, 10, 19, "bytes 10-19/100");
        selection("BYTES=99-99", 206, 99, 99, "bytes 99-99/100");
        selection("bytes=10-", 206, 10, 99, "bytes 10-99/100");
        selection("bytes=95-110", 206, 95, 99, "bytes 95-99/100");
        selection("bytes=95-99999999999999999999", 206, 95, 99, "bytes 95-99/100");
        selection("bytes=100-", 416, 0, -1, "bytes */100");
        selection("bytes=99999999999999999999-", 416, 0, -1, "bytes */100");
        selection("bytes=20-10", 200, 0, 99, null);
        selection("bytes=-20", 200, 0, 99, null);
        selection("bytes=0-1,4-5", 200, 0, 99, null);
        selection("samples=0-9", 200, 0, 99, null);
        equal(new ByteRanges.Selection(416, 0, 0),
                ByteRanges.select(new ByteRanges.RequestedRange(120, null), 100),
                "a parsed range beyond EOF selects an empty 416 response");
        equal(new ByteRanges.Selection(416, 0, 0), ByteRanges.forRequest("bytes=0-0", 0),
                "no byte range is satisfiable in an empty file");
        equal(new ByteRanges.Selection(416, 0, 0),
                ByteRanges.forRequest("bytes=2147483648000-", Integer.MAX_VALUE),
                "the largest file still rejects a start outside the index type");
    }

    private static void selection(String header, int status, int first, int last, String contentRange) {
        int size = 100;
        var result = ByteRanges.forRequest(header, size);
        equal(new ByteRanges.Selection(status, first, last - first + 1), result, "selection for " + header);
        equal(contentRange, ByteRanges.contentRange(result, size), "Content-Range for " + header);
    }

    private static void verifyHttp() throws Exception {
        byte[] audio = LabResources.read("episode.mp3");
        if (audio.length <= 95_999) {
            throw new AssertionError("Recording must contain the entire lesson range.");
        }

        try (AudioServer server = new AudioServer(audio, null);
             HttpClient client = HttpClient.newHttpClient()) {
            var page = client.send(request(server, "/").GET().build(), HttpResponse.BodyHandlers.ofString());
            equal(200, page.statusCode(), "browser page status");
            if (!page.body().contains(AudioServer.RANGE_PATH) || !page.body().contains(AudioServer.FULL_PATH)) {
                throw new AssertionError("Browser page must include both audio routes.");
            }

            response(client, request(server, AudioServer.RANGE_PATH).GET().build(),
                    200, "bytes", null, audio);
            response(client, request(server, AudioServer.FULL_PATH).header("Range", "bytes=48000-95999").build(),
                    200, "none", null, audio);
            response(client, request(server, AudioServer.RANGE_PATH).header("Range", "bytes=48000-95999").build(),
                    206, "bytes", "bytes 48000-95999/" + audio.length, Arrays.copyOfRange(audio, 48_000, 96_000));
            response(client, request(server, AudioServer.RANGE_PATH).header("Range", "bytes=48000-").build(),
                    206, "bytes", "bytes 48000-" + (audio.length - 1) + "/" + audio.length,
                    Arrays.copyOfRange(audio, 48_000, audio.length));
            response(client, request(server, AudioServer.RANGE_PATH).header("Range", "bytes=" + audio.length + "-").build(),
                    416, "bytes", "bytes */" + audio.length, new byte[0]);

            var head = client.send(request(server, AudioServer.RANGE_PATH).header("Range", "bytes=0-1")
                    .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofByteArray());
            equal(200, head.statusCode(), "HEAD ignores Range");
            equal(null, head.headers().firstValue("Content-Range").orElse(null), "HEAD Content-Range");
            equal((long) audio.length, head.headers().firstValueAsLong("Content-Length").orElse(-1), "HEAD Content-Length");
            equal(0, head.body().length, "HEAD body length");
        }
    }

    private static HttpRequest.Builder request(AudioServer server, String path) {
        return HttpRequest.newBuilder(server.url(path)).timeout(Duration.ofSeconds(5));
    }

    private static void response(HttpClient client, HttpRequest request, int status, String acceptRanges,
                                 String contentRange, byte[] body) throws Exception {
        var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        String context = request.uri().getPath() + " " + request.headers().firstValue("Range").orElse("whole file");
        equal(status, response.statusCode(), context + " status");
        equal("audio/mpeg", response.headers().firstValue("Content-Type").orElse(null), context + " Content-Type");
        equal(acceptRanges, response.headers().firstValue("Accept-Ranges").orElse(null), context + " Accept-Ranges");
        equal(contentRange, response.headers().firstValue("Content-Range").orElse(null), context + " Content-Range");
        equal((long) body.length, response.headers().firstValueAsLong("Content-Length").orElse(-1),
                context + " Content-Length");
        if (!Arrays.equals(body, response.body())) {
            throw new AssertionError(context + " response body differs from expected audio bytes");
        }
    }

    private static void equal(Object expected, Object actual, String context) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(context + ": expected " + expected + ", got " + actual);
        }
    }
}
