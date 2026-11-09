package dev.fosk.additems.vanilla;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Uses synthetic assets only; no Mojang files are needed or stored in the repository. */
class VanillaIconsTest {

    @TempDir
    Path root;

    private VanillaIcons icons;

    @BeforeEach
    void setUp() {
        icons = new VanillaIcons(root);
    }

    private void write(String rel, String content) throws IOException {
        Path file = root.resolve(rel);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private void texture(String rel, Color color, int w, int h) throws IOException {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) image.setRGB(x, y, color.getRGB());
        Path file = root.resolve("textures/" + rel + ".png");
        Files.createDirectories(file.getParent());
        ImageIO.write(image, "png", file.toFile());
    }

    private BufferedImage decode(byte[] png) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    @Test
    void flatItemUsesItsTextureViaParentChain() throws Exception {
        write("items/ruby.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/ruby\"}}");
        write("models/item/ruby.json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/ruby\"}}");
        write("models/item/generated.json", "{\"parent\":\"builtin/generated\"}");
        texture("item/ruby", new Color(200, 20, 30), 16, 16);

        BufferedImage image = decode(icons.icon("ruby"));
        assertEquals(16, image.getWidth());
        assertEquals(new Color(200, 20, 30).getRGB(), image.getRGB(8, 8));
    }

