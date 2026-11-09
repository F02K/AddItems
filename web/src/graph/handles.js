import { Position } from '@xyflow/svelte';

/** Where input (target) and output (source) handles sit for a layout direction. */
export function handlePositions(direction) {
  switch (direction) {
    case 'RL': return { target: Position.Right, source: Position.Left };
    case 'TB': return { target: Position.Top, source: Position.Bottom };
    case 'BT': return { target: Position.Bottom, source: Position.Top };
    default: return { target: Position.Left, source: Position.Right };
  }
}
