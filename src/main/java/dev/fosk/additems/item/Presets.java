package dev.fosk.additems.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Locale;

/**
 * Vanilla reference values the dashboard offers as starting points (tool tiers, foods). The user can edit
 * every value afterwards. Source: minecraft.wiki (Tool, Pickaxe, Sword, Food), checked 2026-11-08 for 26.3;
 * see docs/minecraft-versions.md. Damage and speed are the totals shown in the tooltip.
 */
public final class Presets {

    private Presets() {}

    public record Tier(String id, String label, int durability, float miningSpeed, int enchantability) {}

    /** Per-kind stats for one tier: tooltip damage and attack speed. */
    public record Stat(double damage, double speed) {}

    public static final List<Tier> TIERS = List.of(
            new Tier("WOODEN", "Wooden", 59, 2f, 15),
            new Tier("STONE", "Stone", 131, 4f, 5),
            new Tier("COPPER", "Copper", 190, 5f, 13),
            new Tier("IRON", "Iron", 250, 6f, 14),
            new Tier("GOLDEN", "Golden", 32, 12f, 22),
            new Tier("DIAMOND", "Diamond", 1561, 8f, 10),
            new Tier("NETHERITE", "Netherite", 2031, 9f, 15));

    /** Index order matches {@link #TIERS}. */
    private static Stat[] stats(ItemDefinition.ToolKind kind) {
        return switch (kind) {
            case SWORD -> row(new double[]{4, 5, 5, 6, 4, 7, 8}, new double[]{1.6, 1.6, 1.6, 1.6, 1.6, 1.6, 1.6});
            case PICKAXE -> row(new double[]{2, 3, 3, 4, 2, 5, 6}, new double[]{1.2, 1.2, 1.2, 1.2, 1.2, 1.2, 1.2});
            case AXE -> row(new double[]{7, 9, 9, 9, 7, 9, 10}, new double[]{0.8, 0.8, 0.8, 0.9, 1.0, 1.0, 1.0});
            case SHOVEL -> row(new double[]{2.5, 3.5, 3.5, 4.5, 2.5, 5.5, 6.5}, new double[]{1, 1, 1, 1, 1, 1, 1});
            case HOE -> row(new double[]{1, 1, 1, 1, 1, 1, 1}, new double[]{1, 2, 2, 3, 1, 4, 4});
        };
    }

    private static Stat[] row(double[] damage, double[] speed) {
        Stat[] out = new Stat[damage.length];
        for (int i = 0; i < out.length; i++) out[i] = new Stat(damage[i], speed[i]);
        return out;
    }

    public record Food(String id, String label, String material, int nutrition, float saturation, float eatSeconds) {}

    public static final List<Food> FOODS = List.of(
            new Food("apple", "Apple", "APPLE", 4, 2.4f, 1.6f),
            new Food("bread", "Bread", "BREAD", 5, 6f, 1.6f),
            new Food("cookie", "Cookie", "COOKIE", 2, 0.4f, 1.6f),
            new Food("melon_slice", "Melon slice", "MELON_SLICE", 2, 1.2f, 1.6f),
            new Food("dried_kelp", "Dried kelp (fast)", "DRIED_KELP", 1, 0.6f, 0.8f),
            new Food("baked_potato", "Baked potato", "BAKED_POTATO", 5, 6f, 1.6f),
            new Food("cooked_chicken", "Cooked chicken", "COOKED_CHICKEN", 6, 7.2f, 1.6f),
            new Food("golden_carrot", "Golden carrot", "GOLDEN_CARROT", 6, 14.4f, 1.6f),
            new Food("steak", "Steak", "COOKED_BEEF", 8, 12.8f, 1.6f),
            new Food("pumpkin_pie", "Pumpkin pie", "PUMPKIN_PIE", 8, 4.8f, 1.6f),
            new Food("honey_bottle", "Honey bottle (slow)", "HONEY_BOTTLE", 6, 1.2f, 2f));

    /** Vanilla base item name for a tier + kind, e.g. IRON_PICKAXE. Golden/wooden use their own prefixes. */
    public static String baseMaterial(String tierId, ItemDefinition.ToolKind kind) {
        String prefix = tierId.toUpperCase(Locale.ROOT);
        return prefix + "_" + kind.name();
    }

    /** Machine-readable form for the dashboard (/api/meta). Material availability is filled in by the caller. */
    public static JsonObject toJson() {
        JsonObject root = new JsonObject();

        JsonArray tiers = new JsonArray();
        for (Tier t : TIERS) {
            JsonObject o = new JsonObject();
            o.addProperty("id", t.id());
            o.addProperty("label", t.label());
            o.addProperty("durability", t.durability());
            o.addProperty("miningSpeed", t.miningSpeed());
            o.addProperty("enchantability", t.enchantability());
            tiers.add(o);
        }
        root.add("toolTiers", tiers);

        JsonObject toolStats = new JsonObject();
        for (ItemDefinition.ToolKind kind : ItemDefinition.ToolKind.values()) {
            JsonObject perTier = new JsonObject();
            Stat[] stats = stats(kind);
            for (int i = 0; i < TIERS.size(); i++) {
                JsonObject s = new JsonObject();
                s.addProperty("damage", stats[i].damage());
                s.addProperty("speed", stats[i].speed());
                s.addProperty("material", baseMaterial(TIERS.get(i).id(), kind));
                perTier.add(TIERS.get(i).id(), s);
            }
            toolStats.add(kind.name(), perTier);
        }
        root.add("toolStats", toolStats);

        JsonArray foods = new JsonArray();
        for (Food f : FOODS) {
            JsonObject o = new JsonObject();
            o.addProperty("id", f.id());
            o.addProperty("label", f.label());
            o.addProperty("material", f.material());
            o.addProperty("nutrition", f.nutrition());
            o.addProperty("saturation", f.saturation());
            o.addProperty("eatSeconds", f.eatSeconds());
            foods.add(o);
        }
        root.add("foodPresets", foods);
        return root;
    }
}
