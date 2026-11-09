<script>
  import { onMount } from 'svelte';
  import { api } from './api.js';
  import ItemEditor from './ItemEditor.svelte';
  import RecipesView from './RecipesView.svelte';
  import LootView from './LootView.svelte';
  import GraphView from './graph/GraphView.svelte';
  import VanillaIcon from './VanillaIcon.svelte';
  import { vanilla, loadVanilla } from './lib/vanilla.svelte.js';

  let section = $state('items');
  let jump = $state(null); // an entity the graph asked to open: { id }
  let user = $state(null);
  let loginNeeded = $state(false);
  let meta = $state(null);
  let items = $state([]);
  let current = $state(null); // definition handed to the editor
  let isNew = $state(false);
  let textureUrl = $state(null);
  let slotUrls = $state({});
  let editorKey = $state(0);
  let status = $state('');
  let statusError = $state(false);
  let pack = $state({ built: false });
  let filter = $state('');

  const visible = $derived(
    items.filter((i) => (i.displayName + ' ' + i.id).toLowerCase().includes(filter.toLowerCase()))
  );

  function say(message, error = false) {
    status = message;
    statusError = error;
  }

  async function guard(fn) {
    try {
      return await fn();
    } catch (e) {
      if (e.status === 401) loginNeeded = true;
      say(e.message, true);
    }
  }

  async function refresh() {
    items = await api.items();
    pack = await api.pack();
  }

  onMount(async () => {
    try {
      user = (await api.me()).user;
    } catch {
      loginNeeded = true;
      return;
    }
    loadVanilla();
    await guard(async () => {
      meta = await api.meta();
      await refresh();
    });
  });

  function blank() {
    return {
      id: '', type: 'GENERIC', baseMaterial: 'PAPER', displayName: '', lore: [],
      maxStackSize: 64, hasTexture: false,
      food: { nutrition: 4, saturation: 2.4, canAlwaysEat: false, eatSeconds: 1.6 },
      tool: { kind: 'PICKAXE', tier: 'IRON', harvestTier: 'IRON', miningSpeed: 6, durability: 250, enchantability: 14, damage: 4, attackSpeed: 1.2 },
      block: { placeable: false, state: null, hardness: 1.5, preferredTool: 'NONE', requiresTool: false, dropSelf: true,
               textureMode: 'ALL', rendering: 'SOLID', placeSound: '', breakSound: '' }
    };
  }

  function open(def, fresh, texture, slots = {}) {
    current = def;
    isNew = fresh;
    textureUrl = texture;
    slotUrls = slots;
    editorKey += 1;
    say('');
  }

  const createNew = () => open(blank(), true, null);

  /** Opens an item, recipe or drop rule in its own editor (used by the graph). */
  function navigate(kind, id) {
    jump = { id };
    section = kind === 'item' ? 'items' : kind === 'recipe' ? 'recipes' : 'loot';
    if (kind === 'item') {
      const found = items.find((i) => i.id === id);
      if (found) select(found);
    }
  }

  function select(item) {
    const def = { ...blank(), ...$state.snapshot(item) };
    def.food = { ...blank().food, ...item.food };
    def.tool = { ...blank().tool, ...item.tool };
    def.block = { ...blank().block, ...item.block };
    const stamp = Date.now(); // bypass the browser cache after an upload
    const url = (slot) => `/api/items/${item.id}/texture?slot=${slot}&t=${stamp}`;
    open(def, false, item.hasTexture ? url('main') : null,
      item.type === 'BLOCK' ? { top: url('top'), bottom: url('bottom'), side: url('side') } : {});
  }

  async function save(def, files) {
    await guard(async () => {
      const saved = await api.saveItem(def);
      for (const [slot, file] of Object.entries(files ?? {})) await api.uploadTexture(saved.id, file, slot);
      await refresh();
      const fresh = items.find((i) => i.id === saved.id);
      if (fresh) {
        select(fresh);
        say('Saved. Use "Build & send pack" to deliver texture changes.');
      }
    });
  }

  async function remove() {
    if (!confirm(`Delete ${current.id}?`)) return;
    await guard(async () => {
      await api.deleteItem(current.id);
      current = null;
      await refresh();
      say('Deleted.');
    });
  }

  async function build() {
    await guard(async () => {
      pack = await api.buildPack();
      if (pack.sendProblem) say(`Pack built, but NOT sent to players: ${pack.sendProblem}`, true);
      else say(`Pack built (${pack.size} bytes) and sent to online players.`);
    });
  }
