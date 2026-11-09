package dev.fosk.additems.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.fosk.additems.item.ItemDefinition;
import dev.fosk.additems.item.ItemType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minecraft 26.3: resource pack format 97.1 (data pack 121.0).
 *
 * <ul>
 *   <li>pack.mcmeta uses min_format / max_format ([major, minor]); pack_format is omitted.</li>
 *   <li>Items are selected via the item_model component -> assets/&lt;ns&gt;/items/&lt;id&gt;.json
 *       (item model definition) -> assets/&lt;ns&gt;/models/...</li>
 *   <li>Custom blocks are note block states: assets/minecraft/blockstates/note_block.json maps
 *       "instrument=..,note=..,powered=.." to a model.</li>
 * </ul>
 */
public final class PackFormat26_3 implements PackFormat {

    static final int MAJOR = 97;
    static final int MINOR = 1;
    private static final String VANILLA_NOTE_BLOCK_MODEL = "minecraft:block/note_block";

    @Override
    public String minecraftVersion() {
        return "26.3";
    }

    @Override
    public String packMcmeta(String description) {
        JsonObject pack = new JsonObject();
        JsonObject desc = new JsonObject();
        desc.addProperty("text", description);
        pack.add("description", desc);
        pack.add("min_format", formatArray());
        pack.add("max_format", formatArray());
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return root + "\n";
    }

    private static JsonArray formatArray() {
        JsonArray array = new JsonArray();
        array.add(MAJOR);
        array.add(MINOR);
        return array;
    }

    @Override
    public Map<String, byte[]> itemFiles(String ns, ItemDefinition item, Map<String, byte[]> textures) {
        if (item.type == ItemType.BLOCK && item.block.placeable) return placeableBlockFiles(ns, item, textures);

        Map<String, byte[]> files = new LinkedHashMap<>();
        if (item.type == ItemType.ARMOR && item.armor.set.isEmpty()) files.putAll(armorEquipmentFiles(ns, item, textures.get("worn")));
        byte[] main = textures.get("main");
        if (!item.hasTexture || main == null) return files;

        boolean block = item.type == ItemType.BLOCK;
        String textureFolder = block ? "block" : "item";
        String textureRef = ns + ":" + textureFolder + "/" + item.id;
        String modelRef = ns + ":item/" + item.id;

        // 1) item model definition: which model renders for this item_model id
        files.put("assets/" + ns + "/items/" + item.id + ".json", itemDefinition(modelRef));

        // 2) the model itself
        JsonObject textureMap = new JsonObject();
        JsonObject model = new JsonObject();
        if (block) {
            model.addProperty("parent", "minecraft:block/cube_all");
            textureMap.addProperty("all", textureRef);
        } else {
            model.addProperty("parent",
                    item.type == ItemType.TOOL ? "minecraft:item/handheld" : "minecraft:item/generated");
            textureMap.addProperty("layer0", textureRef);
        }
        model.add("textures", textureMap);
        files.put("assets/" + ns + "/models/item/" + item.id + ".json", json(model));

        // 3) texture
        files.put("assets/" + ns + "/textures/" + textureFolder + "/" + item.id + ".png", main);
        return files;
    }

