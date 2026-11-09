<script>
  import VanillaIcon from '../VanillaIcon.svelte';
  import { parseRef, refLabel, prettyName } from '../lib/refs.js';

  // A read-only inventory slot showing one item (custom, vanilla or an "any of" tag) with an optional count badge.
  let { ref = '', count = 1, items, meta, onfocus = null } = $props();

  const p = $derived(parseRef(ref, meta.customNamespace));
  const custom = $derived(p?.kind === 'CUSTOM' ? items.find((i) => i.id === p.name) : null);
  const label = $derived(ref ? refLabel(ref, items, meta.customNamespace) : '');
  let broken = $state(false);
</script>

<button type="button" class="rslot" class:vacant={!ref} title={label} aria-label={label || 'empty slot'}
        disabled={!ref || !onfocus} onclick={() => onfocus?.(ref)}>
  {#if custom}
    {#if custom.hasTexture && !broken}
      <img src={`/api/items/${custom.id}/texture`} alt="" onerror={() => (broken = true)} />
    {:else}
      <VanillaIcon name={custom.baseMaterial.toLowerCase()} size={32} text={false} />
    {/if}
  {:else if p?.kind === 'VANILLA'}
    <VanillaIcon name={p.name} size={32} />
  {:else if p?.kind === 'TAG'}
    <span class="rtag">any<br />{prettyName(p.name)}</span>
  {:else if p?.kind === 'CUSTOM'}
    <span class="rtag">missing</span>
  {/if}
  {#if count > 1}<span class="rcount">{count}</span>{/if}
</button>
