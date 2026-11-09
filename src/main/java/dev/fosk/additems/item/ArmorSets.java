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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Armor sets (one JSON file each in plugins/AddItems/armorsets/, plus the two shared worn textures). Saving a set writes
 * its pieces as normal items through the {@link ItemRegistry}, which owns this object.
 */
public final class ArmorSets {

    /** The two shared worn textures: "humanoid" for helmet, chestplate and boots, "leggings" for the leggings. */
    public static final Set<String> TEXTURE_SLOTS = Set.of("humanoid", "leggings");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path dir;
    private final ItemRegistry items;
    private final Logger log;
    private final Map<String, ArmorSetDefinition> sets = new ConcurrentHashMap<>();

    ArmorSets(Path dataFolder, ItemRegistry items, Logger log) {
        this.dir = dataFolder.resolve("armorsets");
        this.items = items;
        this.log = log;
    }

    void load() throws IOException {
        Files.createDirectories(dir);
        sets.clear();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".json"))::iterator) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    ArmorSetDefinition set = GSON.fromJson(reader, ArmorSetDefinition.class);
                    String error = set == null ? "empty file" : set.validate();
                    if (error != null) {
                        log.warning("Skipping armor set " + file.getFileName() + ": " + error);
                        continue;
                    }
                    refreshTextureFlags(set);
                    sets.put(set.id, set);
                } catch (Exception e) {
                    log.warning("Skipping armor set " + file.getFileName() + ": " + e.getMessage());
                }
            }
        }
    }

    public List<ArmorSetDefinition> all() {
        List<ArmorSetDefinition> list = new ArrayList<>(sets.values());
        list.sort(Comparator.comparing(s -> s.id));
        return list;
    }

    public ArmorSetDefinition get(String id) {
        return id == null ? null : sets.get(id);
    }

    /** Whether a piece of this set in this slot has a worn texture. False for an unknown set. */
    boolean hasWornFor(String setId, ItemDefinition.ArmorSlot slot) {
        ArmorSetDefinition set = get(setId);
        return set != null && set.hasWornFor(slot);
    }

    /**
     * Validates and stores the set, then creates or updates its pieces as items and removes the pieces that are no longer
     * part of it. Returns an error message or null on success.
     */
    public String save(ArmorSetDefinition set) throws IOException {
        synchronized (items) {
            return saveLocked(set);
        }
    }

    private String saveLocked(ArmorSetDefinition set) throws IOException {
        String error = set.validate();
        if (error != null) return error;
        // an item may not already use a piece id, nor the set id itself (the equipment asset is named after the set)
        ItemDefinition clash = items.get(set.id);
        boolean clashIsPiece = false;
        for (ItemDefinition.ArmorSlot slot : set.pieces.keySet()) if (set.itemId(slot).equals(set.id)) clashIsPiece = true;
        if (clash != null && !clashIsPiece) return "an item with the id '" + set.id + "' already exists";
        for (Map.Entry<ItemDefinition.ArmorSlot, ArmorSetDefinition.Piece> entry : set.pieces.entrySet()) {
            ItemDefinition existing = items.get(set.itemId(entry.getKey()));
            if (existing == null || set.id.equals(existing.armor == null ? null : existing.armor.set)) continue;
            // an explicitly named single armor item (not in another set) may be taken into the set
            boolean adoptable = !entry.getValue().item.isEmpty() && existing.type == ItemType.ARMOR
                    && (existing.armor.set == null || existing.armor.set.isEmpty());
            if (!adoptable) return "the item '" + existing.id + "' already exists and is not part of this set";
        }

        ArmorSetDefinition previous = sets.get(set.id);
        refreshTextureFlags(set);
        // drop pieces that were removed from the set
        if (previous != null) {
            for (ItemDefinition.ArmorSlot slot : previous.pieces.keySet()) {
                if (!set.pieces.containsKey(slot)) items.deleteQuietly(previous.itemId(slot));
            }
        }
        sets.put(set.id, set);
        for (Map.Entry<ItemDefinition.ArmorSlot, ArmorSetDefinition.Piece> entry : set.pieces.entrySet()) {
            adoptWornTexture(set, entry.getKey());
            error = items.save(pieceItem(set, entry.getKey(), entry.getValue()));
            if (error != null) return error;
        }
        write(set);
        return null;
    }

    /**
     * A single armor item that joins a set brings its own worn texture along: it becomes the set's shared layer when the set
     * has none yet (otherwise the piece would suddenly look like iron armor).
     */
    private void adoptWornTexture(ArmorSetDefinition set, ItemDefinition.ArmorSlot slot) throws IOException {
        ItemDefinition existing = items.get(set.itemId(slot));
        if (existing == null || !existing.hasWornTexture || !existing.armor.set.isEmpty()) return;
        String layer = slot == ItemDefinition.ArmorSlot.LEGGINGS ? "leggings" : "humanoid";
        if (Files.isRegularFile(textureFile(set.id, layer))) return;
        byte[] worn = items.readTexture(existing.id, ItemRegistry.WORN);
        if (worn != null) Files.write(textureFile(set.id, layer), worn);
        refreshTextureFlags(set);
    }

    /** A piece leaves its set and becomes a single item: it takes the shared worn texture with it. */
    void detachWornTexture(ItemDefinition piece, ArmorSetDefinition set) throws IOException {
        String layer = piece.armor.slot == ItemDefinition.ArmorSlot.LEGGINGS ? "leggings" : "humanoid";
        byte[] shared = readTexture(set.id, layer);
        if (shared != null) items.saveTexture(piece.id, ItemRegistry.WORN, shared);
    }

    /** The item of one piece: an existing one keeps its lore and stack size, only the set-owned values are overwritten. */
    private ItemDefinition pieceItem(ArmorSetDefinition set, ItemDefinition.ArmorSlot slot, ArmorSetDefinition.Piece piece) {
        ItemDefinition existing = items.get(set.itemId(slot));
        ItemDefinition def = existing != null ? existing : new ItemDefinition();
        def.id = set.itemId(slot);
        if (piece.item.isEmpty()) piece.item = "";
        def.type = ItemType.ARMOR;
        def.baseMaterial = Presets.armorBaseMaterial(slot);
        def.maxStackSize = 1;
        String slotName = slot.name().charAt(0) + slot.name().substring(1).toLowerCase(Locale.ROOT);
        def.displayName = set.displayName.isBlank() ? slotName : set.displayName + " " + slotName;
        ItemDefinition.ArmorProps armor = def.armor;
        armor.set = set.id;
        armor.slot = slot;
        armor.tier = set.tier;
        armor.defense = piece.defense;
        armor.durability = piece.durability;
        armor.toughness = set.toughness;
        armor.knockbackResistance = set.knockbackResistance;
        armor.enchantability = set.enchantability;
        armor.equipSound = set.equipSound;
        return def;
    }

    /** Deletes the set and all of its pieces (items and their textures). */
    public boolean delete(String id) throws IOException {
        synchronized (items) {
            return deleteLocked(id);
        }
    }

    private boolean deleteLocked(String id) throws IOException {
        if (id == null || !ArmorSetDefinition.ID_PATTERN.matcher(id).matches()) return false;
        ArmorSetDefinition set = sets.remove(id);
        if (set == null) return false;
        for (ItemDefinition.ArmorSlot slot : set.pieces.keySet()) items.deleteQuietly(set.itemId(slot));
        Files.deleteIfExists(file(id));
        for (String slot : TEXTURE_SLOTS) Files.deleteIfExists(textureFile(id, slot));
        return true;
    }

    /** A piece item was deleted on its own: the set forgets that slot (and disappears when it was the last piece). */
    void pieceDeleted(ItemDefinition item) {
        synchronized (items) {
            pieceDeletedLocked(item);
        }
    }

    private void pieceDeletedLocked(ItemDefinition item) {
        if (item.armor == null || item.armor.set == null || item.armor.set.isEmpty()) return;
        ArmorSetDefinition set = sets.get(item.armor.set);
        if (set == null) return;
        set.pieces.remove(item.armor.slot);
        try {
            if (set.pieces.isEmpty()) {
                sets.remove(set.id);
                Files.deleteIfExists(file(set.id));
                for (String slot : TEXTURE_SLOTS) Files.deleteIfExists(textureFile(set.id, slot));
            } else {
                write(set);
            }
        } catch (IOException e) {
            log.warning("Could not update armor set " + set.id + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ textures

    public void saveTexture(String setId, String slot, byte[] png) throws IOException {
        synchronized (items) {
            saveTextureLocked(setId, slot, png);
        }
    }

    private void saveTextureLocked(String setId, String slot, byte[] png) throws IOException {
        ArmorSetDefinition set = sets.get(setId);
        if (set == null) throw new IllegalArgumentException("unknown set");
        Files.write(textureFile(setId, slot), png);
        refreshTextureFlags(set);
        items.refreshWornFlags(setId);
    }

    public byte[] readTexture(String setId, String slot) throws IOException {
        Path file = textureFile(setId, slot);
        return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
    }

    private void refreshTextureFlags(ArmorSetDefinition set) {
        set.hasHumanoid = Files.isRegularFile(textureFile(set.id, "humanoid"));
        set.hasLeggings = Files.isRegularFile(textureFile(set.id, "leggings"));
    }

    private Path file(String id) {
        return dir.resolve(id + ".json");
    }

    private Path textureFile(String id, String slot) {
        if (id == null || !ArmorSetDefinition.ID_PATTERN.matcher(id).matches()) throw new IllegalArgumentException("invalid id");
        if (!TEXTURE_SLOTS.contains(slot)) throw new IllegalArgumentException("invalid texture slot");
        return dir.resolve(id + "." + slot + ".png");
    }

    private void write(ArmorSetDefinition set) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file(set.id), StandardCharsets.UTF_8)) {
            GSON.toJson(set, writer);
        }
    }
}
