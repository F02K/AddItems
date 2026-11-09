<script>
  import { Handle } from '@xyflow/svelte';
  import VanillaIcon from '../VanillaIcon.svelte';
  import { handlePositions } from './handles.js';
  import { targetLabel, targetIcon } from '../lib/loot.js';

  // What triggers a drop: a mob, a vanilla block or a chest loot table (several are folded into one node).
  let { data, selected } = $props();
  const pos = $derived(handlePositions(data.direction));
  const GROUP = { BLOCK: 'blocks', MOB: 'mobs', CHEST: 'chests' };
  const label = $derived(
    data.count > 1 ? `${data.count} ${GROUP[data.trigger]}` : targetLabel(data.trigger, data.targets[0], [], data.ns)
  );
  const icon = $derived(data.count > 1 ? null : targetIcon(data.trigger, data.targets[0]));
  const sub = $derived({ BLOCK: 'block broken', MOB: 'mob killed', CHEST: 'chest loot' }[data.trigger]);
</script>

<div class="gnode source" class:selected class:transient={data.transient} title={data.targets.join(', ')}>
  <span class="gicon small">{#if icon}<VanillaIcon name={icon} size={24} text={false} />{/if}</span>
  <span class="gtext">
    <strong title={label}>{label}</strong>
    <small>{sub}{data.transient ? ' · drag to an item' : ''}</small>
  </span>
  <Handle type="source" position={pos.source} />
</div>