    @Test
    void animatedStripKeepsOnlyTheFirstFrame() throws Exception {
        write("items/clock.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/clock\"}}");
        write("models/item/clock.json", "{\"textures\":{\"layer0\":\"minecraft:item/clock\"}}");
        texture("item/clock", Color.BLUE, 16, 64);

        BufferedImage image = decode(icons.icon("clock"));
        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());
    }

    @Test
    void blockIsDrawnAsShadedIsometricCube() throws Exception {
        write("items/stone.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:block/stone\"}}");
        write("models/block/stone.json", "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"minecraft:block/stone\"}}");
        write("models/block/cube_all.json", "{\"parent\":\"minecraft:block/cube\",\"textures\":{\"particle\":\"#all\",\"down\":\"#all\",\"up\":\"#all\"}}");
        write("models/block/cube.json", "{}");
        texture("block/stone", new Color(100, 100, 100), 16, 16);

        BufferedImage image = decode(icons.icon("stone"));
        assertEquals(64, image.getWidth());
        int top = image.getRGB(32, 8) & 0xFFFFFF;
        int left = image.getRGB(16, 40) & 0xFFFFFF;
        int right = image.getRGB(48, 40) & 0xFFFFFF;
        assertEquals(0x646464, top, "top face is unshaded");
        assertTrue((left & 0xFF) < 100 && (right & 0xFF) < (left & 0xFF), "left is darker than top, right darker than left");
        assertEquals(0, image.getRGB(2, 2) >>> 24, "corners stay transparent");
    }

    @Test
    void columnBlockUsesEndOnTopAndSideOnTheSides() throws Exception {
        write("items/log.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:block/log\"}}");
        write("models/block/log.json", "{\"textures\":{\"end\":\"minecraft:block/log_top\",\"side\":\"minecraft:block/log\"}}");
        texture("block/log_top", new Color(10, 200, 10), 16, 16);
        texture("block/log", new Color(120, 80, 40), 16, 16);

        BufferedImage image = decode(icons.icon("log"));
        assertEquals(0x0AC80A, image.getRGB(32, 8) & 0xFFFFFF);
        int leftGreen = (image.getRGB(16, 40) >> 8) & 0xFF;
        assertTrue(Math.abs(leftGreen - 64) <= 2, "left face is the shaded side texture (80 * 0.8 = 64), was " + leftGreen);
    }

    @Test
    void conditionAndSelectDefinitionsFallBackToARepresentativeModel() throws Exception {
        write("items/bow.json", "{\"model\":{\"type\":\"minecraft:condition\",\"property\":\"minecraft:using_item\","
                + "\"on_true\":{\"type\":\"minecraft:model\",\"model\":\"item/bow_pulling\"},"
                + "\"on_false\":{\"type\":\"minecraft:model\",\"model\":\"item/bow\"}}}");
        write("models/item/bow.json", "{\"textures\":{\"layer0\":\"item/bow\"}}");
        write("models/item/bow_pulling.json", "{\"textures\":{\"layer0\":\"item/bow_pulling_0\"}}");
        texture("item/bow", Color.GREEN, 16, 16);
        texture("item/bow_pulling_0", Color.RED, 16, 16);

        assertEquals(Color.GREEN.getRGB(), decode(icons.icon("bow")).getRGB(4, 4));
    }

    @Test
    void tintedGrassTopIsColoured() throws Exception {
        write("items/grass_block.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:block/grass_block\"}}");
        write("models/block/grass_block.json", "{\"textures\":{\"up\":\"minecraft:block/grass_block_top\",\"side\":\"minecraft:block/grass_block_side\"}}");
        texture("block/grass_block_top", Color.WHITE, 16, 16);
        texture("block/grass_block_side", Color.GRAY, 16, 16);

        int top = decode(icons.icon("grass_block")).getRGB(32, 8) & 0xFFFFFF;
        assertEquals(0x91BD59, top);
    }

    @Test
    void textureEntryMayBeAnObjectWithSprite() throws Exception {
        write("items/glass.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:block/glass\"}}");
        write("models/block/glass.json", "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":{\"force_translucent\":true,\"sprite\":\"minecraft:block/glass\"}}}");
        write("models/block/cube_all.json", "{\"textures\":{\"up\":\"#all\"}}");
        texture("block/glass", new Color(180, 220, 240), 16, 16);

        assertNotNull(icons.icon("glass"));
    }

    @Test
    void itemModelTintColoursGreyscaleLayers() throws Exception {
        write("items/potion.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/potion\","
                + "\"tints\":[{\"type\":\"minecraft:potion\",\"default\":-13083194}]}}");
        write("models/item/potion.json", "{\"textures\":{\"layer0\":\"minecraft:item/potion\"}}");
        texture("item/potion", Color.WHITE, 16, 16);

        // -13083194 is 0xFF385DC6
        assertEquals(0x385DC6, decode(icons.icon("potion")).getRGB(8, 8) & 0xFFFFFF);
    }

    @Test
    void unknownOrUnsafeNamesGiveNothing() {
        assertNull(icons.icon("does_not_exist"));
        assertNull(icons.icon("../../etc/passwd"));
        assertNull(icons.icon("Stone"));
        assertNull(icons.icon(null));
    }

    @Test
    void namesComeFromLangFileAndItemsBeatBlocks() throws Exception {
        write("lang/en_us.json", "{\"block.minecraft.stone\":\"Stone\",\"item.minecraft.iron_ingot\":\"Iron Ingot\","
                + "\"block.minecraft.iron_ingot\":\"Wrong\",\"block.minecraft.banner.base.red\":\"nested\",\"menu.title\":\"x\"}");
        var names = icons.names();
        assertEquals("Stone", names.get("stone"));
        assertEquals("Iron Ingot", names.get("iron_ingot"));
        assertFalse(names.containsKey("banner.base.red"));
        assertFalse(names.containsKey("menu.title"));
    }

    @Test
    void extractOnlyKeepsAllowedFilesAndIgnoresTraversal(@TempDir Path work) throws Exception {
        Path jar = work.resolve("client.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            put(zip, "assets/minecraft/textures/item/ok.png", "x");
            put(zip, "assets/minecraft/lang/en_us.json", "{}");
            put(zip, "assets/minecraft/sounds/boom.ogg", "x");
            put(zip, "assets/minecraft/textures/item/../../../../evil.png", "x");
            put(zip, "assets/minecraft/models/item/evil.exe", "x");
            put(zip, "net/minecraft/Main.class", "x");
        }
        Path out = work.resolve("out");
        VanillaAssets.extract(jar, out);

        assertTrue(Files.exists(out.resolve("textures/item/ok.png")));
        assertTrue(Files.exists(out.resolve("lang/en_us.json")));
        assertFalse(Files.exists(out.resolve("sounds/boom.ogg")));
        assertFalse(Files.exists(work.resolve("evil.png")));
        assertFalse(Files.exists(out.resolve("models/item/evil.exe")));
        assertFalse(Files.exists(out.resolve("net")));
    }

    @Test
    void onlyHttpsMojangHostsAreAllowed() {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> VanillaAssets.requireMojang("https://piston-data.mojang.com/v1/objects/x/client.jar"));
        for (String bad : new String[]{"http://piston-data.mojang.com/x", "https://evil.example/x", "https://mojang.com.evil.example/x", "file:///etc/passwd"}) {
            org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> VanillaAssets.requireMojang(bad), bad);
        }
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
