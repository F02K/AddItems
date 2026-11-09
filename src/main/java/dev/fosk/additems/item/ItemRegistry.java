package dev.fosk.additems.item;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Stream;

/** Loads, stores and persists item definitions and their textures. Files are the source of truth. */
public final class ItemRegistry {

    public static final long MAX_TEXTURE_BYTES = 256 * 1024;

    /** Texture slots: "main" is the item texture / all block faces; the others are block faces. */
    public static final String MAIN = "main";
    public static final Set<String> SLOTS = Set.of(MAIN, "top", "bottom", "side");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path itemsDir;
    private final Path texturesDir;
    private final Logger log;
    private final Map<String, ItemDefinition> items = new ConcurrentHashMap<>();
    /** Block states custom blocks may use (set by the plugin from the server's note block states). */
    private volatile List<String> statePool = List.of();
    /** Decides whether a sound id exists on this server (set by the plugin; null = accept anything well-formed). */
    private volatile java.util.function.Predicate<String> soundValidator;

    public ItemRegistry(Path dataFolder, Logger log) {
        this.itemsDir = dataFolder.resolve("items");
        this.texturesDir = dataFolder.resolve("textures");
        this.log = log;
    }

    public void setSoundValidator(java.util.function.Predicate<String> validator) {
        this.soundValidator = validator;
    }

    public void setStatePool(List<String> pool) {
        this.statePool = List.copyOf(pool);
    }

