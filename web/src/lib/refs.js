import { vanillaName } from './vanilla.svelte.js';

// Helpers for item references used in recipes: "additems:ruby", "minecraft:iron_ingot", "#minecraft:planks".
export const prettyName = (name) => name.replaceAll('_', ' ').replaceAll('/', ' / ');

export function parseRef(ref, ns) {
  if (!ref) return null;
  const tag = ref.startsWith('#');
  const [space, name] = (tag ? ref.slice(1) : ref).split(':');
  if (!name) return null;
  return { kind: tag ? 'TAG' : space === ns ? 'CUSTOM' : 'VANILLA', space, name };
}

/** Human readable label: custom display name, English vanilla name when known, otherwise the id. */
export function refLabel(ref, items, ns) {
  const p = parseRef(ref, ns);
  if (!p) return '';
  if (p.kind === 'CUSTOM') {
    const item = items.find((i) => i.id === p.name);
    return item ? (item.displayName || item.id).replace(/<[^>]*>/g, '') : `${p.name} (missing)`;
  }
  if (p.kind === 'TAG') return 'any ' + prettyName(p.name);
  return vanillaName(p.name) ?? prettyName(p.name);
}
