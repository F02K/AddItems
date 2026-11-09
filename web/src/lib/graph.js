// Pure graph model: turns items, recipes and loot rules into nodes and edges (no positions, no UI).
//
// Node ids:  item:<id>  vanilla:<name>  tag:<name>  recipe:<id>  loot:<id>  source:<trigger>:<target>
// Edges always point in the direction of production: ingredient -> recipe -> result, source -> loot -> item.
import { parseRef } from './refs.js';

const MAX_SOURCE_NODES = 4; // more targets than this are folded into one "N blocks" node
const strip = (s) => (s ?? '').replace(/<[^>]*>/g, '');

/** Short station name and a detail line for recipe nodes. */
export const STATION_LABEL = {
  SHAPED: ['Crafting', 'shaped'], SHAPELESS: ['Crafting', 'shapeless'], FURNACE: ['Furnace', 'smelting'],
  BLASTING: ['Blast furnace', 'smelting'], SMOKING: ['Smoker', 'cooking'], CAMPFIRE: ['Campfire', 'cooking'],
  STONECUTTING: ['Stonecutter', 'cutting'], SMITHING: ['Smithing table', 'upgrade'], ANVIL: ['Anvil', 'combine']
};

/**
 * @param {object} data  { items, recipes, loot, ns }
 * @param {object} opts  { showVanilla = true, allRecipes = false, allLoot = false }
 */
export function buildGraph({ items, recipes, loot, ns }, { showVanilla = true, allRecipes = false, allLoot = false } = {}) {
  const nodes = new Map();
  const edges = new Map();
  const itemIds = new Set(items.map((i) => i.id));

  const addNode = (id, type, data) => {
    if (!nodes.has(id)) nodes.set(id, { id, type, data });
    return nodes.get(id);
  };

  const addEdge = (source, target, label = '', kind = 'flow') => {
    const key = `${source}>${target}>${label}`;
    if (edges.has(key)) return edges.get(key);
    const edge = { id: `e${edges.size}:${key}`, source, target, label, kind };
    edges.set(key, edge);
    return edge;
  };

  for (const item of items) {
    addNode(`item:${item.id}`, 'item', {
      itemId: item.id, name: strip(item.displayName) || item.id, itemType: item.type,
      hasTexture: !!item.hasTexture, placeable: !!item.block?.placeable, baseMaterial: item.baseMaterial
    });
  }

  /** Node id for an item reference, or null when it is hidden (vanilla items while "show vanilla" is off). */
  const refNode = (ref) => {
    const p = parseRef(ref, ns);
    if (!p) return null;
    if (p.kind === 'CUSTOM') {
      const id = `item:${p.name}`;
      if (!itemIds.has(p.name)) addNode(id, 'item', { itemId: p.name, name: p.name, missing: true, itemType: 'GENERIC' });
      return id;
    }
    if (!showVanilla) return null;
    return p.kind === 'TAG'
      ? addNode(`tag:${p.name}`, 'tag', { name: p.name }).id
      : addNode(`vanilla:${p.name}`, 'vanilla', { name: p.name }).id;
  };

  const touchesCustom = (refs) => refs.some((r) => parseRef(r, ns)?.kind === 'CUSTOM');

  // ---- recipes: ingredients -> station -> result
  for (const r of recipes) {
    const inputs = ingredientList(r);
    if (!allRecipes && !touchesCustom([r.result, ...inputs.map((i) => i.ref)])) continue;

    const hub = `recipe:${r.id}`;
    addNode(hub, 'recipe', { recipeId: r.id, recipeType: r.type, label: (STATION_LABEL[r.type] ?? [r.type, ''])[0], detail: (STATION_LABEL[r.type] ?? [r.type, ''])[1], problem: r.problem ?? null });

    const grouped = new Map();
    for (const input of inputs) {
      const key = `${input.ref}|${input.role ?? ''}`;
      grouped.set(key, { ...input, count: (grouped.get(key)?.count ?? 0) + 1 });
    }
    for (const input of grouped.values()) {
      const from = refNode(input.ref);
      if (!from) continue;
      const parts = [input.role, input.count > 1 ? `×${input.count}` : ''].filter(Boolean);
      addEdge(from, hub, parts.join(' '), 'ingredient');
    }
    const result = refNode(r.result);
    if (result) addEdge(hub, result, r.amount > 1 ? `×${r.amount}` : '', 'result');
  }

  // ---- loot: source -> rule -> items
  for (const rule of loot) {
    const customBlocks = rule.trigger === 'BLOCK'
      ? rule.targets.filter((t) => parseRef(t, ns)?.kind === 'CUSTOM') : [];
    const dropsCustom = rule.drops.some((d) => parseRef(d.item, ns)?.kind === 'CUSTOM');
    if (!allLoot && !dropsCustom && customBlocks.length === 0) continue;

    const hub = `loot:${rule.id}`;
    addNode(hub, 'loot', { ruleId: rule.id, trigger: rule.trigger, problem: rule.problem ?? null });

    // custom blocks are item nodes themselves: block -> (broken) -> drops
    for (const target of customBlocks) {
      const from = refNode(target);
      if (from) addEdge(from, hub, 'broken', 'source');
    }
    const others = rule.targets.filter((t) => !customBlocks.includes(t));
    if (others.length > MAX_SOURCE_NODES) {
      const id = `source:${rule.trigger}:group:${rule.id}`;
      addNode(id, 'source', { trigger: rule.trigger, targets: others, count: others.length });
      addEdge(id, hub, '', 'source');
    } else {
      for (const target of others) {
        const id = `source:${rule.trigger}:${target}`;
        addNode(id, 'source', { trigger: rule.trigger, targets: [target], count: 1 });
        addEdge(id, hub, '', 'source');
      }
    }

    for (const drop of rule.drops) {
      const to = refNode(drop.item);
      if (!to) continue;
      const amount = drop.min === drop.max ? `${drop.min}` : `${drop.min}–${drop.max}`;
      addEdge(hub, to, `${trim(drop.chance)}% · ${amount}`, 'drop');
    }
  }

  // vanilla items, tags and sources that ended up without any connection are noise
  const used = new Set();
  for (const e of edges.values()) { used.add(e.source); used.add(e.target); }
  for (const [id, node] of [...nodes]) {
    if (node.type !== 'item' && !used.has(id)) nodes.delete(id);
  }

  return { nodes: [...nodes.values()], edges: [...edges.values()] };
}