</script>

{#if loginNeeded}
  <main class="center">
    <h1>AddItems</h1>
    <p>You are not logged in. Run <code>/additems login</code> in-game or in the console and open the link.</p>
  </main>
{:else if !meta}
  <main class="center"><p class="muted">Loading...</p></main>
{:else}
  <header>
    <strong>AddItems</strong>
    <span class="muted">Minecraft {meta.minecraftVersion}</span>
    <div class="tabs" role="tablist">
      <button role="tab" aria-selected={section === 'graph'} class:on={section === 'graph'}
              onclick={() => { jump = null; section = 'graph'; }}>Graph</button>
      <button role="tab" aria-selected={section === 'items'} class:on={section === 'items'}
              onclick={() => { jump = null; section = 'items'; }}>Items</button>
      <button role="tab" aria-selected={section === 'recipes'} class:on={section === 'recipes'}
              onclick={() => { jump = null; section = 'recipes'; }}>Recipes</button>
      <button role="tab" aria-selected={section === 'loot'} class:on={section === 'loot'}
              onclick={() => { jump = null; section = 'loot'; }}>Drops</button>
    </div>
    <span class="spacer"></span>
    <span class="muted">{pack.built ? `Pack ${pack.sha1?.slice(0, 8)}` : 'Pack not built'}</span>
    <button onclick={build}>Build &amp; send pack</button>
    <span class="muted">{user ?? ''}</span>
  </header>
  {#if vanilla.state === 'DOWNLOADING' || vanilla.state === 'MISSING'}
    <div class="banner">Downloading vanilla textures from Mojang (one time). Icons appear automatically when done.</div>
  {:else if vanilla.state === 'FAILED'}
    <div class="banner error">{vanilla.message} Icons are optional; everything else works.</div>
  {/if}
  {#if status && !current}<div class="banner" class:error={statusError}>{status}</div>{/if}

  {#if section === 'graph'}
    <GraphView {meta} {items} onopen={navigate} />
  {:else if section === 'recipes'}
    <RecipesView {meta} {items} {jump} />
  {:else if section === 'loot'}
    <LootView {meta} {items} {jump} />
  {:else}
  <div class="layout">
    <nav>
      <button class="primary wide" onclick={createNew}>+ New item</button>
      <input class="search" placeholder="Search items..." bind:value={filter} />
      <ul>
        {#each visible as item (item.id)}
          <li>
            <button class:active={current?.id === item.id && !isNew} onclick={() => select(item)}>
              <span class="thumb">
                {#if item.hasTexture}<img src={`/api/items/${item.id}/texture`} alt="" />
                {:else}<VanillaIcon name={item.baseMaterial.toLowerCase()} size={28} text={false} />{/if}
              </span>
              <span class="label">
                <span>{item.displayName ? item.displayName.replace(/<[^>]*>/g, '') : item.id}</span>
                <small>{item.type.toLowerCase()}{item.hasTexture ? '' : ' · no texture'}</small>
              </span>
            </button>
          </li>
        {:else}
          <li class="muted pad">{items.length ? 'No match.' : 'No items yet.'}</li>
        {/each}
      </ul>
    </nav>

    <main>
      {#if current}
        {#key editorKey}
          <ItemEditor initial={current} {isNew} {meta} {textureUrl} {slotUrls} {status} statusError={statusError}
                      onsave={save} ondelete={remove} />
        {/key}
      {:else}
        <div class="empty">
          <h2>Create your first item</h2>
          <p class="muted">Pick "+ New item" on the left, or select an existing item to edit it.</p>
        </div>
      {/if}
    </main>
  </div>
  {/if}
{/if}
