package dev.fosk.additems.pack;

import dev.fosk.additems.item.ItemDefinition;
import dev.fosk.additems.item.ItemRegistry;
import dev.fosk.additems.item.ItemType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackBuilderTest {

    @TempDir
    Path dir;

    private ItemRegistry registryWith(ItemType type, boolean texture) throws Exception {
        ItemRegistry registry = new ItemRegistry(dir, Logger.getAnonymousLogger());
        registry.load();
        ItemDefinition def = new ItemDefinition();
        def.id = "ruby";
        def.type = type;
        assertNull(registry.save(def));
        if (texture) registry.saveTexture("ruby", new byte[]{1, 2, 3});
        return registry;
    }

    private Set<String> entries(Path zip) throws Exception {
        Set<String> names = new HashSet<>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) names.add(e.getName());
        }
        return names;
    }

    @Test
    void itemWithTextureProducesModelDefinitionModelAndTexture() throws Exception {
        ItemRegistry registry = registryWith(ItemType.TOOL, true);
        var result = new PackBuilder(registry, new PackFormat26_3(), "additems").build(dir.resolve("out/pack.zip"));
        Set<String> names = entries(result.file());
        assertTrue(names.contains("pack.mcmeta"));
        assertTrue(names.contains("assets/additems/items/ruby.json"));
        assertTrue(names.contains("assets/additems/models/item/ruby.json"));
        assertTrue(names.contains("assets/additems/textures/item/ruby.png"));
    }

    @Test
    void blockUsesBlockTextureFolder() throws Exception {
        ItemRegistry registry = registryWith(ItemType.BLOCK, true);
        var result = new PackBuilder(registry, new PackFormat26_3(), "additems").build(dir.resolve("out/pack.zip"));
        assertTrue(entries(result.file()).contains("assets/additems/textures/block/ruby.png"));
    }

    @Test
    void itemWithoutTextureAddsNoAssets() throws Exception {
        ItemRegistry registry = registryWith(ItemType.GENERIC, false);
        var result = new PackBuilder(registry, new PackFormat26_3(), "additems").build(dir.resolve("out/pack.zip"));
        assertEquals(Set.of("pack.mcmeta"), entries(result.file()));
    }

    @Test
    void sameInputGivesSameHash() throws Exception {
        ItemRegistry registry = registryWith(ItemType.FOOD, true);
        PackBuilder builder = new PackBuilder(registry, new PackFormat26_3(), "additems");
        assertEquals(builder.build(dir.resolve("a/pack.zip")).sha1(), builder.build(dir.resolve("b/pack.zip")).sha1());
    }

    private static final java.util.List<String> VARIANTS = java.util.List.of(
            "instrument=harp,note=0,powered=false", "instrument=harp,note=1,powered=false",
            "instrument=harp,note=2,powered=false");

    private String read(Path zip, String entry) throws Exception {
        try (java.util.zip.ZipFile file = new java.util.zip.ZipFile(zip.toFile())) {
            var e = file.getEntry(entry);
            return e == null ? null : new String(file.getInputStream(e).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private ItemRegistry blockRegistry(String mode, boolean withTextures) throws Exception {
        ItemRegistry registry = new ItemRegistry(dir, Logger.getAnonymousLogger());
        registry.load();
        registry.setStatePool(VARIANTS.subList(1, 3));
        ItemDefinition def = new ItemDefinition();
        def.id = "ruby_ore";
        def.type = ItemType.BLOCK;
        def.block.placeable = true;
        def.block.textureMode = mode;
        assertNull(registry.save(def));
        if (withTextures) {
            registry.saveTexture("ruby_ore", new byte[]{1, 2, 3});
            if (mode.equals("SIDES")) registry.saveTexture("ruby_ore", "top", new byte[]{9});
        }
        return registry;
    }

    @Test
    void placeableBlockGetsModelItemDefinitionAndNoteBlockState() throws Exception {
        ItemRegistry registry = blockRegistry("ALL", true);
        var result = new PackBuilder(registry, new PackFormat26_3(), "additems", VARIANTS).build(dir.resolve("out/pack.zip"));

        Set<String> names = entries(result.file());
        assertTrue(names.contains("assets/additems/models/block/ruby_ore.json"));
        assertTrue(names.contains("assets/additems/textures/block/ruby_ore.png"));
        assertTrue(read(result.file(), "assets/additems/items/ruby_ore.json").contains("additems:block/ruby_ore"));

        String state = read(result.file(), "assets/minecraft/blockstates/note_block.json");
        assertTrue(state.contains("\"instrument=harp,note=1,powered=false\":{\"model\":\"additems:block/ruby_ore\"}"));
        assertTrue(state.contains("\"instrument=harp,note=0,powered=false\":{\"model\":\"minecraft:block/note_block\"}"),
                "untouched states keep the vanilla model");
        assertTrue(state.contains("\"instrument=harp,note=2,powered=false\":{\"model\":\"minecraft:block/note_block\"}"));
    }

    private Path buildBlockPack(String mode, String rendering) throws Exception {
        ItemRegistry registry = new ItemRegistry(dir, Logger.getAnonymousLogger());
        registry.load();
        registry.setStatePool(VARIANTS.subList(1, 3));
        ItemDefinition def = new ItemDefinition();
        def.id = "leaves";
        def.type = ItemType.BLOCK;
        def.block.placeable = true;
        def.block.textureMode = mode;
        def.block.rendering = rendering;
        assertNull(registry.save(def));
        registry.saveTexture("leaves", new byte[]{1, 2, 3});
        if (mode.equals("SIDES")) {
            registry.saveTexture("leaves", "top", new byte[]{9});
            registry.saveTexture("leaves", "bottom", new byte[]{8});
            registry.saveTexture("leaves", "side", new byte[]{7});
        }
        return new PackBuilder(registry, new PackFormat26_3(), "additems", VARIANTS).build(dir.resolve("out/pack.zip")).file();
    }

    @Test
    void cutoutBlockUsesAModelWithoutFaceCullingAndCutoutMipmaps() throws Exception {
        Path zip = buildBlockPack("ALL", "CUTOUT");
        String model = read(zip, "assets/additems/models/block/leaves.json");
        assertTrue(model.contains("\"parent\":\"minecraft:block/block\""), "keeps vanilla display transforms");
        assertTrue(model.contains("\"elements\""));
        assertFalse(model.contains("cullface"), "faces must never be culled, or neighbouring blocks hide each other's faces");
        assertTrue(model.contains("\"texture\":\"#all\""));
        assertTrue(model.contains("\"all\":\"additems:block/leaves\""));
        assertEquals("{\"texture\":{\"mipmap_strategy\":\"dark_cutout\"}}\n",
                read(zip, "assets/additems/textures/block/leaves.png.mcmeta"));
        assertTrue(read(zip, "assets/additems/items/leaves.json").contains("additems:block/leaves"));
    }

    @Test
    void cutoutSidesBlockMapsTopBottomAndSideFaces() throws Exception {
        Path zip = buildBlockPack("SIDES", "CUTOUT");
        String model = read(zip, "assets/additems/models/block/leaves.json");
        assertTrue(model.contains("\"up\":{\"uv\":[0,0,16,16],\"texture\":\"#top\"}"));
        assertTrue(model.contains("\"down\":{\"uv\":[0,0,16,16],\"texture\":\"#bottom\"}"));
        assertTrue(model.contains("\"north\":{\"uv\":[0,0,16,16],\"texture\":\"#side\"}"));
        assertFalse(model.contains("cullface"));
        for (String file : new String[]{"leaves_top", "leaves_bottom", "leaves_side"}) {
            assertNotNull(read(zip, "assets/additems/textures/block/" + file + ".png.mcmeta"), file);
        }
    }

    @Test
    void translucentBlockMarksItsTextureAsForceTranslucent() throws Exception {
        Path zip = buildBlockPack("ALL", "TRANSLUCENT");
        String model = read(zip, "assets/additems/models/block/leaves.json");
        assertTrue(model.contains("\"parent\":\"minecraft:block/cube_all\""), "glass keeps culling between glass blocks");
        assertTrue(model.contains("\"all\":{\"force_translucent\":true,\"sprite\":\"additems:block/leaves\"}"));
        assertTrue(read(zip, "assets/additems/textures/block/leaves.png.mcmeta").contains("\"mean\""));
    }

    @Test
    void solidBlockNeedsNoTextureMetadata() throws Exception {
        Path zip = buildBlockPack("ALL", "SOLID");
        assertNull(read(zip, "assets/additems/textures/block/leaves.png.mcmeta"));
        assertTrue(read(zip, "assets/additems/models/block/leaves.json").contains("cube_all"));
    }

    @Test
    void sidesModeFallsBackToTheMainTextureForMissingFaces() throws Exception {
        ItemRegistry registry = blockRegistry("SIDES", true);
        var result = new PackBuilder(registry, new PackFormat26_3(), "additems", VARIANTS).build(dir.resolve("out/pack.zip"));

        String model = read(result.file(), "assets/additems/models/block/ruby_ore.json");
        assertTrue(model.contains("cube_bottom_top"));
        assertTrue(model.contains("\"top\":\"additems:block/ruby_ore_top\""));
        assertTrue(model.contains("\"side\":\"additems:block/ruby_ore\""), "side falls back to main");
        assertTrue(entries(result.file()).contains("assets/additems/textures/block/ruby_ore_top.png"));
    }

    @Test
    void blockWithoutTextureAddsNeitherModelNorBlockstate() throws Exception {
        ItemRegistry registry = blockRegistry("ALL", false);
        var result = new PackBuilder(registry, new PackFormat26_3(), "additems", VARIANTS).build(dir.resolve("out/pack.zip"));
        assertEquals(Set.of("pack.mcmeta"), entries(result.file()));
    }

    @Test
    void noBlockstateFileWithoutCustomBlocks() throws Exception {
        ItemRegistry registry = registryWith(ItemType.TOOL, true);
        var result = new PackBuilder(registry, new PackFormat26_3(), "additems", VARIANTS).build(dir.resolve("out/pack.zip"));
        assertFalse(entries(result.file()).contains("assets/minecraft/blockstates/note_block.json"));
    }

    @Test
    void packMcmetaUsesMinMaxFormatForPackFormat97_1() {
        String json = new PackFormat26_3().packMcmeta("test");
        assertTrue(json.contains("\"min_format\":[97,1]"));
        assertTrue(json.contains("\"max_format\":[97,1]"));
        assertTrue(!json.contains("\"pack_format\""));
    }
}
