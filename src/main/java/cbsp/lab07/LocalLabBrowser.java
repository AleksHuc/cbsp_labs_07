package cbsp.lab07;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

final class LocalLabBrowser {
    private LocalLabBrowser() { }

    static void run(String directory, Map<String, HttpHandler> endpoints) throws IOException, InterruptedException {
        var routes = new HashMap<>(endpoints);
        routes.put("/", resource(directory + "/index.html", "text/html"));
        routes.put("/styles.css", resource("browser/styles.css", "text/css"));
        routes.put("/simulation.js", resource(directory + "/simulation.js", "text/javascript"));

        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                if (!exchange.getRequestMethod().equals("GET")) {
                    exchange.getResponseHeaders().set("Allow", "GET");
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                var route = routes.get(exchange.getRequestURI().getPath());
                if (route == null) {
                    exchange.sendResponseHeaders(404, -1);
                } else {
                    route.handle(exchange);
                }
            }
        });
        server.start();
        try {
            System.out.println("Open http://127.0.0.1:" + server.getAddress().getPort() + "/ in your browser.");
            System.out.println("Press Ctrl+C to stop the server.");
            new CountDownLatch(1).await();
        } finally {
            server.stop(0);
        }
    }

    static HttpHandler resource(String name, String contentType) throws IOException {
        byte[] body = LabResources.read(name);
        return exchange -> send(exchange, 200, contentType + "; charset=utf-8", body);
    }

    static void sendText(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        send(exchange, status, contentType + "; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }
}
