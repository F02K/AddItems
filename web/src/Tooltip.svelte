<script>
  import { parseMini } from './lib/mctext.js';

  let { item } = $props();

  const nameSpans = $derived(parseMini(item.displayName || item.id || 'Unnamed item', { color: '#FFFFFF' }));
  const loreLines = $derived((item.lore ?? []).map((l) => parseMini(l, { color: '#AAAAAA' })));
  const t = $derived(item.type === 'TOOL' ? item.tool : null);
  const a = $derived(item.type === 'ARMOR' ? item.armor : null);
  const SLOT_LABEL = { HELMET: 'Head', CHESTPLATE: 'Body', LEGGINGS: 'Legs', BOOTS: 'Feet' };

  const style = (s) =>
    `color:${s.color ?? '#FFFFFF'};` +
    (s.bold ? 'font-weight:700;' : '') +
    (s.italic ? 'font-style:italic;' : '') +
    (s.underlined || s.strikethrough
      ? `text-decoration:${[s.underlined && 'underline', s.strikethrough && 'line-through'].filter(Boolean).join(' ')};`
      : '');
</script>

<div class="tooltip">
  <div>{#each nameSpans as s}<span style={style(s)}>{s.text}</span>{/each}</div>
  {#each loreLines as line}
    <div>{#each line as s}<span style={style(s)}>{s.text}</span>{/each}</div>
  {/each}
  {#if t}
    <div class="gap"></div>
    <div style="color:#AAAAAA">When in Main Hand:</div>
    <div style="color:#5555FF"> {+t.damage.toFixed(2)} Attack Damage</div>
    <div style="color:#5555FF"> {+t.attackSpeed.toFixed(2)} Attack Speed</div>
    <div class="gap"></div>
    <div style="color:#FFFFFF">Durability: {t.durability} / {t.durability}</div>
  {/if}
  {#if a}
    <div class="gap"></div>
    <div style="color:#AAAAAA">When on {SLOT_LABEL[a.slot]}:</div>
    <div style="color:#5555FF"> +{+Number(a.defense).toFixed(2)} Armor</div>
    {#if a.toughness > 0}<div style="color:#5555FF"> +{+Number(a.toughness).toFixed(2)} Armor Toughness</div>{/if}
    {#if a.knockbackResistance > 0}<div style="color:#5555FF"> +{Math.round(a.knockbackResistance * 100)}% Knockback Resistance</div>{/if}
    <div class="gap"></div>
    <div style="color:#FFFFFF">Durability: {a.durability} / {a.durability}</div>
  {/if}
</div>
