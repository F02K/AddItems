# Roadmap

MVP scaffold (done): generic / block-item / food / tool items, texture upload, pack build + hosting, give command.

Next:
1. Run on a real Paper 26.3 server, verify item_model rendering, tool and food behaviour.
2. Recipes: DONE for crafting, cooking, stonecutter, smithing, anvil (see architecture.md). Still open: brewing
   (needs a plugin bootstrapper), smithing trim, recipe book categories/groups, in-game verification of crafting protection.
3. Placeable custom blocks: DONE for full cubes via note block states (see architecture.md). Open: light emission, custom
   step sounds, non-cube shapes and furniture (item displays), per-tool mining speed, tripwire/mushroom states for more than
   ~550 blocks, creative pick-block, in-game verification.
4. Armor: DONE (type, presets, equippable + equipment assets, armor sets with 1-4 pieces and shared textures, see architecture.md).
   Open: set bonuses, in-game verification, trims, elytra. Recipes/drops are editable inside the item editor (done).
   Transparent / non-cube blocks: dropped. Only option left would be furniture-style item displays + barrier + interaction entity.
4b. Drops and loot: DONE for block breaking, mob kills and chest loot (see architecture.md). Open: fishing, explosions,
   drops for custom blocks (needs step 3), per-world filters.
4c. Item graph tab: DONE (view, highlight, shared layout, creating recipes and drop rules by dragging). Open: deleting or
   editing connections on the canvas, behaviour/armor/enchantment nodes once those features exist, image export.
5. Roles for team members, audit log, version history / undo.
6. 3D models (Blockbench import), item behaviours (trigger -> action), custom enchantments, sounds.
7. Importer for Oraxen / ItemsAdder configs; pack merging with other server packs.
