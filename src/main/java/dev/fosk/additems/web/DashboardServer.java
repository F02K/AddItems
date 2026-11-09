package dev.fosk.additems.web;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.fosk.additems.item.Presets;
import dev.fosk.additems.loot.LootRegistry;
import dev.fosk.additems.loot.LootRule;
import org.bukkit.Registry;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.loot.LootTables;
import dev.fosk.additems.vanilla.VanillaAssets;
import dev.fosk.additems.vanilla.VanillaIcons;
import dev.fosk.additems.recipe.RecipeDefinition;
import dev.fosk.additems.recipe.RecipeRegistry;
import dev.fosk.additems.recipe.RecipeType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import java.util.List;
import java.util.Arrays;
import java.util.Comparator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.fosk.additems.item.ItemDefinition;
import dev.fosk.additems.item.ItemRegistry;
import dev.fosk.additems.item.ItemType;
import dev.fosk.additems.pack.PackBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The team dashboard: static Svelte UI plus a small JSON API. Everything under /api requires a session
 * obtained through a one-time login link. State-changing requests are additionally protected by
 * SameSite=Strict cookies and an Origin check.
 */
public final class DashboardServer {

    /** Actions the dashboard can trigger on the plugin. */
    public interface Actions {
        /** Why custom blocks need a server setting change, or null when everything is fine. */
        String blockSetupProblem();
        /** Sound event ids for autocompletion ( "block.stone.place" ). */
        List<String> soundNames();
        PackBuilder.Result buildPack() throws Exception;
        PackBuilder.Result lastPack();
        String minecraftVersion();
        /** Why the pack cannot be delivered to players right now, or null if it can. */
        String packProblem();
        /** Why a recipe is not active on the server (e.g. missing item), or null. */
        String recipeProblem(String recipeId);
        /** Why a loot rule is not active on the server (missing item or target), or null. */
        String lootProblem(String ruleId);
        /** Re-applies recipes after items or recipes changed; returns once the server thread is done. */
        void contentChanged();
    }

    private static final String COOKIE = "ai_session";
    private static final Gson GSON = new Gson();
    private static final Pattern ITEM_PATH = Pattern.compile("^/api/items/([a-z0-9_]{1,48})(/texture)?$");
    private static final Pattern STATIC_PATH = Pattern.compile("^/[A-Za-z0-9._/-]*$");
    private static final Map<String, String> MIME = Map.of(
            "html", "text/html; charset=utf-8", "js", "text/javascript; charset=utf-8",
            "css", "text/css; charset=utf-8", "svg", "image/svg+xml", "png", "image/png",
            "json", "application/json", "ico", "image/x-icon", "map", "application/json");

    private final String bind;
    private final int port;
    private final boolean secureCookie;
    private static final Pattern ICON_PATH = Pattern.compile("^/api/vanilla/icon/([a-z0-9_]{1,64})$");
    private static final Pattern LOOT_PATH = Pattern.compile("^/api/loot/([a-z0-9_]{1,48})$");
    private static final Pattern RECIPE_PATH =Pattern.compile("^/api/recipes/([a-z0-9_]{1,48})$");

    private final ItemRegistry registry;
    private final RecipeRegistry recipes;
    private final LootRegistry loot;
    private final GraphLayoutStore graph;
    private final VanillaAssets vanilla;
    private final AuthManager auth;
    private final Actions actions;
    private final Logger log;
    private HttpServer server;

