# Architecture

```
src/main/java/dev/fosk/additems/
  AddItemsPlugin.java      lifecycle, config, pack sending (implements DashboardServer.Actions)
  AddItemsCommand.java     /additems login | give | list | reload | pack build|send
  item/                    ItemType, ItemDefinition (JSON POJO + validation), ItemRegistry (files), ItemFactory
  pack/                    PackFormat (version adapter), PackFormat26_3, PackFormats, PackBuilder
  web/                     DashboardServer (UI + API), AuthManager, PackHost
web/                       Svelte 5 + Vite dashboard; built to build/web-dist, bundled in the jar under /web
```

## Data (source of truth = files in `plugins/AddItems/`)

- `items/<id>.json` one file per item, `textures/<id>.png`, `pack/pack.zip` (generated).
- Item id: `[a-z0-9_]{1,48}` (also the file name, so it is validated everywhere to prevent path traversal).
- Items are tagged with PDC `additems:id`; the `item_model` component is `<namespace>:<id>` (namespace from
  `pack.namespace`, default `additems`).

## Web

- Two listeners on the JDK `HttpServer` (no extra dependencies):
  - **Dashboard** (default `127.0.0.1:8123`): static UI + `/api/*`. Login only through the one-time link from
    `/additems login` (10 min, single use) which sets an HttpOnly SameSite=Strict session cookie (12 h).
    Writes check the `Origin` header. Uploads: PNG only, square, <= 1024 px, <= 256 KB.
  - **Pack host** (default `0.0.0.0:8124`): serves only `/pack/<sha1>.zip`, publicly, because players download it.
- API: `GET /api/me|meta|items|pack`, `PUT|GET|DELETE /api/items/{id}`, `PUT|GET /api/items/{id}/texture`,
  `POST /api/pack/build`.
- The dashboard (`web/src`: `App.svelte` list/state, `ItemEditor.svelte` form, `Tooltip.svelte` live preview,
  `MaterialPicker.svelte` searchable base-item picker, `Chips.svelte`, `lib/mctext.js` MiniMessage preview) shows
  per-type property cards. `/api/meta` carries vanilla presets (`item/Presets.java`: tool tiers and stats, foods)
  and the material lists. Picking kind + tier fills durability, speed, damage, enchantability and suggests the
  matching vanilla base item. Editing a stat switches the tier to Custom.
- To add an item type extend `ItemType`, `ItemDefinition` (+ validation), `ItemFactory`, the `PackFormat*`
  classes and `ItemEditor.svelte`.
- Tool damage/speed in the JSON are tooltip totals (`damage`, `attackSpeed`); old files with the earlier
  `attackDamage` field load with default values.
- UI testing without a Minecraft server: serve `build/web-dist` with a tiny Node mock of `/api/me|meta|items|pack`
  and open it in a browser. This caught a real bug (`structuredClone` on Svelte state proxies; use `$state.snapshot`).

## Recipes (`recipe/`)

- `RecipeDefinition` (JSON in `plugins/AddItems/recipes/<id>.json`), `RecipeRegistry` (files), `RecipeManager`
  (server integration), `ItemRef` (`additems:ruby`, `minecraft:stick`, `#minecraft:planks`), `RecipeType`.
- Stations: crafting shaped/shapeless, furnace, blast furnace, smoker, campfire, stonecutter, smithing (transform),
  anvil. Shaped recipes are stored as a 3x3 grid and trimmed to the smallest pattern when registered.
- `RecipeManager.apply()` removes all AddItems recipes and registers them again in one batch, then resends recipes
  and unlocks them in the recipe book of online players (also on join). It runs on startup, `/additems reload`, and after
  every item or recipe change made in the dashboard (`DashboardServer.Actions.contentChanged()` waits for the main thread).
- A recipe whose items no longer exist stays on disk and is shown with a warning (`problem`), but is not registered.
- Custom items are built on vanilla materials, so `PrepareItemCraftEvent` blocks vanilla recipes from accepting them and
  keeps our recipes from accepting custom items they do not list. Known gap: furnace/stonecutter/smithing ingredients
  of vanilla recipes are not protected the same way.
