<script>
  import { filterSounds, isKnownSound, closestSound } from './lib/sounds.js';

  // Text input with autocompletion for sound event ids. Unknown ids are marked, with the closest suggestion.
  let { value = $bindable(''), sounds, placeholder = 'block.stone.place', id } = $props();

  const known = $derived(new Set(sounds));
  let open = $state(false);
  let active = $state(0);

  const suggestions = $derived(open ? filterSounds(sounds, value ?? '') : []);
  const valid = $derived(isKnownSound(known, value));
  // a mistyped id gets the most similar real sound as a one-click fix
  const closest = $derived(!valid ? closestSound(sounds, value) : null);
  // no complaint while the user is still searching and the list offers matches
  const showError = $derived(!valid && (!open || suggestions.length === 0));

  function choose(sound) {
    value = sound;
    open = false;
    active = 0;
  }

  function onkeydown(e) {
    if (!open && (e.key === 'ArrowDown' || e.key === 'ArrowUp')) {
      open = true;
      return;
    }
    if (!suggestions.length) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      active = (active + 1) % suggestions.length;
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      active = (active - 1 + suggestions.length) % suggestions.length;
    } else if (e.key === 'Enter' && open) {
      e.preventDefault(); // do not submit the item form while picking
      choose(suggestions[active]);
    } else if (e.key === 'Escape') {
      open = false;
    }
  }
</script>

<div class="soundinput">
  <input {id} bind:value spellcheck="false" autocomplete="off" {placeholder} class:bad={!valid}
         role="combobox" aria-expanded={open} aria-autocomplete="list" aria-controls={id ? `${id}-list` : undefined}
         onfocus={() => (open = true)} onblur={() => setTimeout(() => (open = false), 120)}
         oninput={() => { open = true; active = 0; }} {onkeydown} />
  {#if open && suggestions.length}
    <ul class="suggest" role="listbox" id={id ? `${id}-list` : undefined}>
      {#each suggestions as s, i (s)}
        <li role="option" aria-selected={i === active} class:on={i === active}>
          <!-- mousedown fires before the input loses focus, so the click is not lost to the blur handler -->
          <button type="button" tabindex="-1" onmousedown={(e) => { e.preventDefault(); choose(s); }}>{s}</button>
        </li>
      {/each}
    </ul>
  {/if}
  {#if showError}
    <small class="error">
      Unknown sound.
      {#if closest}Did you mean <button type="button" class="linklike" onclick={() => choose(closest)}>{closest}</button>?{/if}
    </small>
  {/if}
</div>
