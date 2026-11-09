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

    /**
     * Model, textures and item definition of a placeable block; the held item shows the same block model.
     *
     * <p>Rendering (verified against the 26.3 client assets, see docs/minecraft-versions.md): since 26.1 the pass a block is
     * drawn in comes from the transparency of its texture, so a texture with transparent pixels is cut out automatically.
     * The vanilla cube models cull faces next to opaque blocks. A custom block is an opaque note block, so for see-through
     * blocks (CUTOUT) the model must not use cullface, otherwise the faces between neighbouring blocks vanish and you look
     * through the holes into nothing. Glass-like blocks (TRANSLUCENT) mark their texture with force_translucent.
     */
    private Map<String, byte[]> placeableBlockFiles(String ns, ItemDefinition item, Map<String, byte[]> textures) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        boolean sides = "SIDES".equals(item.block.textureMode);
        String rendering = item.block.rendering == null ? "SOLID" : item.block.rendering;
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

        // texture metadata: how mipmaps are built for cut-out / glass textures (same values vanilla leaves and glass use)
        if (!rendering.equals("SOLID")) {
            String strategy = rendering.equals("CUTOUT") ? "dark_cutout" : "mean";
            for (String png : new ArrayList<>(files.keySet())) files.put(png + ".mcmeta", textureMeta(strategy));
        }

        JsonObject model = new JsonObject();
        JsonObject textureMap = new JsonObject();
        if (rendering.equals("CUTOUT")) {
            model.addProperty("parent", "minecraft:block/block"); // keeps the vanilla display transforms for the held item
            refs.forEach(textureMap::addProperty);
            textureMap.addProperty("particle", sides ? "#side" : "#all");
            model.add("textures", textureMap);
            model.add("elements", cubeWithoutCulling(sides));
        } else {
            model.addProperty("parent", sides ? "minecraft:block/cube_bottom_top" : "minecraft:block/cube_all");
            for (var entry : refs.entrySet()) {
                if (rendering.equals("TRANSLUCENT")) {
                    JsonObject sprite = new JsonObject();
                    sprite.addProperty("force_translucent", true);
                    sprite.addProperty("sprite", entry.getValue());
                    textureMap.add(entry.getKey(), sprite);
                } else {
                    textureMap.addProperty(entry.getKey(), entry.getValue());
                }
            }
            model.add("textures", textureMap);
        }
        files.put("assets/" + ns + "/models/block/" + item.id + ".json", json(model));
        files.put("assets/" + ns + "/items/" + item.id + ".json", itemDefinition(ns + ":block/" + item.id));
        return files;
    }

    /** A full cube whose faces are never culled (no "cullface" key), referencing #all or #top / #bottom / #side. */
    private static JsonArray cubeWithoutCulling(boolean sides) {
        JsonObject faces = new JsonObject();
        for (String direction : List.of("down", "up", "north", "south", "west", "east")) {
            JsonObject face = new JsonObject();
            JsonArray uv = new JsonArray();
            for (int v : new int[]{0, 0, 16, 16}) uv.add(v);
            face.add("uv", uv);
            String slot = !sides ? "#all" : direction.equals("up") ? "#top" : direction.equals("down") ? "#bottom" : "#side";
            face.addProperty("texture", slot);
            faces.add(direction, face);
        }
        JsonObject element = new JsonObject();
        JsonArray from = new JsonArray();
        JsonArray to = new JsonArray();
        for (int i = 0; i < 3; i++) {
            from.add(0);
            to.add(16);
        }
        element.add("from", from);
        element.add("to", to);
        element.add("faces", faces);
        JsonArray elements = new JsonArray();
        elements.add(element);
        return elements;
    }

    private static byte[] textureMeta(String mipmapStrategy) {
        JsonObject texture = new JsonObject();
        texture.addProperty("mipmap_strategy", mipmapStrategy);
        JsonObject root = new JsonObject();
        root.add("texture", texture);
        return json(root);
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
