<script>
  import { onMount } from 'svelte';
  import { api } from './api.js';
  import LootEditor from './LootEditor.svelte';
  import { refLabel } from './lib/refs.js';
  import { TRIGGERS, targetLabel } from './lib/loot.js';

  let { meta, items, jump = null } = $props();

  let rules = $state([]);
  let current = $state(null);
  let isNew = $state(false);
  let editorKey = $state(0);
  let status = $state('');
  let statusError = $state(false);
  let filter = $state('');

  const say = (m, e = false) => { status = m; statusError = e; };
  const triggerLabel = (t) => TRIGGERS.find((x) => x.value === t)?.label ?? t;

  const summary = (r) => {
    const target = r.targets?.length ? targetLabel(r.trigger, r.targets[0], items, meta.customNamespace) + (r.targets.length > 1 ? ` +${r.targets.length - 1}` : '') : r.id;
    const drop = r.drops?.[0] ? refLabel(r.drops[0].item, items, meta.customNamespace) : '';
    return { title: target, sub: `${triggerLabel(r.trigger)} → ${drop}${r.drops?.length > 1 ? ` +${r.drops.length - 1}` : ''}` };
  };

  const visible = $derived(rules.filter((r) => (r.id + ' ' + summary(r).title + ' ' + summary(r).sub).toLowerCase().includes(filter.toLowerCase())));

  async function refresh() {
    rules = await api.loot();
  }

  onMount(async () => {
    try {
      await refresh();
      // the graph can ask for a specific entry to be opened right away
      const wanted = jump ? rules.find((x) => x.id === jump.id) : null;
      if (wanted) select(wanted);
    } catch (e) {
      say(e.message, true);
    }
  });

  function blank() {
    return {
      id: '', trigger: 'MOB', targets: [], drops: [{ item: '', min: 1, max: 1, chance: 100, chanceBonusPerLevel: 0, amountBonusPerLevel: 0 }],
      replaceVanilla: false, requireCorrectTool: true, skipWithSilkTouch: true, requirePlayerKill: true, ignoreSpawnerMobs: false
    };
  }

  function open(def, fresh) {
    current = def;
    isNew = fresh;
    editorKey += 1;
    say('');
  }

  const createNew = () => open(blank(), true);
  const select = (r) => open({ ...blank(), ...$state.snapshot(r) }, false);

  async function save(def) {
    try {
      const saved = await api.saveLoot(def);
      await refresh();
      select(rules.find((x) => x.id === saved.id) ?? saved);
      if (saved.problem) say(`Saved, but not active: ${saved.problem}`, true);
      else say('Saved and active on the server.');
    } catch (e) {
      say(e.message, true);
    }
  }

  async function remove() {
    if (!confirm(`Delete loot rule ${current.id}?`)) return;
    try {
      await api.deleteLoot(current.id);
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
    <button class="primary wide" onclick={createNew}>+ New drop rule</button>
    <input class="search" placeholder="Search rules..." bind:value={filter} />
    <ul>
      {#each visible as r (r.id)}
        {@const s = summary(r)}
        <li>
          <button class:active={current?.id === r.id && !isNew} onclick={() => select(r)}>
            <span class="label">
              <span>{s.title}{r.problem ? ' ⚠' : ''}</span>
              <small>{s.sub}{r.problem ? ' · not active' : ''}</small>
            </span>
          </button>
        </li>
      {:else}
        <li class="muted pad">{rules.length ? 'No match.' : 'No drop rules yet.'}</li>
      {/each}
    </ul>
  </nav>

  <main>
    {#if current}
      {#if current.problem}<div class="banner error">Not active on the server: {current.problem}</div>{/if}
      {#key editorKey}
        <LootEditor initial={current} {isNew} {meta} {items} existingIds={rules.map((r) => r.id)}
                    {status} {statusError} onsave={save} ondelete={remove} />
      {/key}
    {:else}
      <div class="empty">
        <h2>Drops &amp; loot</h2>
        <p class="muted">Add extra drops to blocks and mobs, or put items into generated chests.</p>
        {#if status}<p class:error={statusError}>{status}</p>{/if}
      </div>
    {/if}
  </main>
</div>
