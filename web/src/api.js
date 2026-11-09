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

const json = (method, data, create = false) => ({
  method,
  headers: { 'Content-Type': 'application/json', ...(create ? { 'X-Create': '1' } : {}) },
  body: JSON.stringify(data)
});

export const api = {
  me: () => request('/api/me'),
  meta: () => request('/api/meta'),
  items: () => request('/api/items'),
  // create = true marks the first save of a new item: the server then refuses to overwrite an existing one
  saveItem: (item, create = false) => request(`/api/items/${item.id}`, json('PUT', item, create)),
  deleteItem: (id) => request(`/api/items/${id}`, { method: 'DELETE' }),
  uploadTexture: (id, file, slot = 'main') =>
    request(`/api/items/${id}/texture?slot=${slot}`, { method: 'PUT', headers: { 'Content-Type': 'image/png' }, body: file }),
  vanilla: () => request('/api/vanilla'),
  vanillaNames: () => request('/api/vanilla/names'),
  loot: () => request('/api/loot'),
  saveLoot: (r) => request(`/api/loot/${r.id}`, json('PUT', r)),
  // new recipes and drop rules get their id from the server (the id given is only a suggestion)
  createLoot: (r) => request('/api/loot', json('POST', r)),
  deleteLoot: (id) => request(`/api/loot/${id}`, { method: 'DELETE' }),
  armorSets: () => request('/api/armorsets'),
  saveArmorSet: (s, create = false) => request(`/api/armorsets/${s.id}`, json('PUT', s, create)),
  deleteArmorSet: (id) => request(`/api/armorsets/${id}`, { method: 'DELETE' }),
  uploadSetTexture: (id, file, slot) =>
    request(`/api/armorsets/${id}/texture?slot=${slot}`, { method: 'PUT', headers: { 'Content-Type': 'image/png' }, body: file }),
  recipes: () => request('/api/recipes'),
  saveRecipe: (r) => request(`/api/recipes/${r.id}`, json('PUT', r)),
  createRecipe: (r) => request('/api/recipes', json('POST', r)),
  deleteRecipe: (id) => request(`/api/recipes/${id}`, { method: 'DELETE' }),
  graphLayout: () => request('/api/graph/layout'),
  saveGraphLayout: (layout) => request('/api/graph/layout', json('PUT', layout)),
  pack: () => request('/api/pack'),
  buildPack: () => request('/api/pack/build', { method: 'POST' })
};
