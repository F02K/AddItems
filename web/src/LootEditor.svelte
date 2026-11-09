<script>
  import Chips from './Chips.svelte';
  import Slot from './Slot.svelte';
  import TargetPicker from './TargetPicker.svelte';
  import VanillaIcon from './VanillaIcon.svelte';
  import { refLabel, parseRef } from './lib/refs.js';
  import { TRIGGERS, targetLabel, targetIcon } from './lib/loot.js';

  let { initial, isNew, meta, items, existingIds, status, statusError, onsave, ondelete } = $props();

  // svelte-ignore state_referenced_locally
  let r = $state($state.snapshot(initial));
  let picking = $state(false);

  const triggerOptions = TRIGGERS.map((t) => ({ value: t.value, label: t.label, hint: t.hint }));
  const triggerInfo = $derived(TRIGGERS.find((t) => t.value === r.trigger));

  const customBlockTargets = $derived(items.filter((i) => i.block?.placeable).map((i) => `${meta.customNamespace}:${i.id}`));

  /** Choices for the target picker: the "any" wildcard first, then tags (blocks only), then everything else. */
  const options = $derived(
    r.trigger === 'BLOCK'
      ? ['*', ...customBlockTargets, ...meta.blockTags, ...meta.blockMaterials.map((m) => `minecraft:${m.toLowerCase()}`)]
      : r.trigger === 'MOB'
        ? ['*', ...meta.entityTypes]
        : ['*', ...meta.lootTables]
  );
  const levelName = $derived(r.trigger === 'BLOCK' ? 'Fortune' : r.trigger === 'MOB' ? 'Looting' : '');

  function setTrigger(trigger) {
    if (trigger !== r.trigger) r.targets = []; // targets of one kind make no sense for another
    r.trigger = trigger;
    if (trigger === 'CHEST') r.replaceVanilla = false;
  }

  function toggleTarget(target) {
    if (target === '*') {
      r.targets = r.targets.includes('*') ? [] : ['*'];
      return;
    }
    const without = r.targets.filter((t) => t !== '*');
    r.targets = without.includes(target) ? without.filter((t) => t !== target) : [...without, target];
  }

  const newDrop = () => ({ item: '', min: 1, max: 1, chance: 100, chanceBonusPerLevel: 0, amountBonusPerLevel: 0 });
  const addDrop = () => (r.drops = [...r.drops, newDrop()]);
  const removeDrop = (i) => (r.drops = r.drops.filter((_, k) => k !== i));
  const keepRange = (d) => { if (d.max < d.min) d.max = d.min; };

  const ready = $derived(
    r.targets.length > 0 && r.drops.length > 0 &&
      r.drops.every((d) => d.item && d.min >= 1 && d.max >= d.min && d.chance > 0 && d.chance <= 100)
  );

  function submit(event) {
    event.preventDefault();
    onsave($state.snapshot(r));
  }
</script>

