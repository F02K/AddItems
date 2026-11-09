<script>
  import { onMount } from 'svelte';
  import { api } from './api.js';
  import ItemEditor from './ItemEditor.svelte';
  import ItemRecipes from './ItemRecipes.svelte';
  import ItemDrops from './ItemDrops.svelte';
  import ArmorSetPanel from './ArmorSetPanel.svelte';
  import RuleDialog from './RuleDialog.svelte';
  import { setFromItem, groupItems } from './lib/armorset.js';
  import GraphView from './graph/GraphView.svelte';
  import VanillaIcon from './VanillaIcon.svelte';
  import { vanilla, loadVanilla } from './lib/vanilla.svelte.js';

  let section = $state('items');
  let rule = $state(null); // a recipe or drop rule the graph asked to open: { kind, id }
  let graphKey = $state(0); // bumped when a rule changed, so the graph reloads
  let setPanel = $state(null); // the armor set being edited instead of an item: { def, isNew }
  let setKey = $state(0);
  let itemTab = $state('item'); // 'item' | 'recipes' | 'drops' for the selected item
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
  // the list: single items and armor sets (a set row with its pieces under it)
  const rows = $derived(groupItems(visible));
  let collapsed = $state(new Set()); // ids of armor sets whose pieces are hidden
  function toggleGroup(id) {
    const next = new Set(collapsed);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    collapsed = next;
  }

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
      armor: { set: '', slot: 'CHESTPLATE', tier: 'IRON', defense: 6, toughness: 0, knockbackResistance: 0, durability: 240, enchantability: 9, equipSound: 'item.armor.equip_iron' },
      block: { placeable: false, state: null, hardness: 1.5, preferredTool: 'NONE', requiresTool: false, dropSelf: true,
               textureMode: 'ALL', placeSound: '', breakSound: '' }
    };
  }

  function open(def, fresh, texture, slots = {}) {
    setPanel = null;
    itemTab = 'item';
    current = def;
    isNew = fresh;
    textureUrl = texture;
    slotUrls = slots;
    editorKey += 1;
    say('');
  }

  const createNew = () => open(blank(), true, null);

  /** Opens an item in the Items tab, or a recipe / drop rule in a dialog over the graph (used by the graph). */
  function navigate(kind, id) {
    if (kind === 'item') {
      section = 'items';
      const found = items.find((i) => i.id === id);
      if (found) select(found);
    } else {
      rule = { kind, id };
    }
  }

  async function openSet(id) {
    await guard(async () => {
      const found = (await api.armorSets()).find((s) => s.id === id);
      if (!found) return say(`Armor set ${id} not found.`, true);
      current = null;
      setPanel = { def: found, isNew: false };
      setKey += 1;
    });
  }

  /** The armor toggle was switched on: the item (saved or not) becomes the start of an armor set. */
  function toArmorSet(item, linked) {
    current = null;
    setPanel = { def: setFromItem(meta, item, linked), isNew: true };
    setKey += 1;
  }

  const openPiece = (id) => {
    const found = items.find((i) => i.id === id);
    if (found) select(found);
  };

  function select(item) {
    const def = { ...blank(), ...$state.snapshot(item) };
    def.food = { ...blank().food, ...item.food };
    def.tool = { ...blank().tool, ...item.tool };
    def.armor = { ...blank().armor, ...item.armor };
    def.block = { ...blank().block, ...item.block };
    const stamp = Date.now(); // bypass the browser cache after an upload
    const url = (slot) => `/api/items/${item.id}/texture?slot=${slot}&t=${stamp}`;
    open(def, false, item.hasTexture ? url('main') : null,
      item.type === 'BLOCK' ? { top: url('top'), bottom: url('bottom'), side: url('side') }
        : item.type === 'ARMOR' && item.hasWornTexture ? { worn: url('worn') } : {});
  }

  async function save(def, files) {
    await guard(async () => {
      const saved = await api.saveItem(def, isNew);
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
              onclick={() => (section = 'graph')}>Graph</button>
      <button role="tab" aria-selected={section === 'items'} class:on={section === 'items'}
              onclick={() => (section = 'items')}>Items</button>
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
  {#if status && !current && !setPanel}<div class="banner" class:error={statusError}>{status}</div>{/if}

  {#if section === 'graph'}
    {#key graphKey}<GraphView {meta} {items} onopen={navigate} />{/key}
    {#if rule}
      <RuleDialog kind={rule.kind} id={rule.id} {meta} {items} onclose={() => (rule = null)}
                  onsaved={() => (graphKey += 1)} />
    {/if}
  {:else}
  <div class="layout">
    <nav>
      <button class="primary wide" onclick={createNew}>+ New item</button>
      <input class="search" placeholder="Search items..." bind:value={filter} />
      <ul>
        {#each rows as row (row.kind === 'set' ? 'set:' + row.id : row.item.id)}
          {#if row.kind === 'set'}
            <li class="setrow">
              <div class="sethead">
                <button type="button" class="fold" aria-label={collapsed.has(row.id) ? 'Show pieces' : 'Hide pieces'}
                        aria-expanded={!collapsed.has(row.id)} onclick={() => toggleGroup(row.id)}>{collapsed.has(row.id) ? '▸' : '▾'}</button>
                <button type="button" class:active={setPanel && !setPanel.isNew && setPanel.def.id === row.id} onclick={() => openSet(row.id)}>
                  <span class="thumb"><VanillaIcon name="iron_chestplate" size={28} text={false} /></span>
                  <span class="label">
                    <span>{row.name}</span>
                    <small>armor set · {row.pieces.length} piece{row.pieces.length === 1 ? '' : 's'}</small>
                  </span>
                </button>
              </div>
              {#if !collapsed.has(row.id) || filter}
                <ul class="sub">
                  {#each row.pieces as item (item.id)}
                    <li>
                      <button class:active={current?.id === item.id && !isNew && !setPanel} onclick={() => select(item)}>
                        <span class="thumb">
                          {#if item.hasTexture}<img src={`/api/items/${item.id}/texture`} alt="" />
                          {:else}<VanillaIcon name={item.baseMaterial.toLowerCase()} size={28} text={false} />{/if}
                        </span>
                        <span class="label">
                          <span>{item.displayName ? item.displayName.replace(/<[^>]*>/g, '') : item.id}</span>
                          <small>{item.armor.slot.toLowerCase()}{item.hasTexture ? '' : ' · no texture'}</small>
                        </span>
                      </button>
                    </li>
                  {/each}
                </ul>
              {/if}
            </li>
          {:else}
            <li>
              <button class:active={current?.id === row.item.id && !isNew && !setPanel} onclick={() => select(row.item)}>
                <span class="thumb">
                  {#if row.item.hasTexture}<img src={`/api/items/${row.item.id}/texture`} alt="" />
                  {:else}<VanillaIcon name={row.item.baseMaterial.toLowerCase()} size={28} text={false} />{/if}
                </span>
                <span class="label">
                  <span>{row.item.displayName ? row.item.displayName.replace(/<[^>]*>/g, '') : row.item.id}</span>
                  <small>{row.item.type.toLowerCase()}{row.item.hasTexture ? '' : ' · no texture'}</small>
                </span>
              </button>
            </li>
          {/if}
        {:else}
          <li class="muted pad">{items.length ? 'No match.' : 'No items yet.'}</li>
        {/each}
      </ul>
    </nav>

    <main>
      {#if setPanel}
        {#key setKey}
          <ArmorSetPanel initial={setPanel.def} isNew={setPanel.isNew} {meta} existingIds={items.map((i) => i.id)} onchanged={refresh}
                         onopenitem={openPiece} ondeleted={() => (setPanel = null)} />
        {/key}
      {:else if current}
        <div class="itemtabs" role="tablist">
          <button type="button" role="tab" class:on={itemTab === 'item'} aria-selected={itemTab === 'item'} onclick={() => (itemTab = 'item')}>Item</button>
          <button type="button" role="tab" class:on={itemTab === 'recipes'} aria-selected={itemTab === 'recipes'} disabled={isNew}
                  title={isNew ? 'Save the item first' : ''} onclick={() => (itemTab = 'recipes')}>Recipes</button>
          <button type="button" role="tab" class:on={itemTab === 'drops'} aria-selected={itemTab === 'drops'} disabled={isNew}
                  title={isNew ? 'Save the item first' : ''} onclick={() => (itemTab = 'drops')}>Drops</button>
        </div>
        <!-- the item form stays mounted while another tab is open, so unsaved edits are not lost -->
        <div hidden={itemTab !== 'item'}>
          {#key editorKey}
            <ItemEditor initial={current} {isNew} {meta} existingIds={items.map((i) => i.id)} {textureUrl} {slotUrls} {status} statusError={statusError}
                        onsave={save} ondelete={remove} onopenset={openSet} onarmorset={toArmorSet} />
          {/key}
        </div>
        {#if !isNew && itemTab === 'recipes'}
          {#key current.id}<ItemRecipes item={current} {meta} {items} />{/key}
        {:else if !isNew && itemTab === 'drops'}
          {#key current.id}<ItemDrops item={current} {meta} {items} />{/key}
        {/if}
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
