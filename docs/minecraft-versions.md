# Minecraft version knowledge

Researched 2026-11-08. Re-verify before relying on anything marked *unverified*; Mojang changes pack
formats often. Primary sources: minecraft.wiki (Pack_format, Pack.mcmeta, Items_model_definition,
Equipment, Data_component_format, Java_Edition_26.3), docs.papermc.io, Paper downloads API
(`https://fill.papermc.io/v3/projects/paper`).

## Versioning scheme

Since 2026 Minecraft Java uses year-based versions: `26.1`, `26.2`, `26.3`, ... (not `1.21.x`).
Patch releases look like `26.1.2`.

## Target: 26.3

- Released 2026-09-15 ("Wilderness Bound"). Paper has builds for 26.3 (and 26.2, 26.1.x).
- **Java 25 is required** (Minecraft 26.1+ and Paper 26.1+). Gradle 9.1+ supports Java 25.
- **Resource pack format 97.1**, data pack format 121.0.
- Paper API coordinate: `io.papermc.paper:paper-api:26.3.build.+` (keep the `.build.` segment; `26.3.+`
  could resolve to a different, breaking version). Repo: `https://repo.papermc.io/repository/maven-public/`.
- `plugin.yml` `api-version: '26.3'`.

## Pack format table (minecraft.wiki Pack_format, 2026-11-08)

| Minecraft        | Resource pack | Data pack |
|------------------|---------------|-----------|
| 26.3             | 97.1          | 121.0     |
| 26.2             | 88.0          | 107.1     |
| 26.1 - 26.1.2    | 84.0          | 101.1     |
| 1.21.11          | 75.0          | 94.1      |
| 1.21.9 - 1.21.10 | 69.0          | 88.0      |

Other sources disagreed on some numbers; the wiki table above was used. Older rows are *unverified*.

## pack.mcmeta (resource pack)

Since 25w31a formats are `major.minor`. Since 1.21.9 `min_format` and `max_format` are required and
`supported_formats` is gone. `pack_format` is only needed when supporting formats below 65.
Values may be an int or `[major, minor]`.

```json
{ "pack": { "description": { "text": "..." }, "min_format": [97, 1], "max_format": [97, 1] } }
```

## How custom items work (26.x)

1. The item gets the `item_model` data component with an id like `additems:ruby`.
2. The resource pack maps that id with an **item model definition**:
   `assets/<ns>/items/<id>.json` -> `{"model": {"type": "minecraft:model", "model": "<ns>:item/<id>"}}`.
   Other model types: `composite`, `condition`, `select`, `range_dispatch`, `special`, `empty`,
   `bundle/selected_item`. Tint types: constant, dye, firework, grass, potion, team, custom_model_data
   (`map_color` was removed in 26.3 snapshot 7).
3. The model: `assets/<ns>/models/item/<id>.json` with `parent` `minecraft:item/generated` (flat items),
   `minecraft:item/handheld` (tools) or `minecraft:block/cube_all` (block items); textures in
   `assets/<ns>/textures/item/` or `.../block/`.
4. `custom_model_data` also exists (floats, flags, strings, colors) and can drive `condition` / `select` /
   `range_dispatch` models - useful later for variants of one item.

## Armor / equipment (implemented 2026-11-09, not yet seen in a client)

`equippable` component with `asset_id` -> `assets/<ns>/equipment/<id>.json` with `layers` (`humanoid`,
`humanoid_leggings`, `humanoid_baby` (26.1+), `wings`, `wolf_body`, `horse_body`, ...); texture `ns:path` ->
`assets/<ns>/textures/entity/equipment/<layer>/<path>.png`. 26.3 replaced the old trim palette setting with
`trim_overrides`. The `glider` component works together with `equippable`.

## Data components used by the plugin

