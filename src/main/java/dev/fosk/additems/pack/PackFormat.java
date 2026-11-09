package dev.fosk.additems.pack;

import dev.fosk.additems.item.ItemDefinition;

import java.util.List;
import java.util.Map;

/**
 * Everything that differs between Minecraft versions when generating a resource pack lives behind this
 * interface (pack.mcmeta shape, asset paths, model definition format). See docs/minecraft-versions.md.
 * Add a new implementation and register it in {@link PackFormats} when supporting another version.
 */
public interface PackFormat {

    /** Minecraft version this implementation targets, e.g. "26.3". */
    String minecraftVersion();

    /** Contents of pack.mcmeta. */
    String packMcmeta(String description);

    /**
     * Files (path inside the zip -> bytes) needed for one item. {@code textures} holds the existing texture files by
     * slot ("main", "top", "bottom", "side"). Items without a usable texture produce no files because they keep their
     * vanilla appearance.
     */
    Map<String, byte[]> itemFiles(String namespace, ItemDefinition item, Map<String, byte[]> textures);

    /**
     * The block state file that maps custom block states to their models (note block). {@code blocks} are the
     * placeable blocks that have a model; {@code allVariants} lists every valid variant key of the note block so the
     * untouched ones keep their vanilla model. Returns nothing when there is no custom block.
     */
    Map<String, byte[]> blockstateFiles(String namespace, List<ItemDefinition> blocks, List<String> allVariants);
}
