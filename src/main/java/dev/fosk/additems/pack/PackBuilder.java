package dev.fosk.additems.pack;

import dev.fosk.additems.item.ItemDefinition;
import dev.fosk.additems.item.ItemRegistry;
import dev.fosk.additems.item.ItemType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds a deterministic resource pack zip (same input -> same SHA-1) from all item definitions. */
public final class PackBuilder {

    public record Result(Path file, String sha1, long size) {}

    private final ItemRegistry registry;
    private final PackFormat format;
    private final String namespace;
    private final List<String> noteVariants;

    public PackBuilder(ItemRegistry registry, PackFormat format, String namespace) {
        this(registry, format, namespace, List.of());
    }

    /** @param noteVariants every valid note block variant key, needed to map custom blocks (may be empty) */
    public PackBuilder(ItemRegistry registry, PackFormat format, String namespace, List<String> noteVariants) {
        this.registry = registry;
        this.format = format;
        this.namespace = namespace;
        this.noteVariants = List.copyOf(noteVariants);
    }

    public Result build(Path target) throws IOException {
        // TreeMap keeps entry order stable so the hash only changes when content changes.
        Map<String, byte[]> files = new TreeMap<>();
        files.put("pack.mcmeta", format.packMcmeta("AddItems generated pack").getBytes(StandardCharsets.UTF_8));

        List<ItemDefinition> blocks = new ArrayList<>();
        for (ItemDefinition item : registry.all()) {
            files.putAll(format.itemFiles(namespace, item, registry.readTextures(item.id)));
            if (item.type == ItemType.BLOCK && item.block.placeable && item.block.state != null && item.hasTexture) {
                blocks.add(item);
            }
        }
        for (var set : registry.armorSets().all()) {
            Map<String, byte[]> worn = new java.util.HashMap<>();
            for (String slot : dev.fosk.additems.item.ArmorSets.TEXTURE_SLOTS) {
                byte[] data = registry.armorSets().readTexture(set.id, slot);
                if (data != null) worn.put(slot, data);
            }
            files.putAll(format.armorSetFiles(namespace, set, worn));
        }
        files.putAll(format.blockstateFiles(namespace, blocks, noteVariants));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setTime(0L); // fixed timestamp for reproducible output
                zip.putNextEntry(zipEntry);
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        byte[] bytes = buffer.toByteArray();
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
        return new Result(target, sha1(bytes), bytes.length);
    }

    /** Describes an already built pack file (used to restore state after a restart). */
    public static Result describe(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        return new Result(file, sha1(bytes), bytes.length);
    }

    static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
