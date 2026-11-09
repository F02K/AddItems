package dev.fosk.additems.item;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A group of armor pieces that belong together (one to four pieces, e.g. only a helmet, or helmet and leggings).
 * The set owns the shared values (name, material tier, equip sound, the worn textures); saving it creates or updates one
 * ordinary {@link ItemDefinition} per piece (id "&lt;set&gt;_&lt;slot&gt;"), so recipes and drops still work per piece.
 */
public final class ArmorSetDefinition {

    /** Short enough that "&lt;id&gt;_chestplate" still fits the 48 characters of an item id. */
    public static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_]{1,36}");

    public static final class Piece {
        public double defense = 0;
        public int durability = 100;
        /**
         * The item of this piece. Empty means "&lt;set&gt;_&lt;slot&gt;". Set when an existing single armor item was turned into
         * a piece of a set, so the item keeps its id (recipes and drops that use it keep working).
         */
        public String item = "";
    }

    public String id;
    /** MiniMessage prefix of the piece names, e.g. "&lt;red&gt;Ruby" gives "Ruby Helmet". */
    public String displayName = "";
    /** Preset the values started from (e.g. IRON) or CUSTOM. Informational, for the dashboard. */
    public String tier = "IRON";
    public double toughness = 0;
    public double knockbackResistance = 0;
    public int enchantability = 9;
    public String equipSound = "item.armor.equip_iron";
    /** The pieces this set has; the key decides the slot. */
    public Map<ItemDefinition.ArmorSlot, Piece> pieces = new LinkedHashMap<>();

    /** Computed: the worn textures that exist (helmet/chestplate/boots layer and leggings layer). */
    public boolean hasHumanoid;
    public boolean hasLeggings;

    public String itemId(ItemDefinition.ArmorSlot slot) {
        Piece piece = pieces.get(slot);
        if (piece != null && piece.item != null && !piece.item.isEmpty()) return piece.item;
        return id + "_" + slot.name().toLowerCase(Locale.ROOT);
    }

    /** Whether a piece in this slot finds its worn texture in the set. */
    public boolean hasWornFor(ItemDefinition.ArmorSlot slot) {
        return slot == ItemDefinition.ArmorSlot.LEGGINGS ? hasLeggings : hasHumanoid;
    }

    /** Returns an error message, or null when the set is valid. */
    public String validate() {
        if (id == null || !ID_PATTERN.matcher(id).matches()) return "set id must match [a-z0-9_]{1,36}";
        if (displayName == null) displayName = "";
        if (displayName.length() > 60) return "set name too long";
        if (tier == null || tier.length() > 20) return "invalid tier";
        if (toughness < 0 || toughness > 20) return "toughness must be 0-20";
        if (knockbackResistance < 0 || knockbackResistance > 1) return "knockback resistance must be 0-1";
        if (enchantability < 0 || enchantability > 100) return "enchantability must be 0-100";
        if (equipSound == null || !ItemDefinition.ArmorProps.SOUND.matcher(equipSound).matches()) return "invalid equip sound";
        if (pieces == null || pieces.isEmpty()) return "a set needs at least one piece";
        for (Map.Entry<ItemDefinition.ArmorSlot, Piece> entry : pieces.entrySet()) {
            Piece piece = entry.getValue();
            if (entry.getKey() == null || piece == null) return "invalid piece";
            if (piece.defense < 0 || piece.defense > 30) return "defense must be 0-30";
            if (piece.durability < 1 || piece.durability > 100_000) return "durability must be 1-100000";
            if (piece.item == null) piece.item = "";
            if (!piece.item.isEmpty() && !ItemDefinition.ID_PATTERN.matcher(piece.item).matches()) return "invalid piece item id";
        }
        return null;
    }
}
