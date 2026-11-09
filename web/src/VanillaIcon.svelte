<script>
  import { vanilla, vanillaName } from './lib/vanilla.svelte.js';
  import { prettyName } from './lib/refs.js';

  // Vanilla item icon with a text fallback while the assets are missing or when an item has no icon.
  let { name, size = 32, text = true } = $props();

  let failed = $state(false);
  $effect(() => {
    name;
    failed = false;
  });
</script>

{#if vanilla.ready && !failed}
  <img class="vicon" src={`/api/vanilla/icon/${name}`} alt={vanillaName(name) ?? prettyName(name)}
       width={size} height={size} loading="lazy" onerror={() => (failed = true)} />
{:else if text}
  <span class="name">{vanillaName(name) ?? prettyName(name)}</span>
{/if}