/** Ingredients of a recipe as [{ ref, role? }]; empty refs are skipped. */
export function ingredientList(r) {
  const some = (list) => list.filter((x) => x.ref);
  switch (r.type) {
    case 'SHAPED':
    case 'SHAPELESS':
      return some((r.grid ?? []).map((ref) => ({ ref })));
    case 'SMITHING':
      return some([{ ref: r.template, role: 'template' }, { ref: r.base, role: 'base' }, { ref: r.addition, role: 'addition' }]);
    case 'ANVIL':
      return some([{ ref: r.left, role: 'left' }, { ref: r.right, role: r.rightAmount > 1 ? `right ×${r.rightAmount}` : 'right' }]);
    default:
      return some([{ ref: r.input }]);
  }
}

const trim = (n) => String(+Number(n).toFixed(2));

/**
 * Everything upstream (what is needed) and downstream (what it is used for) of a node.
 * Returns { nodes: Set<id>, edges: Set<edgeId> } including the node itself.
 */
export function related(nodeId, edges) {
  const nodes = new Set([nodeId]);
  const picked = new Set();
  const walk = (start, forward) => {
    const stack = [start];
    const seen = new Set([start]);
    while (stack.length) {
      const current = stack.pop();
      for (const e of edges) {
        const from = forward ? e.source : e.target;
        const to = forward ? e.target : e.source;
        if (from !== current) continue;
        picked.add(e.id);
        nodes.add(to);
        if (!seen.has(to)) { seen.add(to); stack.push(to); }
      }
    }
  };
  walk(nodeId, true);
  walk(nodeId, false);
  return { nodes, edges: picked };
}
