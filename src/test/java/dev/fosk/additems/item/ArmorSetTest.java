package dev.fosk.additems.item;

import dev.fosk.additems.pack.PackBuilder;
import dev.fosk.additems.pack.PackFormat26_3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmorSetTest {

    @TempDir
    Path dir;

    ItemRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        registry = new ItemRegistry(dir, Logger.getAnonymousLogger());
        registry.load();
    }

    private static ArmorSetDefinition set(String id, ItemDefinition.ArmorSlot... slots) {
        ArmorSetDefinition set = new ArmorSetDefinition();
        set.id = id;
        set.displayName = "<red>Ruby";
        for (ItemDefinition.ArmorSlot slot : slots) {
            ArmorSetDefinition.Piece piece = new ArmorSetDefinition.Piece();
            piece.defense = 3;
            piece.durability = 200;
            set.pieces.put(slot, piece);
        }
        return set;
    }

    @Test
    void savingASetCreatesOneItemPerPiece() throws Exception {
        assertNull(registry.armorSets().save(set("ruby", ItemDefinition.ArmorSlot.HELMET, ItemDefinition.ArmorSlot.LEGGINGS)));

        ItemDefinition helmet = registry.get("ruby_helmet");
        assertNotNull(helmet);
        assertEquals(ItemType.ARMOR, helmet.type);
        assertEquals("ruby", helmet.armor.set);
        assertEquals("<red>Ruby Helmet", helmet.displayName);
        assertEquals(3, helmet.armor.defense);
        assertEquals("IRON_HELMET", helmet.baseMaterial);
        assertNotNull(registry.get("ruby_leggings"));
        assertNull(registry.get("ruby_chestplate"), "pieces the set does not have are not created");
    }

    @Test
    void removingAPieceFromTheSetDeletesItsItem() throws Exception {
        registry.armorSets().save(set("ruby", ItemDefinition.ArmorSlot.HELMET, ItemDefinition.ArmorSlot.BOOTS));
        assertNull(registry.armorSets().save(set("ruby", ItemDefinition.ArmorSlot.HELMET)));
        assertNotNull(registry.get("ruby_helmet"));
        assertNull(registry.get("ruby_boots"));
    }

    @Test
    void editingAPieceKeepsItsLoreWhenTheSetIsSavedAgain() throws Exception {
        registry.armorSets().save(set("ruby", ItemDefinition.ArmorSlot.HELMET));
        ItemDefinition helmet = registry.get("ruby_helmet");
        helmet.lore.add("<gray>Shiny");
        assertNull(registry.save(helmet));

        ArmorSetDefinition changed = set("ruby", ItemDefinition.ArmorSlot.HELMET);
        changed.pieces.get(ItemDefinition.ArmorSlot.HELMET).defense = 5;
        assertNull(registry.armorSets().save(changed));
        assertEquals(5, registry.get("ruby_helmet").armor.defense);
        assertEquals(java.util.List.of("<gray>Shiny"), registry.get("ruby_helmet").lore);
    }

    @Test
    void aSetCannotTakeOverAnItemThatIsNotItsPiece() throws Exception {
        ItemDefinition other = new ItemDefinition();
        other.id = "ruby_helmet";
        assertNull(registry.save(other));
        assertNotNull(registry.armorSets().save(set("ruby", ItemDefinition.ArmorSlot.HELMET)));
        assertNotNull(registry.armorSets().save(set("ruby_helmet", ItemDefinition.ArmorSlot.BOOTS)), "set id clashes with an item");
    }

    @Test
    void deletingASetDeletesItsPieces() throws Exception {
        registry.armorSets().save(set("ruby", ItemDefinition.ArmorSlot.HELMET, ItemDefinition.ArmorSlot.BOOTS));
        assertTrue(registry.armorSets().delete("ruby"));
        assertNull(registry.get("ruby_helmet"));
        assertNull(registry.get("ruby_boots"));
        assertNull(registry.armorSets().get("ruby"));
    }

    @Test
    void deletingTheLastPieceRemovesTheSet() throws Exception {
        registry.armorSets().save(set("ruby", ItemDefinition.ArmorSlot.HELMET, ItemDefinition.ArmorSlot.BOOTS));
        registry.delete("ruby_helmet");
        assertEquals(java.util.Set.of(ItemDefinition.ArmorSlot.BOOTS), registry.armorSets().get("ruby").pieces.keySet());
        registry.delete("ruby_boots");
        assertNull(registry.armorSets().get("ruby"));
    }

    @Test
    void setsAndTheirPiecesSurviveAReload() throws Exception {
        registry.armorSets().save(set("ruby", ItemDefinition.ArmorSlot.CHESTPLATE));
        registry.armorSets().saveTexture("ruby", "humanoid", new byte[]{1, 2, 3});

        ItemRegistry again = new ItemRegistry(dir, Logger.getAnonymousLogger());
        again.load();
        assertEquals(java.util.Set.of(ItemDefinition.ArmorSlot.CHESTPLATE), again.armorSets().get("ruby").pieces.keySet());
        assertTrue(again.armorSets().get("ruby").hasHumanoid);
        assertTrue(again.get("ruby_chestplate").hasWornTexture, "the piece sees the shared worn texture");
    }

    @Test
    void wornTextureIsPickedPerLayerOfThePiece() throws Exception {
        registry.armorSets().save(set("ruby", ItemDefinition.ArmorSlot.HELMET, ItemDefinition.ArmorSlot.LEGGINGS));
        registry.armorSets().saveTexture("ruby", "humanoid", new byte[]{1});
        assertTrue(registry.get("ruby_helmet").hasWornTexture);
        assertFalse(registry.get("ruby_leggings").hasWornTexture, "leggings need the leggings layer");
        registry.armorSets().saveTexture("ruby", "leggings", new byte[]{2});
        assertTrue(registry.get("ruby_leggings").hasWornTexture);
    }

    @Test
    void packHasOneSharedEquipmentAssetWithBothLayers() throws Exception {
        registry.armorSets().save(set("ruby", ItemDefinition.ArmorSlot.HELMET, ItemDefinition.ArmorSlot.LEGGINGS));
        registry.armorSets().saveTexture("ruby", "humanoid", new byte[]{1});
        registry.armorSets().saveTexture("ruby", "leggings", new byte[]{2});
        registry.saveTexture("ruby_helmet", new byte[]{9});
        registry.saveTexture("ruby_helmet", "worn", new byte[]{7}); // a stray per-piece texture must not create a second asset

        Path zip = new PackBuilder(registry, new PackFormat26_3(), "additems").build(dir.resolve("out/pack.zip")).file();
        try (ZipFile file = new ZipFile(zip.toFile())) {
            String json = new String(file.getInputStream(file.getEntry("assets/additems/equipment/ruby.json")).readAllBytes());
            assertTrue(json.contains("\"humanoid\":[{\"texture\":\"additems:ruby\"}]"));
            assertTrue(json.contains("\"humanoid_leggings\":[{\"texture\":\"additems:ruby\"}]"));
            assertNotNull(file.getEntry("assets/additems/textures/entity/equipment/humanoid/ruby.png"));
            assertNotNull(file.getEntry("assets/additems/textures/entity/equipment/humanoid_leggings/ruby.png"));
            assertNull(file.getEntry("assets/additems/equipment/ruby_helmet.json"));
            assertNotNull(file.getEntry("assets/additems/textures/item/ruby_helmet.png"), "the piece icon is still its own");
        }
    }

    @Test
    void validationRejectsEmptyAndOversizedSets() {
        assertNotNull(set("ruby").validate(), "no pieces");
        assertNotNull(set("x".repeat(37), ItemDefinition.ArmorSlot.HELMET).validate());
        ArmorSetDefinition bad = set("ruby", ItemDefinition.ArmorSlot.HELMET);
        bad.pieces.get(ItemDefinition.ArmorSlot.HELMET).defense = 99;
        assertNotNull(bad.validate());
    }

    private ItemDefinition singleArmor(String id, ItemDefinition.ArmorSlot slot) throws Exception {
        ItemDefinition def = new ItemDefinition();
        def.id = id;
        def.type = ItemType.ARMOR;
        def.armor.slot = slot;
        def.lore.add("<gray>Mine");
        assertNull(registry.save(def));
        return def;
    }

    @Test
    void aSingleArmorItemCanBecomeAPieceOfASetAndKeepsItsId() throws Exception {
        singleArmor("ruby_hat", ItemDefinition.ArmorSlot.HELMET);
        registry.saveTexture("ruby_hat", "worn", new byte[]{5, 5});

        ArmorSetDefinition set = set("ruby", ItemDefinition.ArmorSlot.HELMET);
        set.pieces.get(ItemDefinition.ArmorSlot.HELMET).item = "ruby_hat";
        assertNull(registry.armorSets().save(set));

        assertNull(registry.get("ruby_helmet"), "no second item is created");
        ItemDefinition hat = registry.get("ruby_hat");
        assertEquals("ruby", hat.armor.set);
        assertEquals(java.util.List.of("<gray>Mine"), hat.lore);
        assertTrue(registry.armorSets().get("ruby").hasHumanoid, "its worn texture became the shared layer");
        assertTrue(hat.hasWornTexture);
    }

    @Test
    void anItemWithoutAnExplicitLinkIsNeverTakenOver() throws Exception {
        singleArmor("ruby_helmet", ItemDefinition.ArmorSlot.HELMET);
        assertNotNull(registry.armorSets().save(set("ruby", ItemDefinition.ArmorSlot.HELMET)));
    }

    @Test
    void thePieceCanKeepTheSetIdAsItsItemId() throws Exception {
        singleArmor("ruby", ItemDefinition.ArmorSlot.CHESTPLATE);
        ArmorSetDefinition set = set("ruby", ItemDefinition.ArmorSlot.CHESTPLATE);
        set.pieces.get(ItemDefinition.ArmorSlot.CHESTPLATE).item = "ruby";
        assertNull(registry.armorSets().save(set));
        assertEquals("ruby", registry.get("ruby").armor.set);
        assertNull(registry.save(registry.get("ruby")), "saving the piece again is fine");
    }

    @Test
    void aPieceCanLeaveItsSetAndKeepsTheLook() throws Exception {
        registry.armorSets().save(set("ruby", ItemDefinition.ArmorSlot.HELMET, ItemDefinition.ArmorSlot.BOOTS));
        registry.armorSets().saveTexture("ruby", "humanoid", new byte[]{1, 2});

        ItemDefinition helmet = registry.get("ruby_helmet");
        ItemDefinition copy = new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(helmet), ItemDefinition.class);
        copy.armor.set = "";
        assertNull(registry.save(copy));

        assertEquals("", registry.get("ruby_helmet").armor.set);
        assertTrue(registry.get("ruby_helmet").hasWornTexture, "got its own copy of the shared texture");
        assertEquals(java.util.Set.of(ItemDefinition.ArmorSlot.BOOTS), registry.armorSets().get("ruby").pieces.keySet());
    }
}
