package dev.fosk.additems.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PackHostTest {

    @TempDir
    Path dir;

    @Test
    void servesOnlyTheCurrentPackUnderItsHashName() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Path zip = dir.resolve("pack.zip");
        Files.write(zip, new byte[]{1, 2, 3, 4});

        PackHost host = new PackHost("127.0.0.1", port);
        host.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + port;

            assertEquals(404, status(client, base + "/pack/abc.zip"), "nothing published yet");

            host.publish("abc", zip);
            HttpResponse<byte[]> ok = client.send(HttpRequest.newBuilder(URI.create(base + "/pack/abc.zip")).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, ok.statusCode());
            assertArrayEquals(new byte[]{1, 2, 3, 4}, ok.body());

            assertEquals(404, status(client, base + "/pack/other.zip"));
            assertEquals(404, status(client, base + "/pack/../pack.zip"));
        } finally {
            host.stop();
        }
    }

    private static int status(HttpClient client, String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
