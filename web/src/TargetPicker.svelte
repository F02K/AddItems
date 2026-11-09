<script>
  import { onMount } from 'svelte';
  import VanillaIcon from './VanillaIcon.svelte';
  import { targetLabel, targetIcon } from './lib/loot.js';

  // Modal to choose loot targets (blocks / mobs / loot tables). Several can be toggled before closing.
  let { trigger, options, items = [], ns = '', selected, ontoggle, onclose } = $props();

  let query = $state('');
  let input;
  onMount(() => input?.focus());

  const q = $derived(query.trim().toLowerCase());
  const shown = $derived(
    options.filter((o) => !q || o.toLowerCase().includes(q.replaceAll(' ', '_')) || targetLabel(trigger, o, items, ns).toLowerCase().includes(q))
  );
  const visible = $derived(shown.slice(0, 120));
</script>

<svelte:window onkeydown={(e) => e.key === 'Escape' && onclose()} />

<div class="overlay" role="presentation" onclick={onclose}>
  <div class="modal" role="dialog" aria-modal="true" aria-label="Choose targets" tabindex="-1"
       onclick={(e) => e.stopPropagation()} onkeydown={() => {}}>
    <input bind:this={input} bind:value={query} placeholder="Search..." spellcheck="false" />
    <div class="results">
      <div class="list">
        {#each visible as o (o)}
          {@const icon = targetIcon(trigger, o)}
          <button type="button" class:picked={selected.includes(o)} onclick={() => ontoggle(o)}>
            <span class="thumb">{#if icon}<VanillaIcon name={icon} size={20} text={false} />{/if}</span>
            {targetLabel(trigger, o, items, ns)}
          </button>
        {:else}
          <span class="muted">No match.</span>
        {/each}
      </div>
      {#if shown.length > 120}<p class="muted">Showing 120 of {shown.length}, keep typing.</p>{/if}
    </div>
    <button type="button" class="primary" onclick={onclose}>Done ({selected.length} selected)</button>
  </div>
</div>
