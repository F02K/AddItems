import { vanillaName } from './vanilla.svelte.js';
import { prettyName } from './refs.js';

export const TRIGGERS = [
  { value: 'BLOCK', label: 'Block broken', hint: 'Extra drops when a player breaks a block' },
  { value: 'MOB', label: 'Mob killed', hint: 'Extra drops when a mob dies' },
  { value: 'CHEST', label: 'Chest loot', hint: 'Extra items in generated chests (dungeons, villages, ...)' }
];

export const titleCase = (s) => prettyName(s).replace(/\b\w/g, (c) => c.toUpperCase());

/** Readable name of a loot target such as "minecraft:zombie", "#minecraft:logs" or "*". */
export function targetLabel(trigger, target, items = [], ns = '') {
  if (target === '*') return trigger === 'BLOCK' ? 'Any block' : trigger === 'MOB' ? 'Any mob' : 'Any chest';
  const tag = target.startsWith('#');
  const [space, name = target] = (tag ? target.slice(1) : target).split(':');
  if (!tag && trigger === 'BLOCK' && space === ns) {
    const item = items.find((i) => i.id === name);
    return item ? (item.displayName || item.id).replace(/<[^>]*>/g, '') : name;
  }
  if (tag) return 'Any ' + prettyName(name);
  if (trigger === 'BLOCK') return vanillaName(name) ?? titleCase(name);
  if (trigger === 'CHEST') return titleCase(name.split('/').pop());
  return titleCase(name);
}

/** Vanilla item whose icon represents a target. */
export function targetIcon(trigger, target) {
  if (target === '*' || target.startsWith('#')) return null;
  const name = target.split(':')[1] ?? '';
  if (trigger === 'BLOCK') return name;
  if (trigger === 'MOB') return `${name}_spawn_egg`;
  return 'chest';
}