- API: `GET /api/recipes`, `PUT|GET|DELETE /api/recipes/{id}`; `/api/meta` adds `recipeTypes`, `itemTags`,
  `customNamespace`. UI: tab "Recipes" (`RecipesView`, `RecipeEditor`, `Slot`, `RefPicker`).

## Item graph (dashboard tab "Graph")

- **What it shows:** every custom item as a node, connected through the recipes and drop rules that involve it. Edges always
  point in the direction of production: ingredient -> recipe -> result, trigger -> drop rule -> item. Node kinds: custom item,
  vanilla item / tag (only when connected), recipe hub (station), drop rule hub, source (mob / vanilla block / chest table;
  more than 4 targets are folded into one "N mobs" node). A custom block is an item node and the source of its own drops.
  Recipes/rules that do not involve a custom item are hidden unless "All recipes" / "All drops" is on.
- **Code:** `web/src/lib/graph.js` (pure model, `buildGraph`, `related`), `lib/layout.js` (dagre layered layout, four
  directions), `graph/GraphView.svelte` (loads recipes, rules and the shared layout), `graph/GraphCanvas.svelte` (Svelte Flow
  canvas, toolbar, selection, connections), node components, `graph/ConnectDialog.svelte`. Unit tests: `web/src/lib/graph.test.js`
  (`npm test` in `web/`, also run by `./gradlew check`).
- **Interaction:** click a node to highlight everything it needs and is used for (others dim), search dims non-matches, the
  "↗" button opens the item / recipe / rule in its editor (App `navigate`). Direction (LR, RL, TB, BT) and "Auto layout" re-run
  the layout.
- **Detail panel ("how to craft it")**: selecting a node opens a panel under the graph (third row of the `.graph` grid; closes with
  "×" or a click on empty canvas). Logic in `web/src/lib/details.js` (`detailsFor`, `craftingCells`, `usedFor`; unit tested),
  components `graph/DetailPanel.svelte`, `graph/RecipeCard.svelte`, `graph/RecipeSlot.svelte`.
  - Item / vanilla item / tag node: "Crafting & smelting" (every recipe that makes it), "Drops from" (rules that drop it, the
    wanted drop highlighted), "When this block is broken" (rules fired by a custom block), "Used in" (chips of the result items).
    A custom item nothing produces shows a hint how to add a recipe or drop rule.
  - Recipe node: that recipe. Loot or source node: its drop rules (all targets and drops with chance and amount).
  - Recipes are drawn like Minecraft's screens: 3x3 crafting grid (shaped keeps the stored positions, shapeless is packed from the
    top left like the recipe book), furnace / blast furnace / smoker / campfire with cook time and XP, stonecutter, smithing table
    (template + base + addition), anvil (left + right with count, level cost). Slots show textures / vanilla icons, a count badge,
    "any <tag>" for tags and the item name as tooltip; clicking a slot or a chip selects that node and zooms to it.
  - Gotcha: the app-wide `header` style leaked into the cards (unreadable title); `.detail > header` and `.rcard header` reset it.
- **Creating connections by dragging** (handles, `isValidConnection` / `onbeforeconnect`): item or vanilla item -> custom item
  opens "New recipe" (station, extra ingredients, amount; smithing and anvil ask for their extra slots) and saves it through
  the recipe API; a source -> custom item opens "New drop rule" (amount, chance); an item dropped on an existing shapeless
  recipe adds an ingredient. "+ Source" puts a mob / block / chest source on the canvas first. Everything created goes through
  the normal validated APIs, so it is active on the server at once.
- **Shared layout:** dragged positions and the direction are stored by `GraphLayoutStore` in `plugins/AddItems/graph-layout.json`
  (`GET|PUT /api/graph/layout`, rebuilt from validated numbers only, max 512 KB, node ids must start with item|vanilla|tag|
  recipe|loot|source). Nodes already on screen keep their position when the graph changes; new nodes are placed next to the
  nodes they connect to.
