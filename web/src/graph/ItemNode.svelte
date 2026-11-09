<script>
  import { Handle } from '@xyflow/svelte';
  import VanillaIcon from '../VanillaIcon.svelte';
  import { handlePositions } from './handles.js';

  // A custom item (or a missing one that a recipe still points at).
  let { data, selected } = $props();
  const pos = $derived(handlePositions(data.direction));
  const TYPE = { GENERIC: 'Item', FOOD: 'Food', TOOL: 'Tool', BLOCK: 'Block' };
  let broken = $state(false);
</script>

<div class="gnode item" class:selected class:missing={data.missing} data-type={data.itemType}>
  <Handle type="target" position={pos.target} />
  <span class="gicon">
    {#if data.hasTexture && !broken}
      <img src={`/api/items/${data.itemId}/texture`} alt="" onerror={() => (broken = true)} />
    {:else if data.baseMaterial}
      <VanillaIcon name={data.baseMaterial.toLowerCase()} size={32} text={false} />
    {/if}
  </span>
  <span class="gtext">
    <strong title={data.name}>{data.name}</strong>
    <small>{data.missing ? 'missing item' : (data.placeable ? 'Placeable block' : TYPE[data.itemType] ?? 'Item')}</small>
  </span>
  {#if !data.missing}
    <button type="button" class="gopen nodrag" title="Open in the item editor" aria-label="Open item"
            onclick={(e) => { e.stopPropagation(); data.open('item', data.itemId); }}>↗</button>
  {/if}
  <Handle type="source" position={pos.source} />
</div>
