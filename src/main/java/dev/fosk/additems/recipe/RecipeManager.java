package dev.fosk.additems.recipe;

import dev.fosk.additems.item.ItemDefinition;
import dev.fosk.additems.item.ItemFactory;
import dev.fosk.additems.item.ItemRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns recipe definitions into real server recipes and keeps them in sync. Must be used on the main thread.
 * Anvil recipes are not a vanilla recipe type, so they are handled through {@link PrepareAnvilEvent}.
 */
public final class RecipeManager implements Listener {

    private final Plugin plugin;
    private final ItemRegistry items;
    private final ItemFactory factory;
    private final RecipeRegistry recipes;

    private final Set<NamespacedKey> registered = new HashSet<>();
    /** Custom item ids each registered crafting recipe is allowed to see in the grid. */
    private final Map<NamespacedKey, Set<String>> allowedCustom = new HashMap<>();
    private final List<RecipeDefinition> anvilRecipes = new ArrayList<>();
    private final List<Recipe> pending = new ArrayList<>();
    private final Map<String, String> problems = new ConcurrentHashMap<>();

    public RecipeManager(Plugin plugin, ItemRegistry items, ItemFactory factory, RecipeRegistry recipes) {
        this.plugin = plugin;
        this.items = items;
        this.factory = factory;
        this.recipes = recipes;
    }

    /** Why a recipe is not active (missing item, ...), or null if it is fine. */
    public String problem(String recipeId) {
        return problems.get(recipeId);
    }

    public int registeredCount() {
        return registered.size() + anvilRecipes.size();
    }

    /** Removes all AddItems recipes and registers them again from the current definitions. */
    public void apply() {
        Bukkit.removeRecipes(new ArrayList<>(registered));
        registered.clear();
        allowedCustom.clear();
        anvilRecipes.clear();
        problems.clear();
        pending.clear();

        for (RecipeDefinition def : recipes.all()) {
            try {
                register(def);
            } catch (IllegalArgumentException | IllegalStateException e) {
                problems.put(def.id, e.getMessage());
                plugin.getLogger().warning("Recipe '" + def.id + "' is not active: " + e.getMessage());
            }
        }
        // One batch so the server resends recipes to clients only once.
        Bukkit.addRecipes(pending);
        pending.clear();
        Bukkit.getOnlinePlayers().forEach(this::discover);
    }

    private void discover(Player player) {
        if (!registered.isEmpty()) player.discoverRecipes(registered);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        discover(event.getPlayer());
    }

    // ------------------------------------------------------------------ registration

    private void register(RecipeDefinition def) {
        NamespacedKey key = new NamespacedKey(plugin, "r_" + def.id);
        ItemStack result = result(def.result, def.amount);
        Set<String> customs = new HashSet<>();
        for (String ref : def.ingredientRefs()) {
            ItemRef parsed = ItemRef.parse(ref, recipes.customNamespace());
            if (parsed != null && parsed.kind() == ItemRef.Kind.CUSTOM) customs.add(parsed.name());
        }

        Recipe recipe;
        switch (def.type) {
            case SHAPED -> {
                RecipeDefinition.Shape shape = def.shape();
                ShapedRecipe shaped = new ShapedRecipe(key, result);
                shaped.shape(shape.pattern());
                shape.ingredients().forEach((letter, ref) -> shaped.setIngredient(letter, choice(ref)));
                recipe = shaped;
            }
            case SHAPELESS -> {
                ShapelessRecipe shapeless = new ShapelessRecipe(key, result);
                for (String ref : def.gridRefs()) shapeless.addIngredient(choice(ref));
                recipe = shapeless;
            }
            case FURNACE -> recipe = new FurnaceRecipe(key, result, choice(def.input), def.experience, def.cookTime);
            case BLASTING -> recipe = new BlastingRecipe(key, result, choice(def.input), def.experience, def.cookTime);
            case SMOKING -> recipe = new SmokingRecipe(key, result, choice(def.input), def.experience, def.cookTime);
            case CAMPFIRE -> recipe = new CampfireRecipe(key, result, choice(def.input), def.experience, def.cookTime);
            case STONECUTTING -> recipe = new StonecuttingRecipe(key, result, choice(def.input));
            case SMITHING -> recipe = new SmithingTransformRecipe(key, result,
                    choice(def.template), choice(def.base), choice(def.addition));
            case ANVIL -> {
                choice(def.left); // fail early if an ingredient cannot be resolved
                choice(def.right);
                anvilRecipes.add(def);
                return;
            }
            default -> throw new IllegalStateException("unsupported recipe type " + def.type);
        }
        pending.add(recipe);
        registered.add(key);
        allowedCustom.put(key, customs);
    }

