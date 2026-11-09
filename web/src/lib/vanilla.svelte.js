import { api } from '../api.js';

// Shared state for the vanilla icon cache (downloaded by the plugin from Mojang on first start).
export const vanilla = $state({ state: 'UNKNOWN', message: '', ready: false, names: {} });

let timer = null;

/** Loads the status, and the English item names once ready. Polls while the server is still downloading. */
export async function loadVanilla() {
  clearTimeout(timer);
  try {
    const status = await api.vanilla();
    vanilla.state = status.state;
    vanilla.message = status.message;
    if (status.state === 'READY' && !vanilla.ready) {
      vanilla.names = await api.vanillaNames();
      vanilla.ready = true;
    }
    if (status.state === 'DOWNLOADING' || status.state === 'MISSING') {
      timer = setTimeout(loadVanilla, 3000);
    }
  } catch {
    // icons are optional; the UI works without them
  }
}

/** English name of a vanilla item id like "iron_ingot", or null when unknown. */
export const vanillaName = (id) => vanilla.names[id] ?? null;
