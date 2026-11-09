<script>
  import { Handle } from '@xyflow/svelte';
  import { handlePositions } from './handles.js';

  // A recipe as a small hub between its ingredients and its result.
  let { data, selected } = $props();
  const pos = $derived(handlePositions(data.direction));
</script>

<div class="gnode hub recipe" class:selected class:problem={data.problem} title={data.problem ?? ''}>
  <Handle type="target" position={pos.target} />
  <span class="gtext">
    <strong>{data.label}</strong>
    <small>{data.problem ? '⚠ not active' : data.detail}</small>
  </span>
  <button type="button" class="gopen nodrag" title="Open in the recipe editor" aria-label="Open recipe"
          onclick={(e) => { e.stopPropagation(); data.open('recipe', data.recipeId); }}>↗</button>
  <Handle type="source" position={pos.source} />
</div>
