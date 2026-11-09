package dev.fosk.additems.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Serves only the generated resource pack, publicly, at /pack/&lt;sha1&gt;.zip. Either on its own port (so the dashboard can
 * stay on loopback while players still download the pack) or, with {@link #serve}, through the dashboard's port.
 */
public final class PackHost {

    private record Served(String sha1, Path file) {}

    private final String bind;
    private final int port;
    private final AtomicReference<Served> current = new AtomicReference<>();
    private HttpServer server;

    public PackHost(String bind, int port) {
        this.bind = bind;
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        server.createContext("/pack/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "AddItems-PackHost");
            t.setDaemon(true);
            return t;
        }));
        server.start();
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    public void publish(String sha1, Path file) {
        current.set(new Served(sha1, file));
    }

    private void handle(HttpExchange ex) throws IOException {
        serve(ex);
    }

    /** Answers one request for the pack (200 with the zip, else 404) and closes the exchange. */
    public void serve(HttpExchange ex) throws IOException {
        try {
            Served served = current.get();
            String path = ex.getRequestURI().getPath();
            if (served == null || !ex.getRequestMethod().equals("GET")
                    || !path.equals("/pack/" + served.sha1() + ".zip")) {
                ex.sendResponseHeaders(404, -1);
                return;
            }
            byte[] bytes = Files.readAllBytes(served.file());
            ex.getResponseHeaders().set("Content-Type", "application/zip");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
        } finally {
            ex.close();
        }
    }
}
