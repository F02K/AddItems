<script>
  import { Handle } from '@xyflow/svelte';
  import VanillaIcon from '../VanillaIcon.svelte';
  import { vanillaName } from '../lib/vanilla.svelte.js';
  import { prettyName } from '../lib/refs.js';
  import { handlePositions } from './handles.js';

  // A vanilla item or an item tag ("any planks") used by a custom item's recipe.
  let { data, selected, type } = $props();
  const pos = $derived(handlePositions(data.direction));
  const isTag = $derived(type === 'tag');
  const label = $derived(isTag ? `any ${prettyName(data.name)}` : (vanillaName(data.name) ?? prettyName(data.name)));
</script>

<div class="gnode vanilla" class:selected class:tag={isTag}>
  <!-- results can be vanilla items too (e.g. 3 rubies -> 2 diamonds); without this handle those edges are not drawn -->
  <Handle type="target" position={pos.target} isConnectable={false} />
  <span class="gicon small">
    {#if !isTag}<VanillaIcon name={data.name} size={24} text={false} />{/if}
  </span>
  <span class="gtext"><strong title={label}>{label}</strong></span>
  <Handle type="source" position={pos.source} />
</div>
