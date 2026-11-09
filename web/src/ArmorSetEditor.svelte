<script>
  import Chips from './Chips.svelte';
  import SoundInput from './SoundInput.svelte';
  import VanillaIcon from './VanillaIcon.svelte';
  import { pieceItemId } from './lib/armorset.js';
  import { idFromName } from './lib/blank.js';

  let { initial, isNew, meta, existingIds = [], status, statusError, stamp, onsave, ondelete, onopenitem = null } = $props();

  const SLOTS = [
    { value: 'HELMET', label: 'Helmet' }, { value: 'CHESTPLATE', label: 'Chestplate' },
    { value: 'LEGGINGS', label: 'Leggings' }, { value: 'BOOTS', label: 'Boots' }
  ];
  // How many pieces the set has: the shortcuts only pick which pieces are on, every value stays editable.
  const LAYOUTS = [
    { id: 'single', label: 'Single piece', slots: ['CHESTPLATE'] },
    { id: 'pair', label: 'Helmet + leggings', slots: ['HELMET', 'LEGGINGS'] },
    { id: 'full', label: 'Full set', slots: ['HELMET', 'CHESTPLATE', 'LEGGINGS', 'BOOTS'] }
  ];

  // svelte-ignore state_referenced_locally
  let set = $state($state.snapshot(initial));
  // Texture files chosen but not uploaded yet: "humanoid" / "leggings" for the worn layers, "icon:<SLOT>" for icons.
  let pending = $state({});
  // svelte-ignore state_referenced_locally
  let previews = $state({});

  const tierOptions = $derived([
    ...meta.armorTiers.map((t) => ({ value: t.id, label: t.label })),
    { value: 'CUSTOM', label: 'Custom' }
  ]);
  const has = (slot) => !!set.pieces[slot];
  const active = $derived(SLOTS.filter((s) => has(s.value)));
  const needsHumanoid = $derived(active.some((s) => s.value !== 'LEGGINGS'));
  const needsLeggings = $derived(has('LEGGINGS'));
  // svelte-ignore state_referenced_locally
  let idTouched = $state(!isNew || !!set.id);
  function nameChanged() {
    if (!idTouched) set.id = idFromName(set.displayName, existingIds, 32);
  }

  const idValid = $derived(/^[a-z0-9_]{1,36}$/.test(set.id));
  const pieceId = (slot) => pieceItemId(set, slot);
  const iconUrl = (slot) => previews['icon:' + slot] ?? (!isNew && set.pieces[slot] ? `/api/items/${pieceId(slot)}/texture?t=${stamp}` : null);
  const wornUrl = (layer) => previews[layer] ?? (!isNew && (layer === 'humanoid' ? set.hasHumanoid : set.hasLeggings)
    ? `/api/armorsets/${set.id}/texture?slot=${layer}&t=${stamp}` : null);

  function pieceValues(tier, slot) {
    const base = meta.armorTiers.find((t) => t.id === tier);
    return base ? { defense: base.slots[slot].defense, durability: base.slots[slot].durability } : null;
  }

  function applyTier(tier) {
    const base = meta.armorTiers.find((t) => t.id === tier);
    if (!base) return;
    Object.assign(set, {
      tier, toughness: base.toughness, knockbackResistance: base.knockbackResistance,
      enchantability: base.enchantability, equipSound: base.equipSound
    });
    for (const slot of Object.keys(set.pieces)) Object.assign(set.pieces[slot], pieceValues(tier, slot));
  }

  function setTier(tier) {
    if (tier === 'CUSTOM') set.tier = 'CUSTOM';
    else applyTier(tier);
  }

  function togglePiece(slot) {
    if (has(slot)) {
      if (active.length > 1) delete set.pieces[slot];
      return;
    }
    set.pieces[slot] = pieceValues(set.tier === 'CUSTOM' ? 'IRON' : set.tier, slot) ?? { defense: 3, durability: 200 };
  }

  function applyLayout(layout) {
    for (const slot of SLOTS.map((s) => s.value)) {
      const wanted = layout.slots.includes(slot);
      if (wanted && !has(slot)) togglePiece(slot);
      else if (!wanted && has(slot)) delete set.pieces[slot];
    }
  }

  const markCustom = () => (set.tier = 'CUSTOM');

  function onFile(key, event) {
    const file = event.target.files[0];
    if (!file) return;
    pending[key] = file;
    previews[key] = URL.createObjectURL(file);
  }

  function submit(event) {
    event.preventDefault();
    onsave($state.snapshot(set), $state.snapshot(pending));
  }
</script>