    @Override
    public Map<String, byte[]> armorSetFiles(String ns, dev.fosk.additems.item.ArmorSetDefinition set, Map<String, byte[]> textures) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        JsonObject layers = new JsonObject();
        for (var layer : List.of(Map.entry("humanoid", "humanoid"), Map.entry("leggings", "humanoid_leggings"))) {
            byte[] texture = textures.get(layer.getKey());
            if (texture == null) continue;
            JsonObject entry = new JsonObject();
            entry.addProperty("texture", ns + ":" + set.id);
            JsonArray entries = new JsonArray();
            entries.add(entry);
            layers.add(layer.getValue(), entries);
            files.put("assets/" + ns + "/textures/entity/equipment/" + layer.getValue() + "/" + set.id + ".png", texture);
        }
        if (layers.size() == 0) return files;
        JsonObject root = new JsonObject();
        root.add("layers", layers);
        files.put("assets/" + ns + "/equipment/" + set.id + ".json", json(root));
        return files;
    }

    /**
     * The equipment asset of an armor piece: assets/&lt;ns&gt;/equipment/&lt;id&gt;.json names the layer ("humanoid" for
     * helmet, chestplate and boots, "humanoid_leggings" for leggings) and the texture lives in
     * textures/entity/equipment/&lt;layer&gt;/&lt;id&gt;.png. The item's equippable component points to it with asset_id.
     */
    private Map<String, byte[]> armorEquipmentFiles(String ns, ItemDefinition item, byte[] worn) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        if (worn == null) return files;
        String layer = item.armor.slot == ItemDefinition.ArmorSlot.LEGGINGS ? "humanoid_leggings" : "humanoid";
        JsonObject entry = new JsonObject();
        entry.addProperty("texture", ns + ":" + item.id);
        JsonArray entries = new JsonArray();
        entries.add(entry);
        JsonObject layers = new JsonObject();
        layers.add(layer, entries);
        JsonObject root = new JsonObject();
        root.add("layers", layers);
        files.put("assets/" + ns + "/equipment/" + item.id + ".json", json(root));
        files.put("assets/" + ns + "/textures/entity/equipment/" + layer + "/" + item.id + ".png", worn);
        return files;
    }

    /** Model, textures and item definition of a placeable block; the held item shows the same block model. */
    private Map<String, byte[]> placeableBlockFiles(String ns, ItemDefinition item, Map<String, byte[]> textures) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        boolean sides = "SIDES".equals(item.block.textureMode);
        byte[] main = textures.get("main");

        // slot ("all" or top / bottom / side) -> texture id, and the png files behind them
        Map<String, String> refs = new LinkedHashMap<>();
        if (sides) {
            // missing faces fall back to the main texture; without any texture there is nothing to draw
            for (String slot : List.of("top", "bottom", "side")) {
                byte[] data = textures.get(slot) != null ? textures.get(slot) : main;
                if (data == null) return new LinkedHashMap<>();
                String file = textures.get(slot) != null ? item.id + "_" + slot : item.id;
                refs.put(slot, ns + ":block/" + file);
                files.put("assets/" + ns + "/textures/block/" + file + ".png", data);
            }
        } else {
            if (main == null) return files;
            refs.put("all", ns + ":block/" + item.id);
            files.put("assets/" + ns + "/textures/block/" + item.id + ".png", main);
        }

        JsonObject model = new JsonObject();
        model.addProperty("parent", sides ? "minecraft:block/cube_bottom_top" : "minecraft:block/cube_all");
        JsonObject textureMap = new JsonObject();
        refs.forEach(textureMap::addProperty);
        model.add("textures", textureMap);
        files.put("assets/" + ns + "/models/block/" + item.id + ".json", json(model));
        files.put("assets/" + ns + "/items/" + item.id + ".json", itemDefinition(ns + ":block/" + item.id));
        return files;
    }

    @Override
    public Map<String, byte[]> blockstateFiles(String ns, List<ItemDefinition> blocks, List<String> allVariants) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        if (blocks.isEmpty() || allVariants.isEmpty()) return files;

        Map<String, String> custom = new LinkedHashMap<>();
        for (ItemDefinition block : blocks) custom.put(block.block.state, ns + ":block/" + block.id);

        JsonObject variants = new JsonObject();
        for (String key : allVariants) {
            JsonObject entry = new JsonObject();
            entry.addProperty("model", custom.getOrDefault(key, VANILLA_NOTE_BLOCK_MODEL));
            variants.add(key, entry);
        }
        JsonObject root = new JsonObject();
        root.add("variants", variants);
        files.put("assets/minecraft/blockstates/note_block.json", json(root));
        return files;
    }

    private static byte[] itemDefinition(String modelRef) {
        JsonObject modelNode = new JsonObject();
        modelNode.addProperty("type", "minecraft:model");
        modelNode.addProperty("model", modelRef);
        JsonObject definition = new JsonObject();
        definition.add("model", modelNode);
        return json(definition);
    }

    private static byte[] json(JsonObject object) {
        return (object + "\n").getBytes(StandardCharsets.UTF_8);
    }
}
