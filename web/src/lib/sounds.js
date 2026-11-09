// Helpers for the sound autocompletion. Sound ids come from the server (Paper's registry), without "minecraft:".

const strip = (s) => s.trim().toLowerCase().replace(/^minecraft:/, '');

/**
 * Suggestions for what the user typed: ids that start with it first, then ids that contain every typed word
 * ("stone break" finds block.stone.break). With nothing typed, block sounds are offered as a starting point.
 */
export function filterSounds(list, query, limit = 40) {
  const q = strip(query);
  if (!q) return list.filter((s) => s.startsWith('block.')).slice(0, limit);
  const words = q.split(/[\s.]+/).filter(Boolean);
  const starts = [];
  const contains = [];
  for (const s of list) {
    if (s.startsWith(q)) {
      starts.push(s);
    } else if (words.every((w) => s.includes(w))) {
      // words that are whole name parts ("stone" in block.stone.place, not in block.lodestone.place) rank first
      const parts = new Set(s.split(/[._]/));
      contains.push({ s, whole: words.filter((w) => parts.has(w)).length });
    }
  }
  contains.sort((a, b) => b.whole - a.whole || a.s.length - b.s.length || (a.s < b.s ? -1 : 1));
  return [...starts, ...contains.map((c) => c.s)].slice(0, limit);
}

/** True if the value is a known sound ("minecraft:" and surrounding spaces are ignored); empty counts as fine. */
export function isKnownSound(known, value) {
  const v = strip(value ?? '');
  return !v || known.has(v);
}

/** Block sound families that have both a place and a break sound: ["glass", "gravel", "stone", ...]. */
export function soundFamilies(list) {
  const set = new Set(list);
  const out = [];
  for (const s of list) {
    const m = /^block\.([a-z_]+)\.place$/.exec(s);
    if (m && set.has(`block.${m[1]}.break`)) out.push(m[1]);
  }
  return out;
}

/** The family a pair of sounds belongs to ("stone" for block.stone.place + block.stone.break), or ''. */
export function familyOf(place, breaking) {
  const p = /^block\.([a-z_]+)\.place$/.exec(strip(place ?? ''));
  const b = /^block\.([a-z_]+)\.break$/.exec(strip(breaking ?? ''));
  return p && b && p[1] === b[1] ? p[1] : '';
}

/**
 * Edit distance (insert, delete, replace, or swap two neighbouring letters = 1), stopping early once it exceeds `max`.
 * Swaps matter: "stoen" is one slip away from "stone" but two from "stem".
 */
function distance(a, b, max) {
  if (Math.abs(a.length - b.length) > max) return max + 1;
  let before = null;
  let prev = Array.from({ length: b.length + 1 }, (_, j) => j);
  for (let i = 1; i <= a.length; i++) {
    const cur = [i];
    let best = i;
    for (let j = 1; j <= b.length; j++) {
      cur[j] = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
      if (before && i > 1 && j > 1 && a[i - 1] === b[j - 2] && a[i - 2] === b[j - 1]) cur[j] = Math.min(cur[j], before[j - 2] + 1);
      best = Math.min(best, cur[j]);
    }
    if (best > max) return max + 1;
    before = prev;
    prev = cur;
  }
  return prev[b.length];
}

/**
 * The most similar known sound for a mistyped id ("block.stoen.place" -> "block.stone.place"), or null when nothing is
 * close enough (at most 3 edits).
 */
export function closestSound(list, value, max = 3) {
  const q = strip(value ?? '');
  if (!q) return null;
  let best = null;
  let bestDistance = max + 1;
  for (const s of list) {
    const d = distance(q, s, Math.min(bestDistance - 1, max));
    if (d < bestDistance) {
      best = s;
      bestDistance = d;
      if (d === 0) break;
    }
  }
  return best;
}
