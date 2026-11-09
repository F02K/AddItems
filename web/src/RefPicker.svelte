<script>
  import { onMount } from 'svelte';
  import { prettyName } from './lib/refs.js';
  import { vanilla, vanillaName } from './lib/vanilla.svelte.js';
  import VanillaIcon from './VanillaIcon.svelte';

  // Modal item chooser: your custom items, vanilla item tags and all vanilla items.
  let { items, meta, allowTags = true, onpick, onclose } = $props();

  let query = $state('');
  let input;

  onMount(() => input?.focus());

  const text = $derived(query.trim().toLowerCase());
  const q = $derived(text.replaceAll(' ', '_'));
  const custom = $derived(items.filter((i) => !q || i.id.includes(q) || (i.displayName ?? '').toLowerCase().includes(text)));
  const tags = $derived(allowTags ? meta.itemTags.filter((t) => !q || t.includes(q)) : []);
  // Search by id ("iron_ingot") and, once available, by English name ("Iron Ingot").
  const matching = $derived(
    meta.materials.filter((m) => {
      if (!q) return true;
      const id = m.toLowerCase();
      return id.includes(q) || (vanilla.names[id] ?? '').toLowerCase().includes(text);
    })
  );
  const vanillaShown = $derived(matching.slice(0, 80));
</script>

<svelte:window onkeydown={(e) => e.key === 'Escape' && onclose()} />

<div class="overlay" role="presentation" onclick={onclose}>
  <div class="modal" role="dialog" aria-modal="true" aria-label="Choose an item" tabindex="-1"
       onclick={(e) => e.stopPropagation()} onkeydown={() => {}}>
    <input bind:this={input} bind:value={query} placeholder="Search items..." spellcheck="false" />
    <div class="results">
      {#if custom.length}
        <h4>Your items</h4>
        <div class="list">
          {#each custom as i (i.id)}
            <button type="button" onclick={() => onpick(`${meta.customNamespace}:${i.id}`)}>
              <span class="thumb">{#if i.hasTexture}<img src={`/api/items/${i.id}/texture`} alt="" />{/if}</span>
              {(i.displayName || i.id).replace(/<[^>]*>/g, '')}
            </button>
          {/each}
        </div>
      {/if}
      {#if tags.length}
        <h4>Any of a group</h4>
        <div class="list">
          {#each tags as t}
            <button type="button" onclick={() => onpick(t)}>{prettyName(t.split(':')[1])}</button>
          {/each}
        </div>
      {/if}
      <h4>Vanilla items {#if matching.length > 80}<small>(showing 80 of {matching.length}, keep typing)</small>{/if}</h4>
      <div class="list">
        {#each vanillaShown as m (m)}
          <button type="button" onclick={() => onpick(`minecraft:${m.toLowerCase()}`)}>
            <span class="thumb"><VanillaIcon name={m.toLowerCase()} size={20} text={false} /></span>
            {vanillaName(m.toLowerCase()) ?? prettyName(m.toLowerCase())}
          </button>
        {:else}
          <span class="muted">No match.</span>
        {/each}
      </div>
    </div>
  </div>
</div>
