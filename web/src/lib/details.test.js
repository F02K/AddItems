import { describe, it, expect } from 'vitest';
import { nodeRef, refNodeId, detailsFor, craftingCells, usedFor, recipesMaking, recipesUsing } from './details.js';

const ns = 'additems';
const grid = (cells) => Array.from({ length: 9 }, (_, i) => cells[i] ?? '');
const recipe = (id, extra) => ({ id, type: 'SHAPED', result: '', amount: 1, grid: grid([]), ...extra });
const R = 'additems:ruby';

const data = () => ({
  ns,
  recipes: [
    recipe('pick', { result: 'additems:ruby_pickaxe', grid: grid([R, R, R, '', 'minecraft:stick', '', '', 'minecraft:stick']) }),
    recipe('sword', { result: 'additems:ruby_sword', type: 'SHAPELESS', grid: grid(['', R, '', '', 'minecraft:stick']) }),
    recipe('smelt', { result: R, type: 'FURNACE', input: 'additems:raw_ruby' }),
    recipe('anvil', { result: 'additems:ruby_pickaxe', type: 'ANVIL', left: 'minecraft:iron_pickaxe', right: R, rightAmount: 2 })
  ],
  loot: [
    { id: 'zombie', trigger: 'MOB', targets: ['minecraft:zombie', 'minecraft:skeleton'], drops: [{ item: 'additems:raw_ruby' }] },
    { id: 'ore', trigger: 'BLOCK', targets: ['additems:ruby_ore'], drops: [{ item: 'additems:raw_ruby' }] },
    { id: 'many', trigger: 'MOB', targets: ['a', 'b', 'c', 'd', 'e'].map((x) => `minecraft:${x}`), drops: [{ item: R }] }
  ]
});

describe('node references', () => {
  it('maps item-like nodes to references and back', () => {
    expect(nodeRef('item:ruby', ns)).toBe(R);
    expect(nodeRef('vanilla:stick', ns)).toBe('minecraft:stick');
    expect(nodeRef('tag:planks', ns)).toBe('#minecraft:planks');
    expect(nodeRef('recipe:pick', ns)).toBeNull();
    expect(refNodeId(R, ns)).toBe('item:ruby');
    expect(refNodeId('minecraft:stick', ns)).toBe('vanilla:stick');
    expect(refNodeId('#minecraft:planks', ns)).toBe('tag:planks');
  });
});

describe('detailsFor', () => {
  it('lists how an item is made, where it drops and what it is used in', () => {
    const d = detailsFor('item:ruby_pickaxe', data());
    expect(d.kind).toBe('item');
    expect(d.making.map((r) => r.id).sort()).toEqual(['anvil', 'pick']);
    expect(detailsFor('item:ruby', data()).using.map((r) => r.id).sort()).toEqual(['anvil', 'pick', 'sword']);
    expect(detailsFor('item:raw_ruby', data()).dropping.map((l) => l.id).sort()).toEqual(['ore', 'zombie']);
  });

  it('finds the rules that fire when a custom block is broken', () => {
    expect(detailsFor('item:ruby_ore', data()).breaking.map((l) => l.id)).toEqual(['ore']);
  });

  it('works for vanilla items and tags used as ingredients', () => {
    expect(detailsFor('vanilla:stick', data()).using.map((r) => r.id).sort()).toEqual(['pick', 'sword']);
    expect(detailsFor('vanilla:stick', data()).making).toEqual([]);
  });

  it('returns the single entry for recipe and loot nodes', () => {
    expect(detailsFor('recipe:smelt', data()).recipe.type).toBe('FURNACE');
    expect(detailsFor('loot:ore', data()).rules.map((l) => l.id)).toEqual(['ore']);
    expect(detailsFor('recipe:missing', data())).toBeNull();
  });

  it('resolves source nodes, including folded groups, to their rules', () => {
    expect(detailsFor('source:MOB:minecraft:skeleton', data()).rules.map((l) => l.id)).toEqual(['zombie']);
    expect(detailsFor('source:MOB:group:many', data()).rules.map((l) => l.id)).toEqual(['many']);
    expect(detailsFor('source:MOB:minecraft:cow', data())).toBeNull();
  });
});

describe('helpers', () => {
  it('packs shapeless ingredients from the top left but keeps shaped positions', () => {
    const d = data();
    expect(craftingCells(d.recipes[1]).slice(0, 3)).toEqual([R, 'minecraft:stick', '']);
    expect(craftingCells(d.recipes[0])[4]).toBe('minecraft:stick');
    expect(craftingCells(d.recipes[0])).toHaveLength(9);
  });

  it('groups recipes by result for the used-in chips', () => {
    const d = data();
    const used = usedFor(recipesUsing(R, d.recipes));
    expect(used.map((u) => u.ref).sort()).toEqual(['additems:ruby_pickaxe', 'additems:ruby_sword']);
    expect(used.find((u) => u.ref === 'additems:ruby_pickaxe').recipeIds.sort()).toEqual(['anvil', 'pick']);
    expect(recipesMaking(R, d.recipes).map((r) => r.id)).toEqual(['smelt']);
  });
});
