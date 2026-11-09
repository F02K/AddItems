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
}
