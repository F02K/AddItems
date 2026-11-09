<script>
  import VanillaIcon from './VanillaIcon.svelte';

  // Searchable vanilla item picker: type to filter, or choose from the suggestion list.
  let { value = $bindable(), options, known, id, hint = '' } = $props();
  const valid = $derived(!value || known.has(value));
</script>

<div class="picker">
  <div class="picker-row">
    <span class="picker-icon">
      {#if value && valid}<VanillaIcon name={value.toLowerCase()} size={28} text={false} />{/if}
    </span>
    <input list={id} bind:value spellcheck="false" autocomplete="off" placeholder="Search e.g. IRON_PICKAXE"
           class:bad={!valid} oninput={() => (value = value.toUpperCase().replace(/\s+/g, '_'))} />
  </div>
  <datalist {id}>
    {#each options as name}<option value={name}></option>{/each}
  </datalist>
  {#if !valid}
    <small class="error">Not a known vanilla item. The server falls back to PAPER.</small>
  {:else if hint}
    <small>{hint}</small>
  {/if}
</div>
