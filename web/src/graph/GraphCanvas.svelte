<script>
  import { tick, untrack } from 'svelte';
  import { SvelteFlow, Background, Controls, MiniMap, MarkerType, useSvelteFlow } from '@xyflow/svelte';
  import '@xyflow/svelte/dist/style.css';
  import { api } from '../api.js';
  import Chips from '../Chips.svelte';
  import TargetPicker from '../TargetPicker.svelte';
  import ConnectDialog from './ConnectDialog.svelte';
  import DetailPanel from './DetailPanel.svelte';
  import ItemNode from './ItemNode.svelte';
  import VanillaNode from './VanillaNode.svelte';
  import RecipeNode from './RecipeNode.svelte';
  import LootNode from './LootNode.svelte';
  import SourceNode from './SourceNode.svelte';
  import { buildGraph, related } from '../lib/graph.js';
  import { detailsFor, refNodeId } from '../lib/details.js';
  import { autoLayout } from '../lib/layout.js';
  import { vanillaName } from '../lib/vanilla.svelte.js';
  import { targetLabel } from '../lib/loot.js';
  import { parseRef } from '../lib/refs.js';

  let { meta, items, recipes, loot, layout, onopen, onreload } = $props();

  const ns = $derived(meta.customNamespace);
  const nodeTypes = { item: ItemNode, vanilla: VanillaNode, tag: VanillaNode, recipe: RecipeNode, loot: LootNode, source: SourceNode };
  const COLORS = { item: '#6366f1', vanilla: '#78716c', tag: '#a8a29e', recipe: '#d97706', loot: '#16a34a', source: '#0891b2' };

  const DIRECTIONS = [
    { value: 'LR', label: 'Left → Right', hint: 'Inputs on the left, results on the right' },
    { value: 'RL', label: 'Right → Left', hint: 'Inputs on the right, results on the left' },
    { value: 'TB', label: 'Top ↓ Bottom', hint: 'Inputs on top, results at the bottom' },
    { value: 'BT', label: 'Bottom ↑ Top', hint: 'Inputs at the bottom, results on top' }
  ];

  const { fitView, getViewport } = useSvelteFlow();

  let nodes = $state.raw([]);
  let edges = $state.raw([]);

  // svelte-ignore state_referenced_locally
  let direction = $state(layout.direction ?? 'LR');
  // Positions the team dragged nodes to (plain object on purpose: changing it must not re-run the layout effect).
  // svelte-ignore state_referenced_locally
  let positions = { ...(layout.positions ?? {}) };
  let layoutVersion = $state(0);

  let showVanilla = $state(true);
  let allRecipes = $state(false);
  let allLoot = $state(false);
  let selectedId = $state(null);
  let search = $state('');
  let transient = $state.raw([]); // source nodes added by hand that are not part of a drop rule yet
  let notice = $state('');
  let noticeError = $state(false);
  let dialog = $state(null);
  let sourceMenu = $state(false);
  let picker = $state(null);

  // ---- graph model + layout -----------------------------------------------------------------

  const graph = $derived.by(() => {
    const g = buildGraph({ items, recipes, loot, ns }, { showVanilla, allRecipes, allLoot });
    const have = new Set(g.nodes.map((n) => n.id));
    return { nodes: [...g.nodes, ...transient.filter((t) => !have.has(t.id))], edges: g.edges };
  });
  const auto = $derived(autoLayout(graph.nodes, graph.edges, direction));
  const counts = $derived({
    items: graph.nodes.filter((n) => n.type === 'item' && !n.data.missing).length,
    recipes: graph.nodes.filter((n) => n.type === 'recipe').length,
    rules: graph.nodes.filter((n) => n.type === 'loot').length
  });

  const labelOf = (n) =>
    [n.data.name, n.data.label, vanillaName(n.data.name), ...(n.data.targets ?? []).map((t) => targetLabel(n.data.trigger, t, items, ns))]
      .filter(Boolean).join(' ').toLowerCase();

  /** Which nodes/edges stay bright: everything connected to the selection, or whatever matches the search. */
  const focus = $derived.by(() => {
    if (selectedId && graph.nodes.some((n) => n.id === selectedId)) {
      const r = related(selectedId, graph.edges);
      return { nodes: r.nodes, edges: r.edges };
    }
    const q = search.trim().toLowerCase();
    if (q) {
      const hit = new Set(graph.nodes.filter((n) => labelOf(n).includes(q)).map((n) => n.id));
      return { nodes: hit, edges: new Set(graph.edges.filter((e) => hit.has(e.source) && hit.has(e.target)).map((e) => e.id)) };
    }
    return null;
  });

  /**
   * Keeps the picture stable when the graph changes: nodes already on screen stay where they are, and a new node is
   * placed next to the nodes it is connected to (before its inputs / after its outputs) instead of reshuffling everything.
   */
  let skipPin = false; // set by relayout(): the next layout pass must use the fresh automatic positions
  function settlePositions() {
    if (skipPin) {
      skipPin = false;
      return;
    }
    for (const n of untrack(() => nodes)) {
      if (!positions[n.id]) positions[n.id] = { x: Math.round(n.position.x), y: Math.round(n.position.y) };
    }
    const horizontal = direction === 'LR' || direction === 'RL';
    const sign = direction === 'LR' || direction === 'TB' ? 1 : -1;
    const along = horizontal ? 'x' : 'y';
    const across = horizontal ? 'y' : 'x';
    const gap = horizontal ? 260 : 130;

    for (const n of graph.nodes) {
      if (positions[n.id]) continue;
      const before = graph.edges.filter((e) => e.target === n.id && positions[e.source]).map((e) => positions[e.source]);
      const after = graph.edges.filter((e) => e.source === n.id && positions[e.target]).map((e) => positions[e.target]);
      const known = [...before, ...after];
      if (!known.length) continue; // nothing to anchor to: the automatic layout position is used
      const mean = known.reduce((sum, p) => sum + p[across], 0) / known.length;
      const base = before.length
        ? (sign > 0 ? Math.max(...before.map((p) => p[along])) + gap : Math.min(...before.map((p) => p[along])) - gap)
        : (sign > 0 ? Math.min(...after.map((p) => p[along])) - gap : Math.max(...after.map((p) => p[along])) + gap);
      const spot = { [along]: Math.round(base), [across]: Math.round(mean) };
      const taken = () => Object.values(positions).some((p) => Math.abs(p.x - spot.x) < 200 && Math.abs(p.y - spot.y) < 70);
      for (let i = 0; i < 40 && taken(); i++) spot[across] += horizontal ? 80 : 210;
      positions[n.id] = spot;
    }
  }

  let firstFit = true;
  $effect(() => {
    layoutVersion; // re-run after a relayout
    const dim = focus;
    settlePositions();
    nodes = graph.nodes.map((n) => ({
      id: n.id,
      type: n.type,
      position: positions[n.id] ?? auto[n.id] ?? { x: 0, y: 0 },
      data: { ...n.data, ns, direction, open: onopen },
      selected: n.id === selectedId,
      class: dim && !dim.nodes.has(n.id) ? 'dim' : ''
    }));
    edges = graph.edges.map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
      label: e.label || undefined,
      animated: e.kind === 'drop',
      class: `edge-${e.kind}${dim && !dim.edges.has(e.id) ? ' dim' : ''}`,
      markerEnd: { type: MarkerType.ArrowClosed }
    }));
    if (firstFit && nodes.length) {
      firstFit = false;
      tick().then(() => setTimeout(() => fitView({ padding: 0.2, duration: 0 }), 60));
    }
  });

  // ---- layout persistence -------------------------------------------------------------------

  let saveTimer;
  function saveLayout() {
    clearTimeout(saveTimer);
    saveTimer = setTimeout(() => {
      const ids = new Set(graph.nodes.map((n) => n.id));
      const kept = Object.fromEntries(Object.entries(positions).filter(([id]) => ids.has(id)));
      api.saveGraphLayout({ direction, positions: kept }).catch((e) => flash(`Could not save the layout: ${e.message}`, true));
    }, 500);
  }

  async function relayout() {
    positions = {};
    skipPin = true;
    layoutVersion += 1;
    saveLayout();
    await tick();
    setTimeout(() => fitView({ padding: 0.2, duration: 200 }), 60);
  }

  function setDirection(value) {
    direction = value;
    relayout();
  }

  function onDragStop({ nodes: moved, targetNode }) {
    for (const n of moved?.length ? moved : [targetNode]) {
      if (n) positions[n.id] = { x: Math.round(n.position.x), y: Math.round(n.position.y) };
    }
    saveLayout();
  }

  // ---- interaction --------------------------------------------------------------------------

  function flash(message, error = false) {
    notice = message;
    noticeError = error;
    clearTimeout(flash.timer);
    flash.timer = setTimeout(() => (notice = ''), 6000);
  }

  const nodeById = (id) => graph.nodes.find((n) => n.id === id);

  function validConnection(c) {
    const from = nodeById(c.source);
    const to = nodeById(c.target);
    if (!from || !to || from.id === to.id) return false;
    const input = ['item', 'vanilla', 'tag'].includes(from.type);
    if (to.type === 'item') return !to.data.missing && (input || from.type === 'source');
    if (to.type === 'recipe') return input;
    return false;
  }

  function connect(c) {
    const from = nodeById(c.source);
    const to = nodeById(c.target);
    if (!from || !to) return;
    if (to.type === 'recipe') return addIngredient(from, to);
    dialog = { mode: from.type === 'source' ? 'drop' : 'recipe', from, to };
  }

  /** Dropping an item onto an existing recipe adds it as an ingredient (only shapeless recipes have free slots). */
  async function addIngredient(from, recipeNode) {
    const recipe = recipes.find((r) => r.id === recipeNode.data.recipeId);
    const ref = from.type === 'item' ? `${ns}:${from.data.itemId}` : from.type === 'tag' ? `#minecraft:${from.data.name}` : `minecraft:${from.data.name}`;
    if (!recipe || recipe.type !== 'SHAPELESS') {
      flash('Only shapeless crafting recipes can take another ingredient here. Open the recipe to change its layout.', true);
      return;
    }
    const grid = Array.from({ length: 9 }, (_, i) => recipe.grid?.[i] ?? '');
    const free = grid.indexOf('');
    if (free < 0) return flash('This recipe already has 9 ingredients.', true);
    grid[free] = ref;
    try {
      await api.saveRecipe({ ...recipe, grid, problem: undefined });
      flash('Ingredient added.');
      onreload();
    } catch (e) {
      flash(e.message, true);
    }
  }

  function created(message) {
    dialog = null;
    flash(message);
    onreload();
  }

  // ---- detail panel under the graph ----------------------------------------------------------

  /** How the selected node is made / what it drops, drawn like Minecraft's screens (null when nothing is selected). */
  const details = $derived(selectedId ? detailsFor(selectedId, { recipes, loot, ns }) : null);

  /** Selects the node for an item reference and brings it into view (used by slots and chips in the panel). */
  function focusRef(ref) {
    const id = refNodeId(ref, ns);
    if (!graph.nodes.some((n) => n.id === id)) {
      flash('That item is not in the graph right now (vanilla items may be hidden).', true);
      return;
    }
    selectedId = id;
    tick().then(() => fitView({ nodes: [{ id }], duration: 300, maxZoom: 1.1, padding: 0.8 }));
  }

  // ---- manual source nodes ------------------------------------------------------------------

  const sourceOptions = $derived.by(() => {
    if (!picker) return [];
    if (picker.trigger === 'BLOCK') return ['*', ...meta.blockTags, ...meta.blockMaterials.map((m) => `minecraft:${m.toLowerCase()}`)];
    if (picker.trigger === 'MOB') return ['*', ...meta.entityTypes];
    return ['*', ...meta.lootTables];
  });

  function togglePicked(target) {
    picker.picked = picker.picked.includes(target) ? picker.picked.filter((t) => t !== target) : [...picker.picked, target];
  }

  function finishPicker() {
    const { trigger, picked } = picker;
    picker = null;
    if (!picked.length) return;
    const vp = getViewport();
    const added = picked.map((target, i) => {
      const id = `source:${trigger}:${target}`;
      positions[id] = { x: Math.round((-vp.x + 120) / vp.zoom), y: Math.round((-vp.y + 120 + i * 70) / vp.zoom) };
      return { id, type: 'source', data: { trigger, targets: [target], count: 1, transient: true } };
    });
    transient = [...transient.filter((t) => !added.some((a) => a.id === t.id)), ...added];
    layoutVersion += 1;
    flash('Source added. Drag from its handle to an item to create a drop rule.');
    saveLayout();
  }

  const openPicker = (trigger) => { sourceMenu = false; picker = { trigger, picked: [] }; };
