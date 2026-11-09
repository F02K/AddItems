// What the graph's detail panel shows for a selected node: how to make an item, where it drops, what it is used for.
import { ingredientList } from './graph.js';

/** The item reference ("additems:ruby", "minecraft:stick", "#minecraft:planks") behind an item-like node id, or null. */
export function nodeRef(nodeId, ns) {
  const at = nodeId.indexOf(':');
  const kind = nodeId.slice(0, at);
  const name = nodeId.slice(at + 1);
  if (kind === 'item') return `${ns}:${name}`;
  if (kind === 'vanilla') return `minecraft:${name}`;
  if (kind === 'tag') return `#minecraft:${name}`;
  return null;
}

/** Inverse of nodeRef: the graph node id for an item reference. */
export function refNodeId(ref, ns) {
  if (ref.startsWith('#')) return `tag:${ref.slice(1).split(':')[1]}`;
  const [space, name] = ref.split(':');
  return space === ns ? `item:${name}` : `vanilla:${name}`;
}

export const recipesMaking = (ref, recipes) => recipes.filter((r) => r.result === ref);
export const recipesUsing = (ref, recipes) => recipes.filter((r) => ingredientList(r).some((i) => i.ref === ref));
export const lootDropping = (ref, loot) => loot.filter((l) => l.drops.some((d) => d.item === ref));
/** Drop rules that fire when this (custom) block is broken. */
export const lootFromBlock = (ref, loot) => loot.filter((l) => l.trigger === 'BLOCK' && l.targets.includes(ref));

/**
 * Details for a node id, or null when there is nothing to show.
 *  - item-like nodes: recipes that make it, rules that drop it, rules fired by breaking it, recipes that use it
 *  - recipe / loot nodes: that single entry
 *  - source nodes: the drop rules they trigger
 */
export function detailsFor(nodeId, { recipes, loot, ns }) {
  const kind = nodeId.slice(0, nodeId.indexOf(':'));

  if (kind === 'recipe') {
    const recipe = recipes.find((r) => `recipe:${r.id}` === nodeId);
    return recipe ? { kind: 'recipe', recipe } : null;
  }
  if (kind === 'loot') {
    const rule = loot.find((l) => `loot:${l.id}` === nodeId);
    return rule ? { kind: 'loot', rules: [rule] } : null;
  }
  if (kind === 'source') {
    const [, trigger, ...rest] = nodeId.split(':');
    const target = rest.join(':');
    const group = rest[0] === 'group' ? rest.slice(1).join(':') : null;
    const rules = loot.filter((l) => l.trigger === trigger && (group ? l.id === group : l.targets.includes(target)));
    return rules.length ? { kind: 'loot', rules } : null;
  }

  const ref = nodeRef(nodeId, ns);
  if (!ref) return null;
  return {
    kind: 'item',
    ref,
    making: recipesMaking(ref, recipes),
    dropping: lootDropping(ref, loot),
    breaking: lootFromBlock(ref, loot),
    using: recipesUsing(ref, recipes)
  };
}

/** Distinct result items of recipes, for the "used in" chips: [{ ref, recipeIds }]. */
export function usedFor(recipes) {
  const byResult = new Map();
  for (const r of recipes) {
    if (!byResult.has(r.result)) byResult.set(r.result, []);
    byResult.get(r.result).push(r.id);
  }
  return [...byResult].map(([ref, recipeIds]) => ({ ref, recipeIds }));
}

/** The 3x3 cells of a crafting recipe. Shapeless recipes are shown packed from the top left, like the recipe book. */
export function craftingCells(recipe) {
  const cells = Array.from({ length: 9 }, (_, i) => recipe.grid?.[i] ?? '');
  if (recipe.type !== 'SHAPELESS') return cells;
  const packed = cells.filter(Boolean);
  return Array.from({ length: 9 }, (_, i) => packed[i] ?? '');
}

/** Station name and the vanilla block that represents it, for the header of a recipe view. */
export const STATIONS = {
  SHAPED: { label: 'Crafting table', icon: 'crafting_table', note: 'Shaped: place the items exactly like this' },
  SHAPELESS: { label: 'Crafting table', icon: 'crafting_table', note: 'Shapeless: any arrangement works' },
  FURNACE: { label: 'Furnace', icon: 'furnace', note: 'Smelting' },
  BLASTING: { label: 'Blast furnace', icon: 'blast_furnace', note: 'Smelting, twice as fast' },
  SMOKING: { label: 'Smoker', icon: 'smoker', note: 'Cooking, twice as fast' },
  CAMPFIRE: { label: 'Campfire', icon: 'campfire', note: 'Slow cooking without fuel' },
  STONECUTTING: { label: 'Stonecutter', icon: 'stonecutter', note: 'Cutting' },
  SMITHING: { label: 'Smithing table', icon: 'smithing_table', note: 'Template, base and addition' },
  ANVIL: { label: 'Anvil', icon: 'anvil', note: 'Combine two items for a level cost' }
};
