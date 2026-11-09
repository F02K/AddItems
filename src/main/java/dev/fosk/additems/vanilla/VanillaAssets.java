package dev.fosk.additems.vanilla;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Fetches Mojang's official client.jar for the server's Minecraft version once, extracts only the item/block
 * textures, models, item definitions and English language file into the plugin's cache folder, and serves
 * icons from there. Mojang's assets are never bundled with the plugin or committed to the repository; they
 * are downloaded to the server operator's machine directly from Mojang (see docs/minecraft-versions.md).
 */
public final class VanillaAssets {

    public enum State { DISABLED, MISSING, DOWNLOADING, READY, FAILED }

    static final String MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String[] KEEP_PREFIXES = {
            "textures/item/", "textures/block/", "models/item/", "models/block/", "items/"};
    private static final String LANG_FILE = "lang/en_us.json";
    private static final long MAX_ENTRY_BYTES = 4L * 1024 * 1024;
    private static final int MAX_ENTRIES = 30_000;

    private final Path root;
    private final Path cacheDir;
    private final String version;
    private final Logger log;
    private final boolean enabled;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build();

    private volatile State state;
    private volatile String message = "";
    private volatile VanillaIcons icons;

    public VanillaAssets(Path dataFolder, String version, boolean enabled, Logger log) {
        this.cacheDir = dataFolder.resolve("cache");
        this.root = cacheDir.resolve("vanilla").resolve(version);
        this.version = version;
        this.enabled = enabled;
        this.log = log;
        if (Files.isRegularFile(root.resolve(".ready"))) {
            state = State.READY;
            icons = new VanillaIcons(root);
        } else {
            state = enabled ? State.MISSING : State.DISABLED;
            message = enabled ? "" : "Disabled in config (vanilla-assets.enabled: false).";
        }
    }

    public State state() { return state; }
    public String message() { return message; }
    public String version() { return version; }
    /** Icon/name lookups once the assets are ready, otherwise null. */
    public VanillaIcons icons() { return icons; }

    /** Starts the download on the calling thread if needed. Call it from an async task. */
    public void ensureReady() {
        if (state == State.READY || state == State.DISABLED || state == State.DOWNLOADING) return;
        state = State.DOWNLOADING;
        message = "Downloading vanilla textures from Mojang (one time)...";
        log.info("Downloading Minecraft " + version + " assets from Mojang (one time, kept in " + root + ")");
        try {
            download();
            icons = new VanillaIcons(root);
            state = State.READY;
            message = "";
            log.info("Vanilla textures ready.");
        } catch (Exception e) {
            state = State.FAILED;
            message = "Could not download vanilla textures: " + e.getMessage();
            log.log(Level.WARNING, message, e);
        }
    }

    // ------------------------------------------------------------------ download

    private void download() throws Exception {
        JsonObject manifest = getJson(MANIFEST_URL);
        String versionUrl = null;
        JsonArray versions = manifest.getAsJsonArray("versions");
        for (JsonElement el : versions) {
            JsonObject v = el.getAsJsonObject();
            if (version.equals(v.get("id").getAsString())) versionUrl = v.get("url").getAsString();
        }
        if (versionUrl == null) throw new IOException("Minecraft " + version + " not found in Mojang's version manifest");

        JsonObject client = getJson(versionUrl).getAsJsonObject("downloads").getAsJsonObject("client");
        String url = client.get("url").getAsString();
        String sha1 = client.get("sha1").getAsString();

        Files.createDirectories(cacheDir);
        Path jar = Files.createTempFile(cacheDir, "client-", ".jar");
        try {
            String actual = fetchTo(url, jar);
            if (!actual.equalsIgnoreCase(sha1)) {
                throw new IOException("checksum mismatch (expected " + sha1 + ", got " + actual + ")");
            }
            Path tmpRoot = cacheDir.resolve("vanilla").resolve(version + ".tmp");
            deleteTree(tmpRoot);
            extract(jar, tmpRoot);
            Files.writeString(tmpRoot.resolve(".ready"), sha1);
            deleteTree(root);
            Files.createDirectories(root.getParent());
            Files.move(tmpRoot, root, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(jar);
        }
    }

    private JsonObject getJson(String url) throws IOException, InterruptedException {
        requireMojang(url);
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode() + " for " + url);
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    /** Streams the URL into the file and returns its SHA-1. */
    private String fetchTo(String url, Path target) throws Exception {
        requireMojang(url);
        HttpResponse<InputStream> response = http.send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(10)).build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode() + " for " + url);
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream in = new DigestInputStream(response.body(), digest);
             OutputStream out = Files.newOutputStream(target)) {
            in.transferTo(out);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Only talk to Mojang over HTTPS, even if a JSON document tells us otherwise. */
    static void requireMojang(String url) throws IOException {
        URI uri = URI.create(url);
        String host = uri.getHost();
        if (!"https".equals(uri.getScheme()) || host == null || !(host.equals("mojang.com") || host.endsWith(".mojang.com"))) {
            throw new IOException("refusing to download from " + url);
        }
    }

    // ------------------------------------------------------------------ extraction

    /** Copies the needed files out of the jar. Anything outside the allowed folders is ignored (zip-slip safe). */
    static void extract(Path jar, Path targetRoot) throws IOException {
        Path base = targetRoot.toAbsolutePath().normalize();
        Files.createDirectories(base);
        int count = 0;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (var entries = zip.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith("assets/minecraft/")) continue;
                String rel = name.substring("assets/minecraft/".length());
                if (!wanted(rel)) continue;
                if (++count > MAX_ENTRIES) throw new IOException("too many entries in client.jar");

                Path out = base.resolve(rel).normalize();
                if (!out.startsWith(base)) continue; // path traversal attempt
                if (entry.getSize() > MAX_ENTRY_BYTES) continue;
                Files.createDirectories(out.getParent());
                try (InputStream in = zip.getInputStream(entry)) {
                    byte[] data = in.readNBytes((int) MAX_ENTRY_BYTES + 1);
                    if (data.length > MAX_ENTRY_BYTES) continue;
                    Files.write(out, data);
                }
            }
        }
    }

    private static boolean wanted(String rel) {
        if (rel.contains("..") || rel.contains("\\")) return false;
        if (rel.equals(LANG_FILE)) return true;
        boolean prefix = false;
        for (String p : KEEP_PREFIXES) if (rel.startsWith(p)) prefix = true;
        return prefix && (rel.endsWith(".png") || rel.endsWith(".json") || rel.endsWith(".mcmeta"));
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) walk.sorted(java.util.Comparator.reverseOrder())::iterator) Files.delete(p);
        }
    }
}
