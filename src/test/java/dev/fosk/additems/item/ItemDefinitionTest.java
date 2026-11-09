package dev.fosk.additems.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ItemDefinitionTest {

    private static ItemDefinition valid() {
        ItemDefinition def = new ItemDefinition();
        def.id = "ruby_pickaxe";
        return def;
    }

    @Test
    void acceptsValidDefinition() {
        assertNull(valid().validate());
    }

    @Test
    void rejectsUnsafeIds() {
        for (String bad : new String[]{null, "", "Ruby", "../etc", "a b", "x".repeat(49)}) {
            ItemDefinition def = valid();
            def.id = bad;
            assertNotNull(def.validate(), "id should be rejected: " + bad);
        }
    }

    @Test
    void harvestTierMustBeAVanillaTier() {
        ItemDefinition tool = valid();
        tool.type = ItemType.TOOL;
        assertNull(tool.validate());
        tool.tool.harvestTier = "DIAMOND";
        assertNull(tool.validate());
        tool.tool.harvestTier = "RUBY";
        assertNotNull(tool.validate());
        tool.tool.harvestTier = null;
        assertNotNull(tool.validate());
    }

    @Test
    void rejectsOutOfRangeFoodAndToolValues() {
        ItemDefinition food = valid();
        food.type = ItemType.FOOD;
        food.food.nutrition = -1;
        assertNotNull(food.validate());

        ItemDefinition tool = valid();
        tool.type = ItemType.TOOL;
        tool.tool.durability = 0;
        assertNotNull(tool.validate());
    }

    @Test
    void validatesArmorValues() {
        ItemDefinition armor = valid();
        armor.type = ItemType.ARMOR;
        assertNull(armor.validate());
        armor.armor.defense = 31;
        assertNotNull(armor.validate());
        armor.armor.defense = 6;
        armor.armor.knockbackResistance = 1.5;
        assertNotNull(armor.validate());
        armor.armor.knockbackResistance = 0.1;
        armor.armor.equipSound = "Bad Sound!";
        assertNotNull(armor.validate());
        armor.armor.equipSound = "item.armor.equip_iron";
        armor.armor.durability = 0;
        assertNotNull(armor.validate());
    }

    @Test
    void armorPresetsHaveOneValuePerPiece() {
        for (Presets.ArmorTier tier : Presets.ARMOR_TIERS) {
            org.junit.jupiter.api.Assertions.assertEquals(ItemDefinition.ArmorSlot.values().length, tier.defense().length, tier.id());
            org.junit.jupiter.api.Assertions.assertEquals(ItemDefinition.ArmorSlot.values().length, tier.durability().length, tier.id());
        }
    }
}
