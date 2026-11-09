<script>
  import Chips from './Chips.svelte';
  import MaterialPicker from './MaterialPicker.svelte';
  import Tooltip from './Tooltip.svelte';
  import VanillaIcon from './VanillaIcon.svelte';
  import SoundInput from './SoundInput.svelte';
  import { soundFamilies, familyOf } from './lib/sounds.js';

  let { initial, isNew, meta, textureUrl, slotUrls = {}, status, statusError, onsave, ondelete } = $props();

  // The editor owns the form state; App re-creates it (via {#key}) whenever another item is selected.
  // svelte-ignore state_referenced_locally
  let item = $state($state.snapshot(initial));
  // svelte-ignore state_referenced_locally
  let loreText = $state((initial.lore ?? []).join('\n'));
  // Texture files chosen but not uploaded yet, and the image shown per slot (main, top, bottom, side).
  let pendingFiles = $state({});
  // svelte-ignore state_referenced_locally
  let previews = $state({ main: textureUrl, ...slotUrls });
  const preview = $derived(previews.main ?? previews.top ?? previews.side ?? null);

  const TYPE_INFO = {
    GENERIC: { label: 'Item', hint: 'Plain item (material, trinket, ...)' },
    FOOD: { label: 'Food', hint: 'Can be eaten' },
    TOOL: { label: 'Tool / weapon', hint: 'Sword, pickaxe, axe, shovel, hoe' },
    BLOCK: { label: 'Block', hint: 'Block-shaped item' }
  };
  const typeOptions = $derived(meta.types.map((t) => ({ value: t, label: TYPE_INFO[t]?.label ?? t, hint: TYPE_INFO[t]?.hint })));
  const kindOptions = $derived(meta.toolKinds.map((k) => ({ value: k, label: k[0] + k.slice(1).toLowerCase() })));
  const tierOptions = $derived([
    ...meta.toolTiers.map((t) => ({ value: t.id, label: t.label })),
    { value: 'CUSTOM', label: 'Custom' }
  ]);

  const known = $derived(new Set(meta.materials));
  const pickerOptions = $derived(
    item.type === 'TOOL' ? meta.materials.filter((m) => m.endsWith('_' + item.tool.kind))
      : item.type === 'FOOD' ? meta.foodMaterials
      : item.type === 'BLOCK' ? meta.blockMaterials
      : meta.materials
  );

  const DEFAULT_BASE = { GENERIC: 'PAPER', BLOCK: 'STONE', FOOD: 'BREAD', TOOL: 'IRON_PICKAXE' };
  const BASE_HINT = {
    TOOL: 'Decides the right-click behaviour (axe strips logs, hoe tills, shovel makes paths) and the look without a texture.',
    FOOD: 'Vanilla look until a texture is set.',
    BLOCK: 'Vanilla look until a texture is set.',
    GENERIC: 'Vanilla look until a texture is set.'
  };

  // ---- tool presets -------------------------------------------------------------------------

  /** Vanilla base item for a tier + kind, falling back to iron when this version lacks it. */
  function toolBase(tier, kind) {
    const wanted = meta.toolStats[kind]?.[tier]?.material;
    return wanted && known.has(wanted) ? wanted : meta.toolStats[kind].IRON.material;
  }

  function followBase(oldSuggestion, newSuggestion) {
    // Only move the base item along if the user has not picked a custom one.
    if (!item.baseMaterial || item.baseMaterial === oldSuggestion) item.baseMaterial = newSuggestion;
  }

  function setTier(tier) {
    const t = item.tool;
    const old = toolBase(t.tier === 'CUSTOM' ? 'IRON' : t.tier, t.kind);
    if (tier === 'CUSTOM') {
      t.tier = 'CUSTOM';
      return;
    }
    applyTier(tier, t.kind);
    followBase(old, toolBase(tier, t.kind));
  }

  function applyTier(tier, kind) {
    const base = meta.toolTiers.find((x) => x.id === tier);
    const stats = meta.toolStats[kind][tier];
    Object.assign(item.tool, {
      tier, kind, harvestTier: tier,
      durability: base.durability, miningSpeed: base.miningSpeed, enchantability: base.enchantability,
      damage: stats.damage, attackSpeed: stats.speed
    });
  }

  function setKind(kind) {
    const t = item.tool;
    const oldTier = t.tier === 'CUSTOM' ? 'IRON' : t.tier;
    const old = toolBase(oldTier, t.kind);
    if (t.tier === 'CUSTOM') {
      t.kind = kind;
    } else {
      applyTier(t.tier, kind);
    }
    followBase(old, toolBase(oldTier, kind));
  }

  const markCustom = () => (item.tool.tier = 'CUSTOM');

  // ---- food presets -------------------------------------------------------------------------

  let foodPreset = $state('');
  function applyFood(id) {
    foodPreset = id;
    const p = meta.foodPresets.find((f) => f.id === id);
    if (!p) return;
    Object.assign(item.food, { nutrition: p.nutrition, saturation: p.saturation, eatSeconds: p.eatSeconds });
    if (known.has(p.material)) item.baseMaterial = p.material;
  }

  // ---- type switching -----------------------------------------------------------------------

  function setType(type) {
    const previousDefault = DEFAULT_BASE[item.type];
    item.type = type;
    if (type === 'TOOL') {
      item.maxStackSize = 1;
      if (!item.baseMaterial || item.baseMaterial === previousDefault || !item.baseMaterial.endsWith('_' + item.tool.kind)) {
        if (item.tool.tier !== 'CUSTOM') applyTier(item.tool.tier, item.tool.kind);
        item.baseMaterial = toolBase(item.tool.tier === 'CUSTOM' ? 'IRON' : item.tool.tier, item.tool.kind);
      }
    } else {
      if (item.maxStackSize === 1) item.maxStackSize = 64;
      if (!item.baseMaterial || item.baseMaterial === previousDefault || item.baseMaterial.match(/_(SWORD|PICKAXE|AXE|SHOVEL|HOE)$/)) {
        item.baseMaterial = DEFAULT_BASE[type];
      }
    }
  }

  // ---- texture / save -----------------------------------------------------------------------

  function onFile(slot, event) {
    const file = event.target.files[0];
    if (!file) return;
    pendingFiles[slot] = file;
    previews[slot] = URL.createObjectURL(file);
  }

  function submit(event) {
    event.preventDefault();
    item.lore = loreText.split('\n').filter((l) => l.trim().length > 0);
    onsave($state.snapshot(item), $state.snapshot(pendingFiles));
  }

  // ---- placeable blocks ---------------------------------------------------------------------

  const TEXTURE_SLOTS = [
    ['main', 'All faces / fallback'], ['top', 'Top'], ['bottom', 'Bottom'], ['side', 'Sides']
  ];
  const slotsShown = $derived(
    item.type === 'BLOCK' && item.block.placeable && item.block.textureMode === 'SIDES' ? TEXTURE_SLOTS : TEXTURE_SLOTS.slice(0, 1)
  );
  const HARDNESS_PRESETS = [['Dirt', 0.5], ['Stone', 1.5], ['Iron ore', 3], ['Obsidian', 50]];
  const TOOL_OPTIONS = [
    { value: 'NONE', label: 'Any' }, { value: 'AXE', label: 'Axe' }, { value: 'PICKAXE', label: 'Pickaxe' },
    { value: 'SHOVEL', label: 'Shovel' }, { value: 'HOE', label: 'Hoe' }
  ];
  const textureModeOptions = [
    { value: 'ALL', label: 'One texture, all sides' }, { value: 'SIDES', label: 'Top, bottom and sides' }
  ];

  const RENDERINGS = [
    { value: 'SOLID', label: 'Solid', hint: 'Normal opaque block' },
    { value: 'CUTOUT', label: 'See-through', hint: 'Transparent pixels show what is behind (leaves, grates, vines)' },
    { value: 'TRANSLUCENT', label: 'Glass-like', hint: 'Partly transparent pixels (glass, ice)' }
  ];
  const RENDERING_HINT = {
    SOLID: 'Drawn as a normal solid cube.',
    CUTOUT: 'Transparent pixels of the texture are see-through and faces next to other blocks are never hidden, so a canopy of these blocks looks like leaves.',
    TRANSLUCENT: 'For textures with partly transparent pixels, like glass.'
  };

  // sounds: ids come from the server; a sound set fills the place and break sound with a matching pair
  const sounds = $derived(meta.sounds ?? []);
  const families = $derived(soundFamilies(sounds));
  const currentFamily = $derived(familyOf(item.block.placeSound, item.block.breakSound));
  function setSoundFamily(event) {
    const family = event.target.value;
    if (family === '') return;
    item.block.placeSound = family === '__none' ? '' : `block.${family}.place`;
    item.block.breakSound = family === '__none' ? '' : `block.${family}.break`;
  }

  function setPlaceable() {
    // Placeable blocks are note blocks in the world, so their item is a note block as well.
    if (item.block.placeable) item.baseMaterial = 'NOTE_BLOCK';
    else if (item.baseMaterial === 'NOTE_BLOCK') item.baseMaterial = DEFAULT_BASE.BLOCK;
  }

  const idValid = $derived(/^[a-z0-9_]{1,48}$/.test(item.id));
