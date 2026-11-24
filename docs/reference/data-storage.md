# Data & backups

Blockfolk stores its runtime configuration under `plugins/Blockfolk/`.

| Path | Contents |
| --- | --- |
| `config.yml` | Global timeouts, MineSkin, and OpenRouter settings. |
| `definitions/*.yml` | One file per NPC preset, including behaviour, combat, equipment, and AI settings. |
| `shops.yml` | Shared shops, keyed by shop key: name, assortment (`rotating-slots`, `refresh-minutes`), and trades. Each trade has an `id`, its items, and optional `price`, `payout`, `availability`, `weight`, `stock`, and `stock-scope`; absent keys mean the default. |
| `shop-state.yml` | What changes while players trade: each shop's current stock period start (`period-start`), its rotation picks, and how often limited trades were sold (`sold`, and `sold-to` per player). Deleting it restocks every shop and draws new rotations. |
| `definition-order.yml` | Preset browser ordering. |
| `instances.yml` | Spawned variants: UUID, preset, position, spawn point, and optional `label`, `skin`, and `route-swaps` overrides. Absent overrides inherit from the preset. |
| `routes.yml` | NPC-owned routes and their point actions. |
| `locations.yml` | Named global locations and icons. |
| `scenes.yml` | Scenes: triggers, area, ending, and every role's actions. |
| `abilities.yml` | Shared attack definitions, origins, shapes, timing, effects, and visuals. |

## Backup

Back up the entire `plugins/Blockfolk/` directory together. Presets and scenes can reference routes, locations, scenes, shops, and abilities, so restoring only individual files can leave missing references.

For the cleanest snapshot, stop the server before copying the directory. Blockfolk uses debounced writes for its YAML repositories, so a live filesystem copy may catch related files at slightly different moments.

## Editing files manually

The in-game GUI is the supported editing path for preset and repository data. If manual repair is necessary, stop the server, make a backup, edit the YAML, then start the server and check its log for malformed entries.
