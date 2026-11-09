<script>
  import RefPicker from './RefPicker.svelte';
  import VanillaIcon from './VanillaIcon.svelte';
  import { parseRef, refLabel } from './lib/refs.js';

  // One clickable ingredient / result cell. Click to choose, x to clear.
  let { value = '', items, meta, label = '', allowTags = true, big = false, onchange } = $props();

  let open = $state(false);
  const p = $derived(parseRef(value, meta.customNamespace));
  const custom = $derived(p?.kind === 'CUSTOM' ? items.find((i) => i.id === p.name) : null);
  const text = $derived(refLabel(value, items, meta.customNamespace));
</script>

<div class="cell" class:big>
  <button type="button" class="slot" class:blank={!value} title={text || label} aria-label={text || label || 'Choose item'}
          onclick={() => (open = true)}>
    {#if custom?.hasTexture}
      <img src={`/api/items/${custom.id}/texture`} alt="" />
    {:else if p?.kind === 'VANILLA'}
      <VanillaIcon name={p.name} size={big ? 64 : 48} />
    {:else if value}
      <span class="name">{text}</span>
    {:else}
      <span class="plus">+</span>
    {/if}
  </button>
  {#if value}
    <button type="button" class="clear" aria-label="Clear" onclick={() => onchange('')}>×</button>
  {/if}
</div>

{#if open}
  <RefPicker {items} {meta} {allowTags} onclose={() => (open = false)}
             onpick={(ref) => { open = false; onchange(ref); }} />
{/if}
