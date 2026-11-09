package dev.fosk.additems.item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.Enchantable;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.Tool;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.tags.BlockTypeTagKeys;
import io.papermc.paper.registry.set.RegistryKeySet;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.util.TriState;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.BlockType;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/** Turns an {@link ItemDefinition} into a real {@link ItemStack} using data components. */
public final class ItemFactory {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Plugin plugin;
    private final String namespace;
    private final NamespacedKey idKey;

    public ItemFactory(Plugin plugin, String namespace) {
        this.plugin = plugin;
        this.namespace = namespace;
        this.idKey = new NamespacedKey(plugin, "id");
    }

    /** Reads the custom item id from a stack, or null if it is not an AddItems item. */
    public String idOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        return stack.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    public ItemStack create(ItemDefinition def, int amount) {
        Material material = Material.matchMaterial(def.baseMaterial);
        if (material == null || !material.isItem()) material = Material.PAPER;
        // Placeable custom blocks are note blocks in the world, so their item is a note block too (the placement
        // then goes through the normal block place event); the look comes from item_model.
        if (def.type == ItemType.BLOCK && def.block.placeable) material = Material.NOTE_BLOCK;
        // Armor is a vanilla piece of the same slot; the equippable component below replaces its vanilla look and stats.
        if (def.type == ItemType.ARMOR) material = Material.matchMaterial(Presets.armorBaseMaterial(def.armor.slot));
        ItemStack stack = new ItemStack(material, Math.max(1, amount));

        stack.editPersistentDataContainer(pdc -> pdc.set(idKey, PersistentDataType.STRING, def.id));

        if (def.hasTexture) {
            stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(namespace, def.id));
        }
        if (def.displayName != null && !def.displayName.isBlank()) {
            stack.setData(DataComponentTypes.ITEM_NAME, MM.deserialize(def.displayName));
        }
        if (def.lore != null && !def.lore.isEmpty()) {
            // Lore defaults to italic dark purple on the client; use plain gray unless the text sets a style.
            List<Component> lines = def.lore.stream()
                    .map(line -> MM.deserialize(line)
                            .colorIfAbsent(NamedTextColor.GRAY)
                            .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE))
                    .toList();
            stack.setData(DataComponentTypes.LORE, ItemLore.lore(lines));
        }
        stack.setData(DataComponentTypes.MAX_STACK_SIZE, def.maxStackSize);

        switch (def.type) {
            case FOOD -> applyFood(stack, def.food);
            case TOOL -> applyTool(stack, def);
            case ARMOR -> applyArmor(stack, def);
            default -> { /* GENERIC and BLOCK need no extra components yet */ }
        }
        return stack;
    }

    private void applyFood(ItemStack stack, ItemDefinition.FoodProps food) {
        stack.setData(DataComponentTypes.FOOD, FoodProperties.food()
                .nutrition(food.nutrition)
                .saturation(food.saturation)
                .canAlwaysEat(food.canAlwaysEat)
                .build());
        stack.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
                .consumeSeconds(food.eatSeconds)
                .build());
    }

    private void applyArmor(ItemStack stack, ItemDefinition def) {
        ItemDefinition.ArmorProps armor = def.armor;
        stack.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        stack.setData(DataComponentTypes.MAX_DAMAGE, armor.durability);
        stack.setData(DataComponentTypes.DAMAGE, 0);

        EquipmentSlot slot = EquipmentSlot.valueOf(armor.slot.equipmentSlot());
        Equippable.Builder equippable = Equippable.equippable(slot);
        // Without a worn texture the piece keeps the vanilla iron look on the body.
        // A piece of a set shares the set's equipment asset (named after the set), a single piece has its own.
        if (def.hasWornTexture) equippable.assetId(Key.key(namespace, armor.set.isEmpty() ? def.id : armor.set));
        if (armor.equipSound != null && !armor.equipSound.isBlank()) equippable.equipSound(Key.key(armor.equipSound));
        stack.setData(DataComponentTypes.EQUIPPABLE, equippable.build());

        // Vanilla's own modifier ids ("armor.helmet" ...), so the tooltip reads like a vanilla piece.
        EquipmentSlotGroup group = switch (armor.slot) {
            case HELMET -> EquipmentSlotGroup.HEAD;
            case CHESTPLATE -> EquipmentSlotGroup.CHEST;
            case LEGGINGS -> EquipmentSlotGroup.LEGS;
            case BOOTS -> EquipmentSlotGroup.FEET;
        };
        NamespacedKey modifierKey = NamespacedKey.minecraft("armor." + armor.slot.name().toLowerCase());
        ItemAttributeModifiers.Builder attributes = ItemAttributeModifiers.itemAttributes()
                .addModifier(Attribute.ARMOR, new AttributeModifier(modifierKey, armor.defense,
                        AttributeModifier.Operation.ADD_NUMBER, group));
        if (armor.toughness > 0) {
            attributes.addModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(modifierKey, armor.toughness,
                    AttributeModifier.Operation.ADD_NUMBER, group));
        }
        if (armor.knockbackResistance > 0) {
            attributes.addModifier(Attribute.KNOCKBACK_RESISTANCE, new AttributeModifier(modifierKey,
                    armor.knockbackResistance, AttributeModifier.Operation.ADD_NUMBER, group));
        }
        stack.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, attributes.build());
        if (armor.enchantability > 0) {
            stack.setData(DataComponentTypes.ENCHANTABLE, Enchantable.enchantable(armor.enchantability));
        }
    }

    private void applyTool(ItemStack stack, ItemDefinition def) {
        ItemDefinition.ToolProps tool = def.tool;

        // Tools are not stackable and use durability.
        stack.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        stack.setData(DataComponentTypes.MAX_DAMAGE, tool.durability);
        stack.setData(DataComponentTypes.DAMAGE, 0);

        boolean sword = tool.kind == ItemDefinition.ToolKind.SWORD;
        // Vanilla: swords use 2 durability per block and cannot break blocks in creative mode.
        Tool.Builder builder = Tool.tool().defaultMiningSpeed(1.0f).damagePerBlock(sword ? 2 : 1)
                .canDestroyBlocksInCreative(!sword);
        RegistryKeySet<BlockType> mineable = switch (tool.kind) {
            case PICKAXE -> mineableTag(BlockTypeTagKeys.MINEABLE_PICKAXE);
            case AXE -> mineableTag(BlockTypeTagKeys.MINEABLE_AXE);
            case SHOVEL -> mineableTag(BlockTypeTagKeys.MINEABLE_SHOVEL);
            case HOE -> mineableTag(BlockTypeTagKeys.MINEABLE_HOE);
            case SWORD -> null;
        };
        if (mineable != null) {
            // Same order as vanilla: first the blocks this tier is too weak for (no drops), then the mineable blocks.
            RegistryKeySet<BlockType> tooHard = incorrectForTier(tool.harvestTier);
            if (tooHard != null) builder.addRule(Tool.rule(tooHard, null, TriState.FALSE));
            builder.addRule(Tool.rule(mineable, tool.miningSpeed, TriState.TRUE));
        }
        stack.setData(DataComponentTypes.TOOL, builder.build());

        // Use vanilla's own modifier ids: the client then shows the total ("7 Attack Damage") like a vanilla sword
        // instead of a separate "+6" line. The modifier excludes the player's base damage (1) and attack speed (4).
        stack.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes()
                .addModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                        NamespacedKey.minecraft("base_attack_damage"), tool.damage - 1.0,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND))
                .addModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                        NamespacedKey.minecraft("base_attack_speed"), tool.attackSpeed - 4.0,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND))
                .build());
        if (tool.enchantability > 0) {
            stack.setData(DataComponentTypes.ENCHANTABLE, Enchantable.enchantable(tool.enchantability));
        }
    }

    /** Human-readable list of the components that are really set on a stack (for /additems dump). */
    public List<String> describe(ItemStack stack) {
        List<String> out = new java.util.ArrayList<>();
        out.add("base item: " + stack.getType());
        out.add("item_model: " + stack.getData(DataComponentTypes.ITEM_MODEL));
        out.add("max_stack_size: " + stack.getData(DataComponentTypes.MAX_STACK_SIZE));
        out.add("max_damage: " + stack.getData(DataComponentTypes.MAX_DAMAGE)
                + ", damage: " + stack.getData(DataComponentTypes.DAMAGE));
        Enchantable enchantable = stack.getData(DataComponentTypes.ENCHANTABLE);
        if (enchantable != null) out.add("enchantable: " + enchantable.value());

        ItemAttributeModifiers attributes = stack.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (attributes != null) {
            for (var entry : attributes.modifiers()) {
                out.add("attribute " + entry.attribute().key() + ": " + entry.modifier().getAmount() + " ("
                        + entry.modifier().getOperation() + ", id " + entry.modifier().getKey() + ", slot "
                        + entry.getGroup() + ")");
            }
        }
        Tool tool = stack.getData(DataComponentTypes.TOOL);
        if (tool != null) {
            out.add("tool: default speed " + tool.defaultMiningSpeed() + ", damage per block " + tool.damagePerBlock()
                    + ", creative destroy " + tool.canDestroyBlocksInCreative());
            for (Tool.Rule rule : tool.rules()) {
                out.add("  rule: speed " + rule.speed() + ", correct for drops " + rule.correctForDrops()
                        + ", blocks " + rule.blocks().size());
            }
        }
        FoodProperties food = stack.getData(DataComponentTypes.FOOD);
        if (food != null) {
            out.add("food: nutrition " + food.nutrition() + ", saturation " + food.saturation()
                    + ", can always eat " + food.canAlwaysEat());
        }
        Consumable consumable = stack.getData(DataComponentTypes.CONSUMABLE);
        if (consumable != null) out.add("consumable: " + consumable.consumeSeconds() + " s");
        out.add("all components: " + stack.getDataTypes().stream().map(t -> t.key().asString()).sorted().toList());
        return out;
    }

    /** Blocks a tool of this tier cannot harvest (vanilla tag incorrect_for_*_tool), or null for an unknown tier. */
    private static RegistryKeySet<BlockType> incorrectForTier(String tier) {
        if (tier == null) return null;
        return switch (tier) {
            case "WOODEN" -> mineableTag(BlockTypeTagKeys.INCORRECT_FOR_WOODEN_TOOL);
            case "STONE" -> mineableTag(BlockTypeTagKeys.INCORRECT_FOR_STONE_TOOL);
            case "COPPER" -> mineableTag(BlockTypeTagKeys.INCORRECT_FOR_COPPER_TOOL);
            case "IRON" -> mineableTag(BlockTypeTagKeys.INCORRECT_FOR_IRON_TOOL);
            case "GOLDEN" -> mineableTag(BlockTypeTagKeys.INCORRECT_FOR_GOLD_TOOL);
            case "DIAMOND" -> mineableTag(BlockTypeTagKeys.INCORRECT_FOR_DIAMOND_TOOL);
            case "NETHERITE" -> mineableTag(BlockTypeTagKeys.INCORRECT_FOR_NETHERITE_TOOL);
            default -> null;
        };
    }

    private static RegistryKeySet<BlockType> mineableTag(io.papermc.paper.registry.tag.TagKey<BlockType> tag) {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.BLOCK).getTag(tag);
    }
}
