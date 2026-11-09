package dev.fosk.additems.loot;

import dev.fosk.additems.recipe.ItemRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * One loot rule (one JSON file): "when {trigger} happens to one of {targets}, roll these extra drops".
 * Plain data holder (Gson).
 */
public final class LootRule {

    public enum Trigger {
        /** A player breaks a block. Targets: block ids / block tags. */
        BLOCK,
        /** A mob dies. Targets: entity types. */
        MOB,
        /** A chest or other container generates its loot. Targets: loot table keys. */
        CHEST
    }

    public static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_]{1,48}");
    private static final Pattern TARGET = Pattern.compile("^(\\*|#?[a-z0-9_.-]{1,32}:[a-z0-9_./-]{1,96})$");

    public String id;
    public Trigger trigger = Trigger.MOB;
    /** Block ids ("minecraft:iron_ore", "#minecraft:logs"), entity types ("minecraft:zombie") or loot tables; "*" = all. */
    public List<String> targets = new ArrayList<>();
    public List<Drop> drops = new ArrayList<>();

    /** Remove the vanilla drops of the same event (BLOCK and MOB only). */
    public boolean replaceVanilla = false;
    /** BLOCK: only drop when the tool is the right one for the block (like vanilla drops). */
    public boolean requireCorrectTool = true;
    /** BLOCK: do not add drops when the tool has Silk Touch. */
    public boolean skipWithSilkTouch = true;
    /** MOB: only when a player (or their tamed pet) killed it. */
    public boolean requirePlayerKill = true;
    /** MOB: do not drop for mobs that came from a monster spawner. */
    public boolean ignoreSpawnerMobs = false;

    public static final class Drop {
        /** Item reference: "additems:ruby" or "minecraft:diamond". */
        public String item;
        public int min = 1;
        public int max = 1;
        /** Probability in percent (0-100). */
        public double chance = 100;
        /** Extra chance (percentage points) per level of Fortune (blocks), Looting (mobs). */
        public double chanceBonusPerLevel = 0;
        /** Extra amount (up to) per enchantment level. */
        public int amountBonusPerLevel = 0;
    }

    /**
     * Rolls one drop: returns the amount to drop (0 = no drop). {@code level} is the Fortune / Looting level.
     */
    public static int roll(Drop drop, int level, Random random) {
        double chance = Math.min(100.0, drop.chance + drop.chanceBonusPerLevel * level);
        if (random.nextDouble() * 100.0 >= chance) return 0;
        int amount = drop.min + (drop.max > drop.min ? random.nextInt(drop.max - drop.min + 1) : 0);
        if (level > 0 && drop.amountBonusPerLevel > 0) {
            amount += random.nextInt(drop.amountBonusPerLevel * level + 1);
        }
        return Math.max(1, Math.min(64, amount));
    }

    /** Structural validation (syntax only; whether items/targets exist is checked when activating). */
    public String validate(String customNamespace) {
        if (id == null || !ID_PATTERN.matcher(id).matches()) return "id must match [a-z0-9_]{1,48}";
        if (trigger == null) return "trigger is required";
        if (targets == null || targets.isEmpty()) return "add at least one target";
        if (targets.size() > 200) return "too many targets";
        for (String target : targets) {
            if (target == null || !TARGET.matcher(target).matches()) return "invalid target: " + target;
            if (target.startsWith("#") && trigger != Trigger.BLOCK) return "tags are only allowed for block rules";
        }
        if (drops == null || drops.isEmpty()) return "add at least one drop";
        if (drops.size() > 20) return "at most 20 drops per rule";
        for (Drop d : drops) {
            ItemRef ref = ItemRef.parse(d.item, customNamespace);
            if (ref == null || ref.kind() == ItemRef.Kind.TAG) return "each drop needs a valid item";
            if (d.min < 1 || d.min > 64) return "min must be 1-64";
            if (d.max < d.min || d.max > 64) return "max must be between min and 64";
            if (!(d.chance > 0 && d.chance <= 100)) return "chance must be above 0 and at most 100";
            if (d.chanceBonusPerLevel < 0 || d.chanceBonusPerLevel > 100) return "chance bonus must be 0-100";
            if (d.amountBonusPerLevel < 0 || d.amountBonusPerLevel > 64) return "amount bonus must be 0-64";
        }
        return null;
    }
}
