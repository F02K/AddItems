import { describe, it, expect } from 'vitest';
import { groupItems, pieceItemId } from './armorset.js';

const piece = (id, slot, set = '', displayName = '') => ({ id, type: 'ARMOR', displayName, armor: { set, slot } });
const plain = (id) => ({ id, type: 'GENERIC', displayName: '' });

describe('groupItems', () => {
  it('keeps single items and single armor as separate rows', () => {
    const rows = groupItems([plain('ruby'), piece('lonely_helmet', 'HELMET')]);
    expect(rows.map((r) => r.kind)).toEqual(['item', 'item']);
  });

  it('groups the pieces of a set into one row at the place of its first piece, in body order', () => {
    const rows = groupItems([
      plain('a_item'),
      piece('ruby_boots', 'BOOTS', 'ruby', '<red>Ruby Boots'),
      plain('b_item'),
      piece('ruby_helmet', 'HELMET', 'ruby', '<red>Ruby Helmet')
    ]);
    expect(rows.map((r) => r.kind)).toEqual(['item', 'set', 'item']);
    const set = rows[1];
    expect(set.id).toBe('ruby');
    expect(set.name).toBe('Ruby');
    expect(set.pieces.map((p) => p.id)).toEqual(['ruby_helmet', 'ruby_boots']);
  });

  it('falls back to the set id when no piece has a name', () => {
    expect(groupItems([piece('x_helmet', 'HELMET', 'x')])[0].name).toBe('x');
  });
});

describe('pieceItemId', () => {
  it('uses the linked item or <set>_<slot>', () => {
    expect(pieceItemId({ id: 'ruby', pieces: { HELMET: { item: '' } } }, 'HELMET')).toBe('ruby_helmet');
    expect(pieceItemId({ id: 'ruby', pieces: { HELMET: { item: 'ruby_hat' } } }, 'HELMET')).toBe('ruby_hat');
  });
});
