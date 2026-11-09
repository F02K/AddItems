package dev.fosk.additems.item;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresetsTest {

    @Test
    void everyKindHasStatsForEveryTier() {
        JsonObject stats = Presets.toJson().getAsJsonObject("toolStats");
        for (ItemDefinition.ToolKind kind : ItemDefinition.ToolKind.values()) {
            assertEquals(Presets.TIERS.size(), stats.getAsJsonObject(kind.name()).size(), kind.name());
        }
    }

    @Test
    void vanillaIronPickaxeValues() {
        JsonObject iron = Presets.toJson().getAsJsonObject("toolStats")
                .getAsJsonObject("PICKAXE").getAsJsonObject("IRON");
        assertEquals(4.0, iron.get("damage").getAsDouble());
        assertEquals(1.2, iron.get("speed").getAsDouble());
        assertEquals("IRON_PICKAXE", iron.get("material").getAsString());
    }

    @Test
    void presetValuesPassValidation() {
        for (Presets.Tier tier : Presets.TIERS) {
            ItemDefinition def = new ItemDefinition();
            def.id = "t";
            def.type = ItemType.TOOL;
            def.tool.durability = tier.durability();
            def.tool.miningSpeed = tier.miningSpeed();
            def.tool.enchantability = tier.enchantability();
            assertEquals(null, def.validate(), tier.id());
        }
        for (Presets.Food food : Presets.FOODS) {
            ItemDefinition def = new ItemDefinition();
            def.id = "f";
            def.type = ItemType.FOOD;
            def.food.nutrition = food.nutrition();
            def.food.saturation = food.saturation();
            def.food.eatSeconds = food.eatSeconds();
            assertEquals(null, def.validate(), food.id());
        }
        assertTrue(Presets.FOODS.size() > 5);
    }
}
