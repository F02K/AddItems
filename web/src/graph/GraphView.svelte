<script>
  import { onMount } from 'svelte';
  import { SvelteFlowProvider } from '@xyflow/svelte';
  import { api } from '../api.js';
  import GraphCanvas from './GraphCanvas.svelte';

  // Loads everything the graph needs (recipes, drop rules, the shared layout) and hosts the canvas.
  let { meta, items, onopen } = $props();

  let recipes = $state([]);
  let loot = $state([]);
  let layout = $state(null);
  let error = $state('');

  async function reload() {
    try {
      [recipes, loot] = await Promise.all([api.recipes(), api.loot()]);
    } catch (e) {
      error = e.message;
    }
  }

  onMount(async () => {
    await reload();
    try {
      layout = await api.graphLayout();
    } catch {
      layout = { direction: 'LR', positions: {} };
    }
  });
</script>

{#if error}
  <p class="banner error">{error}</p>
{:else if layout}
  <SvelteFlowProvider>
    <GraphCanvas {meta} {items} {recipes} {loot} {layout} {onopen} onreload={reload} />
  </SvelteFlowProvider>
{:else}
  <p class="muted pad">Loading the graph...</p>
{/if}