<form class="editor" onsubmit={submit}>
  <div class="cols">
    <div class="stack">
      <section class="card">
        <h3>1 · The set</h3>
        <div class="grid">
          <label>Name <small>MiniMessage, pieces become "Ruby Helmet" ...</small>
            <input bind:value={set.displayName} oninput={nameChanged} placeholder="<red>Ruby" />
          </label>
          <label>ID <small>lowercase letters, digits, _ (up to 36)</small>
            <input bind:value={set.id} disabled={!isNew} placeholder="ruby" required class:bad={set.id && !idValid}
                   oninput={() => (idTouched = true)} />
          </label>
        </div>
        <span class="lbl">Pieces <small>each piece is its own item with its own recipes and drops</small></span>
        <div class="chips">
          {#each LAYOUTS as layout}
            <button type="button" onclick={() => applyLayout(layout)}>{layout.label}</button>
          {/each}
        </div>
        <div class="chips">
          {#each SLOTS as s}
            <label class="check"><input type="checkbox" checked={has(s.value)} onchange={() => togglePiece(s.value)}
                                         disabled={has(s.value) && active.length === 1} /> {s.label}</label>
          {/each}
        </div>
      </section>

      <section class="card">
        <h3>2 · Stats</h3>
        <span class="lbl">Material tier <small>fills in vanilla values - change any value afterwards</small></span>
        <Chips options={tierOptions} value={set.tier} onchange={setTier} label="Armor tier" />
        <div class="grid">
          <label>Toughness <input type="number" step="0.5" min="0" max="20" bind:value={set.toughness} oninput={markCustom} /></label>
          <label>Knockback resistance <small>0.1 = 10 %</small>
            <input type="number" step="0.05" min="0" max="1" bind:value={set.knockbackResistance} oninput={markCustom} />
          </label>
          <label>Enchantability <input type="number" min="0" max="100" bind:value={set.enchantability} oninput={markCustom} /></label>
        </div>
        <label>Equip sound <small>sound id when put on</small>
          <SoundInput bind:value={set.equipSound} sounds={meta.sounds ?? []} id="set-sound-equip" placeholder="item.armor.equip_iron" />
        </label>
        <table class="pieces">
          <thead><tr><th>Piece</th><th>Armor points</th><th>Durability</th></tr></thead>
          <tbody>
            {#each active as s (s.value)}
              <tr>
                <td>{s.label}</td>
                <td><input type="number" step="0.5" min="0" max="30" bind:value={set.pieces[s.value].defense} oninput={markCustom} aria-label="{s.label} armor points" /></td>
                <td><input type="number" min="1" bind:value={set.pieces[s.value].durability} oninput={markCustom} aria-label="{s.label} durability" /></td>
              </tr>
            {/each}
          </tbody>
        </table>
      </section>

      <section class="card">
        <h3>3 · Look</h3>
        <span class="lbl">Inventory icons <small>square PNG per piece</small></span>
        <div class="icons">
          {#each active as s (s.value)}
            <div class="texture">
              <div class="slot">
                {#if iconUrl(s.value)}<img src={iconUrl(s.value)} alt="" onerror={() => (previews['icon:' + s.value] = null)} />
                {:else}<VanillaIcon name={`iron_${s.value.toLowerCase()}`} size={40} text={false} />{/if}
              </div>
              <label class="grow">{s.label}
                <input type="file" accept="image/png" onchange={(e) => onFile('icon:' + s.value, e)} disabled={isNew} />
              </label>
            </div>
          {/each}
        </div>
        {#if isNew}<small class="muted">Save the set first, then add the icons and worn textures.</small>{/if}

        <span class="lbl">Worn on the body <small>64×32 like the vanilla armor layers (or square); shared by all pieces</small></span>
        {#if needsHumanoid}
          <div class="texture">
            <div class="slot wide">{#if wornUrl('humanoid')}<img src={wornUrl('humanoid')} alt="" onerror={() => (previews.humanoid = null)} />{/if}</div>
            <label class="grow">Helmet, chestplate and boots layer
              <input type="file" accept="image/png" onchange={(e) => onFile('humanoid', e)} disabled={isNew} />
            </label>
          </div>
        {/if}
        {#if needsLeggings}
          <div class="texture">
            <div class="slot wide">{#if wornUrl('leggings')}<img src={wornUrl('leggings')} alt="" onerror={() => (previews.leggings = null)} />{/if}</div>
            <label class="grow">Leggings layer
              <input type="file" accept="image/png" onchange={(e) => onFile('leggings', e)} disabled={isNew} />
            </label>
          </div>
        {/if}
        <small class="muted">Without a worn texture the pieces look like iron armor on the body.</small>
      </section>
    </div>

    <aside class="side">
      <div class="card sticky">
        <h3>Save</h3>
        <p class="muted summary">
          {#if set.id}
            {active.length} piece{active.length === 1 ? '' : 's'}: {active.map((s) => pieceId(s.value)).join(', ')}
          {:else}Choose an id for the set.{/if}
        </p>
        <div class="actions">
          <button class="primary" type="submit" disabled={!idValid}>Save</button>
          {#if !isNew}<button type="button" class="danger" onclick={ondelete}>Delete set</button>{/if}
        </div>
        {#if !isNew && onopenitem}
          <span class="lbl">Open a piece <small>lore, recipes and drops are set per piece</small></span>
          <div class="chips">
            {#each active as s (s.value)}
              <button type="button" onclick={() => onopenitem(pieceId(s.value))}>{s.label}</button>
            {/each}
          </div>
        {/if}
        {#if status}<p class:error={statusError} class="status">{status}</p>{/if}
        <small class="muted">
          Saving updates the piece items. Lore and extra settings you changed on a piece stay; the stats, name and
          textures come from the set. Recipes and drops are added on each piece.
        </small>
      </div>
    </aside>
  </div>
</form>
