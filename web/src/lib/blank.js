// Default objects and id helpers shared by places that create recipes or loot rules without the full editors.

export const blankRecipe = () => ({
  id: '', type: 'SHAPELESS', result: '', amount: 1, grid: Array(9).fill(''),
  input: '', experience: 0.35, cookTime: 200,
  template: '', base: '', addition: '', left: '', right: '', rightAmount: 1, levelCost: 1
});

export const blankLootRule = () => ({
  id: '', trigger: 'MOB', targets: [], drops: [],
  replaceVanilla: false, requireCorrectTool: true, skipWithSilkTouch: true, requirePlayerKill: true, ignoreSpawnerMobs: false
});

export const blankDrop = () => ({ item: '', min: 1, max: 1, chance: 100, chanceBonusPerLevel: 0, amountBonusPerLevel: 0 });

/** A valid, unused id ([a-z0-9_], at most 48 characters) derived from free text. */
export function uniqueId(base, existing) {
  const clean = (base || 'entry').toLowerCase().replaceAll(/[^a-z0-9_]+/g, '_').replaceAll(/^_+|_+$/g, '') || 'entry';
  const taken = new Set(existing);
  let id = clean.slice(0, 44);
  for (let n = 2; taken.has(id); n++) id = `${clean.slice(0, 44)}_${n}`;
  return id;
}
