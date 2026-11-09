// Tiny renderer for the MiniMessage subset used in item names and lore (colors + basic styles).
// Unknown tags are dropped. This is only a preview; the server does the real parsing.
const COLORS = {
  black: '#000000', dark_blue: '#0000AA', dark_green: '#00AA00', dark_aqua: '#00AAAA',
  dark_red: '#AA0000', dark_purple: '#AA00AA', gold: '#FFAA00', gray: '#AAAAAA',
  dark_gray: '#555555', blue: '#5555FF', green: '#55FF55', aqua: '#55FFFF',
  red: '#FF5555', light_purple: '#FF55FF', yellow: '#FFFF55', white: '#FFFFFF'
};
const STYLES = {
  bold: 'bold', b: 'bold', italic: 'italic', i: 'italic', em: 'italic',
  underlined: 'underlined', u: 'underlined', strikethrough: 'strikethrough', st: 'strikethrough'
};

/** Returns [{ text, color, bold, italic, underlined, strikethrough }] for a MiniMessage string. */
export function parseMini(input, base = {}) {
  const spans = [];
  const stack = [];
  let current = { color: base.color ?? null, bold: false, italic: base.italic ?? false, underlined: false, strikethrough: false };
  const tag = /<(\/?)([a-zA-Z_#0-9]+)(?::[^>]*)?>/g;
  let last = 0;
  let m;
  const push = (text) => {
    if (text) spans.push({ text, ...current });
  };
  while ((m = tag.exec(input))) {
    push(input.slice(last, m.index));
    last = tag.lastIndex;
    const closing = m[1] === '/';
    const name = m[2].toLowerCase();
    if (closing) {
      const idx = stack.findLastIndex((s) => s.name === name);
      if (idx >= 0) {
        current = stack[idx].before;
        stack.length = idx;
      }
    } else if (name === 'reset') {
      current = { color: null, bold: false, italic: false, underlined: false, strikethrough: false };
      stack.length = 0;
    } else if (COLORS[name] || /^#[0-9a-f]{6}$/.test(name)) {
      stack.push({ name, before: current });
      current = { ...current, color: COLORS[name] ?? name };
    } else if (STYLES[name]) {
      stack.push({ name, before: current });
      current = { ...current, [STYLES[name]]: true };
    }
  }
  push(input.slice(last));
  return spans;
}
