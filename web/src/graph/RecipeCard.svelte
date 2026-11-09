<script>
  import VanillaIcon from '../VanillaIcon.svelte';
  import RecipeSlot from './RecipeSlot.svelte';
  import { craftingCells, STATIONS } from '../lib/details.js';

  // One recipe drawn like the matching Minecraft screen: crafting grid, furnace, smithing table, anvil, stonecutter.
  let { recipe, items, meta, onfocus, onopen = null } = $props();

  const station = $derived(STATIONS[recipe.type] ?? { label: recipe.type, icon: 'crafting_table', note: '' });
  const cells = $derived(craftingCells(recipe));
  const cooking = $derived(['FURNACE', 'BLASTING', 'SMOKING', 'CAMPFIRE'].includes(recipe.type));
  const crafting = $derived(recipe.type === 'SHAPED' || recipe.type === 'SHAPELESS');
</script>

<article class="rcard" class:problem={recipe.problem}>
  <header>
    <VanillaIcon name={station.icon} size={22} text={false} />
    <strong>{station.label}</strong>
    <span class="rid">{recipe.id}</span>
    {#if onopen}
      <button type="button" class="ropen" title="Open in the recipe editor" onclick={() => onopen('recipe', recipe.id)}>↗</button>
    {/if}
  </header>

  <div class="rbody">
    {#if crafting}
      <div class="rgrid">
        {#each cells as cell}<RecipeSlot ref={cell} {items} {meta} {onfocus} />{/each}
      </div>
    {:else if cooking || recipe.type === 'STONECUTTING'}
      <RecipeSlot ref={recipe.input} {items} {meta} {onfocus} />
    {:else if recipe.type === 'SMITHING'}
      <div class="rrow">
        <RecipeSlot ref={recipe.template} {items} {meta} {onfocus} /><span class="rplus">+</span>
        <RecipeSlot ref={recipe.base} {items} {meta} {onfocus} /><span class="rplus">+</span>
        <RecipeSlot ref={recipe.addition} {items} {meta} {onfocus} />
      </div>
    {:else if recipe.type === 'ANVIL'}
      <div class="rrow">
        <RecipeSlot ref={recipe.left} {items} {meta} {onfocus} /><span class="rplus">+</span>
        <RecipeSlot ref={recipe.right} count={recipe.rightAmount} {items} {meta} {onfocus} />
      </div>
    {/if}

    <span class="rarrow">
      →
      {#if cooking}<small>{(recipe.cookTime / 20).toFixed(recipe.cookTime % 20 ? 1 : 0)} s</small>{/if}
      {#if recipe.type === 'ANVIL'}<small class="xp">{recipe.levelCost} levels</small>{/if}
    </span>

    <RecipeSlot ref={recipe.result} count={recipe.amount} {items} {meta} {onfocus} />
  </div>

  <footer>
    {#if recipe.problem}
      <span class="rwarn">⚠ not active: {recipe.problem}</span>
    {:else}
      <span>{station.note}{cooking && recipe.experience ? ` · ${recipe.experience} XP` : ''}</span>
    {/if}
  </footer>
</article>