    private ItemStack result(String ref, int amount) {
        ItemRef parsed = ItemRef.parse(ref, recipes.customNamespace());
        if (parsed == null) throw new IllegalArgumentException("invalid result '" + ref + "'");
        return switch (parsed.kind()) {
            case CUSTOM -> {
                ItemDefinition def = items.get(parsed.name());
                if (def == null) throw new IllegalArgumentException("unknown custom item '" + parsed.name() + "'");
                yield factory.create(def, amount);
            }
            case VANILLA -> new ItemStack(material(parsed), amount);
            case TAG -> throw new IllegalArgumentException("a tag cannot be a result");
        };
    }

    private RecipeChoice choice(String ref) {
        ItemRef parsed = ItemRef.parse(ref, recipes.customNamespace());
        if (parsed == null) throw new IllegalArgumentException("invalid item '" + ref + "'");
        return switch (parsed.kind()) {
            case CUSTOM -> {
                ItemDefinition def = items.get(parsed.name());
                if (def == null) throw new IllegalArgumentException("unknown custom item '" + parsed.name() + "'");
                // Match by our id only, so damaged tools etc. still count. ExactChoice is deprecated (26.2).
                String wanted = def.id;
                yield RecipeChoice.predicateChoice(stack -> wanted.equals(factory.idOf(stack)),
                        factory.create(def, 1));
            }
            case VANILLA -> new RecipeChoice.MaterialChoice(material(parsed));
            case TAG -> {
                Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_ITEMS,
                        new NamespacedKey(parsed.namespace(), parsed.name()), Material.class);
                if (tag == null) throw new IllegalArgumentException("unknown tag '#" + parsed.name() + "'");
                yield new RecipeChoice.MaterialChoice(tag);
            }
        };
    }

    private static Material material(ItemRef ref) {
        Material material = ref.namespace().equals("minecraft") ? Material.matchMaterial(ref.name()) : null;
        if (material == null || !material.isItem() || material.isAir()) {
            throw new IllegalArgumentException("unknown item '" + ref.asString() + "'");
        }
        return material;
    }

    // ------------------------------------------------------------------ protection / anvil

    /**
     * Custom items are built on vanilla materials, so a vanilla recipe would happily accept them as ingredients
     * (a custom stick used as a normal stick). Block that, and keep our own recipes from accepting custom items
     * they do not list.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (recipe == null) return;
        Set<String> allowed = Set.of();
        if (recipe instanceof org.bukkit.Keyed keyed) {
            NamespacedKey key = keyed.getKey();
            if (key.getNamespace().equals(plugin.getName().toLowerCase())) {
                allowed = allowedCustom.getOrDefault(key, Set.of());
            } else if (!key.getNamespace().equals(NamespacedKey.MINECRAFT)) {
                return; // other plugins' recipes are none of our business
            }
        }
        for (ItemStack stack : event.getInventory().getMatrix()) {
            String id = factory.idOf(stack);
            if (id != null && !allowed.contains(id)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        ItemStack first = inventory.getFirstItem();
        ItemStack second = inventory.getSecondItem();
        if (first == null || second == null) return;
        for (RecipeDefinition def : anvilRecipes) {
            if (matches(def.left, first, 1) && matches(def.right, second, def.rightAmount)) {
                event.setResult(result(def.result, 1));
                event.getView().setRepairCost(def.levelCost);
                event.getView().setRepairItemCountCost(def.rightAmount);
                return;
            }
        }
    }

    private boolean matches(String ref, ItemStack stack, int minAmount) {
        if (stack == null || stack.getAmount() < minAmount) return false;
        ItemRef parsed = ItemRef.parse(ref, recipes.customNamespace());
        if (parsed == null) return false;
        String id = factory.idOf(stack);
        return switch (parsed.kind()) {
            case CUSTOM -> parsed.name().equals(id);
            case VANILLA -> id == null && stack.getType().getKey().getKey().equals(parsed.name());
            case TAG -> {
                Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_ITEMS,
                        new NamespacedKey(parsed.namespace(), parsed.name()), Material.class);
                yield id == null && tag != null && tag.isTagged(stack.getType());
            }
        };
    }
}
