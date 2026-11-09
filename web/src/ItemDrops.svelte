<script>
  import { onMount } from 'svelte';
  import { api } from './api.js';
  import LootEditor from './LootEditor.svelte';
  import { refLabel } from './lib/refs.js';
  import { TRIGGERS, targetLabel } from './lib/loot.js';
  import { blankLootRule, blankDrop } from './lib/blank.js';
  import { lootDropping, lootFromBlock } from './lib/details.js';

  // The "Drops" tab of an item: the rules that make it drop (from mobs, blocks, chests) and, for a placeable block,
  // what it drops itself when broken. Edited inline.
  let { item, meta, items } = $props();

  const ref = $derived(`${meta.customNamespace}:${item.id}`);
  const isBlock = $derived(item.type === 'BLOCK' && item.block?.placeable);

  let rules = $state([]);
  let editing = $state(null); // { def, isNew }
  let editorKey = $state(0);
  let status = $state('');
  let statusError = $state(false);

  const say = (m, e = false) => { status = m; statusError = e; };
  const dropping = $derived(lootDropping(ref, rules));
  const breaking = $derived(lootFromBlock(ref, rules).filter((r) => !dropping.includes(r)));
  const triggerLabel = (t) => TRIGGERS.find((x) => x.value === t)?.label ?? t;
  const label = (r) => refLabel(r, items, meta.customNamespace) || r;
  const plainName = $derived(item.displayName ? item.displayName.replace(/<[^>]*>/g, '') : item.id);

  async function refresh() {
    rules = await api.loot();
  }

  onMount(() => refresh().catch((e) => say(e.message, true)));

  function open(def, isNew) {
    editing = { def, isNew };
    editorKey += 1;
    say('');
  }

  const openExisting = (r) => open({ ...blankLootRule(), ...$state.snapshot(r) }, false);
  const createDrop = () => open({ ...blankLootRule(), id: `${item.id}_drops`, drops: [{ ...blankDrop(), item: ref }] }, true);
  const createBreak = () => open({ ...blankLootRule(), id: `${item.id}_break`, trigger: 'BLOCK', targets: [ref], drops: [] }, true);

  const title = (r) => {
    const target = r.targets?.length
      ? targetLabel(r.trigger, r.targets[0], items, meta.customNamespace) + (r.targets.length > 1 ? ` +${r.targets.length - 1}` : '')
      : r.id;
    return `${triggerLabel(r.trigger)}: ${target}`;
  };

  async function save(def) {
    try {
      const saved = editing.isNew ? await api.createLoot(def) : await api.saveLoot(def);
      await refresh();
      const fresh = rules.find((r) => r.id === saved.id);
      if (fresh) openExisting(fresh);
      if (saved.problem) say(`Saved, but not active: ${saved.problem}`, true);
      else say('Saved and active on the server.');
    } catch (e) {
      say(e.message, true);
    }
  }

  async function remove() {
    if (!confirm(`Delete drop rule ${editing.def.id}?`)) return;
    try {
      await api.deleteLoot(editing.def.id);
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
    <h4>Where {plainName} drops</h4>
    {#each dropping as r (r.id)}
      <button type="button" class="rule" class:on={editing?.def.id === r.id && !editing.isNew} onclick={() => openExisting(r)}>
        <strong>{title(r)}{r.problem ? ' ⚠' : ''}</strong>
        <small>{r.drops.map((d) => label(d.item)).join(', ')}</small>
      </button>
    {:else}
      <span class="muted">Does not drop anywhere yet.</span>
    {/each}
    <button type="button" class="add" onclick={createDrop}>+ New drop rule</button>
  </div>

  {#if isBlock}
    <div class="rule-list">
      <h4>What {plainName} drops when broken</h4>
      {#each breaking as r (r.id)}
        <button type="button" class="rule" class:on={editing?.def.id === r.id && !editing.isNew} onclick={() => openExisting(r)}>
          <strong>{r.drops.map((d) => label(d.item)).join(', ') || r.id}</strong>
          <small>{r.replaceVanilla ? 'replaces the normal drop' : 'adds to the normal drop'}</small>
        </button>
      {:else}
        <span class="muted">Only the normal drop (see "Drops itself" in the Item tab).</span>
      {/each}
      <button type="button" class="add" onclick={createBreak}>+ New break drop</button>
    </div>
  {/if}

  {#if editing}
    {#key editorKey}
      <LootEditor initial={editing.def} isNew={editing.isNew} {meta} {items} existingIds={rules.map((r) => r.id)}
                  {status} statusError={statusError} onsave={save} ondelete={remove} />
    {/key}
  {:else if status}
    <p class:error={statusError} class="status">{status}</p>
  {/if}
</div>
