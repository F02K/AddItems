<script>
  import { Handle } from '@xyflow/svelte';
  import { handlePositions } from './handles.js';

  // A drop rule as a small hub between what triggers it and the items it drops.
  let { data, selected } = $props();
  const pos = $derived(handlePositions(data.direction));
  const LABEL = { BLOCK: 'block drops', MOB: 'mob drops', CHEST: 'chest loot' };
</script>

<div class="gnode hub loot" class:selected class:problem={data.problem} title={data.problem ?? ''}>
  <Handle type="target" position={pos.target} />
  <span class="gtext">
    <strong>{LABEL[data.trigger] ?? 'drops'}</strong>
    <small>{data.problem ? '⚠ not active' : 'drop rule'}</small>
  </span>
  <button type="button" class="gopen nodrag" title="Open in the drops editor" aria-label="Open drop rule"
          onclick={(e) => { e.stopPropagation(); data.open('loot', data.ruleId); }}>↗</button>
  <Handle type="source" position={pos.source} />
</div>