- **Rules learned while verifying against a real server dataset** (10 items, 17 recipes of every type, 8 loot rules incl. broken
  references, textures, a placeable block with separate faces): every node that can be the *result* of an edge needs a target
  handle (vanilla items too, otherwise Svelte Flow silently does not draw the edge); a relayout must not pin the old positions
  (`skipPin`); the plain item texture URL falls back to the top/side/bottom texture for blocks with separate faces
  (`ItemRegistry#readPreviewTexture`); the header counts only existing items; long names wrap to two lines and carry a tooltip.
  Check list used: nodes and edges of the DOM equal what the raw `/api/*` data implies (ids, labels, counts), no overlaps,
  nothing outside the fitted view, handles on the right sides for all four directions, filters leave no dangling edge, layout
  survives a reload, creating a recipe / drop rule by dragging produces a valid active entry on the server.
- Svelte Flow draws edges only after nodes were measured and painted; in a hidden or unpainted tab they can be missing for a while.
- Not yet: deleting or editing connections on the canvas (use the editors), grouping, export as image.

## Custom blocks (`block/`)

- **Technique: note block states.** A placeable block (`ItemDefinition.block.placeable`, only for type BLOCK) owns one note
  block state `instrument=..,note=..,powered=..`, assigned on first save (or on load for hand-written files), written back to
  the item JSON and never changed afterwards. The pack's `assets/minecraft/blockstates/note_block.json` maps that state to
  `additems:block/<id>`; all other ~1350 variants are listed with the vanilla model (a variant that is missing would render
  as the missing texture). The file is only emitted when at least one custom block has a texture.
- **State pool** (`CustomBlocks.statePool`): only `powered=false` and `note>=1` (vanilla placement gives note 0, so a plain note
  block never looks like a custom block). ~550 states on 26.3. Variant names are read from the server via
  `BlockData#getAsString`, not hard-coded.
- **Item:** the block's item is a `NOTE_BLOCK` (forced in `ItemFactory`), `item_model` points to the block model, so placing
  goes through the normal `BlockPlaceEvent` (protection plugins see it) and `CustomBlockListener` swaps in the custom state.
- **Textures:** slots `main`, `top`, `bottom`, `side` (`textures/<id>.png`, `<id>.top.png`, ...; `.` cannot occur in ids).
  Mode ALL -> `cube_all`, mode SIDES -> `cube_bottom_top` with fallback to `main` for missing faces.
- **Mining speed:** vanilla note block hardness is 0.8; on `BlockDamageEvent` a modifier on the player's
  `BLOCK_BREAK_SPEED` attribute scales it to the custom hardness (removed on abort/break/quit).
- **Breaking:** `CustomBlockListener.onBreak` (HIGHEST, after loot rules at HIGH) suppresses the note block drop and drops the
  custom item if `dropSelf` and the best-tool rule allow it; a loot rule with "replace drops" switches drops off first.
  Explosions drop the custom item with the explosion's yield. Optional place/break sound events.
- **Protection:** right-click tuning and `NotePlayEvent` are cancelled, `BlockPhysicsEvent` of note blocks is cancelled
  (keeps `powered=false`). Vanilla note blocks therefore do not play notes while AddItems is active.
- **Required server setting:** `block-updates.disable-noteblock-updates: true` in `config/paper-global.yml` (restart needed).
  No Bukkit event covers the shape update that changes the instrument when the block above/below changes; the Paper option
  does. Verified on a real 26.3 server: with the option all instrument/note/powered checks pass after placing blocks above,
  below and redstone next to a custom block, without it the instrument changes. `CustomBlocks.checkServerSetup` reads the
  file at startup, logs a warning and the dashboard shows a notice in the block card.
- **No transparent blocks (decision 2026-11-09):** every custom block is an opaque note block for the client, which culls the
  neighbouring faces next to it (you looked through glass/leaves into the void under the block). A resource pack cannot change
  that. A `rendering` mode (cutout / translucent) existed briefly and was removed. If it comes back, the carrier must not be a
  note block: item displays + barrier + interaction entity (like furniture) are the idea; reserving vanilla leaves/glass states
  was rejected because it would block vanilla blocks. Details: docs/roadmap.md.