</script>

<form class="editor" onsubmit={submit}>
  <div class="cols">
    <div class="stack">
      <section class="card">
        <h3>1 · What is it?</h3>
        <Chips options={typeOptions} value={item.type} onchange={setType} label="Item type" />
        <div class="grid">
          <label>Name <small>MiniMessage, e.g. &lt;gold&gt;Ruby Pickaxe</small>
            <input bind:value={item.displayName} placeholder="<gold>Ruby Pickaxe" />
          </label>
          <label>ID <small>lowercase letters, digits, _</small>
            <input bind:value={item.id} disabled={!isNew} placeholder="ruby_pickaxe" required class:bad={item.id && !idValid} />
          </label>
        </div>
      </section>

      <section class="card">
        <h3>2 · Look</h3>
        {#each slotsShown as [slot, label] (slot)}
          <div class="texture">
            <div class="slot">
              {#if previews[slot]}
                <img src={previews[slot]} alt="texture" onerror={() => (previews[slot] = null)} />
              {:else if slot === 'main'}
                <VanillaIcon name={item.baseMaterial.toLowerCase()} size={56} text={false} />
              {/if}
            </div>
            <label class="grow">{slotsShown.length > 1 ? label : 'Texture'}
              <small>square PNG, up to 1024×1024 and 256 KB (16×16 is typical)</small>
              <input type="file" accept="image/png" onchange={(e) => onFile(slot, e)} />
            </label>
          </div>
        {/each}
        {#if item.type === 'BLOCK' && item.block.placeable}
          <small class="muted">Placeable blocks are note blocks, so the base item is fixed.</small>
        {:else}
          <label>Base item <small>vanilla item this is built on</small>
            <MaterialPicker bind:value={item.baseMaterial} options={pickerOptions} {known}
                            id={`bases-${item.type}-${item.tool.kind}`} hint={BASE_HINT[item.type]} />
          </label>
        {/if}
      </section>

      {#if item.type === 'TOOL'}
        <section class="card">
          <h3>3 · Tool</h3>
          <span class="lbl">Kind</span>
          <Chips options={kindOptions} value={item.tool.kind} onchange={setKind} label="Tool kind" />
          <span class="lbl">Material tier <small>fills in vanilla stats - change any value afterwards</small></span>
          <Chips options={tierOptions} value={item.tool.tier} onchange={setTier} label="Tier" />
          <div class="grid">
            <label>Durability <input type="number" min="1" bind:value={item.tool.durability} oninput={markCustom} /></label>
            <label>Mining speed <input type="number" step="0.5" min="0" bind:value={item.tool.miningSpeed} oninput={markCustom} /></label>
            <label>Attack damage <input type="number" step="0.5" min="1" bind:value={item.tool.damage} oninput={markCustom} /></label>
            <label>Attack speed <input type="number" step="0.1" min="0.1" bind:value={item.tool.attackSpeed} oninput={markCustom} /></label>
            <label>Enchantability <input type="number" min="0" bind:value={item.tool.enchantability} oninput={markCustom} /></label>
          </div>
          {#if item.tool.kind !== 'SWORD'}
            <label>Harvest level <small>which blocks drop items, like the vanilla tiers (e.g. Iron can mine diamond ore, Stone cannot)</small>
              <select bind:value={item.tool.harvestTier}>
                {#each meta.toolTiers as t}<option value={t.id}>{t.label}</option>{/each}
              </select>
            </label>
          {/if}
          {#if item.tool.kind === 'SWORD'}
            <small class="muted">Swords have no mining rules here; they only deal damage.</small>
          {/if}
        </section>
      {:else if item.type === 'FOOD'}
        <section class="card">
          <h3>3 · Food</h3>
          <label>Start from a vanilla food
            <select value={foodPreset} onchange={(e) => applyFood(e.target.value)}>
              <option value="">Choose a preset...</option>
              {#each meta.foodPresets as f}<option value={f.id}>{f.label} ({f.nutrition} hunger)</option>{/each}
            </select>
          </label>
          <div class="grid">
            <label>Hunger restored <input type="number" min="0" max="100" bind:value={item.food.nutrition} /></label>
            <label>Saturation <input type="number" step="0.1" min="0" bind:value={item.food.saturation} /></label>
            <label>Eating time (s) <input type="number" step="any" min="0.05" bind:value={item.food.eatSeconds} /></label>
          </div>
          <label class="check"><input type="checkbox" bind:checked={item.food.canAlwaysEat} /> Can eat when not hungry</label>
        </section>
      {:else if item.type === 'BLOCK'}
        <section class="card">
          <h3>3 · Block</h3>
          <label class="check">
            <input type="checkbox" bind:checked={item.block.placeable} onchange={setPlaceable} />
            Placeable block <small>(a real block in the world; otherwise only an item shaped like a block)</small>
          </label>
          {#if item.block.placeable}
            {#if meta.blockSetupProblem}
              <div class="notice">{meta.blockSetupProblem}</div>
            {/if}
            <span class="lbl">Textures</span>
            <Chips options={textureModeOptions} value={item.block.textureMode} onchange={(v) => (item.block.textureMode = v)} label="Texture mode" />
            <span class="lbl">Look <small>{RENDERING_HINT[item.block.rendering]}</small></span>
            <Chips options={RENDERINGS} value={item.block.rendering} onchange={(v) => (item.block.rendering = v)} label="Rendering" />
            <div class="grid">
              <label>Hardness <small>higher = slower to break</small>
                <input type="number" step="any" min="0.05" max="100" bind:value={item.block.hardness} />
              </label>
              <label>Best tool
                <select bind:value={item.block.preferredTool}>
                  {#each TOOL_OPTIONS as t}<option value={t.value}>{t.label}</option>{/each}
                </select>
              </label>
            </div>
            <div class="chips">
              {#each HARDNESS_PRESETS as [name, value]}
                <button type="button" onclick={() => (item.block.hardness = value)}>{name} ({value})</button>
              {/each}
            </div>
            <label class="check"><input type="checkbox" bind:checked={item.block.requiresTool} disabled={item.block.preferredTool === 'NONE'} />
              Drops only when broken with the best tool</label>
            <label class="check"><input type="checkbox" bind:checked={item.block.dropSelf} />
              Drops itself <small>(turn off and use the Drops tab for other drops)</small></label>
            <label>Sound set <small>fills place and break sound with a matching pair</small>
              <select value={currentFamily} onchange={setSoundFamily}>
                <option value="">{currentFamily ? '' : 'Custom / choose...'}</option>
                <option value="__none">No sound</option>
                {#each families as f}<option value={f}>{f.replaceAll('_', ' ')}</option>{/each}
              </select>
            </label>
            <div class="grid">
              <label>Place sound <small>type to search, e.g. stone place</small>
                <SoundInput bind:value={item.block.placeSound} {sounds} id="sound-place" placeholder="block.stone.place" />
              </label>
              <label>Break sound <small>type to search, e.g. stone break</small>
                <SoundInput bind:value={item.block.breakSound} {sounds} id="sound-break" placeholder="block.stone.break" />
              </label>
            </div>
            {#if item.block.state}<small class="muted">Block state: {item.block.state}</small>{/if}
            <small class="muted">Walking sounds and the break animation stay wooden (note block). Light is not supported yet.</small>
          {:else}
            <p class="muted">Without "placeable" the texture is used on all six faces of an item that looks like a block.</p>
          {/if}
        </section>
      {/if}

      <section class="card">
        <h3>{item.type === 'GENERIC' || item.type === 'BLOCK' ? '3' : '4'} · Details</h3>
        <label>Lore <small>one line per row, MiniMessage allowed</small>
          <textarea rows="3" bind:value={loreText} placeholder="<gray>Forged in the deep"></textarea>
        </label>
        <label class="short">Max stack size
          <input type="number" min="1" max="99" bind:value={item.maxStackSize} disabled={item.type === 'TOOL'} />
        </label>
      </section>
    </div>

    <aside class="side">
      <div class="card sticky">
        <h3>Preview</h3>
        <div class="stage">
          <div class="slot big">
            {#if preview}<img src={preview} alt="texture" />{:else}<VanillaIcon name={item.baseMaterial.toLowerCase()} size={80} />{/if}
          </div>
          <Tooltip {item} />
        </div>
        <div class="actions">
          <button class="primary" type="submit" disabled={!idValid}>Save</button>
          {#if !isNew}<button type="button" class="danger" onclick={ondelete}>Delete</button>{/if}
        </div>
        {#if status}<p class:error={statusError} class="status">{status}</p>{/if}
      </div>
    </aside>
  </div>
</form>
