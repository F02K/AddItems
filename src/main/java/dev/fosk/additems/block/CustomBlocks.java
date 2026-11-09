package dev.fosk.additems.block;

import dev.fosk.additems.item.ItemDefinition;
import dev.fosk.additems.item.ItemFactory;
import dev.fosk.additems.item.ItemRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Instrument;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Custom blocks are real note blocks: every custom block owns one unique note block state (instrument, note,
 * powered) and the resource pack gives that state its own model. This class knows which states exist, which of them
 * custom blocks may use, and maps world blocks back to their item definition.
 *
 * <p>States handed out are only those with {@code powered=false} and {@code note>=1}: a note block placed normally has
 * note 0, so a vanilla note block never shows up as a custom block by accident.
 */
public final class CustomBlocks {

    private final ItemRegistry items;
    private final ItemFactory factory;
    private final List<String> variants;
    private final List<String> pool;

    public CustomBlocks(ItemRegistry items, ItemFactory factory) {
        this.items = items;
        this.factory = factory;

        Set<String> all = new LinkedHashSet<>();
        Set<String> free = new LinkedHashSet<>();
        for (Instrument instrument : Instrument.values()) {
            NoteBlock data = (NoteBlock) Bukkit.createBlockData(Material.NOTE_BLOCK);
            data.setInstrument(instrument);
            for (int note = 0; note <= 24; note++) {
                data.setNote(new Note(note));
                for (boolean powered : new boolean[]{false, true}) {
                    data.setPowered(powered);
                    String key = stateKey(data);
                    all.add(key);
                    if (!powered && note >= 1) free.add(key);
                }
            }
        }
        this.variants = List.copyOf(all);
        this.pool = List.copyOf(free);
    }

    /** Why custom blocks would misbehave on this server (null when the server is set up correctly). */
    private volatile String setupProblem;

    public String setupProblem() {
        return setupProblem;
    }

    /**
     * Note blocks change their instrument when the block below or above changes, which would turn a custom block into
     * another one. Paper can switch that off: {@code block-updates.disable-noteblock-updates: true} in
     * {@code config/paper-global.yml} (verified on 26.3). Reads the file because there is no API for it.
     */
    public void checkServerSetup(java.io.File serverRoot) {
        java.io.File file = new java.io.File(serverRoot, "config/paper-global.yml");
        boolean enabled = false;
        if (file.isFile()) {
            enabled = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file)
                    .getBoolean("block-updates.disable-noteblock-updates", false);
        }
        setupProblem = enabled ? null
                : "Custom blocks need 'block-updates.disable-noteblock-updates: true' in config/paper-global.yml "
                + "(then restart the server). Without it, custom blocks change their look when blocks above or below them change.";
    }

    /** Every valid note block variant key (used to build the resource pack's block state file). */
    public List<String> allVariants() {
        return variants;
    }

    /** States custom blocks may be assigned, in allocation order. */
    public List<String> statePool() {
        return pool;
    }

    /** "instrument=harp,note=1,powered=false" for a note block's data. */
    static String stateKey(BlockData data) {
        String text = data.getAsString();
        int open = text.indexOf('[');
        return open < 0 ? "" : text.substring(open + 1, text.length() - 1);
    }

    public BlockData dataFor(ItemDefinition def) {
        return Bukkit.createBlockData("minecraft:note_block[" + def.block.state + "]");
    }

    /** The custom block at a world position, or null if it is not one (including vanilla note blocks). */
    public ItemDefinition at(Block block) {
        if (block.getType() != Material.NOTE_BLOCK) return null;
        return items.byBlockState(stateKey(block.getBlockData()));
    }

    /** Places a custom block without any events or physics. */
    public void place(Block block, ItemDefinition def) {
        block.setBlockData(dataFor(def), false);
    }

    /** True if the tool counts as the given kind (NONE, AXE, PICKAXE, SHOVEL, HOE), vanilla or custom. */
    public boolean toolMatches(String kind, ItemStack tool) {
        if (kind == null || kind.equals("NONE")) return true;
        if (tool == null || tool.getType().isAir()) return false;
        String customId = factory.idOf(tool);
        if (customId != null) {
            ItemDefinition def = items.get(customId);
            return def != null && def.type == dev.fosk.additems.item.ItemType.TOOL && def.tool.kind.name().equals(kind);
        }
        return tool.getType().name().endsWith("_" + kind);
    }

    /** Names for debugging: all custom blocks currently defined. */
    public List<String> describe() {
        List<String> out = new ArrayList<>();
        for (ItemDefinition def : items.all()) {
            if (def.block != null && def.block.placeable) out.add(def.id + " -> " + def.block.state);
        }
        return out;
    }
}
