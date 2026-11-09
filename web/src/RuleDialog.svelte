<script>
  import { onMount } from 'svelte';
  import { api } from './api.js';
  import RecipeEditor from './RecipeEditor.svelte';
  import LootEditor from './LootEditor.svelte';
  import { blankRecipe, blankLootRule } from './lib/blank.js';

  // A recipe or drop rule in the full editor, as a dialog. Pass the entry itself (`def`, `isNew`) or only its `id`.
  // onclose() just closes; onsaved(entry, change) is called after a save ('saved') or delete ('deleted') and then closes.
  let { kind, id = null, def = null, isNew = false, meta, items, onclose, onsaved } = $props();

  let recipes = $state([]);
  let rules = $state([]);
  let current = $state(null);
  let creating = $state(false);
  let error = $state('');
  let failed = $state('');

  const existingIds = $derived((kind === 'recipe' ? recipes : rules).map((r) => r.id));

  function normalize(entry) {
    if (kind === 'recipe') {
      const merged = { ...blankRecipe(), ...$state.snapshot(entry) };
      merged.grid = Array.from({ length: 9 }, (_, i) => merged.grid?.[i] ?? '');
      return merged;
    }
    return { ...blankLootRule(), ...$state.snapshot(entry) };
  }

  onMount(async () => {
    try {
      [recipes, rules] = await Promise.all([api.recipes(), api.loot()]);
      if (def) {
        current = normalize(def);
        creating = isNew;
      } else {
        const found = (kind === 'recipe' ? recipes : rules).find((r) => r.id === id);
        if (found) current = normalize(found);
        else failed = 'This entry no longer exists.';
      }
    } catch (e) {
      failed = e.message;
    }
  });

  async function save(entry) {
    try {
      const saved = kind === 'recipe'
        ? (creating ? await api.createRecipe(entry) : await api.saveRecipe(entry))
        : (creating ? await api.createLoot(entry) : await api.saveLoot(entry));
      onsaved?.(saved, 'saved');
      onclose();
    } catch (e) {
      error = e.message;
    }
  }

  async function remove() {
    const what = kind === 'recipe' ? 'recipe' : 'drop rule';
    if (!confirm(`Delete ${what} ${current.id}?`)) return;
    try {
      if (kind === 'recipe') await api.deleteRecipe(current.id);
      else await api.deleteLoot(current.id);
      onsaved?.(current, 'deleted');
      onclose();
    } catch (e) {
      error = e.message;
    }
  }
</script>

<div class="overlay" role="presentation" onclick={(e) => { if (e.target === e.currentTarget) onclose(); }}>
  <div class="modal wide" role="dialog" aria-modal="true" aria-label={kind === 'recipe' ? 'Recipe' : 'Drop rule'}>
    <div class="modal-head">
      <strong>{kind === 'recipe' ? 'Recipe' : 'Drop rule'}</strong>
      <button type="button" onclick={onclose}>Close</button>
    </div>
    {#if failed}
      <p class="error">{failed}</p>
    {:else if current}
      {#if current.problem}<div class="banner error">Not active on the server: {current.problem}</div>{/if}
      {#if kind === 'recipe'}
        <RecipeEditor initial={current} isNew={creating} {meta} {items} {existingIds}
                      status={error} statusError={true} onsave={save} ondelete={remove} />
      {:else}
        <LootEditor initial={current} isNew={creating} {meta} {items} {existingIds}
                    status={error} statusError={true} onsave={save} ondelete={remove} />
      {/if}
    {:else}
      <p class="muted">Loading...</p>
    {/if}
  </div>
</div>
