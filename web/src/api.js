// Thin wrapper around the dashboard JSON API. Throws Error(message) on failure.
async function request(path, options = {}) {
  const res = await fetch(path, options);
  const isJson = (res.headers.get('content-type') || '').includes('json');
  const body = isJson ? await res.json() : null;
  if (!res.ok) {
    const err = new Error(body?.error || `Request failed (${res.status})`);
    err.status = res.status;
    throw err;
  }
  return body;
}

const json = (method, data) => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(data)
});

export const api = {
  me: () => request('/api/me'),
  meta: () => request('/api/meta'),
  items: () => request('/api/items'),
  saveItem: (item) => request(`/api/items/${item.id}`, json('PUT', item)),
  deleteItem: (id) => request(`/api/items/${id}`, { method: 'DELETE' }),
  uploadTexture: (id, file, slot = 'main') =>
    request(`/api/items/${id}/texture?slot=${slot}`, { method: 'PUT', headers: { 'Content-Type': 'image/png' }, body: file }),
  vanilla: () => request('/api/vanilla'),
  vanillaNames: () => request('/api/vanilla/names'),
  loot: () => request('/api/loot'),
  saveLoot: (r) => request(`/api/loot/${r.id}`, json('PUT', r)),
  deleteLoot: (id) => request(`/api/loot/${id}`, { method: 'DELETE' }),
  recipes: () => request('/api/recipes'),
  saveRecipe: (r) => request(`/api/recipes/${r.id}`, json('PUT', r)),
  deleteRecipe: (id) => request(`/api/recipes/${id}`, { method: 'DELETE' }),
  graphLayout: () => request('/api/graph/layout'),
  saveGraphLayout: (layout) => request('/api/graph/layout', json('PUT', layout)),
  pack: () => request('/api/pack'),
  buildPack: () => request('/api/pack/build', { method: 'POST' })
};
