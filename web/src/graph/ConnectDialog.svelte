<script>
  import Chips from '../Chips.svelte';
  import Slot from '../Slot.svelte';
  import { api } from '../api.js';
  import { blankRecipe, blankLootRule, blankDrop, uniqueId } from '../lib/blank.js';
  import { refLabel } from '../lib/refs.js';
  import { targetLabel } from '../lib/loot.js';

  // Creates something from a connection drawn on the graph: a recipe (item -> item) or a drop rule (source -> item).
  let { mode, from, to, meta, items, recipes, loot, oncreated, onclose } = $props();

  const ns = $derived(meta.customNamespace);
  const toRef = $derived(`${ns}:${to.data.itemId}`);
  const fromRef = $derived(
    from.type === 'item' ? `${ns}:${from.data.itemId}`
      : from.type === 'tag' ? `#minecraft:${from.data.name}` : `minecraft:${from.data.name}`
  );
  const fromName = $derived(refLabel(fromRef, items, ns));
  const toName = $derived(refLabel(toRef, items, ns));

  let error = $state('');
  let busy = $state(false);

  // ---- recipe from an item ------------------------------------------------------------------
  const STATIONS = $derived(meta.recipeTypes.filter((t) => t.id !== 'SHAPED'));
  let type = $state('SHAPELESS');
  let amount = $state(1);
  let extra = $state(['', '', '']); // more shapeless ingredients
  let template = $state('');
  let addition = $state('');
  let right = $state('');
  let rightAmount = $state(1);
  let levelCost = $state(1);

  const info = $derived(meta.recipeTypes.find((t) => t.id === type));
  const cooking = $derived(['FURNACE', 'BLASTING', 'SMOKING', 'CAMPFIRE'].includes(type));
  const multi = $derived(type === 'SHAPELESS' || type === 'STONECUTTING');

  function buildRecipe() {
    const r = blankRecipe();
    r.type = type;
    r.result = toRef;
    r.amount = multi ? amount : 1;
    if (type === 'SHAPELESS') {
      const refs = [fromRef, ...extra.filter(Boolean)];
      r.grid = Array.from({ length: 9 }, (_, i) => refs[i] ?? '');
    } else if (cooking || type === 'STONECUTTING') {
      r.input = fromRef;
      r.cookTime = info?.defaultCookTime || 200;
    } else if (type === 'SMITHING') {
      if (!template || !addition) throw new Error('Smithing needs a template and an addition.');
      Object.assign(r, { template, base: fromRef, addition });
    } else if (type === 'ANVIL') {
      if (!right) throw new Error('Choose the right item for the anvil.');
      Object.assign(r, { left: fromRef, right, rightAmount, levelCost });
    }
    r.id = uniqueId(`${toName}_from_${fromName}`, recipes.map((x) => x.id));
    return r;
  }

  // ---- drop rule from a source --------------------------------------------------------------
  let min = $state(1);
  let max = $state(1);
  let chance = $state(100);

  function buildRule() {
    const rule = blankLootRule();
    rule.trigger = from.data.trigger;
    rule.targets = [...from.data.targets];
    rule.drops = [{ ...blankDrop(), item: toRef, min, max: Math.max(min, max), chance }];
    const first = from.data.targets[0] === '*' ? from.data.trigger.toLowerCase() : (from.data.targets[0].split(':')[1] ?? 'loot');
    rule.id = uniqueId(`${first}_drops`, loot.map((x) => x.id));
    return rule;
  }

  async function create() {
    error = '';
    busy = true;
    try {
      if (mode === 'recipe') {
        const recipe = buildRecipe();
        await api.saveRecipe(recipe);
        oncreated(`Recipe "${recipe.id}" created: ${fromName} → ${toName}`);
      } else {
        const rule = buildRule();
        await api.saveLoot(rule);
        oncreated(`Drop rule "${rule.id}" created: ${targetLabel(rule.trigger, rule.targets[0], items, ns)} drops ${toName}`);
      }
    } catch (e) {
      error = e.message;
    } finally {
      busy = false;
    }
  }
</script>

<svelte:window onkeydown={(e) => e.key === 'Escape' && onclose()} />

<div class="overlay" role="presentation" onclick={onclose}>
  <div class="modal dialog" role="dialog" aria-modal="true" aria-label="Create connection" tabindex="-1"
       onclick={(e) => e.stopPropagation()} onkeydown={() => {}}>
    {#if mode === 'recipe'}
      <h3>New recipe</h3>
      <p class="flow"><strong>{fromName}</strong> <span>→</span> <strong>{toName}</strong></p>
      <span class="lbl">Where is it made?</span>
      <Chips options={STATIONS.map((t) => ({ value: t.id, label: t.label, hint: t.hint }))} value={type}
             onchange={(v) => (type = v)} label="Station" />

      {#if type === 'SHAPELESS'}
        <span class="lbl">More ingredients <small>(optional; {fromName} is already included)</small></span>
        <div class="extras">
          {#each extra as cell, i}
            <Slot value={cell} {items} {meta} label={`Ingredient ${i + 2}`} onchange={(ref) => (extra[i] = ref)} />
          {/each}
        </div>
      {:else if type === 'SMITHING'}
        <div class="extras">
          <div class="col"><small>Template</small><Slot value={template} {items} {meta} label="Template" onchange={(r) => (template = r)} /></div>
          <div class="col"><small>Base</small><div class="fixed">{fromName}</div></div>
          <div class="col"><small>Addition</small><Slot value={addition} {items} {meta} label="Addition" onchange={(r) => (addition = r)} /></div>
        </div>
      {:else if type === 'ANVIL'}
        <div class="extras">
          <div class="col"><small>Left</small><div class="fixed">{fromName}</div></div>
          <div class="col"><small>Right</small><Slot value={right} {items} {meta} label="Right item" onchange={(r) => (right = r)} /></div>
        </div>
        <div class="grid">
          <label>Right items used <input type="number" min="1" max="64" bind:value={rightAmount} /></label>
          <label>Level cost <input type="number" min="0" max="39" bind:value={levelCost} /></label>
        </div>
      {/if}

      {#if multi}
        <label class="short">Result amount <input type="number" min="1" max="64" bind:value={amount} /></label>
      {/if}
      <small class="muted">You can fine-tune everything later in the Recipes tab.</small>
    {:else}
      <h3>New drop rule</h3>
      <p class="flow">
        <strong>{targetLabel(from.data.trigger, from.data.targets[0], items, ns)}{from.data.count > 1 ? ` +${from.data.count - 1}` : ''}</strong>
        <span>→ drops →</span> <strong>{toName}</strong>
      </p>
      <div class="grid">
        <label>Amount
          <span class="range">
            <input type="number" min="1" max="64" bind:value={min} aria-label="Minimum" />
            <span>to</span>
            <input type="number" min="1" max="64" bind:value={max} aria-label="Maximum" />
          </span>
        </label>
        <label>Chance % <input type="number" min="0.01" max="100" step="any" bind:value={chance} /></label>
      </div>
      <small class="muted">More options (Fortune, Looting, replace drops) are in the Drops tab.</small>
    {/if}

    {#if error}<p class="error">{error}</p>{/if}
    <div class="actions">
      <button type="button" class="primary" disabled={busy} onclick={create}>Create</button>
      <button type="button" onclick={onclose}>Cancel</button>
    </div>
  </div>
</div>
