# Data & backups

Blockfolk stores its runtime configuration under `plugins/Blockfolk/`.

| Path | Contents |
| --- | --- |
| `config.yml` | Global timeouts, MineSkin, and OpenRouter settings. |
| `definitions/*.yml` | One file per NPC preset, including behaviour, combat, equipment, and AI settings. |
| `definition-order.yml` | Preset browser ordering. |
| `instances.yml` | Persistent spawned instances and their UUIDs. |
| `routes.yml` | Routes, route icons, point actions, and route ordering. |
| `locations.yml` | Named global locations and icons. |
| `custom-events.yml` | Global custom-event definitions and ordering. |

## Backup

Back up the entire `plugins/Blockfolk/` directory together. Presets can reference routes, locations, and custom events, so restoring only individual files can leave missing references.

For the cleanest snapshot, stop the server before copying the directory. Blockfolk uses debounced writes for its YAML repositories, so a live filesystem copy may catch related files at slightly different moments.

## Editing files manually

The in-game GUI is the supported editing path for preset and repository data. If manual repair is necessary, stop the server, make a backup, edit the YAML, then start the server and check its log for malformed entries.
