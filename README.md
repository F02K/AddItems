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

## Letting your team use the dashboard from outside

By default the dashboard only answers on the server machine itself. To open it from anywhere:

```yaml
dashboard:
  public-access: true
  port: 8123
  public-url: "http://play.example.com:8123"   # what people type; used in the /additems login links
```

Then make sure the **TCP port (8123) is open** in the firewall and in your hosting panel; it is a second port next to the
Minecraft one. The resource pack has its own port (`pack.port`, 8124) that players must be able to reach as well. To open only one extra
port, let the dashboard port serve the pack too: `pack.host: dashboard` (then `pack.public-url` can stay empty). Only
`/pack/<sha1>.zip` is reachable without a login.
The dashboard speaks plain HTTP, so for a public server put an HTTPS reverse proxy in front of it and set `public-url`
to the `https://` address. Login is always done with a single-use link from `/additems login`.

Developer docs: [docs/](docs/) (Minecraft version notes, architecture, roadmap).
