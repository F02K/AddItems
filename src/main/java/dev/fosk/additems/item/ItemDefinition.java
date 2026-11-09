package dev.fosk.additems.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Persisted description of a custom item (one JSON file per item). Plain data holder (Gson).
 * Only the property group matching {@link #type} is used.
 */
public final class ItemDefinition {

    public static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_]{1,48}");

    public String id;
    public ItemType type = ItemType.GENERIC;
    /** Vanilla material the item is based on (fallback look + behaviour). */
    public String baseMaterial = "PAPER";
    public String displayName;
    public List<String> lore = new ArrayList<>();
    public int maxStackSize = 64;
    /** True once a texture PNG has been uploaded; drives pack generation. */
    public boolean hasTexture;

    public FoodProps food = new FoodProps();
    public ToolProps tool = new ToolProps();
    public BlockProps block = new BlockProps();

    /**
     * Settings of a placeable custom block. Custom blocks are real note blocks in the world; each one owns a unique
     * note block state (instrument / note / powered) that the resource pack maps to its own model.
     */
    public static final class BlockProps {
        public static final Pattern STATE = Pattern.compile("instrument=[a-z_]{1,32},note=\\d{1,2},powered=(true|false)");
        public static final Pattern SOUND = Pattern.compile("[a-z0-9_.:/-]{0,100}");
        public static final Set<String> TOOLS = Set.of("NONE", "AXE", "PICKAXE", "SHOVEL", "HOE");
        public static final Set<String> TEXTURE_MODES = Set.of("ALL", "SIDES");
        public static final Set<String> RENDERINGS = Set.of("SOLID", "CUTOUT", "TRANSLUCENT");

        public boolean placeable = false;
        /** Assigned automatically on first save, e.g. "instrument=harp,note=1,powered=false". Never changes. */
        public String state;
        /** Vanilla scale: dirt 0.5, stone 1.5, obsidian 50. */
        public double hardness = 1.5;
        /** Tool kind that mines it best / is needed for drops: NONE, AXE, PICKAXE, SHOVEL, HOE. */
        public String preferredTool = "NONE";
        /** Without the preferred tool the block drops nothing (like ores). */
        public boolean requiresTool = false;
        /** The block drops its own item when broken. Turn off and use a drop rule for other drops. */
        public boolean dropSelf = true;
        /** ALL: one texture on every face. SIDES: separate top, bottom and side textures. */
        public String textureMode = "ALL";
        /**
         * How the block is drawn. SOLID: normal opaque cube. CUTOUT: see-through where the texture is transparent and faces are
         * never culled (leaves, grates). TRANSLUCENT: glass-like, partly transparent pixels. The block still blocks light and
         * movement like a note block either way.
         */
        public String rendering = "SOLID";
        /** Optional sound events played on placing / breaking, e.g. "block.stone.place". */
        public String placeSound = "";
        public String breakSound = "";
    }

    public static final class FoodProps {
        public int nutrition = 4;
        public float saturation = 2.4f;
        public boolean canAlwaysEat = false;
        public float eatSeconds = 1.6f;
    }

    public enum ToolKind { SWORD, PICKAXE, AXE, SHOVEL, HOE }

    public static final class ToolProps {
        public ToolKind kind = ToolKind.PICKAXE;
        /** Preset the values started from (e.g. IRON) or CUSTOM. Informational, for the dashboard. */
        public String tier = "IRON";
        /** Which blocks give drops (WOODEN ... NETHERITE), like the vanilla tool tiers. Independent of {@link #tier}. */
        public String harvestTier = "IRON";
        public float miningSpeed = 6.0f;
        public int durability = 250;
        public int enchantability = 14;
        /** Total attack damage as shown in the tooltip (the factory subtracts the player's base 1). */
        public double damage = 4.0;
        /** Total attack speed as shown in the tooltip (the factory subtracts the player's base 4). */
        public double attackSpeed = 1.2;
    }

    /** Returns an error message, or null when the definition is valid. */
    public String validate() {
        if (id == null || !ID_PATTERN.matcher(id).matches()) return "id must match [a-z0-9_]{1,48}";
        if (type == null) return "type is required";
        if (baseMaterial == null || baseMaterial.isBlank()) return "baseMaterial is required";
        if (maxStackSize < 1 || maxStackSize > 99) return "maxStackSize must be 1-99";
        if (displayName != null && displayName.length() > 100) return "displayName too long";
        if (lore == null) lore = new ArrayList<>();
        if (lore.size() > 20) return "too many lore lines";
        for (String line : lore) if (line == null || line.length() > 200) return "lore line too long";
        if (block == null) block = new BlockProps();
        if (block.placeable && type != ItemType.BLOCK) return "only block items can be placeable";
        if (block.placeable || block.state != null) {
            if (!(block.hardness >= 0.05 && block.hardness <= 100)) return "hardness must be 0.05-100";
            if (block.preferredTool == null || !BlockProps.TOOLS.contains(block.preferredTool)) return "invalid preferred tool";
            if (block.textureMode == null || !BlockProps.TEXTURE_MODES.contains(block.textureMode)) return "invalid texture mode";
            if (block.rendering == null || !BlockProps.RENDERINGS.contains(block.rendering)) return "invalid rendering";
            if (block.state != null && !block.state.isBlank() && !BlockProps.STATE.matcher(block.state).matches()) {
                return "invalid block state";
            }
            for (String sound : new String[]{block.placeSound, block.breakSound}) {
                if (sound != null && !BlockProps.SOUND.matcher(sound).matches()) return "invalid sound id";
            }
        }
        if (type == ItemType.FOOD) {
            if (food == null) return "food properties required";
            if (food.nutrition < 0 || food.nutrition > 100) return "nutrition must be 0-100";
            if (food.saturation < 0 || food.saturation > 100) return "saturation must be 0-100";
            if (food.eatSeconds < 0.05f || food.eatSeconds > 60) return "eatSeconds must be 0.05-60";
        }
        if (type == ItemType.TOOL) {
            if (tool == null || tool.kind == null) return "tool properties required";
            if (tool.durability < 1 || tool.durability > 100_000) return "durability must be 1-100000";
            if (tool.miningSpeed < 0 || tool.miningSpeed > 1000) return "miningSpeed must be 0-1000";
            if (tool.damage < 1 || tool.damage > 1000) return "damage must be 1-1000";
            if (tool.attackSpeed < 0.1 || tool.attackSpeed > 20) return "attackSpeed must be 0.1-20";
            if (tool.enchantability < 0 || tool.enchantability > 100) return "enchantability must be 0-100";
            if (tool.tier == null || tool.tier.length() > 20) return "invalid tier";
            if (tool.harvestTier == null || !Presets.TIERS.stream().anyMatch(t -> t.id().equals(tool.harvestTier))) {
                return "harvestTier must be one of the vanilla tiers";
            }
        }
        return null;
    }
}
