# AddItems

Paper plugin with a team web dashboard for creating custom items (blocks, food, tools, ...) and delivering
them through an automatically generated resource pack. Target: Minecraft 26.3 (Java 25).

## Build

```bash
./gradlew build        # needs JDK 25 and Node 20+; output: build/libs/AddItems-*.jar
```

## Server setup for custom blocks

Placeable custom blocks are note blocks. Set this once in `config/paper-global.yml` and restart the server:

```yaml
block-updates:
  disable-noteblock-updates: true
```

Without it, custom blocks change their look when the block above or below them changes (AddItems warns at startup).
Vanilla note blocks do not play notes while AddItems is active.

## Use

1. Put the jar into `plugins/` of a Paper 26.3 server, start it once and edit `plugins/AddItems/config.yml`
   (set `pack.public-url` so players can download the pack).
2. `/additems login` -> open the link -> create items, upload textures, "Build & send pack".
3. `/additems give <id> [amount] [player]`.

Developer docs: [docs/](docs/) (Minecraft version notes, architecture, roadmap).
