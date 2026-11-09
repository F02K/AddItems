<script>
  // Segmented button group. options: [{ value, label, hint? }]
  let { options, value, onchange, label = '' } = $props();
</script>

<div class="chips" role="radiogroup" aria-label={label}>
  {#each options as o (o.value)}
    <button type="button" role="radio" aria-label={o.label} aria-checked={value === o.value} class:on={value === o.value}
            title={o.hint ?? ''} onclick={() => onchange(o.value)}>{o.label}</button>
  {/each}
</div>