<form class="editor" onsubmit={submit}>
  <div class="cols">
    <div class="stack">
      <section class="card">
        <h3>1 · When?</h3>
        <Chips options={triggerOptions} value={r.trigger} onchange={setTrigger} label="Trigger" />
        {#if triggerInfo}<small class="muted">{triggerInfo.hint}</small>{/if}
      </section>

      <section class="card">
        <h3>2 · Which {r.trigger === 'BLOCK' ? 'blocks' : r.trigger === 'MOB' ? 'mobs' : 'chests'}?</h3>
        <div class="targets">
          {#each r.targets as t (t)}
            {@const icon = targetIcon(r.trigger, t)}
            <span class="target">
              {#if icon}<VanillaIcon name={icon} size={18} text={false} />{/if}
              {targetLabel(r.trigger, t, items, meta.customNamespace)}
              <button type="button" aria-label="Remove" onclick={() => toggleTarget(t)}>×</button>
            </span>
          {:else}
            <span class="muted">Nothing selected yet.</span>
          {/each}
          <button type="button" class="add" onclick={() => (picking = true)}>+ Add</button>
        </div>
      </section>

      <section class="card">
        <h3>3 · What drops?</h3>
        {#each r.drops as d, i}
          <div class="drop">
            <Slot value={d.item} {items} {meta} allowTags={false} label="Drop item" onchange={(ref) => (d.item = ref)} />
            <div class="drop-fields">
              <label>Amount
                <span class="range">
                  <input type="number" min="1" max="64" bind:value={d.min} oninput={() => keepRange(d)} aria-label="Minimum" />
                  <span>to</span>
                  <input type="number" min="1" max="64" bind:value={d.max} aria-label="Maximum" />
                </span>
              </label>
              <label>Chance %
                <input type="number" min="0.01" max="100" step="any" bind:value={d.chance} />
              </label>
              {#if levelName}
                <label>+ chance per {levelName} level
                  <input type="number" min="0" max="100" step="0.5" bind:value={d.chanceBonusPerLevel} />
                </label>
                <label>+ amount per {levelName} level
                  <input type="number" min="0" max="64" bind:value={d.amountBonusPerLevel} />
                </label>
              {/if}
            </div>
            <button type="button" class="danger" aria-label="Remove drop" onclick={() => removeDrop(i)}>×</button>
          </div>
        {/each}
        <button type="button" class="add" onclick={addDrop}>+ Add drop</button>
        <small class="muted">Each drop is rolled on its own: chance first, then a random amount between the two numbers.</small>
      </section>

      <section class="card">
        <h3>4 · Options</h3>
        {#if r.trigger !== 'CHEST'}
          <label class="check"><input type="checkbox" bind:checked={r.replaceVanilla} /> Replace the normal drops (instead of adding to them)</label>
        {/if}
        {#if r.trigger === 'BLOCK'}
          <label class="check"><input type="checkbox" bind:checked={r.requireCorrectTool} /> Only with the right tool (like normal drops)</label>
          <label class="check"><input type="checkbox" bind:checked={r.skipWithSilkTouch} /> No extra drops with Silk Touch</label>
          <small class="muted">Creative mode never gives drops. Explosions and pistons are not covered.</small>
        {:else if r.trigger === 'MOB'}
          <label class="check"><input type="checkbox" bind:checked={r.requirePlayerKill} /> Only when a player killed it</label>
          <label class="check"><input type="checkbox" bind:checked={r.ignoreSpawnerMobs} /> Not for mobs from monster spawners</label>
        {:else}
          <small class="muted">Items are added to the chest's loot before it is filled.</small>
        {/if}
      </section>
    </div>

    <aside class="side">
      <div class="card sticky">
        <h3>Save</h3>
        {#if !isNew}<small class="muted">ID: {r.id}</small>{/if}
        <p class="muted summary">
          {#if r.targets.length && r.drops.some((d) => d.item)}
            {triggerInfo?.label}: {r.targets.slice(0, 3).map((t) => targetLabel(r.trigger, t, items, meta.customNamespace)).join(', ')}{r.targets.length > 3 ? ` +${r.targets.length - 3}` : ''}
            → {r.drops.filter((d) => d.item).map((d) => refLabel(d.item, items, meta.customNamespace)).join(', ')}
          {:else}Choose targets and at least one drop.{/if}
        </p>
        <div class="actions">
          <button class="primary" type="submit" disabled={!ready}>Save</button>
          {#if !isNew}<button type="button" class="danger" onclick={ondelete}>Delete</button>{/if}
        </div>
        {#if status}<p class:error={statusError} class="status">{status}</p>{/if}
        <small class="muted">Rules are active on the server immediately after saving.</small>
      </div>
    </aside>
  </div>
</form>

{#if picking}
  <TargetPicker trigger={r.trigger} {options} {items} ns={meta.customNamespace} selected={r.targets} ontoggle={toggleTarget} onclose={() => (picking = false)} />
{/if}
