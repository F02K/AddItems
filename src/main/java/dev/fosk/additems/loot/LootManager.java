package dev.fosk.additems.loot;

import dev.fosk.additems.block.CustomBlocks;
import dev.fosk.additems.item.ItemDefinition;
import dev.fosk.additems.item.ItemFactory;
import dev.fosk.additems.item.ItemRegistry;
import dev.fosk.additems.recipe.ItemRef;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies loot rules to the game: extra drops when blocks are broken or mobs die, and extra items in generated
 * chest loot. Rules are compiled by {@link #apply()} (targets resolved, items checked); broken rules are
 * reported through {@link #problem(String)} instead of failing silently.
 */
public final class LootManager implements Listener {

    /** A rule with its targets already resolved. */
    private record Active(LootRule rule, boolean any, Set<Material> blocks, Set<String> customBlocks,
                          Set<EntityType> mobs, Set<NamespacedKey> tables) {
        boolean matchesBlock(Material m) { return any || blocks.contains(m); }
        boolean matchesCustomBlock(String id) { return any || customBlocks.contains(id); }
        boolean matchesMob(EntityType t) { return any || mobs.contains(t); }
        boolean matchesTable(NamespacedKey k) { return any || tables.contains(k); }
    }

    private final Plugin plugin;
    private final ItemRegistry items;
    private final ItemFactory factory;
    private final LootRegistry registry;
    private final CustomBlocks customBlocks;
    private final Random random = new Random();

    private volatile List<Active> blockRules = List.of();
    private volatile List<Active> mobRules = List.of();
    private volatile List<Active> chestRules = List.of();
    private final Map<String, String> problems = new ConcurrentHashMap<>();

    public LootManager(Plugin plugin, ItemRegistry items, ItemFactory factory, LootRegistry registry,
                       CustomBlocks customBlocks) {
        this.customBlocks = customBlocks;
        this.plugin = plugin;
        this.items = items;
        this.factory = factory;
        this.registry = registry;
    }

    public String problem(String ruleId) {
        return problems.get(ruleId);
    }

    public int activeCount() {
        return blockRules.size() + mobRules.size() + chestRules.size();
    }

    // ------------------------------------------------------------------ compiling

    /** Resolves every rule's targets and drop items. Safe to call whenever items or rules change. */
    public void apply() {
        List<Active> block = new ArrayList<>();
        List<Active> mob = new ArrayList<>();
        List<Active> chest = new ArrayList<>();
        problems.clear();

        for (LootRule rule : registry.all()) {
            try {
                Active active = compile(rule);
                switch (rule.trigger) {
                    case BLOCK -> block.add(active);
                    case MOB -> mob.add(active);
                    case CHEST -> chest.add(active);
                }
            } catch (IllegalArgumentException e) {
                problems.put(rule.id, e.getMessage());
                plugin.getLogger().warning("Loot rule '" + rule.id + "' is not active: " + e.getMessage());
            }
        }
        blockRules = List.copyOf(block);
        mobRules = List.copyOf(mob);
        chestRules = List.copyOf(chest);
    }

    private Active compile(LootRule rule) {
        for (LootRule.Drop drop : rule.drops) stackFor(drop.item, 1); // fails if an item does not exist

        boolean any = rule.targets.contains("*");
        Set<Material> blocks = new HashSet<>();
        Set<String> customBlocks = new HashSet<>();
        Set<EntityType> mobs = new HashSet<>();
        Set<NamespacedKey> tables = new HashSet<>();
        if (!any) {
            for (String target : rule.targets) {
                switch (rule.trigger) {
                    case BLOCK -> {
                        ItemRef ref = ItemRef.parse(target, registry.customNamespace());
                        if (ref != null && ref.kind() == ItemRef.Kind.CUSTOM) {
                            ItemDefinition def = items.get(ref.name());
                            if (def == null || !def.block.placeable) {
                                throw new IllegalArgumentException("unknown custom block '" + ref.name() + "'");
                            }
                            customBlocks.add(def.id);
                        } else {
                            blocks.addAll(resolveBlocks(target));
                        }
                    }
                    case MOB -> mobs.add(resolveMob(target));
                    case CHEST -> tables.add(resolveTable(target));
                }
            }
        }
        return new Active(rule, any, blocks, customBlocks, mobs, tables);
    }

    private Set<Material> resolveBlocks(String target) {
        ItemRef ref = ItemRef.parse(target, "");
        if (ref == null || !ref.namespace().equals("minecraft")) throw new IllegalArgumentException("unknown block '" + target + "'");
        if (ref.kind() == ItemRef.Kind.TAG) {
            Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft(ref.name()), Material.class);
            if (tag == null) throw new IllegalArgumentException("unknown block tag '" + target + "'");
            return tag.getValues();
        }
        Material material = Material.matchMaterial(ref.name());
        if (material == null || !material.isBlock()) throw new IllegalArgumentException("unknown block '" + target + "'");
        return Set.of(material);
    }

    private EntityType resolveMob(String target) {
        ItemRef ref = ItemRef.parse(target, "");
        EntityType type = ref == null ? null : Registry.ENTITY_TYPE.get(new NamespacedKey(ref.namespace(), ref.name()));
        if (type == null) throw new IllegalArgumentException("unknown mob '" + target + "'");
        return type;
    }

    private NamespacedKey resolveTable(String target) {
        ItemRef ref = ItemRef.parse(target, "");
        NamespacedKey key = ref == null ? null : new NamespacedKey(ref.namespace(), ref.name());
        if (key == null || Bukkit.getLootTable(key) == null) throw new IllegalArgumentException("unknown loot table '" + target + "'");
        return key;
    }

    /** Builds the item for a drop reference; throws IllegalArgumentException if it does not exist. */
    private ItemStack stackFor(String ref, int amount) {
        ItemRef parsed = ItemRef.parse(ref, registry.customNamespace());
        if (parsed == null) throw new IllegalArgumentException("invalid item '" + ref + "'");
        return switch (parsed.kind()) {
            case CUSTOM -> {
                ItemDefinition def = items.get(parsed.name());
                if (def == null) throw new IllegalArgumentException("unknown custom item '" + parsed.name() + "'");
                yield factory.create(def, amount);
            }
            case VANILLA -> {
                Material material = parsed.namespace().equals("minecraft") ? Material.matchMaterial(parsed.name()) : null;
                if (material == null || !material.isItem() || material.isAir()) {
                    throw new IllegalArgumentException("unknown item '" + parsed.asString() + "'");
                }
                yield new ItemStack(material, amount);
            }
            case TAG -> throw new IllegalArgumentException("a tag cannot be dropped");
        };
    }

    // ------------------------------------------------------------------ rolling

    /** Rolls all drops of a rule and returns the stacks to hand out (empty if nothing dropped). */
    private List<ItemStack> rollDrops(LootRule rule, int level) {
        List<ItemStack> out = new ArrayList<>();
        for (LootRule.Drop drop : rule.drops) {
            int amount = LootRule.roll(drop, level, random);
            if (amount <= 0) continue;
            try {
                ItemStack base = stackFor(drop.item, 1);
                int perStack = Math.max(1, base.getMaxStackSize());
                for (int left = amount; left > 0; left -= perStack) {
                    ItemStack part = base.clone();
                    part.setAmount(Math.min(perStack, left));
                    out.add(part);
                }
            } catch (IllegalArgumentException e) {
                // item vanished since apply(); the next apply() reports it
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        List<Active> rules = blockRules;
        if (rules.isEmpty()) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        Block block = event.getBlock();
        ItemStack tool = player.getInventory().getItemInMainHand();
        boolean silk = tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0;
        int fortune = tool.getEnchantmentLevel(Enchantment.FORTUNE);
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        Material type = block.getType();
        ItemDefinition custom = customBlocks.at(block);
        Boolean correct = null;

        for (Active active : rules) {
            LootRule rule = active.rule();
            if (custom != null ? !active.matchesCustomBlock(custom.id) : !active.matchesBlock(type)) continue;
            if (rule.skipWithSilkTouch && silk) continue;
            if (rule.requireCorrectTool) {
                // custom blocks decide this themselves: "requires tool" with their preferred tool kind
                if (custom != null) {
                    if (!customBlocks.toolMatches(custom.block.preferredTool, tool)) continue;
                } else {
                    if (correct == null) correct = block.isPreferredTool(tool);
                    if (!correct) continue;
                }
            }
            if (rule.replaceVanilla) event.setDropItems(false);
            for (ItemStack stack : rollDrops(rule, fortune)) block.getWorld().dropItemNaturally(at, stack);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMobDeath(EntityDeathEvent event) {
        List<Active> rules = mobRules;
        LivingEntity entity = event.getEntity();
        if (rules.isEmpty() || entity instanceof Player) return;

        Player killer = entity.getKiller();
        int looting = killer == null ? 0 : killer.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.LOOTING);

        List<Active> applicable = new ArrayList<>();
        for (Active active : rules) {
            LootRule rule = active.rule();
            if (!active.matchesMob(entity.getType())) continue;
            if (rule.requirePlayerKill && killer == null) continue;
            if (rule.ignoreSpawnerMobs && entity.fromMobSpawner()) continue;
            applicable.add(active);
        }
        // clear vanilla drops first so drops added by earlier rules are not removed again
        if (applicable.stream().anyMatch(a -> a.rule().replaceVanilla)) event.getDrops().clear();
        for (Active active : applicable) event.getDrops().addAll(rollDrops(active.rule(), looting));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChestLoot(LootGenerateEvent event) {
        List<Active> rules = chestRules;
        if (rules.isEmpty()) return;
        NamespacedKey table = event.getLootTable().getKey();
        for (Active active : rules) {
            if (active.matchesTable(table)) event.getLoot().addAll(rollDrops(active.rule(), 0));
        }
    }
}
