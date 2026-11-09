<script>
  import Chips from './Chips.svelte';
  import Slot from './Slot.svelte';
  import { parseRef, refLabel } from './lib/refs.js';

  let { initial, isNew, meta, items, existingIds, status, statusError, onsave, ondelete } = $props();

  // svelte-ignore state_referenced_locally
  let r = $state($state.snapshot(initial));
  // svelte-ignore state_referenced_locally
  let idTouched = $state(!isNew);

  const typeOptions = $derived(meta.recipeTypes.map((t) => ({ value: t.id, label: t.label, hint: t.hint })));
  const info = $derived(meta.recipeTypes.find((t) => t.id === r.type));
  const isCrafting = $derived(r.type === 'SHAPED' || r.type === 'SHAPELESS');
  const isCooking = $derived(['FURNACE', 'BLASTING', 'SMOKING', 'CAMPFIRE'].includes(r.type));
  const canMulti = $derived(isCrafting || r.type === 'STONECUTTING');

  function setType(type) {
    const before = meta.recipeTypes.find((t) => t.id === r.type);
    r.type = type;
    const now = meta.recipeTypes.find((t) => t.id === type);
    // Move the cook time along only while it still holds the previous station's default.
    if (now.defaultCookTime && (!r.cookTime || r.cookTime === before?.defaultCookTime || !before?.defaultCookTime)) {
      r.cookTime = now.defaultCookTime;
    }
    if (!(type === 'SHAPED' || type === 'SHAPELESS' || type === 'STONECUTTING')) r.amount = 1;
  }

  function setResult(ref) {
    r.result = ref;
    if (!idTouched && !r.id && ref) {
      const base = (parseRef(ref, meta.customNamespace)?.name ?? 'recipe').replaceAll(/[^a-z0-9_]/g, '_');
      let candidate = base;
      for (let n = 2; existingIds.includes(candidate); n++) candidate = `${base}_${n}`;
      r.id = candidate.slice(0, 48);
    }
  }

  const setCell = (i, ref) => (r.grid[i] = ref);
  const idValid = $derived(/^[a-z0-9_]{1,48}$/.test(r.id));
  const ready = $derived(idValid && !!r.result);

  function submit(event) {
    event.preventDefault();
    onsave($state.snapshot(r));
  }
</script>

<form class="editor" onsubmit={submit}>
  <div class="cols">
    <div class="stack">
      <section class="card">
        <h3>1 · Where is it made?</h3>
        <Chips options={typeOptions} value={r.type} onchange={setType} label="Station" />
        {#if info}<small class="muted">{info.hint}</small>{/if}
      </section>

      <section class="card">
        <h3>2 · Recipe</h3>

        {#if isCrafting}
          <div class="craft">
            <div class="grid3">
              {#each r.grid as cell, i}
                <Slot value={cell} {items} {meta} label={`Grid cell ${i + 1}`} onchange={(ref) => setCell(i, ref)} />
              {/each}
            </div>
            <span class="arrow">→</span>
            <Slot value={r.result} {items} {meta} allowTags={false} big label="Result" onchange={setResult} />
          </div>
          {#if r.type === 'SHAPELESS'}<small class="muted">Order does not matter; place the ingredients anywhere.</small>{/if}
        {:else if isCooking || r.type === 'STONECUTTING'}
          <div class="craft">
            <Slot value={r.input} {items} {meta} label="Input" onchange={(ref) => (r.input = ref)} />
            <span class="arrow">→</span>
            <Slot value={r.result} {items} {meta} allowTags={false} big label="Result" onchange={setResult} />
          </div>
        {:else if r.type === 'SMITHING'}
          <div class="craft">
            <div class="row">
              <div class="col"><small>Template</small>
                <Slot value={r.template} {items} {meta} label="Template" onchange={(ref) => (r.template = ref)} /></div>
              <span class="plus-sign">+</span>
              <div class="col"><small>Base</small>
                <Slot value={r.base} {items} {meta} label="Base" onchange={(ref) => (r.base = ref)} /></div>
              <span class="plus-sign">+</span>
              <div class="col"><small>Addition</small>
                <Slot value={r.addition} {items} {meta} label="Addition" onchange={(ref) => (r.addition = ref)} /></div>
            </div>
            <span class="arrow">→</span>
            <Slot value={r.result} {items} {meta} allowTags={false} big label="Result" onchange={setResult} />
          </div>
        {:else if r.type === 'ANVIL'}
          <div class="craft">
            <div class="row">
              <div class="col"><small>Left (the item)</small>
                <Slot value={r.left} {items} {meta} label="Left" onchange={(ref) => (r.left = ref)} /></div>
              <span class="plus-sign">+</span>
              <div class="col"><small>Right (material)</small>
                <Slot value={r.right} {items} {meta} label="Right" onchange={(ref) => (r.right = ref)} /></div>
            </div>
            <span class="arrow">→</span>
            <Slot value={r.result} {items} {meta} allowTags={false} big label="Result" onchange={setResult} />
          </div>
        {/if}

        <div class="grid">
          {#if canMulti}
            <label>Result amount
              <input type="number" min="1" max="64" bind:value={r.amount} />
            </label>
          {/if}
          {#if isCooking}
            <label>Cook time (ticks) <small>20 ticks = 1 s</small>
              <input type="number" min="1" bind:value={r.cookTime} />
            </label>
            <label>Experience
              <input type="number" step="0.05" min="0" bind:value={r.experience} />
            </label>
          {/if}
          {#if r.type === 'ANVIL'}
            <label>Level cost <small>0 to 39</small>
              <input type="number" min="0" max="39" bind:value={r.levelCost} />
            </label>
            <label>Right items used
              <input type="number" min="1" max="64" bind:value={r.rightAmount} />
            </label>
          {/if}
        </div>
      </section>
    </div>

    <aside class="side">
      <div class="card sticky">
        <h3>Save</h3>
        <label>Recipe ID <small>lowercase letters, digits, _</small>
          <input bind:value={r.id} disabled={!isNew} required class:bad={r.id && !idValid}
                 oninput={() => (idTouched = true)} placeholder="ruby_pickaxe" />
        </label>
        <p class="muted summary">
          {#if r.result}Makes <strong>{r.amount > 1 ? r.amount + '× ' : ''}{refLabel(r.result, items, meta.customNamespace)}</strong>
            at the {info?.label.toLowerCase()}.{:else}Choose a result item.{/if}
        </p>
        <div class="actions">
          <button class="primary" type="submit" disabled={!ready}>Save</button>
          {#if !isNew}<button type="button" class="danger" onclick={ondelete}>Delete</button>{/if}
        </div>
        {#if status}<p class:error={statusError} class="status">{status}</p>{/if}
        <small class="muted">Saved recipes are active on the server immediately; players see them in the recipe book.</small>
      </div>
    </aside>
  </div>
</form>
