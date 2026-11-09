<script>
  import { onMount } from 'svelte';
  import { api } from './api.js';
  import RecipeEditor from './RecipeEditor.svelte';
  import { refLabel } from './lib/refs.js';

  let { meta, items, jump = null } = $props();

  let recipes = $state([]);
  let current = $state(null);
  let isNew = $state(false);
  let editorKey = $state(0);
  let status = $state('');
  let statusError = $state(false);
  let filter = $state('');

  const say = (m, e = false) => { status = m; statusError = e; };
  const label = (type) => meta.recipeTypes.find((t) => t.id === type)?.label ?? type;
  const visible = $derived(recipes.filter((r) =>
    (r.id + ' ' + refLabel(r.result, items, meta.customNamespace)).toLowerCase().includes(filter.toLowerCase())));

  async function refresh() {
    recipes = await api.recipes();
  }

  onMount(async () => {
    try {
      await refresh();
      // the graph can ask for a specific entry to be opened right away
      const wanted = jump ? recipes.find((x) => x.id === jump.id) : null;
      if (wanted) select(wanted);
    } catch (e) {
      say(e.message, true);
    }
  });

  function blank() {
    return {
      id: '', type: 'SHAPED', result: '', amount: 1, grid: Array(9).fill(''),
      input: '', experience: 0.35, cookTime: 200,
      template: '', base: '', addition: '', left: '', right: '', rightAmount: 1, levelCost: 1
    };
  }

  function open(def, fresh) {
    current = def;
    isNew = fresh;
    editorKey += 1;
    say('');
  }

  const createNew = () => open(blank(), true);

  function select(r) {
    const def = { ...blank(), ...$state.snapshot(r) };
    def.grid = Array.from({ length: 9 }, (_, i) => def.grid?.[i] ?? '');
    open(def, false);
  }

  async function save(def) {
    try {
      const saved = await api.saveRecipe(def);
      await refresh();
      select(recipes.find((x) => x.id === saved.id) ?? saved);
      if (saved.problem) say(`Saved, but not active: ${saved.problem}`, true);
      else say('Saved and active on the server.');
    } catch (e) {
      say(e.message, true);
    }
  }

  async function remove() {
    if (!confirm(`Delete recipe ${current.id}?`)) return;
    try {
      await api.deleteRecipe(current.id);
      current = null;
      await refresh();
      say('Deleted.');
    } catch (e) {
      say(e.message, true);
    }
  }
</script>

<div class="layout">
  <nav>
    <button class="primary wide" onclick={createNew}>+ New recipe</button>
    <input class="search" placeholder="Search recipes..." bind:value={filter} />
    <ul>
      {#each visible as r (r.id)}
        <li>
          <button class:active={current?.id === r.id && !isNew} onclick={() => select(r)}>
            <span class="label">
              <span>{refLabel(r.result, items, meta.customNamespace) || r.id}{r.problem ? ' ⚠' : ''}</span>
              <small>{label(r.type)}{r.problem ? ' · not active' : ''}</small>
            </span>
          </button>
        </li>
      {:else}
        <li class="muted pad">{recipes.length ? 'No match.' : 'No recipes yet.'}</li>
      {/each}
    </ul>
  </nav>

  <main>
    {#if current}
      {#if current.problem}<div class="banner error">Not active on the server: {current.problem}</div>{/if}
      {#key editorKey}
        <RecipeEditor initial={current} {isNew} {meta} {items} existingIds={recipes.map((r) => r.id)}
                      {status} {statusError} onsave={save} ondelete={remove} />
      {/key}
    {:else}
      <div class="empty">
        <h2>Recipes</h2>
        <p class="muted">Create crafting, furnace, stonecutter, smithing and anvil recipes for your items.</p>
        {#if status}<p class:error={statusError}>{status}</p>{/if}
      </div>
    {/if}
  </main>
</div>
