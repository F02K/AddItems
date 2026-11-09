<script>
  import { api } from './api.js';
  import ArmorSetEditor from './ArmorSetEditor.svelte';
  import { pieceItemId } from './lib/armorset.js';

  // The editor of one armor set (shown in the Items tab). Saving writes the piece items; onchanged() reloads the item list.
  let { initial, isNew, meta, existingIds = [], onchanged, onopenitem, ondeleted } = $props();

  let stamp = $state(Date.now());
  let status = $state('');
  let statusError = $state(false);
  let editorKey = $state(0);
  // svelte-ignore state_referenced_locally
  let current = $state(initial);
  // svelte-ignore state_referenced_locally
  let creating = $state(isNew);

  const say = (m, e = false) => { status = m; statusError = e; };

  async function save(def, files) {
    try {
      const saved = await api.saveArmorSet(def, creating);
      for (const [key, file] of Object.entries(files ?? {})) {
        if (key.startsWith('icon:')) await api.uploadTexture(pieceItemId(saved, key.slice(5)), file, 'main');
        else await api.uploadSetTexture(saved.id, file, key);
      }
      await onchanged?.();
      current = (await api.armorSets()).find((s) => s.id === saved.id) ?? saved;
      creating = false;
      stamp = Date.now(); // bypass the browser cache after an upload
      editorKey += 1;
      say('Saved. Use "Build & send pack" to deliver texture changes.');
    } catch (e) {
      say(e.message, true);
    }
  }

  async function remove() {
    if (!confirm(`Delete the set ${current.id} and all of its pieces (items, textures)? Recipes and drops that use them stop working.`)) return;
    try {
      await api.deleteArmorSet(current.id);
      await onchanged?.();
      ondeleted?.();
    } catch (e) {
      say(e.message, true);
    }
  }
</script>

{#key editorKey}
  <ArmorSetEditor initial={current} isNew={creating} {meta} {existingIds} {status} statusError={statusError} {stamp}
                  onsave={save} ondelete={remove} {onopenitem} />
{/key}