    public void load() throws IOException {
        Files.createDirectories(itemsDir);
        Files.createDirectories(texturesDir);
        items.clear();
        try (Stream<Path> files = Files.list(itemsDir)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".json"))::iterator) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    ItemDefinition def = GSON.fromJson(reader, ItemDefinition.class);
                    if (def != null) normalizeSounds(def);
                    String error = def == null ? "empty file" : def.validate();
                    if (error != null) {
                        log.warning("Skipping " + file.getFileName() + ": " + error);
                        continue;
                    }
                    String soundProblem = checkSounds(def);
                    if (soundProblem != null) log.warning("Item " + def.id + ": " + soundProblem + " (the sound will be silent)");
                    def.hasTexture = computeHasTexture(def);
                    items.put(def.id, def);
                } catch (Exception e) {
                    log.warning("Skipping " + file.getFileName() + ": " + e.getMessage());
                }
            }
        }
        assignMissingStates();
    }

    /** Placeable blocks edited by hand may lack a block state; give them one and write it back to the file. */
    private synchronized void assignMissingStates() {
        for (ItemDefinition def : all()) {
            if (def.type != ItemType.BLOCK || !def.block.placeable || def.block.state != null) continue;
            String problem = assignBlockState(def);
            if (problem != null) {
                log.warning("Custom block " + def.id + " has no block state: " + problem);
                continue;
            }
            try {
                Path file = itemsDir.resolve(def.id + ".json");
                try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    GSON.toJson(def, writer);
                }
                log.info("Assigned block state " + def.block.state + " to custom block " + def.id);
            } catch (IOException e) {
                log.warning("Could not save block state of " + def.id + ": " + e.getMessage());
            }
        }
    }

    public List<ItemDefinition> all() {
        List<ItemDefinition> list = new ArrayList<>(items.values());
        list.sort(Comparator.comparing(d -> d.id));
        return list;
    }

    public ItemDefinition get(String id) {
        return id == null ? null : items.get(id);
    }

    /** The placeable block item that owns a note block state ("instrument=harp,note=1,powered=false"), or null. */
    public ItemDefinition byBlockState(String state) {
        if (state == null) return null;
        for (ItemDefinition def : items.values()) {
            if (def.block != null && def.block.placeable && state.equals(def.block.state)) return def;
        }
        return null;
    }

    /** Validates and stores the definition. Returns an error message or null on success. */
    public synchronized String save(ItemDefinition def) throws IOException {
        normalizeSounds(def); // before validation: a pasted space or "minecraft:" must not make a good sound invalid
        String error = def.validate();
        if (error != null) return error;
        error = checkSounds(def);
        if (error != null) return error;
        error = assignBlockState(def);
        if (error != null) return error;
        def.hasTexture = computeHasTexture(def);
        Path file = itemsDir.resolve(def.id + ".json");
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(def, writer);
        }
        items.put(def.id, def);
        return null;
    }

    /** Trims the block sounds and drops the default "minecraft:" namespace. */
    private static void normalizeSounds(ItemDefinition def) {
        if (def.block == null) return;
        def.block.placeSound = normalizeSound(def.block.placeSound);
        def.block.breakSound = normalizeSound(def.block.breakSound);
    }

    /** Rejects block sounds the server does not know (when a validator is set). */
    private String checkSounds(ItemDefinition def) {
        if (def.block == null) return null;
        java.util.function.Predicate<String> valid = soundValidator;
        if (valid == null) return null;
        for (String sound : new String[]{def.block.placeSound, def.block.breakSound}) {
            if (sound != null && !sound.isEmpty() && !valid.test(sound)) return "unknown sound '" + sound + "'";
        }
        return null;
    }

    private static String normalizeSound(String sound) {
        if (sound == null) return "";
        String s = sound.trim();
        return s.startsWith("minecraft:") ? s.substring("minecraft:".length()) : s;
    }

    /**
     * Gives a placeable block its permanent block state. Once assigned the state never changes, so blocks already
     * placed in the world keep their identity when other items are added or removed.
     */
    private String assignBlockState(ItemDefinition def) {
        if (def.type != ItemType.BLOCK) return null;
        ItemDefinition.BlockProps block = def.block;
        ItemDefinition existing = items.get(def.id);
        if ((block.state == null || block.state.isBlank()) && existing != null) block.state = existing.block.state;
        if (block.state != null && block.state.isBlank()) block.state = null;
        if (!block.placeable && block.state == null) return null;

        List<String> pool = statePool;
        if (block.state != null) {
            if (!pool.isEmpty() && !pool.contains(block.state)) return "block state is not available on this server";
            for (ItemDefinition other : items.values()) {
                if (!other.id.equals(def.id) && block.state.equals(other.block.state)) return "block state already used by " + other.id;
            }
            return null;
        }
        for (String candidate : pool) {
            boolean used = false;
            for (ItemDefinition other : items.values()) {
                if (!other.id.equals(def.id) && candidate.equals(other.block.state)) { used = true; break; }
            }
            if (!used) {
                block.state = candidate;
                return null;
            }
        }
        return pool.isEmpty() ? "custom blocks are not available (no block states known)" : "no free block states left";
    }

    public synchronized boolean delete(String id) throws IOException {
        if (id == null || !ItemDefinition.ID_PATTERN.matcher(id).matches()) return false;
        boolean existed = items.remove(id) != null;
        Files.deleteIfExists(itemsDir.resolve(id + ".json"));
        for (String slot : SLOTS) Files.deleteIfExists(textureFile(id, slot));
        return existed;
    }

    // ------------------------------------------------------------------ textures

    public synchronized void saveTexture(String id, byte[] png) throws IOException {
        saveTexture(id, MAIN, png);
    }

    public synchronized void saveTexture(String id, String slot, byte[] png) throws IOException {
        ItemDefinition def = items.get(id);
        if (def == null) throw new IllegalArgumentException("unknown item");
        Files.write(textureFile(id, slot), png);
        def.hasTexture = computeHasTexture(def);
    }

    public byte[] readTexture(String id) throws IOException {
        return readTexture(id, MAIN);
    }

    public byte[] readTexture(String id, String slot) throws IOException {
        Path file = textureFile(id, slot);
        return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
    }

    /**
     * A texture to show as the item's icon in the dashboard: the main texture, or for blocks drawn with separate faces
     * the top, side or bottom one (they have no main texture).
     */
    public byte[] readPreviewTexture(String id) throws IOException {
        for (String slot : List.of(MAIN, "top", "side", "bottom")) {
            byte[] data = readTexture(id, slot);
            if (data != null) return data;
        }
        return null;
    }

    /** All textures that exist for an item, keyed by slot. */
    public Map<String, byte[]> readTextures(String id) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        for (String slot : List.of(MAIN, "top", "bottom", "side")) {
            byte[] data = readTexture(id, slot);
            if (data != null) out.put(slot, data);
        }
        return out;
    }

    /** An item counts as textured when its model can be drawn from the files that exist. */
    private boolean computeHasTexture(ItemDefinition def) {
        if (Files.isRegularFile(textureFile(def.id, MAIN))) return true;
        return def.type == ItemType.BLOCK && def.block.placeable && "SIDES".equals(def.block.textureMode)
                && Files.isRegularFile(textureFile(def.id, "top")) && Files.isRegularFile(textureFile(def.id, "bottom"))
                && Files.isRegularFile(textureFile(def.id, "side"));
    }

    /** Callers must pass an id that matched {@link ItemDefinition#ID_PATTERN} and a known slot. */
    private Path textureFile(String id, String slot) {
        if (id == null || !ItemDefinition.ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("invalid id");
        }
        if (!SLOTS.contains(slot)) throw new IllegalArgumentException("invalid texture slot");
        // '.' cannot appear in ids, so "<id>.top.png" can never collide with another item's main texture
        return texturesDir.resolve(slot.equals(MAIN) ? id + ".png" : id + "." + slot + ".png");
    }
}
