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

/**
 * A valid, unused id ([a-z0-9_], at most `max` characters including a "_2" style suffix) derived from free text.
 * This is only a suggestion for the form: the server has the last word (see POST /api/recipes) and never overwrites.
 */
export function uniqueId(base, existing, max = 48) {
  const clean = (base || 'entry').toLowerCase().replaceAll(/[^a-z0-9_]+/g, '_').replaceAll(/^_+|_+$/g, '') || 'entry';
  const taken = new Set(existing);
  const stem = clean.slice(0, max - 4);
  let id = clean.slice(0, max);
  for (let n = 2; taken.has(id); n++) id = `${stem}_${n}`;
  return id;
}

/** An id suggestion from a MiniMessage display name ("<gold>Ruby Pickaxe" -> "ruby_pickaxe"). */
export const idFromName = (name, existing, max = 48) => uniqueId((name ?? '').replace(/<[^>]*>/g, ''), existing, max);
