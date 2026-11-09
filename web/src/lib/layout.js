import dagre from '@dagrejs/dagre';

// Approximate node sizes used for layout (must match the CSS of the node components closely).
export const NODE_SIZE = {
  item: { width: 190, height: 72 },
  vanilla: { width: 150, height: 46 },
  tag: { width: 150, height: 46 },
  recipe: { width: 150, height: 52 },
  loot: { width: 130, height: 46 },
  source: { width: 160, height: 46 }
};

/**
 * Automatic layered layout. Returns { [nodeId]: { x, y } } (top-left corners).
 * direction: LR (inputs left, products right), RL, TB, BT.
 */
export function autoLayout(nodes, edges, direction = 'LR') {
  const g = new dagre.graphlib.Graph();
  g.setGraph({ rankdir: direction, nodesep: 36, ranksep: 90, marginx: 20, marginy: 20 });
  g.setDefaultEdgeLabel(() => ({}));

  for (const n of nodes) g.setNode(n.id, { ...(NODE_SIZE[n.type] ?? NODE_SIZE.item) });
  for (const e of edges) if (g.hasNode(e.source) && g.hasNode(e.target)) g.setEdge(e.source, e.target);
  dagre.layout(g);

  const out = {};
  for (const n of nodes) {
    const p = g.node(n.id);
    const size = NODE_SIZE[n.type] ?? NODE_SIZE.item;
    out[n.id] = { x: Math.round(p.x - size.width / 2), y: Math.round(p.y - size.height / 2) };
  }
  return out;
}
