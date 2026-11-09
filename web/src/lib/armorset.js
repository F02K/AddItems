// A new armor set with the iron values for all four pieces; the editor lets the user switch pieces off.
export function blankArmorSet(meta) {
  const base = meta.armorTiers.find((t) => t.id === 'IRON');
  const pieces = {};
  for (const slot of ['HELMET', 'CHESTPLATE', 'LEGGINGS', 'BOOTS']) {
    pieces[slot] = { defense: base.slots[slot].defense, durability: base.slots[slot].durability };
  }
  return {
    id: '', displayName: '', tier: 'IRON', toughness: base.toughness, knockbackResistance: base.knockbackResistance,
    enchantability: base.enchantability, equipSound: base.equipSound, pieces, hasHumanoid: false, hasLeggings: false
  };
}

const SLOT_WORD = /\s*(helmet|chestplate|leggings|boots)\s*$/i;

/**
 * The set that results from turning a single armor item into a set. A saved item stays the piece of its slot (it keeps its id,
 * recipes and drops); an unsaved one starts a full set with the item's values.
 */
export function setFromItem(meta, item, linked) {
  const set = blankArmorSet(meta);
  const a = item.armor;
  Object.assign(set, {
    id: (item.id ?? '').slice(0, 36),
    displayName: (item.displayName ?? '').replace(SLOT_WORD, ''),
    tier: a.tier, toughness: a.toughness, knockbackResistance: a.knockbackResistance,
    enchantability: a.enchantability, equipSound: a.equipSound
  });
  if (linked) set.pieces = { [a.slot]: { defense: a.defense, durability: a.durability, item: item.id } };
  return set;
}

/** The item id of a piece: the linked item, or "<set>_<slot>". */
export const pieceItemId = (set, slot) => set.pieces[slot]?.item || `${set.id}_${slot.toLowerCase()}`;

const SLOT_ORDER = ['HELMET', 'CHESTPLATE', 'LEGGINGS', 'BOOTS'];

/**
 * The item list as rows: single items as { kind: 'item', item } and every armor set once, at the place of its first piece, as
 * { kind: 'set', id, name, pieces } with the pieces in body order (helmet to boots).
 */
export function groupItems(items) {
  const rows = [];
  const sets = new Map();
  for (const item of items) {
    const setId = item.type === 'ARMOR' ? item.armor?.set : '';
    if (!setId) {
      rows.push({ kind: 'item', item });
      continue;
    }
    let row = sets.get(setId);
    if (!row) {
      row = { kind: 'set', id: setId, name: setId, pieces: [] };
      sets.set(setId, row);
      rows.push(row);
    }
    row.pieces.push(item);
  }
  for (const row of sets.values()) {
    row.pieces.sort((a, b) => SLOT_ORDER.indexOf(a.armor.slot) - SLOT_ORDER.indexOf(b.armor.slot));
    const named = row.pieces.find((p) => p.displayName);
    // "<red>Ruby Helmet" -> "Ruby": the set's name without the slot word and without MiniMessage tags
    row.name = named ? named.displayName.replace(/<[^>]*>/g, '').replace(SLOT_WORD, '') || row.id : row.id;
  }
  return rows;
}
