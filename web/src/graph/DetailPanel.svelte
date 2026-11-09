<script>
  import VanillaIcon from '../VanillaIcon.svelte';
  import RecipeCard from './RecipeCard.svelte';
  import RecipeSlot from './RecipeSlot.svelte';
  import { refLabel } from '../lib/refs.js';
  import { usedFor } from '../lib/details.js';
  import { targetLabel, targetIcon, TRIGGERS } from '../lib/loot.js';

  // Detail panel under the graph: how the selected item is made (drawn like Minecraft's screens), where it drops,
  // and what it is used for. Slots and chips focus the matching node in the graph.
  let { details, items, meta, onclose, onfocus, onopen } = $props();

  const ns = $derived(meta.customNamespace);
  const title = $derived(
    details.kind === 'item' ? refLabel(details.ref, items, ns)
      : details.kind === 'recipe' ? `Recipe: ${refLabel(details.recipe.result, items, ns)}`
      : 'Drop rule'
  );
  const triggerLabel = (t) => TRIGGERS.find((x) => x.value === t)?.label ?? t;
  const used = $derived(details.kind === 'item' ? usedFor(details.using) : []);
  // the hint only makes sense for our own items; a vanilla stick is not expected to be made by AddItems
  const nothing = $derived(details.kind === 'item' && details.ref.startsWith(`${ns}:`) && !details.making.length && !details.dropping.length && !details.breaking.length);

  const amount = (d) => (d.min === d.max ? `${d.min}` : `${d.min}–${d.max}`);
  const chance = (d) => `${+Number(d.chance).toFixed(2)}%`;
</script>

{#snippet lootRule(rule, highlight)}
  <article class="rcard dropcard" class:problem={rule.problem}>
    <header>
      <strong>{triggerLabel(rule.trigger)}</strong>
      <span class="rid">{rule.id}</span>
      <button type="button" class="ropen" title="Open in the drops editor" onclick={() => onopen('loot', rule.id)}>↗</button>
    </header>
    <div class="dtargets">
      {#each rule.targets.slice(0, 6) as t}
        {@const icon = targetIcon(rule.trigger, t)}
        <span class="dtarget">
          {#if icon}<VanillaIcon name={icon} size={18} text={false} />{/if}
          {targetLabel(rule.trigger, t, items, ns)}
        </span>
      {/each}
      {#if rule.targets.length > 6}<span class="muted">+{rule.targets.length - 6} more</span>{/if}
    </div>
    <div class="ddrops">
      {#each rule.drops as d}
        <span class="ddrop" class:highlight={highlight === d.item}>
          <RecipeSlot ref={d.item} {items} {meta} {onfocus} />
          <span class="dtext"><small>{refLabel(d.item, items, ns)}</small><span><strong>{chance(d)}</strong> · {amount(d)}×</span></span>
        </span>
      {/each}
    </div>
    <footer>
      {#if rule.problem}<span class="rwarn">⚠ not active: {rule.problem}</span>{/if}
    </footer>
  </article>
{/snippet}

<section class="detail">
  <header>
    <strong>{title}</strong>
    {#if details.kind === 'item'}<span class="muted">how to get it and what it is used for</span>{/if}
    <span class="spacer"></span>
    <button type="button" class="dclose" aria-label="Close details" onclick={onclose}>×</button>
  </header>

  <div class="detail-body">
    {#if details.kind === 'recipe'}
      <RecipeCard recipe={details.recipe} {items} {meta} {onfocus} />
    {:else if details.kind === 'loot'}
      {#each details.rules as rule (rule.id)}{@render lootRule(rule, null)}{/each}
    {:else}
      {#if details.making.length}
        <div class="dgroup">
          <h4>Crafting & smelting</h4>
          <div class="dcards">
            {#each details.making as r (r.id)}<RecipeCard recipe={r} {items} {meta} {onfocus} {onopen} />{/each}
          </div>
        </div>
      {/if}
      {#if details.dropping.length}
        <div class="dgroup">
          <h4>Drops from</h4>
          <div class="dcards">
            {#each details.dropping as rule (rule.id)}{@render lootRule(rule, details.ref)}{/each}
          </div>
        </div>
      {/if}
      {#if details.breaking.length}
        <div class="dgroup">
          <h4>When this block is broken</h4>
          <div class="dcards">
            {#each details.breaking as rule (rule.id)}{@render lootRule(rule, null)}{/each}
          </div>
        </div>
      {/if}
      {#if nothing}
        <p class="muted dnothing">Nothing makes or drops this item yet. Drag a connection from another item or a source to it to add a recipe or a drop rule.</p>
      {/if}
      {#if used.length}
        <div class="dgroup">
          <h4>Used in</h4>
          <div class="dchips">
            {#each used as u (u.ref)}
              <button type="button" class="dchip" onclick={() => onfocus(u.ref)}>
                <RecipeSlot ref={u.ref} {items} {meta} />
                {refLabel(u.ref, items, ns)}{u.recipeIds.length > 1 ? ` ×${u.recipeIds.length}` : ''}
              </button>
            {/each}
          </div>
        </div>
      {/if}
    {/if}
  </div>
</section>
