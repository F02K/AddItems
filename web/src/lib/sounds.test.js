import { describe, it, expect } from 'vitest';
import { filterSounds, isKnownSound, soundFamilies, familyOf, closestSound } from './sounds.js';

const sounds = [
  'block.stone.break', 'block.stone.place', 'block.stone.step', 'block.wood.break', 'block.wood.place',
  'block.amethyst_block.chime', 'block.glass.break', 'block.glass.place', 'entity.zombie.hurt', 'ui.button.click',
  'block.grass.place', // no break sound
  'block.lodestone.place', 'block.pointed_dripstone.place', 'block.dripstone_block.place'
];

describe('filterSounds', () => {
  it('puts ids that start with the query first', () => {
    const r = filterSounds(sounds, 'block.stone');
    expect(r.slice(0, 3)).toEqual(['block.stone.break', 'block.stone.place', 'block.stone.step']);
  });

  it('finds ids containing every typed word, in any order', () => {
    expect(filterSounds(sounds, 'stone break')).toEqual(['block.stone.break']);
    expect(filterSounds(sounds, 'place stone')[0]).toBe('block.stone.place'); // best match first, others may follow
  });

  it('ranks whole name parts above words that merely occur inside another word', () => {
    // lodestone / dripstone also contain 'stone', but block.stone.place is what the user means
    expect(filterSounds(sounds, 'stone place')[0]).toBe('block.stone.place');
    expect(filterSounds(sounds, 'stone place')).toContain('block.lodestone.place');
  });

  it('ignores case, spaces and the minecraft: prefix', () => {
    expect(filterSounds(sounds, '  Minecraft:BLOCK.wood.pl ')).toEqual(['block.wood.place']);
  });

  it('offers block sounds when nothing is typed, and respects the limit', () => {
    const r = filterSounds(sounds, '', 3);
    expect(r).toHaveLength(3);
    expect(r.every((s) => s.startsWith('block.'))).toBe(true);
    expect(filterSounds(sounds, 'block', 2)).toHaveLength(2);
  });

  it('matches partly typed words, but not letters in the wrong order', () => {
    expect(filterSounds(sounds, 'block.ston.place')[0]).toBe('block.stone.place'); // 'ston' is part of 'stone'
    expect(filterSounds(sounds, 'block.stoen.place')).toEqual([]);
  });
});

describe('isKnownSound', () => {
  const known = new Set(sounds);
  it('accepts known sounds with or without the namespace, and empty values', () => {
    expect(isKnownSound(known, 'block.stone.place')).toBe(true);
    expect(isKnownSound(known, ' minecraft:block.stone.place ')).toBe(true);
    expect(isKnownSound(known, '')).toBe(true);
    expect(isKnownSound(known, undefined)).toBe(true);
  });
  it('rejects unknown ones', () => {
    expect(isKnownSound(known, 'block.ston.place')).toBe(false);
  });
});

describe('sound families', () => {
  it('lists only families with both a place and a break sound', () => {
    expect(soundFamilies(sounds)).toEqual(['stone', 'wood', 'glass']);
  });

  it('recognises the family of a pair of sounds', () => {
    expect(familyOf('block.stone.place', 'block.stone.break')).toBe('stone');
    expect(familyOf('minecraft:block.wood.place', 'block.wood.break')).toBe('wood');
    expect(familyOf('block.stone.place', 'block.wood.break')).toBe('');
    expect(familyOf('', '')).toBe('');
  });
});

describe('closestSound', () => {
  it('suggests the known sound a typo most likely meant', () => {
    expect(closestSound(sounds, 'block.stoen.place')).toBe('block.stone.place');
    expect(closestSound(sounds, 'block.wod.break')).toBe('block.wood.break');
    expect(closestSound(sounds, 'Minecraft:block.glas.place')).toBe('block.glass.place');
  });

  it('counts a swap of two neighbouring letters as a single slip', () => {
    // 'stoen' -> 'stone' is one swap, 'stoen' -> 'stem' needs two edits; the swap wins even though 'stem' sorts first
    expect(closestSound(['block.stem.place', 'block.stone.place'], 'block.stoen.place')).toBe('block.stone.place');
    expect(closestSound(['block.stone.place', 'block.stone.break'], 'block.stone.plcae')).toBe('block.stone.place');
  });

  it('returns nothing when nothing is close, and for empty input', () => {
    expect(closestSound(sounds, 'totally.different.thing')).toBeNull();
    expect(closestSound(sounds, '')).toBeNull();
  });
});
