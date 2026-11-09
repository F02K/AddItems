package dev.fosk.additems.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Stores how the team arranged the item graph (node positions and flow direction) in
 * plugins/AddItems/graph-layout.json so everybody sees the same picture. Input is validated and rebuilt from scratch,
 * never stored as received.
 */
public final class GraphLayoutStore {

    public static final int MAX_BODY_BYTES = 512 * 1024;
    private static final int MAX_NODES = 5000;
    private static final double LIMIT = 100_000;
    private static final Set<String> DIRECTIONS = Set.of("LR", "RL", "TB", "BT");
    /** Node ids look like "item:ruby", "recipe:pick" or "source:MOB:minecraft:zombie" (see web/src/lib/graph.js). */
    private static final Pattern NODE_ID =
            Pattern.compile("(item|vanilla|tag|recipe|loot|source):[A-Za-z0-9_:./#*-]{1,150}");
    private static final Gson GSON = new GsonBuilder().create();

    private final Path file;
    private volatile JsonObject current;

    public GraphLayoutStore(Path dataFolder) {
        this.file = dataFolder.resolve("graph-layout.json");
        this.current = load();
    }

    public JsonObject get() {
        return current;
    }

    /** Validates and stores a layout. Returns an error message or null on success. */
    public synchronized String save(String json) throws IOException {
        JsonObject clean;
        try {
            clean = sanitize(JsonParser.parseString(json));
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (RuntimeException e) {
            return "invalid JSON";
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(clean), StandardCharsets.UTF_8);
        current = clean;
        return null;
    }

    static JsonObject sanitize(JsonElement input) {
        if (!input.isJsonObject()) throw new IllegalArgumentException("layout must be an object");
        JsonObject in = input.getAsJsonObject();

        String direction = in.has("direction") && in.get("direction").isJsonPrimitive() ? in.get("direction").getAsString() : "LR";
        if (!DIRECTIONS.contains(direction)) throw new IllegalArgumentException("direction must be LR, RL, TB or BT");

        JsonObject positions = new JsonObject();
        if (in.has("positions")) {
            if (!in.get("positions").isJsonObject()) throw new IllegalArgumentException("positions must be an object");
            JsonObject given = in.getAsJsonObject("positions");
            if (given.size() > MAX_NODES) throw new IllegalArgumentException("too many nodes");
            for (var entry : given.entrySet()) {
                if (!NODE_ID.matcher(entry.getKey()).matches() || entry.getKey().contains("..")
                        || !entry.getValue().isJsonObject()) {
                    throw new IllegalArgumentException("invalid node entry: " + entry.getKey());
                }
                JsonObject p = entry.getValue().getAsJsonObject();
                double x = number(p, "x");
                double y = number(p, "y");
                JsonObject out = new JsonObject();
                out.addProperty("x", Math.round(x));
                out.addProperty("y", Math.round(y));
                positions.add(entry.getKey(), out);
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("direction", direction);
        result.add("positions", positions);
        return result;
    }

    private static double number(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive() || !o.get(key).getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("position needs numeric " + key);
        }
        double value = o.get(key).getAsDouble();
        if (!Double.isFinite(value) || Math.abs(value) > LIMIT) throw new IllegalArgumentException("position out of range");
        return value;
    }

    private JsonObject load() {
        try {
            if (Files.isRegularFile(file)) return sanitize(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)));
        } catch (Exception ignored) {
            // a broken layout file must never block startup; fall back to an automatic layout
        }
        return sanitize(JsonParser.parseString("{}"));
    }
}
