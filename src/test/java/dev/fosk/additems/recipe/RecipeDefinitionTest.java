package dev.fosk.additems.recipe;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RecipeDefinitionTest {

    private static final String NS = "additems";

    private static RecipeDefinition shaped(String... cells) {
        RecipeDefinition def = new RecipeDefinition();
        def.id = "ruby_pickaxe";
        def.type = RecipeType.SHAPED;
        def.result = "additems:ruby_pickaxe";
        def.grid = List.of(cells);
        return def;
    }

    @Test
    void shapeTrimsEmptyRowsAndColumns() {
        // . R .      ->  R
        // . S .          S
        // . . .
        RecipeDefinition def = shaped("", "additems:ruby", "", "", "minecraft:stick", "", "", "", "");
        RecipeDefinition.Shape shape = def.shape();
        assertArrayEquals(new String[]{"A", "B"}, shape.pattern());
        assertEquals("additems:ruby", shape.ingredients().get('A'));
        assertEquals("minecraft:stick", shape.ingredients().get('B'));
    }

    @Test
    void shapeReusesLetterForSameIngredient() {
        RecipeDefinition def = shaped("minecraft:stick", "", "minecraft:stick", "", "", "", "", "", "");
        assertArrayEquals(new String[]{"A A"}, def.shape().pattern());
        assertEquals(1, def.shape().ingredients().size());
    }

    @Test
    void emptyGridHasNoShapeAndFailsValidation() {
        RecipeDefinition def = shaped("", "", "", "", "", "", "", "", "");
        assertNull(def.shape());
        assertNotNull(def.validate(NS));
    }

    @Test
    void validatesReferencesAndAmounts() {
        RecipeDefinition ok = shaped("minecraft:stick", "", "", "", "", "", "", "", "");
        assertNull(ok.validate(NS));

        RecipeDefinition badRef = shaped("not a ref", "", "", "", "", "", "", "", "");
        assertNotNull(badRef.validate(NS));

        RecipeDefinition tagResult = shaped("minecraft:stick", "", "", "", "", "", "", "", "");
        tagResult.result = "#minecraft:planks";
        assertNotNull(tagResult.validate(NS));

        RecipeDefinition tooMany = shaped("minecraft:stick", "", "", "", "", "", "", "", "");
        tooMany.amount = 65;
        assertNotNull(tooMany.validate(NS));
    }

    @Test
    void cookingAnvilAndSmithingRequireTheirFields() {
        RecipeDefinition furnace = new RecipeDefinition();
        furnace.id = "x";
        furnace.type = RecipeType.FURNACE;
        furnace.result = "minecraft:iron_ingot";
        assertNotNull(furnace.validate(NS)); // no input
        furnace.input = "additems:raw_ruby";
        assertNull(furnace.validate(NS));

        RecipeDefinition anvil = new RecipeDefinition();
        anvil.id = "a";
        anvil.type = RecipeType.ANVIL;
        anvil.result = "additems:ruby";
        anvil.left = "minecraft:iron_ingot";
        assertNotNull(anvil.validate(NS)); // no right item
        anvil.right = "minecraft:diamond";
        anvil.levelCost = 5;
        assertNull(anvil.validate(NS));
        anvil.levelCost = 40;
        assertNotNull(anvil.validate(NS));

        RecipeDefinition smithing = new RecipeDefinition();
        smithing.id = "s";
        smithing.type = RecipeType.SMITHING;
        smithing.result = "additems:ruby";
        smithing.template = "minecraft:netherite_upgrade_smithing_template";
        smithing.base = "minecraft:diamond_pickaxe";
        assertNotNull(smithing.validate(NS)); // no addition
        smithing.addition = "minecraft:netherite_ingot";
        assertNull(smithing.validate(NS));
    }

    @Test
    void onlyCraftingAndStonecutterMayYieldMoreThanOne() {
        RecipeDefinition furnace = new RecipeDefinition();
        furnace.id = "x";
        furnace.type = RecipeType.FURNACE;
        furnace.result = "minecraft:iron_ingot";
        furnace.input = "minecraft:iron_ore";
        furnace.amount = 2;
        assertNotNull(furnace.validate(NS));
    }

    @Test
    void itemRefParsing() {
        assertEquals(ItemRef.Kind.CUSTOM, ItemRef.parse("additems:ruby", NS).kind());
        assertEquals(ItemRef.Kind.VANILLA, ItemRef.parse("minecraft:stick", NS).kind());
        assertEquals(ItemRef.Kind.TAG, ItemRef.parse("#minecraft:planks", NS).kind());
        assertEquals("iron_ingot", ItemRef.parse("MINECRAFT:Iron_Ingot", NS).name());
        assertNull(ItemRef.parse("../../etc", NS));
        assertNull(ItemRef.parse("stick", NS));
        assertNull(ItemRef.parse(null, NS));
    }
}