</script>

<div class="graph">
  <div class="graph-toolbar">
    <Chips options={DIRECTIONS} value={direction} onchange={setDirection} label="Direction" />
    <span class="sep"></span>
    <label class="check"><input type="checkbox" bind:checked={showVanilla} /> Vanilla items</label>
    <label class="check" title="Also show recipes that do not involve a custom item"><input type="checkbox" bind:checked={allRecipes} /> All recipes</label>
    <label class="check" title="Also show drop rules that do not involve a custom item"><input type="checkbox" bind:checked={allLoot} /> All drops</label>
    <span class="sep"></span>
    <input class="graph-search" type="search" placeholder="Find a node..." bind:value={search} />
    <button type="button" onclick={relayout}>Auto layout</button>
    <span class="menu">
      <button type="button" onclick={() => (sourceMenu = !sourceMenu)}>+ Source</button>
      {#if sourceMenu}
        <span class="menu-list">
          <button type="button" onclick={() => openPicker('MOB')}>Mob killed</button>
          <button type="button" onclick={() => openPicker('BLOCK')}>Block broken</button>
          <button type="button" onclick={() => openPicker('CHEST')}>Chest loot</button>
        </span>
      {/if}
    </span>
    <span class="spacer"></span>
    <span class="muted">{counts.items} items · {counts.recipes} recipes · {counts.rules} drop rules</span>
  </div>

  <div class="graph-canvas">
    <SvelteFlow bind:nodes bind:edges {nodeTypes} minZoom={0.1} maxZoom={2} colorMode="system" deleteKey={null}
                isValidConnection={validConnection} onbeforeconnect={(c) => { connect(c); return false; }}
                onnodeclick={({ node }) => (selectedId = selectedId === node.id ? null : node.id)}
                onpaneclick={() => (selectedId = null)} onnodedragstop={onDragStop}>
      <Background gap={24} />
      <Controls showLock={false} />
      <MiniMap pannable zoomable nodeColor={(n) => COLORS[n.type] ?? '#999'} />
    </SvelteFlow>

    {#if notice}<div class="graph-notice" class:error={noticeError}>{notice}</div>{/if}
    {#if !nodes.length}
      <div class="graph-empty">
        <h2>Nothing to connect yet</h2>
        <p class="muted">Create items, recipes and drop rules. They show up here as soon as they exist.</p>
      </div>
    {/if}

    <div class="graph-legend">
      <span><i style="background:{COLORS.item}"></i>Item</span>
      <span><i style="background:{COLORS.recipe}"></i>Recipe</span>
      <span><i style="background:{COLORS.loot}"></i>Drop rule</span>
      <span><i style="background:{COLORS.source}"></i>Source</span>
      <span><i style="background:{COLORS.vanilla}"></i>Vanilla</span>
    </div>
  </div>

  {#if details}
    <DetailPanel {details} {items} {meta} onclose={() => (selectedId = null)} onfocus={focusRef} {onopen} />
  {/if}
</div>

{#if dialog}
  <ConnectDialog {...dialog} {meta} {items} {recipes} {loot} oncreated={created} onclose={() => (dialog = null)} />
{/if}
{#if picker}
  <TargetPicker trigger={picker.trigger} options={sourceOptions} {items} {ns} selected={picker.picked}
                ontoggle={togglePicked} onclose={finishPicker} />
{/if}
