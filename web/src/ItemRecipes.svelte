<script>
  import { onMount } from 'svelte';
  import { api } from './api.js';
  import RecipeEditor from './RecipeEditor.svelte';
  import { refLabel } from './lib/refs.js';
  import { blankRecipe } from './lib/blank.js';
  import { recipesMaking, recipesUsing } from './lib/details.js';

  // The "Recipes" tab of an item: every recipe that makes it, edited inline. The result is always this item.
  let { item, meta, items } = $props();

  const ref = $derived(`${meta.customNamespace}:${item.id}`);

  let recipes = $state([]);
  let editing = $state(null); // { def, isNew }
  let editorKey = $state(0);
  let status = $state('');
  let statusError = $state(false);

  const say = (m, e = false) => { status = m; statusError = e; };
  const making = $derived(recipesMaking(ref, recipes));
  const using = $derived(recipesUsing(ref, recipes).filter((r) => r.result !== ref));
  const stationLabel = (type) => meta.recipeTypes.find((t) => t.id === type)?.label ?? type;
  const label = (r) => refLabel(r, items, meta.customNamespace) || r;

  async function refresh() {
    recipes = await api.recipes();
  }

  onMount(() => refresh().catch((e) => say(e.message, true)));

  function open(def, isNew) {
    editing = { def, isNew };
    editorKey += 1;
    say('');
  }

  function openExisting(r) {
    const def = { ...blankRecipe(), ...$state.snapshot(r), result: r.result };
    def.grid = Array.from({ length: 9 }, (_, i) => def.grid?.[i] ?? '');
    open(def, false);
  }

  const create = () => open({ ...blankRecipe(), type: 'SHAPED', result: ref, id: item.id }, true); // the server makes the id unique

  async function save(def) {
    try {
      const saved = editing.isNew ? await api.createRecipe(def) : await api.saveRecipe(def);
      await refresh();
      const fresh = recipes.find((r) => r.id === saved.id);
      if (fresh) openExisting(fresh);
      if (saved.problem) say(`Saved, but not active: ${saved.problem}`, true);
      else say('Saved and active on the server.');
    } catch (e) {
      say(e.message, true);
    }
  }

  async function remove() {
    if (!confirm(`Delete recipe ${editing.def.id}?`)) return;
    try {
      await api.deleteRecipe(editing.def.id);
      editing = null;
      await refresh();
      say('Deleted.');
    } catch (e) {
      say(e.message, true);
    }
  }
</script>

<div class="subtab">
  <div class="rule-list">
    {#each making as r (r.id)}
      <button type="button" class="rule" class:on={editing?.def.id === r.id && !editing.isNew} onclick={() => openExisting(r)}>
        <strong>{stationLabel(r.type)}{r.problem ? ' ⚠' : ''}</strong>
        <small>{r.amount > 1 ? r.amount + '× ' : ''}{r.id}</small>
      </button>
    {/each}
    <button type="button" class="add" onclick={create}>+ New recipe for {item.displayName ? item.displayName.replace(/<[^>]*>/g, '') : item.id}</button>
  </div>
  {#if !making.length && !editing}
    <p class="muted">No recipe makes this item yet. The result of every recipe here is this item.</p>
  {/if}

  {#if editing}
    {#key editorKey}
      <RecipeEditor initial={editing.def} isNew={editing.isNew} {meta} {items} existingIds={recipes.map((r) => r.id)}
                    {status} statusError={statusError} lockResult onsave={save} ondelete={remove} />
    {/key}
  {:else if status}
    <p class:error={statusError} class="status">{status}</p>
  {/if}

  {#if using.length}
    <div class="rule-list">
      <h4>Used as an ingredient in</h4>
      {#each using as r (r.id)}
        <span class="muted">{label(r.result)} <small>({stationLabel(r.type)}, {r.id})</small></span>
      {/each}
    </div>
  {/if}
</div>
