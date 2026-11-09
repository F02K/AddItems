package dev.fosk.additems.vanilla;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Builds PNG icons for vanilla items from the extracted client assets: flat items use their texture layers,
 * block items are drawn as small isometric cubes. Also reads English item names from the language file.
 */
public final class VanillaIcons {

    private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,64}");
    private static final Pattern MODEL_ID = Pattern.compile("[a-z0-9_./:-]{1,128}");
    private static final int CUBE_SIZE = 64;

    /** Colors vanilla applies from biome colormaps; plains defaults are close enough for an icon. */
    private static final Map<String, Integer> TINTS = Map.ofEntries(
            Map.entry("block/grass_block_top", 0x91BD59), Map.entry("block/short_grass", 0x91BD59),
            Map.entry("block/tall_grass_top", 0x91BD59), Map.entry("block/tall_grass_bottom", 0x91BD59),
            Map.entry("block/fern", 0x91BD59), Map.entry("block/large_fern_top", 0x91BD59),
            Map.entry("block/oak_leaves", 0x77AB2F), Map.entry("block/jungle_leaves", 0x77AB2F),
            Map.entry("block/acacia_leaves", 0x77AB2F), Map.entry("block/dark_oak_leaves", 0x77AB2F),
            Map.entry("block/mangrove_leaves", 0x77AB2F), Map.entry("block/vine", 0x77AB2F),
            Map.entry("block/birch_leaves", 0x80A755), Map.entry("block/spruce_leaves", 0x619961),
            Map.entry("block/lily_pad", 0x208030));

    private final Path root;
    private final Map<String, Optional<byte[]>> cache = new ConcurrentHashMap<>();
    private volatile Map<String, String> names;

    public VanillaIcons(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** PNG bytes for an item id like "iron_ingot", or null if nothing sensible can be drawn. */
    public byte[] icon(String name) {
        if (name == null || !NAME.matcher(name).matches()) return null;
        return cache.computeIfAbsent(name, n -> Optional.ofNullable(render(n))).orElse(null);
    }

    /** English display names keyed by item id ("iron_ingot" -> "Iron Ingot"). */
    public Map<String, String> names() {
        Map<String, String> result = names;
        if (result == null) {
            result = loadNames();
            names = result;
        }
        return result;
    }

    // ------------------------------------------------------------------ names

    private Map<String, String> loadNames() {
        Map<String, String> out = new TreeMap<>();
        Path lang = root.resolve("lang/en_us.json");
        if (!Files.isRegularFile(lang)) return out;
        try {
            JsonObject json = JsonParser.parseString(Files.readString(lang, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String prefix : new String[]{"block.minecraft.", "item.minecraft."}) { // items win over blocks
                for (Map.Entry<String, JsonElement> e : json.entrySet()) {
                    String key = e.getKey();
                    if (!key.startsWith(prefix)) continue;
                    String id = key.substring(prefix.length());
                    if (NAME.matcher(id).matches() && e.getValue().isJsonPrimitive()) {
                        out.put(id, e.getValue().getAsString());
                    }
                }
            }
        } catch (Exception ignored) {
            // names are a nicety; icons still work without them
        }
        return out;
    }

    // ------------------------------------------------------------------ rendering

    private byte[] render(String name) {
        try {
            JsonArray[] tints = new JsonArray[1];
            String modelId = modelFor(name, tints);
            if (modelId == null) return null;
            Map<String, String> textures = resolveTextures(modelId);
            BufferedImage image = textures.containsKey("layer0") ? flat(textures, tints[0]) : cube(textures);
            if (image == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /** Finds the model an item displays, via its item model definition or a same-named model. */
    private String modelFor(String name, JsonArray[] tintsOut) throws IOException {
        JsonObject definition = readJson("items/" + name + ".json");
        if (definition != null && definition.has("model") && definition.get("model").isJsonObject()) {
            JsonObject node = pickNode(definition.getAsJsonObject("model"), 0);
            if (node != null) {
                if (node.has("tints") && node.get("tints").isJsonArray()) tintsOut[0] = node.getAsJsonArray("tints");
                String id = str(node, "model");
                if (id == null) id = str(node, "base"); // "special" models only expose a base model
                if (id != null) return id;
            }
        }
        if (Files.isRegularFile(root.resolve("models/item/" + name + ".json"))) return "item/" + name;
        if (Files.isRegularFile(root.resolve("models/block/" + name + ".json"))) return "block/" + name;
        return null;
    }

    /** Walks an item model definition to the most representative plain model. */
    private static JsonObject pickNode(JsonObject node, int depth) {
        if (depth > 8) return null;
        String type = str(node, "type");
        if (type == null) return null;
        switch (type.replace("minecraft:", "")) {
            case "model":
                return node;
            case "condition": {
                JsonObject id = child(node, "on_false", depth);
                return id != null ? id : child(node, "on_true", depth);
            }
            case "select": {
                JsonObject id = child(node, "fallback", depth);
                if (id != null) return id;
                if (node.has("cases") && node.get("cases").isJsonArray() && !node.getAsJsonArray("cases").isEmpty()) {
                    JsonObject first = node.getAsJsonArray("cases").get(0).getAsJsonObject();
                    return child(first, "model", depth);
                }
                return null;
            }
            case "range_dispatch": {
                JsonObject id = child(node, "fallback", depth);
                if (id != null) return id;
                if (node.has("entries") && node.get("entries").isJsonArray() && !node.getAsJsonArray("entries").isEmpty()) {
                    JsonObject first = node.getAsJsonArray("entries").get(0).getAsJsonObject();
                    return child(first, "model", depth);
                }
                return null;
            }
            case "composite":
                if (node.has("models") && node.get("models").isJsonArray() && !node.getAsJsonArray("models").isEmpty()) {
                    JsonElement first = node.getAsJsonArray("models").get(0);
                    return first.isJsonObject() ? pickNode(first.getAsJsonObject(), depth + 1) : null;
                }
                return null;
            case "special":
                return node;
            default:
                return null;
        }
    }

    private static JsonObject child(JsonObject node, String key, int depth) {
        if (!node.has(key) || !node.get(key).isJsonObject()) return null;
        return pickNode(node.getAsJsonObject(key), depth + 1);
    }

    /** Merges `textures` along the parent chain and resolves "#name" indirections. */
    private Map<String, String> resolveTextures(String modelId) throws IOException {
        Map<String, String> merged = new LinkedHashMap<>();
        String current = modelId;
        for (int i = 0; i < 16 && current != null; i++) {
            JsonObject model = readJson("models/" + strip(current) + ".json");
            if (model == null) break;
            if (model.has("textures") && model.get("textures").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : model.getAsJsonObject("textures").entrySet()) {
                    String value = textureValue(e.getValue());
                    if (value != null) merged.putIfAbsent(e.getKey(), value);
                }
            }
            current = model.has("parent") ? model.get("parent").getAsString() : null;
        }
        Map<String, String> resolved = new LinkedHashMap<>();
        for (String key : merged.keySet()) {
            String value = merged.get(key);
            for (int i = 0; i < 8 && value != null && value.startsWith("#"); i++) value = merged.get(value.substring(1));
            if (value != null && !value.startsWith("#")) resolved.put(key, value);
        }
        return resolved;
    }

    /** Since 26.x a texture entry may be a plain id or an object like {"sprite": "...", "force_translucent": true}. */
    private static String textureValue(JsonElement value) {
        if (value.isJsonPrimitive()) return value.getAsString();
        if (value.isJsonObject() && value.getAsJsonObject().has("sprite")) {
            JsonElement sprite = value.getAsJsonObject().get("sprite");
            return sprite.isJsonPrimitive() ? sprite.getAsString() : null;
        }
        return null;
    }

    private BufferedImage flat(Map<String, String> textures, JsonArray tints) throws IOException {
        BufferedImage base = loadTexture(textures.get("layer0"));
        if (base == null) return null;
        BufferedImage out = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(tinted(base, tintColor(tints, 0)), 0, 0, null);
        for (int i = 1; i < 5 && textures.containsKey("layer" + i); i++) {
            BufferedImage layer = loadTexture(textures.get("layer" + i));
            if (layer != null) {
                g.drawImage(tinted(layer, tintColor(tints, i)), 0, 0, base.getWidth(), base.getHeight(), null);
            }
        }
        g.dispose();
        return out;
    }

    /** Color of the item model's tint at an index (potion liquid, dyed leather, ...), or null for none. */
    private static Integer tintColor(JsonArray tints, int index) {
        if (tints == null || index >= tints.size() || !tints.get(index).isJsonObject()) return null;
        JsonObject tint = tints.get(index).getAsJsonObject();
        String type = str(tint, "type");
        if (type == null) return null;
        switch (type.replace("minecraft:", "")) {
            case "constant":
                return color(tint.get("value"));
            case "grass":
                return 0x91BD59;
            default: // dye, potion, firework, team: the default is the color of an untinted stack
                return color(tint.get("default"));
        }
    }

    private static Integer color(JsonElement value) {
        if (value == null) return null;
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) return value.getAsInt() & 0xFFFFFF;
        if (value.isJsonArray() && value.getAsJsonArray().size() >= 3) {
            JsonArray a = value.getAsJsonArray();
            int[] c = new int[3];
            for (int i = 0; i < 3; i++) c[i] = Math.max(0, Math.min(255, Math.round(a.get(i).getAsFloat() * 255)));
            return (c[0] << 16) | (c[1] << 8) | c[2];
        }
        return null;
    }

    private static BufferedImage tinted(BufferedImage image, Integer tint) {
        if (tint == null) return image;
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) out.setRGB(x, y, multiply(image.getRGB(x, y), tint));
        }
        return out;
    }

    private static int multiply(int argb, int tint) {
        int r = ((argb >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255;
        int g = ((argb >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255;
        int b = (argb & 0xFF) * (tint & 0xFF) / 255;
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private BufferedImage cube(Map<String, String> t) throws IOException {
        BufferedImage top = face(t, "up", "top", "end", "all", "particle");
        BufferedImage left = face(t, "south", "front", "side", "all", "north", "east", "west", "particle");
        BufferedImage right = face(t, "east", "side", "west", "north", "front", "all", "south", "particle");
        BufferedImage any = top != null ? top : left != null ? left : right;
        if (any == null) return null;
        if (top == null) top = any;
        // Shade the side faces by darkening their pixels (not by painting over the polygon), so transparent
        // parts of glass or leaves stay transparent. Top 100%, left 80%, right 60% like the inventory lighting.
        left = tinted(left == null ? any : left, 0xCCCCCC);
        right = tinted(right == null ? any : right, 0x999999);

        int s = CUBE_SIZE;
        BufferedImage out = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // texture pixel space -> screen: each face is a sheared, scaled copy of its texture
        double tw = top.getWidth(), lw = left.getWidth(), rw = right.getWidth();
        drawFace(g, top, new AffineTransform(s / (2 * tw), s / (4 * tw), -s / (2 * tw), s / (4 * tw), s / 2.0, 0),
                new Polygon(new int[]{s / 2, s, s / 2, 0}, new int[]{0, s / 4, s / 2, s / 4}, 4), 0);
        drawFace(g, left, new AffineTransform(s / (2 * lw), s / (4 * lw), 0, s / (2.0 * left.getHeight()), 0, s / 4.0),
                new Polygon(new int[]{0, s / 2, s / 2, 0}, new int[]{s / 4, s / 2, s, 3 * s / 4}, 4), 0);
        drawFace(g, right, new AffineTransform(s / (2 * rw), -s / (4 * rw), 0, s / (2.0 * right.getHeight()), s / 2.0, s / 2.0),
                new Polygon(new int[]{s / 2, s, s, s / 2}, new int[]{s / 2, s / 4, 3 * s / 4, s}, 4), 0);
        g.dispose();
        return out;
    }

    private static void drawFace(Graphics2D g, BufferedImage texture, AffineTransform transform, Polygon area, int shadeAlpha) {
        g.drawImage(texture, transform, null);
        if (shadeAlpha > 0) {
            var clip = g.getClip();
            g.setClip(area);
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(new Color(0, 0, 0, shadeAlpha));
            g.fill(area);
            g.setClip(clip);
        }
    }

    private BufferedImage face(Map<String, String> textures, String... keys) throws IOException {
        for (String key : keys) {
            String ref = textures.get(key);
            if (ref != null) {
                BufferedImage image = loadTexture(ref);
                if (image != null) return image;
            }
        }
        return null;
    }

    /** Loads a texture, keeps only the first frame of animated strips and applies a known biome tint. */
    private BufferedImage loadTexture(String ref) throws IOException {
        if (ref == null || !MODEL_ID.matcher(ref).matches()) return null;
        String path = strip(ref);
        Path file = root.resolve("textures/" + path + ".png").normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) return null;
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) return null;

        BufferedImage frame = new BufferedImage(image.getWidth(), Math.min(image.getHeight(), image.getWidth()),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = frame.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();

        return tinted(frame, TINTS.get(path));
    }

    // ------------------------------------------------------------------ helpers

    /** Removes the "minecraft:" namespace; other namespaces are not available in the client assets. */
    private static String strip(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }

    private JsonObject readJson(String relative) throws IOException {
        if (relative.contains("..")) return null;
        Path file = root.resolve(relative).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) return null;
        JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
    }
}
