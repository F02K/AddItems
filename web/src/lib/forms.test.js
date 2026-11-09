import { describe, it, expect } from 'vitest';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

// A number input only accepts min, min + step, min + 2*step, ... A default such as 1.6 with min=0.05 step=0.1 is therefore
// INVALID, and the browser then blocks the whole form without a visible message. Inputs must use step="any" or a min that is
// a multiple of the step.

const root = join(import.meta.dirname, '..');

function svelteFiles(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? svelteFiles(path) : path.endsWith('.svelte') ? [path] : [];
  });
}

/** Number of decimals in a literal like "0.05", used to compare without floating point noise. */
const scale = (s) => (s.includes('.') ? s.split('.')[1].length : 0);

function misaligned(tag) {
  const min = /\bmin="([^"{]+)"/.exec(tag)?.[1];
  const step = /\bstep="([^"{]+)"/.exec(tag)?.[1];
  if (min === undefined || step === undefined || step === 'any') return false;
  const f = 10 ** Math.max(scale(min), scale(step));
  return Math.round(Number(min) * f) % Math.round(Number(step) * f) !== 0;
}

describe('number inputs', () => {
  const inputs = svelteFiles(root).flatMap((file) =>
    [...readFileSync(file, 'utf8').matchAll(/<input\b[^>]*type="number"[^>]*>/g)].map((m) => ({ file: file.slice(root.length + 1), tag: m[0] }))
  );

  it('finds the inputs it is supposed to check', () => {
    expect(inputs.length).toBeGreaterThan(20);
  });

  it('never combine a min with a step that does not divide it (they silently block saving)', () => {
    const bad = inputs.filter((i) => misaligned(i.tag)).map((i) => `${i.file}: ${i.tag.slice(0, 110)}`);
    expect(bad).toEqual([]);
  });

  it('the checker itself recognises the broken pattern', () => {
    expect(misaligned('<input type="number" min="0.05" step="0.1">')).toBe(true);
    expect(misaligned('<input type="number" min="0.01" max="100" step="0.5">')).toBe(true);
    expect(misaligned('<input type="number" min="0.05" step="0.05">')).toBe(false);
    expect(misaligned('<input type="number" min="1" step="0.5">')).toBe(false);
    expect(misaligned('<input type="number" min="0.05" step="any">')).toBe(false);
  });
});