- **Every custom block stays "solid"** (note block properties, cannot be changed from a plugin): it blocks light and
  skylight, neighbouring vanilla faces are culled against it, mobs can spawn on top, it suffocates.
- **Block sounds**: `place` / `break` sound ids are checked against Paper's sound registry (`SoundCatalog`, ~1990 ids, listed
  without `minecraft:`); `ItemRegistry` trims them, strips `minecraft:` and rejects unknown ones (`unknown sound '...'`); a
  hand-written file with an unknown sound still loads, with a warning. The dashboard (`SoundInput.svelte`,
  `lib/sounds.js`) autocompletes from `meta.sounds`: prefix matches first, then words that are whole name parts (so
  "stone place" finds `block.stone.place` before `block.lodestone.place`), arrow keys + Enter, "Did you mean ...?" for typos
  (edit distance with swaps) and a "Sound set" picker that fills a matching place/break pair.
- **Loot:** `BLOCK` loot rules can target custom blocks (`additems:<id>`).
- **Commands:** `/additems place <id> [x y z] [world]` places a block directly (also from the console).
- Not supported yet: light emission, custom step/hit sounds (still wooden), non-cube shapes (planned via item displays), creative
  pick-block, mining speed per tool kind (speed follows the note block's normal tool behaviour, only scaled by hardness).
- Untested without a player: placing from the item, mining with the speed modifier, breaking drops, explosions.

## Dashboard layout (2026-11-09)

Two tabs only: **Graph** (overview, drag to connect) and **Items**. A saved item has its own tabs **Item | Recipes | Drops**
(`App.svelte`; the item form stays mounted while another tab is open so unsaved edits survive):

- `ItemRecipes.svelte`: the recipes that make this item, edited inline in the normal `RecipeEditor` with the **result locked to
  this item** (`lockResult` / `Slot locked`); "+ New recipe" starts a shaped recipe with this item as result; recipes that use the
  item as an ingredient are listed read-only.
- `ItemDrops.svelte`: rules that drop the item (mobs, blocks, chests) and, for a placeable block, rules fired by breaking it,
  edited inline in `LootEditor`.
- Recipes and rules that involve no custom item are only reachable through the graph: its arrow buttons open them in
  `RuleDialog` over the graph. The old Recipes and Drops tabs were removed.

## Ids (2026-11-09)

- **Recipes and drop rules never get a hand-typed id.** The editors have no id field. New ones are created with
  `POST /api/recipes` / `POST /api/loot`; the body id is only a suggestion (item id, `<item>_drops`, `<item>_break`, graph:
  `<to>_from_<from>`) and the server returns a free one (`Ids.unique`: base, base_2, base_3 ..., max 48 characters) under a lock, so
  two creations can never overwrite each other. `PUT /api/<kind>/<id>` is only for editing an existing entry.
- **Items and armor sets** keep a typed id, but it is filled in from the display name (`idFromName`) until typed by hand, and the
  first save of a new one sends the header `X-Create` (`api.saveItem(def, isNew)`): the server answers 409 instead of overwriting.
  An armor set also refuses an id that an item (other than its own pieces) already uses.
- Armor set pieces are `<set>_<slot>` (or a linked existing item); the set id is limited to 36 characters so that fits.
- Not done: deleting an item does not delete the recipes whose result it was (they show up as not active).

## Armor (`ItemType.ARMOR`)

- `ItemDefinition.armor`: slot (helmet / chestplate / leggings / boots), tier (informational), defense, toughness,
  knockback resistance, durability, enchantability, equip sound. Presets in `Presets.ARMOR_TIERS` (wiki values, 2026-11-09;
  chainmail defense 2/5/4/1 is from memory, the wiki page fetch did not list it).
- `ItemFactory.applyArmor`: base item is `IRON_<slot>`; sets `equippable` (slot, `asset_id` = `additems:<id>` only when a worn
  texture exists, equip sound), `max_damage`, `enchantable`, and `attribute_modifiers` for armor / toughness / knockback
  resistance with vanilla ids `minecraft:armor.<slot>` and the matching equipment slot group.
- Textures: slot `main` = inventory icon (`textures/item/<id>.png`), slot `worn` = body texture. `PackFormat26_3.armorEquipmentFiles`
  writes `assets/<ns>/equipment/<id>.json` (`layers.humanoid` or `humanoid_leggings` for leggings) and
  `textures/entity/equipment/<layer>/<id>.png`. Worn textures may be 2:1 (64x32) or square (`DashboardServer.validatePng`).
  `hasWornTexture` is recomputed by the registry like `hasTexture`.
- **Armor sets (2026-11-09)** (`ArmorSetDefinition`, `ArmorSets`, owned by `ItemRegistry#armorSets`; files in
  `plugins/AddItems/armorsets/<id>.json` plus `<id>.humanoid.png` / `<id>.leggings.png`): a set has 1-4 pieces (key = slot),
  shared tier values (toughness, knockback resistance, enchantability, equip sound) and per-piece defense / durability.
  Saving a set writes one normal item per piece (`<set>_<slot>`, `armor.set` = set id, type ARMOR) and deletes pieces that left
  the set; existing pieces keep lore, stack size and anything the set does not own. Deleting a set deletes its pieces; deleting
  the last piece item deletes the set. The pack gets ONE equipment asset named after the set (`equipment/<set>.json`, layers
  `humanoid` and/or `humanoid_leggings`, both pointing to `<ns>:<set>`); the pieces' `equippable.asset_id` is the set id,
  `hasWornTexture` of a piece = the set has the layer for its slot. A single armor item without a set still uses its own `worn`
  texture and asset. Lock order: set operations lock the `ItemRegistry` instance (same lock as its synchronized methods).
  API: `/api/armorsets`, `/api/armorsets/<id>`, `/api/armorsets/<id>/texture?slot=humanoid|leggings`. In the dashboard the set is edited inside the
  Items tab (`ArmorSetPanel`, `ArmorSetEditor`) and is a **toggle "Armor set"** in the armor section of an item: ticking it on a new
  item starts a set (all four pieces, values of the item); ticking it on a saved single armor item turns it into a set whose piece
  is that very item (`Piece.item` = its id, so recipes and drops keep working, its worn texture becomes the shared layer); unticking
  it on a piece and saving detaches the piece again (`ItemRegistry.save` -> `ArmorSets.pieceDeleted` + `detachWornTexture`, it keeps
  a copy of the shared texture). A piece shows its set and locks the armor values; the set editor links to each piece.
  `Piece.item` empty means the default id `<set>_<slot>`; an explicit item may only adopt a single armor item without a set.
- Not done: set bonuses (effects when several pieces are worn), elytra /
  `glider`, trim overrides, `humanoid_baby` and animal layers, in-game verification (compiled and pack-tested only).

## Drops and loot (`loot/`)

- `LootRule` (JSON in `plugins/AddItems/loot/<id>.json`), `LootRegistry` (files), `LootManager` (listeners + compiled rules).
- A rule = trigger + targets + drops + options:
  - `BLOCK` (targets: block ids, block tags like `#minecraft:logs`, `*`): `BlockBreakEvent`, ignored in creative. Options:
    require the right tool (`Block#isPreferredTool`), skip with Silk Touch, replace vanilla drops (`setDropItems(false)`).
    Fortune raises chance/amount.
  - `MOB` (targets: entity types, `*`): `EntityDeathEvent` drops list. Options: only when a player killed it, ignore spawner
    mobs (`Entity#fromMobSpawner`), replace vanilla drops (cleared once before adding). Looting raises chance/amount.
  - `CHEST` (targets: loot table keys, `*`): `LootGenerateEvent` loot list (no enchantment bonuses).
- Each drop: item (custom or vanilla), min/max, chance %, chance bonus and amount bonus per enchantment level. Rolled by
  `LootRule.roll` (unit tested). Stacks larger than the item's max stack size are split.
- `apply()` compiles rules (resolves tags, entity types, loot tables, items) on startup, reload and after any item/loot/recipe
  change; broken rules are listed with a `problem` and not applied.
- API: `GET /api/loot`, `PUT|GET|DELETE /api/loot/{id}`; `/api/meta` adds `entityTypes`, `lootTables` (chests only) and `blockTags`.
  UI: tab "Drops" (`LootView`, `LootEditor`, `TargetPicker`).
- Verified on a real server via console kills (extra drop added, vanilla drops replaced, player-kill requirement respected).
  Not verified without a player: block breaking and chest generation. Not covered: explosions, pistons, fishing, custom blocks.

## Vanilla icons and names (`vanilla/`)

- `VanillaAssets`: on first start (async) fetches `version_manifest_v2.json` -> version json -> `client.jar` from Mojang
  (HTTPS, host must end in `mojang.com`, SHA-1 verified, ~41 MB), extracts only `textures/item|block`, `models/item|block`,
  `items/*.json`, `lang/en_us.json` (zip-slip safe, size caps) into `plugins/AddItems/cache/vanilla/<version>/` (~12 MB),
  deletes the jar. States: DISABLED / MISSING / DOWNLOADING / READY / FAILED. Config: `vanilla-assets.enabled`.
- `VanillaIcons`: item id -> item model definition (`items/<id>.json`, picks a representative model from
  condition/select/range_dispatch/composite/special) -> merges `textures` along the `parent` chain (26.x allows a texture
  to be an object `{"sprite": ...}`) -> flat item (layers, first frame of animated strips, `tints` of the item model) or an
  isometric 64x64 cube for block models (top/left/right at 100/80/60 % brightness, biome tints for grass and leaves).
  Names come from `en_us.json` (`item.minecraft.*` over `block.minecraft.*`). Results are cached in memory.
- API: `GET /api/vanilla` (state), `/api/vanilla/names`, `/api/vanilla/icon/{id}` (PNG, cached 1 day).
- Dashboard: `lib/vanilla.svelte.js` (state + polling while downloading), `VanillaIcon.svelte` (icon with text fallback);
  used in slots, pickers, item preview and lists. Search in the item picker also matches English names.
- Known limits: special models (chests, beds, banners, shulker boxes, heads, shields) are only approximated by their
  base model's particle texture; non-cube blocks (stairs, fences) are drawn as cubes.

## Dashboard form pitfalls

- **Native number validation can silently block saving.** `<input type="number" min="0.05" step="0.1">` only accepts 0.05, 0.15,
  0.25 ..., so a default of 1.6 (food eating time) or a preset of 0.5 (block hardness) is invalid and the browser refuses to submit
  the form without any visible message ("Save does nothing"). The same affected the drop chance (`min 0.01 step 0.5` rejects 100).
  Rule: use `step="any"`, or a `min` that is a multiple of the step. `web/src/lib/forms.test.js` scans all components for this.
- A reliable end-to-end check is `form.checkValidity()` on every existing item, recipe and drop rule plus freshly created ones.

## Pack pipeline

`PackBuilder` collects `pack.mcmeta` + per-item files from the active `PackFormat` into a sorted map and writes a
zip with fixed timestamps, so identical input gives an identical SHA-1 (clients only re-download on change).
Items without a texture add no assets and keep their vanilla look.

## Pack delivery checklist (learned while testing, 2026-11-08)

- Players only get the pack if `pack.public-url` is set (e.g. `http://127.0.0.1:8124` for a local test, a public
  host/port otherwise). `packProblem()` reports why delivery is skipped; the command, dashboard and log show it.
- The last built `pack/pack.zip` is restored on startup, so joining players get it without rebuilding.
- The pack port (default 8124) must be reachable by clients, not only the Minecraft port.

## Not done yet / known limits

- BLOCK items only render as a cube item; they are **not** placeable custom blocks yet.
- Roles (admin/editor/viewer) are not implemented; every logged-in user can edit.
- Not yet run on a real Paper 26.3 server (only unit tests for pack building and validation exist).
- The dashboard speaks plain HTTP; use a reverse proxy for HTTPS when exposing it.
