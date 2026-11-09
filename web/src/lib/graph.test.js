import { describe, it, expect } from 'vitest';
import { buildGraph, related, ingredientList } from './graph.js';
import { autoLayout } from './layout.js';

const ns = 'additems';
const item = (id, extra = {}) => ({ id, type: 'GENERIC', displayName: `<red>${id}`, baseMaterial: 'PAPER', hasTexture: false, ...extra });
const grid = (cells) => Array.from({ length: 9 }, (_, i) => cells[i] ?? '');
const recipe = (id, extra) => ({ id, type: 'SHAPED', result: '', amount: 1, grid: grid([]), ...extra });

const sample = () => ({
  ns,
  items: [item('ruby'), item('ruby_pickaxe', { type: 'TOOL' }), item('ruby_ore', { type: 'BLOCK', block: { placeable: true } }), item('lonely')],
  recipes: [
    recipe('pick', { result: 'additems:ruby_pickaxe', grid: grid(['additems:ruby', 'additems:ruby', 'additems:ruby', '', 'minecraft:stick', '', '', 'minecraft:stick']) }),
    recipe('vanilla_only', { result: 'minecraft:bread', grid: grid(['minecraft:wheat']) })
  ],
  loot: [{
    id: 'ore_drops', trigger: 'BLOCK', targets: ['additems:ruby_ore'],
    drops: [{ item: 'additems:ruby', min: 1, max: 3, chance: 25 }]
  }, {
    id: 'zombie', trigger: 'MOB', targets: ['minecraft:zombie'], drops: [{ item: 'additems:ruby', min: 2, max: 2, chance: 100 }]
  }, {
    id: 'vanilla_loot', trigger: 'MOB', targets: ['minecraft:cow'], drops: [{ item: 'minecraft:diamond', min: 1, max: 1, chance: 100 }]
  }]
});

const ids = (g, type) => g.nodes.filter((n) => n.type === type).map((n) => n.id).sort();

describe('buildGraph', () => {
  it('makes a node per custom item, even without connections', () => {
    expect(ids(buildGraph(sample()), 'item')).toEqual(['item:lonely', 'item:ruby', 'item:ruby_ore', 'item:ruby_pickaxe']);
  });

  it('connects ingredients through the recipe to the result and merges duplicates', () => {
    const g = buildGraph(sample());
    const into = g.edges.filter((e) => e.target === 'recipe:pick');
    expect(into.map((e) => `${e.source} ${e.label}`).sort()).toEqual(['item:ruby ×3', 'vanilla:stick ×2']);
    expect(g.edges.some((e) => e.source === 'recipe:pick' && e.target === 'item:ruby_pickaxe')).toBe(true);
  });

  it('skips recipes and loot that do not involve a custom item', () => {
    const g = buildGraph(sample());
    expect(g.nodes.some((n) => n.id === 'recipe:vanilla_only')).toBe(false);
    expect(g.nodes.some((n) => n.id === 'loot:vanilla_loot')).toBe(false);
    expect(g.nodes.some((n) => n.id === 'vanilla:wheat')).toBe(false);
    expect(buildGraph(sample(), { allRecipes: true, allLoot: true }).nodes.some((n) => n.id === 'recipe:vanilla_only')).toBe(true);
  });

  it('links drop rules: source -> rule -> item with chance and amount', () => {
    const g = buildGraph(sample());
    expect(g.edges.find((e) => e.source === 'source:MOB:minecraft:zombie' && e.target === 'loot:zombie')).toBeTruthy();
    expect(g.edges.find((e) => e.source === 'loot:zombie' && e.target === 'item:ruby').label).toBe('100% · 2');
    expect(g.edges.find((e) => e.source === 'loot:ore_drops' && e.target === 'item:ruby').label).toBe('25% · 1–3');
  });

  it('uses a custom block itself as the source of its drops', () => {
    const g = buildGraph(sample());
    const edge = g.edges.find((e) => e.target === 'loot:ore_drops');
    expect(edge.source).toBe('item:ruby_ore');
    expect(edge.label).toBe('broken');
  });

  it('hides vanilla nodes on request and drops nodes left without connections', () => {
    const g = buildGraph(sample(), { showVanilla: false });
    expect(ids(g, 'vanilla')).toEqual([]);
    expect(g.edges.some((e) => e.source === 'vanilla:stick')).toBe(false);
    expect(g.nodes.some((n) => n.id === 'recipe:pick')).toBe(true);
  });

  it('folds many loot targets into one source node', () => {
    const data = sample();
    data.loot = [{ id: 'many', trigger: 'MOB', targets: ['a:1', 'a:2', 'a:3', 'a:4', 'a:5'].map((t) => t.replace('a:', 'minecraft:mob')),
      drops: [{ item: 'additems:ruby', min: 1, max: 1, chance: 10 }] }];
    const g = buildGraph(data);
    const sources = g.nodes.filter((n) => n.type === 'source');
    expect(sources).toHaveLength(1);
    expect(sources[0].data.count).toBe(5);
  });

  it('marks references to missing custom items', () => {
    const data = sample();
    data.recipes = [recipe('broken', { result: 'additems:ruby', grid: grid(['additems:gone']) })];
    const missing = buildGraph(data).nodes.find((n) => n.id === 'item:gone');
    expect(missing.data.missing).toBe(true);
  });

  it('labels smithing and anvil inputs by role', () => {
    const data = sample();
    data.recipes = [
      recipe('smith', { type: 'SMITHING', result: 'additems:ruby_pickaxe', template: 'minecraft:t', base: 'additems:ruby', addition: 'minecraft:ingot' }),
      recipe('anvil', { type: 'ANVIL', result: 'additems:ruby_pickaxe', left: 'additems:ruby', right: 'minecraft:diamond', rightAmount: 2 })
    ];
    const g = buildGraph(data);
    expect(g.edges.find((e) => e.source === 'item:ruby' && e.target === 'recipe:smith').label).toBe('base');
    expect(g.edges.find((e) => e.source === 'vanilla:diamond' && e.target === 'recipe:anvil').label).toBe('right ×2');
  });
});

describe('related', () => {
  it('collects everything upstream and downstream of a node', () => {
    const g = buildGraph(sample());
    const r = related('item:ruby', g.edges);
    expect(r.nodes.has('recipe:pick')).toBe(true);
    expect(r.nodes.has('item:ruby_pickaxe')).toBe(true);
    expect(r.nodes.has('loot:zombie')).toBe(true);
    expect(r.nodes.has('item:lonely')).toBe(false);
  });
});

describe('ingredientList', () => {
  it('skips empty cells', () => {
    expect(ingredientList(recipe('x', { grid: grid(['minecraft:stick']) }))).toEqual([{ ref: 'minecraft:stick' }]);
  });
});

describe('autoLayout', () => {
  it('places ingredients left of results for LR and mirrors for RL', () => {
    const g = buildGraph(sample());
    const lr = autoLayout(g.nodes, g.edges, 'LR');
    expect(lr['item:ruby'].x).toBeLessThan(lr['recipe:pick'].x);
    expect(lr['recipe:pick'].x).toBeLessThan(lr['item:ruby_pickaxe'].x);
    const rl = autoLayout(g.nodes, g.edges, 'RL');
    expect(rl['item:ruby'].x).toBeGreaterThan(rl['recipe:pick'].x);
  });

  it('gives every node a finite position', () => {
    const g = buildGraph(sample());
    for (const p of Object.values(autoLayout(g.nodes, g.edges, 'TB'))) {
      expect(Number.isFinite(p.x) && Number.isFinite(p.y)).toBe(true);
    }
  });
});