    public DashboardServer(String bind, int port, boolean secureCookie, ItemRegistry registry,
                           RecipeRegistry recipes, LootRegistry loot, VanillaAssets vanilla, GraphLayoutStore graph,
                           AuthManager auth,
                           Actions actions,
                           Logger log) {
        this.vanilla = vanilla;
        this.bind = bind;
        this.port = port;
        this.secureCookie = secureCookie;
        this.registry = registry;
        this.recipes = recipes;
        this.loot = loot;
        this.graph = graph;
        this.auth = auth;
        this.actions = actions;
        this.log = log;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "AddItems-Dashboard");
            t.setDaemon(true);
            return t;
        }));
        server.start();
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    // ------------------------------------------------------------------ routing

    private void handle(HttpExchange ex) throws IOException {
        try {
            URI uri = ex.getRequestURI();
            String path = uri.getPath();
            String method = ex.getRequestMethod();

            if (path.equals("/login") && method.equals("GET")) {
                login(ex, uri.getRawQuery());
            } else if (path.startsWith("/api/")) {
                api(ex, path, method);
            } else if (method.equals("GET") || method.equals("HEAD")) {
                serveStatic(ex, path);
            } else {
                sendText(ex, 405, "method not allowed");
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Dashboard request failed", e);
            sendJson(ex, 500, error("internal error"));
        } finally {
            ex.close();
        }
    }

    private void login(HttpExchange ex, String query) throws IOException {
        String token = null;
        if (query != null) {
            for (String part : query.split("&")) {
                if (part.startsWith("token=")) token = part.substring(6);
            }
        }
        String session = auth.redeem(token);
        if (session == null) {
            sendText(ex, 403, "Login link invalid or expired. Run /additems login again.");
            return;
        }
        ex.getResponseHeaders().add("Set-Cookie", COOKIE + "=" + session
                + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=43200" + (secureCookie ? "; Secure" : ""));
        ex.getResponseHeaders().add("Location", "/");
        ex.sendResponseHeaders(302, -1);
    }

    private void api(HttpExchange ex, String path, String method) throws Exception {
        String user = auth.userFor(sessionOf(ex));
        if (user == null) {
            sendJson(ex, 401, error("not logged in"));
            return;
        }
        boolean mutating = !method.equals("GET");
        if (mutating && !originOk(ex)) {
            sendJson(ex, 403, error("bad origin"));
            return;
        }

        if (path.equals("/api/me") && method.equals("GET")) {
            JsonObject o = new JsonObject();
            o.addProperty("user", user);
            sendJson(ex, 200, o);
        } else if (path.equals("/api/meta") && method.equals("GET")) {
            sendJson(ex, 200, meta());
        } else if (path.equals("/api/items") && method.equals("GET")) {
            sendJson(ex, 200, registry.all());
        } else if (path.equals("/api/vanilla") && method.equals("GET")) {
            JsonObject o = new JsonObject();
            o.addProperty("state", vanilla.state().name());
            o.addProperty("message", vanilla.message());
            o.addProperty("version", vanilla.version());
            sendJson(ex, 200, o);
        } else if (path.equals("/api/vanilla/names") && method.equals("GET")) {
            VanillaIcons icons = vanilla.icons();
            sendJson(ex, 200, icons == null ? Map.of() : icons.names());
        } else if (path.startsWith("/api/vanilla/icon/") && method.equals("GET")) {
            Matcher im = ICON_PATH.matcher(path);
            VanillaIcons icons = vanilla.icons();
            byte[] png = im.matches() && icons != null ? icons.icon(im.group(1)) : null;
            if (png == null) sendJson(ex, 404, error("no icon"));
            else sendBytes(ex, 200, "image/png", png, "private, max-age=86400");
        } else if (path.equals("/api/graph/layout") && method.equals("GET")) {
            sendJson(ex, 200, graph.get());
        } else if (path.equals("/api/graph/layout") && method.equals("PUT")) {
            String problem = graph.save(new String(readBody(ex, GraphLayoutStore.MAX_BODY_BYTES), StandardCharsets.UTF_8));
            if (problem != null) sendJson(ex, 400, error(problem));
            else sendJson(ex, 200, ok());
        } else if (path.equals("/api/loot") && method.equals("GET")) {
            sendJson(ex, 200, lootList());
        } else if (path.equals("/api/recipes") && method.equals("GET")) {
            sendJson(ex, 200, recipeList());
        } else if (path.equals("/api/pack") && method.equals("GET")) {
            sendJson(ex, 200, packInfo(actions.lastPack()));
        } else if (path.equals("/api/pack/build") && method.equals("POST")) {
            sendJson(ex, 200, packInfo(actions.buildPack()));
        } else {
            Matcher lm = LOOT_PATH.matcher(path);
            if (lm.matches()) {
                lootRoute(ex, lm.group(1), method);
                return;
            }
            Matcher rm = RECIPE_PATH.matcher(path);
            if (rm.matches()) {
                recipeRoute(ex, rm.group(1), method);
                return;
            }
            Matcher m = ITEM_PATH.matcher(path);
            if (!m.matches()) {
                sendJson(ex, 404, error("not found"));
                return;
            }
            itemRoute(ex, m.group(1), m.group(2) != null, method);
        }
    }

    private JsonArray lootList() {
        JsonArray out = new JsonArray();
        for (LootRule rule : loot.all()) {
            JsonObject o = GSON.toJsonTree(rule).getAsJsonObject();
            String problem = actions.lootProblem(rule.id);
            if (problem != null) o.addProperty("problem", problem);
            out.add(o);
        }
        return out;
    }

    private void lootRoute(HttpExchange ex, String id, String method) throws Exception {
        switch (method) {
            case "GET" -> {
                LootRule rule = loot.get(id);
                if (rule == null) sendJson(ex, 404, error("unknown loot rule"));
                else sendJson(ex, 200, rule);
            }
            case "PUT" -> {
                LootRule rule;
                try {
                    rule = GSON.fromJson(new String(readBody(ex, 128 * 1024), StandardCharsets.UTF_8), LootRule.class);
                } catch (RuntimeException e) {
                    sendJson(ex, 400, error("invalid JSON"));
                    return;
                }
                if (rule == null || !id.equals(rule.id)) {
                    sendJson(ex, 400, error("id in body must match URL"));
                    return;
                }
                String problem = loot.save(rule);
                if (problem != null) {
                    sendJson(ex, 400, error(problem));
                    return;
                }
                actions.contentChanged();
                JsonObject o = GSON.toJsonTree(loot.get(id)).getAsJsonObject();
                String inactive = actions.lootProblem(id);
                if (inactive != null) o.addProperty("problem", inactive);
                sendJson(ex, 200, o);
            }
            case "DELETE" -> {
                if (loot.delete(id)) {
                    actions.contentChanged();
                    sendJson(ex, 200, ok());
                } else {
                    sendJson(ex, 404, error("unknown loot rule"));
                }
            }
            default -> sendJson(ex, 405, error("method not allowed"));
        }
    }

    private JsonArray recipeList() {
        JsonArray out = new JsonArray();
        for (RecipeDefinition def : recipes.all()) {
            JsonObject o = GSON.toJsonTree(def).getAsJsonObject();
            String problem = actions.recipeProblem(def.id);
            if (problem != null) o.addProperty("problem", problem);
            out.add(o);
        }
        return out;
    }

    private void recipeRoute(HttpExchange ex, String id, String method) throws Exception {
        switch (method) {
            case "GET" -> {
                RecipeDefinition def = recipes.get(id);
                if (def == null) sendJson(ex, 404, error("unknown recipe"));
                else sendJson(ex, 200, def);
            }
            case "PUT" -> {
                RecipeDefinition def;
                try {
                    def = GSON.fromJson(new String(readBody(ex, 64 * 1024), StandardCharsets.UTF_8),
                            RecipeDefinition.class);
                } catch (RuntimeException e) {
                    sendJson(ex, 400, error("invalid JSON"));
                    return;
                }
                if (def == null || !id.equals(def.id)) {
                    sendJson(ex, 400, error("id in body must match URL"));
                    return;
                }
                String problem = recipes.save(def);
                if (problem != null) {
                    sendJson(ex, 400, error(problem));
                    return;
                }
                actions.contentChanged();
                JsonObject o = GSON.toJsonTree(recipes.get(id)).getAsJsonObject();
                String inactive = actions.recipeProblem(id);
                if (inactive != null) o.addProperty("problem", inactive);
                sendJson(ex, 200, o);
            }
            case "DELETE" -> {
                if (recipes.delete(id)) {
                    actions.contentChanged();
                    sendJson(ex, 200, ok());
                } else {
                    sendJson(ex, 404, error("unknown recipe"));
                }
            }
            default -> sendJson(ex, 405, error("method not allowed"));
        }
    }

    private void itemRoute(HttpExchange ex, String id, boolean texture, String method) throws Exception {
        if (texture) {
            String slot = slotOf(ex);
            if (slot == null) {
                sendJson(ex, 400, error("unknown texture slot"));
                return;
            }
            if (method.equals("GET")) {
                // the plain URL is used as the item's icon everywhere, so blocks with separate faces fall back to one of them
                byte[] png = slot.equals(ItemRegistry.MAIN) ? registry.readPreviewTexture(id) : registry.readTexture(id, slot);
                if (png == null) sendJson(ex, 404, error("no texture"));
                else sendBytes(ex, 200, "image/png", png);
            } else if (method.equals("PUT")) {
                putTexture(ex, id, slot);
            } else {
                sendJson(ex, 405, error("method not allowed"));
            }
            return;
        }
        switch (method) {
            case "GET" -> {
                ItemDefinition def = registry.get(id);
                if (def == null) sendJson(ex, 404, error("unknown item"));
                else sendJson(ex, 200, def);
            }
            case "PUT" -> {
                ItemDefinition def;
                try {
                    def = GSON.fromJson(new String(readBody(ex, 64 * 1024), StandardCharsets.UTF_8),
                            ItemDefinition.class);
                } catch (RuntimeException e) {
                    sendJson(ex, 400, error("invalid JSON"));
                    return;
                }
                if (def == null || !id.equals(def.id)) {
                    sendJson(ex, 400, error("id in body must match URL"));
                    return;
                }
                String problem = registry.save(def);
                if (problem != null) {
                    sendJson(ex, 400, error(problem));
                } else {
                    actions.contentChanged(); // recipes embed item stacks, so they must be rebuilt
                    sendJson(ex, 200, registry.get(id));
                }
            }
            case "DELETE" -> {
                if (registry.delete(id)) {
                    actions.contentChanged();
                    sendJson(ex, 200, ok());
                } else {
                    sendJson(ex, 404, error("unknown item"));
                }
            }
            default -> sendJson(ex, 405, error("method not allowed"));
        }
    }

    /** The "?slot=" query parameter: main (default), top, bottom or side; null if it is something else. */
    private static String slotOf(HttpExchange ex) {
        String query = ex.getRequestURI().getRawQuery();
        if (query == null) return ItemRegistry.MAIN;
        for (String part : query.split("&")) {
            if (part.startsWith("slot=")) {
                String slot = part.substring(5);
                return ItemRegistry.SLOTS.contains(slot) ? slot : null;
            }
        }
        return ItemRegistry.MAIN;
    }

    private void putTexture(HttpExchange ex, String id, String slot) throws Exception {
        if (registry.get(id) == null) {
            sendJson(ex, 404, error("unknown item"));
            return;
        }
        byte[] data = readBody(ex, (int) ItemRegistry.MAX_TEXTURE_BYTES);
        String problem = validatePng(data);
        if (problem != null) {
            sendJson(ex, 400, error(problem));
            return;
        }
        registry.saveTexture(id, slot, data);
        sendJson(ex, 200, ok());
    }

    /** Only real, square PNGs of a sane size are accepted. Returns an error message or null. */
    static String validatePng(byte[] data) throws IOException {
        if (data.length < 8 || (data[0] & 0xFF) != 0x89 || data[1] != 'P' || data[2] != 'N' || data[3] != 'G') {
            return "file is not a PNG";
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
        if (image == null) return "file is not a readable PNG";
        if (image.getWidth() != image.getHeight()) return "texture must be square";
        if (image.getWidth() > 1024) return "texture larger than 1024x1024";
        return null;
    }

    // ------------------------------------------------------------------ responses

    private JsonObject meta() {
        JsonObject o = Presets.toJson();
        o.addProperty("minecraftVersion", actions.minecraftVersion());
        o.add("types", GSON.toJsonTree(ItemType.values()));
        o.add("toolKinds", GSON.toJsonTree(ItemDefinition.ToolKind.values()));

        // Vanilla items the base-item pickers can offer (names as Material constants).
        JsonArray all = new JsonArray();
        JsonArray blocks = new JsonArray();
        JsonArray foods = new JsonArray();
        Arrays.stream(Material.values())
                .filter(m -> !m.isLegacy() && m.isItem() && !m.isAir())
                .sorted(Comparator.comparing(Enum::name))
                .forEach(m -> {
                    all.add(m.name());
                    if (m.isBlock()) blocks.add(m.name());
                    if (m.isEdible()) foods.add(m.name());
                });
        JsonArray soundList = new JsonArray();
        actions.soundNames().forEach(soundList::add);
        o.add("sounds", soundList);
        o.add("materials", all);
        o.add("blockMaterials", blocks);
        o.add("foodMaterials", foods);

        JsonArray recipeTypes = new JsonArray();
        for (RecipeType t : RecipeType.values()) {
            JsonObject r = new JsonObject();
            r.addProperty("id", t.name());
            r.addProperty("label", t.label());
            r.addProperty("hint", t.hint());
            r.addProperty("defaultCookTime", t.defaultCookTime());
            recipeTypes.add(r);
        }
        o.add("recipeTypes", recipeTypes);

        // Vanilla item tags usable as "any of these" ingredients (only those that exist in this version).
        JsonArray tags = new JsonArray();
        for (String name : List.of("planks", "logs", "logs_that_burn", "wool", "sand", "coals", "flowers", "saplings",
                "leaves", "stone_crafting_materials", "stone_tool_materials", "wooden_slabs", "wooden_stairs",
                "fishes", "beds", "terracotta")) {
            if (Bukkit.getTag(Tag.REGISTRY_ITEMS, NamespacedKey.minecraft(name), Material.class) != null) {
                tags.add("#minecraft:" + name);
            }
        }
        o.add("itemTags", tags);
        o.addProperty("customNamespace", recipes.customNamespace());
        String blockSetup = actions.blockSetupProblem();
        if (blockSetup != null) o.addProperty("blockSetupProblem", blockSetup);

        // Loot targets: living entity types, chest loot tables and block tags that exist in this version.
        JsonArray mobs = new JsonArray();
        Registry.ENTITY_TYPE.stream()
                .filter(t -> t.getEntityClass() != null && LivingEntity.class.isAssignableFrom(t.getEntityClass())
                        && t != EntityType.PLAYER && t != EntityType.ARMOR_STAND)
                .map(t -> t.getKey().asString()).sorted().forEach(mobs::add);
        o.add("entityTypes", mobs);
        JsonArray tables = new JsonArray();
        Arrays.stream(LootTables.values()).map(t -> t.getKey().asString())
                .filter(k -> k.startsWith("minecraft:chests/")).sorted().forEach(tables::add);
        o.add("lootTables", tables);
        JsonArray blockTags = new JsonArray();
        for (String name : List.of("logs", "leaves", "planks", "flowers", "crops", "coal_ores", "iron_ores", "gold_ores",
                "copper_ores", "diamond_ores", "redstone_ores", "lapis_ores", "emerald_ores", "base_stone_overworld",
                "sand", "dirt", "wool", "saplings")) {
            if (Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft(name), Material.class) != null) {
                blockTags.add("#minecraft:" + name);
            }
        }
        o.add("blockTags", blockTags);
        return o;
    }

    private JsonObject packInfo(PackBuilder.Result result) {
        JsonObject o = new JsonObject();
        o.addProperty("built", result != null);
        String problem = actions.packProblem();
        if (problem != null) o.addProperty("sendProblem", problem);
        if (result != null) {
            o.addProperty("sha1", result.sha1());
            o.addProperty("size", result.size());
        }
        return o;
    }

    private static JsonObject error(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        return o;
    }

    private static JsonObject ok() {
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        return o;
    }

    private void serveStatic(HttpExchange ex, String path) throws IOException {
        if (path.equals("/")) path = "/index.html";
        if (!STATIC_PATH.matcher(path).matches() || path.contains("..")) {
            sendText(ex, 404, "not found");
            return;
        }
        try (InputStream in = DashboardServer.class.getResourceAsStream("/web" + path)) {
            if (in == null) {
                sendText(ex, 404, "not found");
                return;
            }
            String ext = path.substring(path.lastIndexOf('.') + 1);
            byte[] body = in.readAllBytes();
            ex.getResponseHeaders().add("X-Content-Type-Options", "nosniff");
            ex.getResponseHeaders().add("Content-Security-Policy",
                    "default-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'");
            sendBytes(ex, 200, MIME.getOrDefault(ext, "application/octet-stream"), body);
        }
    }

    private void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        sendBytes(ex, status, "application/json; charset=utf-8",
                GSON.toJson(body).getBytes(StandardCharsets.UTF_8));
    }

    private void sendText(HttpExchange ex, int status, String text) throws IOException {
        sendBytes(ex, status, "text/plain; charset=utf-8", text.getBytes(StandardCharsets.UTF_8));
    }

    private void sendBytes(HttpExchange ex, int status, String type, byte[] body) throws IOException {
        sendBytes(ex, status, type, body, "no-store");
    }

    private void sendBytes(HttpExchange ex, int status, String type, byte[] body, String cacheControl)
            throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", cacheControl);
        boolean head = ex.getRequestMethod().equals("HEAD");
        ex.sendResponseHeaders(status, head ? -1 : body.length);
        if (!head) ex.getResponseBody().write(body);
    }

    // ------------------------------------------------------------------ helpers

    private byte[] readBody(HttpExchange ex, int max) throws IOException {
        byte[] data = ex.getRequestBody().readNBytes(max + 1);
        if (data.length > max) throw new IOException("request body too large");
        return data;
    }

    private String sessionOf(HttpExchange ex) {
        List<String> cookies = ex.getRequestHeaders().get("Cookie");
        if (cookies == null) return null;
        for (String header : cookies) {
            for (String part : header.split(";")) {
                String p = part.trim();
                if (p.startsWith(COOKIE + "=")) return p.substring(COOKIE.length() + 1);
            }
        }
        return null;
    }

    /** Rejects cross-site writes: if an Origin header is present its host must match the Host header. */
    private boolean originOk(HttpExchange ex) {
        String origin = ex.getRequestHeaders().getFirst("Origin");
        if (origin == null) return true;
        String host = ex.getRequestHeaders().getFirst("Host");
        try {
            URI o = URI.create(origin);
            String originHost = o.getPort() == -1 ? o.getHost() : o.getHost() + ":" + o.getPort();
            return host != null && host.equalsIgnoreCase(originHost);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