`item_model`, `item_name`, `lore`, `max_stack_size`, `max_damage`, `damage`, `food`, `consumable`, `tool`,
`attribute_modifiers`, `equippable`, `enchantable`. Relevant later: `custom_data`, `custom_model_data`,
`block_state`, `glider`. In 26.4 snapshots `consumable.sound` and `equippable.equip_sound` change shape
(*upcoming, unverified for release*).

## Paper API notes (compiled against the 26.3 API)

- `ItemStack#setData(DataComponentTypes.X, value)`; builders: `FoodProperties.food()`,
  `Consumable.consumable()`, `Tool.tool()`, `Tool.rule(RegistryKeySet<BlockType>, Float, TriState)`,
  `ItemAttributeModifiers.itemAttributes()`, `ItemLore.lore(...)`.
- Block tags for tool rules:
  `RegistryAccess.registryAccess().getRegistry(RegistryKey.BLOCK).getTag(BlockTypeTagKeys.MINEABLE_PICKAXE)`.
- `Player#setResourcePack(UUID, String url, byte[] sha1, Component prompt, boolean required)`; reuse one UUID so
  a re-sent pack replaces the old one.
- Recipes (verified against 26.3, 2026-11-08):
  - `RecipeChoice.ExactChoice` is **deprecated for removal since 26.2**. Use
    `RecipeChoice.predicateChoice(Predicate<ItemStack>, exampleStack)` to match custom items by PDC id; vanilla items
    and tags use `RecipeChoice.MaterialChoice` (also `RecipeChoice.itemType(...)` exists).
  - `Bukkit.addRecipe(Recipe, boolean)` / `removeRecipe(key, boolean)` are deprecated ("Minecraft always resends
    recipes"). Register in batch with `Bukkit.addRecipes(Iterable)` and `Bukkit.removeRecipes(Iterable<NamespacedKey>)`.
  - Recipe classes: `ShapedRecipe`, `ShapelessRecipe`, `FurnaceRecipe`/`BlastingRecipe`/`SmokingRecipe`/`CampfireRecipe`
    (key, result, choice, xp, cookTime), `StonecuttingRecipe`, `SmithingTransformRecipe(key, result, template, base, addition)`.
  - Item tags by key: `Bukkit.getTag(Tag.REGISTRY_ITEMS, NamespacedKey.minecraft("planks"), Material.class)`.
  - There is **no anvil recipe type**: handled via `PrepareAnvilEvent` (`getView().setRepairCost`, `setRepairItemCountCost`).
  - Brewing stand recipes need Paper's `PotionMix` registration from a plugin bootstrapper (`paper-plugin.yml`),
    not implemented yet.
- Plugin commands are declared in `plugin.yml` (`paper-plugin.yml` has no `commands` section).

## Tool components in practice (verified on a real 26.3 server, 2026-11-08)

- Attack damage / speed: use `ATTRIBUTE_MODIFIERS` with the **vanilla modifier ids** `minecraft:base_attack_damage` and
  `minecraft:base_attack_speed` (amount = tooltip total - 1 and - 4). With custom ids the client lists an extra "+6 Attack
  Damage" line instead of the vanilla-style total ("7 Attack Damage").
- Mining: `TOOL` rules are evaluated in order. Vanilla order: first `incorrect_for_<tier>_tool` (correct_for_drops = false,
  no speed), then `mineable/<kind>` (speed, correct_for_drops = true). Without the first rule a wooden tool would
  drop diamond ore. Tags: `BlockTypeTagKeys.INCORRECT_FOR_{WOODEN,STONE,COPPER,IRON,GOLD,DIAMOND,NETHERITE}_TOOL`.
  Swords: damage per block 2 and `canDestroyBlocksInCreative(false)`.
- A fresh `ItemStack(material)` already carries the vanilla defaults (`weapon`, `repairable`, `tool`, `attack_animation`,
  `interact_animation`, ...); our `setData` calls override only what we set.
- `/additems dump <id>` prints the components really set on the created stack (use it to debug "arrives wrong" reports).

## Vanilla stat presets (used by the dashboard, `item/Presets.java`)

Source: minecraft.wiki Tool / Pickaxe / Sword / Food pages, 2026-11-08. Damage and attack speed are the
**tooltip totals**; the factory stores modifiers as `damage - 1` and `speed - 4` (player base values).
Tiers (durability / mining speed / enchantability): wooden 59/2/15, stone 131/4/5, copper 190/5/13,
iron 250/6/14, golden 32/12/22, diamond 1561/8/10, netherite 2031/9/15. Copper tools exist since 1.21.9.
Sword damage 4,5,5,6,4,7,8 (speed 1.6); pickaxe 2,3,3,4,2,5,6 (1.2); axe 7,9,9,9,7,9,10 (speed 0.8,0.8,0.8,0.9,1,1,1);
shovel 2.5,3.5,3.5,4.5,2.5,5.5,6.5 (1.0); hoe 1 (speed 1,2,2,3,1,4,4) in tier order above.
Pickaxe and sword values were confirmed on their own wiki pages; axe, shovel and hoe come from the Tool page table.
Food presets: apple 4/2.4, bread 5/6, steak 8/12.8, golden carrot 6/14.4, cookie 2/0.4, ... (see Presets.FOODS).
The base item of a tool decides right-click behaviour (axe strips, hoe tills, shovel paths).

## Block rendering in 26.x (read from the real 26.3 client assets, 2026-11-08)

- `models/block/leaves.json`: `parent block/block`, one full cube with `tintindex 0` and `cullface` on all six faces;
  `oak_leaves.png.mcmeta` = `{"texture": {"mipmap_strategy": "dark_cutout"}}`; `glass.png.mcmeta` = `{"texture":
  {"mipmap_strategy": "mean"}}`. Other texture metadata keys seen: `animation`, `alpha_cutoff_bias`.
- `models/block/glass.json`: `parent block/cube_all`, `textures.all` = `{"force_translucent": true, "sprite": "minecraft:block/glass"}`.
  A texture entry in a model may be a plain id or this object form (26.1 snapshot 7+). From the 26.1 changelog (quoted in a
  third-party issue, not Mojang docs): any block model can use cutout or translucent textures, the render pass comes from the
  contents of the sprite, `force_translucent` forces the translucent pass.
- Light and occlusion are properties of the block, not of the model: a note block always blocks light and culls neighbours.
  So custom blocks cannot be see-through (floor visible through glass-like blocks is impossible with a note block carrier);
  transparent blocks were dropped on 2026-11-09.
- Paper: `Registry.SOUNDS` lists every sound event (`Registry.SOUNDS.getKey(sound)`), 1991 on 26.3;
  `world.playSound(location, "block.stone.place", ...)` takes the id without `minecraft:`.

## Mojang client assets (checked 2026-11-08 for 26.3)

- Manifest: `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json` -> per-version json -> `downloads.client`
  `{url, sha1, size}` (26.3: 41,483,720 bytes). The asset index (`assetIndex`, ~485 MB total) holds sounds and other
  languages and is **not** needed; item/block textures, models, item definitions and `lang/en_us.json` are inside client.jar.
- Item model definitions (`assets/minecraft/items/<id>.json`) carry `tints` (`potion`, `dye`, `constant`, `grass`, ...) with
  ARGB integer defaults. Model `textures` values can be strings or `{"sprite": "...", "force_translucent": true}` (e.g. glass).
- Do not redistribute these files; they are fetched at runtime on the operator's server.

## Adding support for a new Minecraft version

1. Check the wiki pages above for changes to pack.mcmeta, item model definitions, models, equipment.
2. Add `PackFormatXX_Y implements PackFormat` in `dev.fosk.additems.pack` and register it in `PackFormats`.
3. Bump `minecraftVersion` in `build.gradle.kts` (drives the Paper API dependency and `api-version`).
4. Update this file (table + notes) and adjust the tests in `PackBuilderTest`.
