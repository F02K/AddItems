package dev.fosk.additems.item;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomBlockRegistryTest {

    private static final List<String> POOL = List.of(
            "instrument=harp,note=1,powered=false", "instrument=harp,note=2,powered=false",
            "instrument=harp,note=3,powered=false");

    @TempDir
    Path dir;

    private ItemRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        registry = new ItemRegistry(dir, Logger.getAnonymousLogger());
        registry.load();
        registry.setStatePool(POOL);
    }

    private ItemDefinition block(String id) {
        ItemDefinition def = new ItemDefinition();
        def.id = id;
        def.type = ItemType.BLOCK;
        def.block.placeable = true;
        return def;
    }

    @Test
    void placeableBlocksGetUniqueStatesInOrder() throws Exception {
        ItemDefinition a = block("ruby_ore");
        ItemDefinition b = block("ruby_block");
        assertNull(registry.save(a));
        assertNull(registry.save(b));
        assertEquals(POOL.get(0), registry.get("ruby_ore").block.state);
        assertEquals(POOL.get(1), registry.get("ruby_block").block.state);
        assertEquals("ruby_block", registry.byBlockState(POOL.get(1)).id);
    }

    @Test
    void stateStaysWhenTheDashboardSendsNoneBack() throws Exception {
        registry.save(block("ruby_ore"));
        String assigned = registry.get("ruby_ore").block.state;

        ItemDefinition edited = block("ruby_ore");
        edited.block.state = null; // dashboard did not echo the field
        edited.block.hardness = 3;
        assertNull(registry.save(edited));
        assertEquals(assigned, registry.get("ruby_ore").block.state);
    }

    @Test
    void freedStatesAreNotRenumberedAndAreReusedForNewBlocks() throws Exception {
        registry.save(block("a"));
        registry.save(block("b"));
        registry.save(block("c"));
        registry.delete("a");
        assertEquals(POOL.get(1), registry.get("b").block.state, "other blocks keep their state");
        registry.save(block("d"));
        assertEquals(POOL.get(0), registry.get("d").block.state, "freed state is reused");
    }

    @Test
    void runsOutOfStatesGracefully() throws Exception {
        for (int i = 0; i < POOL.size(); i++) assertNull(registry.save(block("b" + i)));
        assertNotNull(registry.save(block("overflow")));
    }

    @Test
    void duplicateOrUnknownStatesAreRejected() throws Exception {
        registry.save(block("a"));
        ItemDefinition thief = block("b");
        thief.block.state = POOL.get(0);
        assertNotNull(registry.save(thief));

        ItemDefinition unknown = block("c");
        unknown.block.state = "instrument=bass,note=7,powered=false";
        assertNotNull(registry.save(unknown));
    }

    @Test
    void onlyBlockItemsCanBePlaceableAndValuesAreValidated() {
        ItemDefinition food = new ItemDefinition();
        food.id = "x";
        food.type = ItemType.FOOD;
        food.block.placeable = true;
        assertNotNull(food.validate());

        ItemDefinition tooSoft = block("x");
        tooSoft.block.hardness = 0;
        assertNotNull(tooSoft.validate());

        ItemDefinition badTool = block("x");
        badTool.block.preferredTool = "LASER";
        assertNotNull(badTool.validate());

        ItemDefinition badSound = block("x");
        badSound.block.placeSound = "Bad Sound!";
        assertNotNull(badSound.validate());

        assertNull(block("x").validate());
    }

    @Test
    void texturesLiveInSlotsAndSidesModeNeedsAllThree() throws Exception {
        ItemDefinition def = block("log_like");
        def.block.textureMode = "SIDES";
        registry.save(def);
        assertFalse(registry.get("log_like").hasTexture);

        registry.saveTexture("log_like", "top", new byte[]{1});
        registry.saveTexture("log_like", "bottom", new byte[]{2});
        assertFalse(registry.get("log_like").hasTexture, "side still missing");
        registry.saveTexture("log_like", "side", new byte[]{3});
        assertTrue(registry.get("log_like").hasTexture);
        assertEquals(3, registry.readTextures("log_like").size());

        assertTrue(Files.exists(dir.resolve("textures/log_like.top.png")));
        registry.delete("log_like");
        assertFalse(Files.exists(dir.resolve("textures/log_like.top.png")));
    }

    @Test
    void iconFallsBackToAFaceWhenABlockHasNoMainTexture() throws Exception {
        ItemDefinition def = block("log_like");
        def.block.textureMode = "SIDES";
        registry.save(def);
        assertNull(registry.readPreviewTexture("log_like"));

        registry.saveTexture("log_like", "bottom", new byte[]{7});
        assertEquals(7, registry.readPreviewTexture("log_like")[0], "bottom is the last resort");
        registry.saveTexture("log_like", "side", new byte[]{5});
        assertEquals(5, registry.readPreviewTexture("log_like")[0], "side beats bottom");
        registry.saveTexture("log_like", "top", new byte[]{3});
        assertEquals(3, registry.readPreviewTexture("log_like")[0], "top is preferred");
        registry.saveTexture("log_like", new byte[]{1});
        assertEquals(1, registry.readPreviewTexture("log_like")[0], "main wins when it exists");
    }

    @Test
    void unknownSoundsAreRejectedAndTheDefaultNamespaceIsStripped() throws Exception {
        registry.setSoundValidator(s -> s.equals("block.stone.place") || s.equals("block.stone.break"));

        ItemDefinition ok = block("ore");
        ok.block.placeSound = " minecraft:block.stone.place ";
        ok.block.breakSound = "block.stone.break";
        assertNull(registry.save(ok));
        assertEquals("block.stone.place", registry.get("ore").block.placeSound, "trimmed and without minecraft:");

        ItemDefinition typo = block("typo");
        typo.block.placeSound = "block.ston.place";
        String error = registry.save(typo);
        assertNotNull(error);
        assertTrue(error.contains("block.ston.place"), error);
        assertNull(registry.get("typo"), "an item with an unknown sound is not stored");

        assertNull(registry.save(block("silent")), "no sound at all is fine");
    }

    @Test
    void renderingMustBeOneOfTheKnownModes() {
        for (String mode : new String[]{"SOLID", "CUTOUT", "TRANSLUCENT"}) {
            ItemDefinition def = block("x");
            def.block.rendering = mode;
            assertNull(def.validate(), mode);
        }
        ItemDefinition bad = block("x");
        bad.block.rendering = "GLOWING";
        assertNotNull(bad.validate());
        bad.block.rendering = null;
        assertNotNull(bad.validate());
    }

    @Test
    void unknownTextureSlotsAreRejected() throws Exception {
        registry.save(block("a"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> registry.saveTexture("a", "../evil", new byte[]{1}));
    }
}
