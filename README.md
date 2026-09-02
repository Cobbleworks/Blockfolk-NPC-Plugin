<p align="center">
  <img src="images/plugin-banner.png" alt="Cobbleworks - Blockfolk NPC Plugin banner" width="818" />
</p>
<h1 align="center">Cobbleworks - Blockfolk NPC Plugin</h1>
<p align="center">
  <b>Create persistent fake-player NPCs without editing data files by hand.</b><br>
  <b>Build personalities, routines, routes, conversations, combat profiles, and optional AI behavior through in-game menus.</b>
</p>
<p align="center">
  <a href="https://github.com/Cobbleworks/Blockfolk-NPC-Plugin/releases"><img src="https://img.shields.io/github/v/release/Cobbleworks/Blockfolk-NPC-Plugin?include_prereleases&style=flat-square&color=4CAF50" alt="Latest Release"></a>&nbsp;&nbsp;<a href="https://github.com/Cobbleworks/Blockfolk-NPC-Plugin/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue?style=flat-square" alt="License"></a>&nbsp;&nbsp;<img src="https://img.shields.io/badge/Java-25+-orange?style=flat-square" alt="Java Version">&nbsp;&nbsp;<img src="https://img.shields.io/badge/Minecraft-26.2-green?style=flat-square" alt="Minecraft Version">&nbsp;&nbsp;<img src="https://img.shields.io/badge/Platform-Paper-yellow?style=flat-square" alt="Platform">&nbsp;&nbsp;<img src="https://img.shields.io/badge/Status-Active-brightgreen?style=flat-square" alt="Status">
</p>

Blockfolk is a GUI-driven NPC system for Paper servers. Administrators create reusable NPC presets, spawn persistent instances, and configure their appearance and behavior from inventory menus. NPCs can follow routes, react to players and world events, fight, gather resources, move items, hold conversations, and integrate with quests. Optional OpenRouter support lets an NPC choose only from the actions that an administrator explicitly enables.

## **Core Features**

- **Persistent NPC presets:** Reuse one definition across multiple independently stored instances
- **In-game administration:** Configure skins, equipment, names, dialog, inventories, routes, and behavior without hand-editing YAML
- **Behavior sequences:** React to interaction, proximity, combat, time, waypoints, custom events, and other supported triggers
- **Routes and locations:** Build walking routes in the world and reuse named destinations across NPCs
- **Configurable combat:** Define attacks, targets, alliances, loot, experience, respawn timing, and nearby boss bars
- **World interaction:** Mine configured resources, harvest crops, and transfer items to or from containers
- **Optional AI behavior:** Use OpenRouter for contextual conversation and validated, administrator-approved actions
- **Quest integration:** Expose persistent Blockfolk NPCs directly to BeautyQuests

## **Supported Platforms**

- **Server Software:** Paper
- **Minecraft Version:** 26.2
- **Java Requirement:** Java 25+
- **Optional Integrations:** BeautyQuests, OpenRouter

## **Table of Contents**

1. [Core Features](#core-features)
2. [Supported Platforms](#supported-platforms)
3. [Getting Started](#getting-started)
    - [Prerequisites](#prerequisites)
    - [Installation Steps](#installation-steps)
    - [Verifying Installation](#verifying-installation)
4. [Third-Party Plugins and Services](#third-party-plugins-and-services)
    - [BeautyQuests](#beautyquests)
    - [OpenRouter](#openrouter)
5. [Configuration](#configuration)
6. [How It Works](#how-it-works)
    - [Presets and Instances](#presets-and-instances)
    - [Behaviors and Routes](#behaviors-and-routes)
    - [AI Behavior](#ai-behavior)
7. [Commands](#commands)
8. [Permissions](#permissions)
9. [Documentation](#documentation)
10. [Building from Source](#building-from-source)
11. [License](#license)
12. [Screenshots](#screenshots)

## **Getting Started**

### **Prerequisites**

- A **Paper 26.2** server
- **Java 25** or newer
- Operator access or the `blockfolk.admin` permission

BeautyQuests and OpenRouter are optional. Blockfolk's deterministic NPC system works without either integration.

### **Installation Steps**

1. Download the latest `Blockfolk-x.x.x.jar` from [Releases](https://github.com/Cobbleworks/Blockfolk-NPC-Plugin/releases)
2. Stop the server and copy the jar into its `plugins/` directory
3. Start the server once to create `plugins/Blockfolk/config.yml` and the plugin data files
4. Run `/bf` to open the NPC preset browser
5. Create a preset, configure it, and use its **Spawn** action to place a persistent instance

### **Verifying Installation**

- Run `/plugins` and confirm that `Blockfolk` is shown in green
- Run `/bf` and confirm that the preset browser opens
- Create a test NPC and restart the server to confirm that its instance returns

## **Third-Party Plugins and Services**

### BeautyQuests

[BeautyQuests](https://github.com/SkytAsul/BeautyQuests) is an optional soft dependency. When installed, spawned Blockfolk NPCs appear in BeautyQuests' NPC selector. Quest starters, stages, markers, and navigation pauses refer to the NPC's persistent instance UUID, so assignments survive restarts and combat respawns.

### OpenRouter

[OpenRouter](https://openrouter.ai/) is optional and is contacted only for presets with AI behavior enabled. Configure `openrouter.api-key` and `openrouter.model` in `config.yml`. Requests run asynchronously, and returned actions are checked against the capabilities enabled for the preset; model output cannot execute arbitrary commands or code.

API usage may incur charges under the selected provider's terms. Keep the API key private and never commit a populated server configuration.

## **Configuration**

The default `config.yml` controls input timeouts, proximity transition cooldowns, MineSkin access, OpenRouter requests, AI throttling, conversation history, perception limits, mining limits, and temporary-inventory capacity.

| Setting | Purpose |
|---------|---------|
| `chat-input-timeout-seconds` | Time allowed for administrator text input |
| `question-timeout-seconds` | Time allowed for a player to answer an NPC question |
| `proximity-transition-cooldown-seconds` | Debounces rapid approach and leave transitions |
| `mineskin-api-key` | Optional key for higher MineSkin request limits |
| `openrouter.*` | Endpoint, key, model, timeout, and response limit for optional AI behavior |
| `ai-control.*` | AI cooldown, memory, perception, mining, and inventory safeguards |

See the [configuration reference](docs/reference/configuration.md) for every option and its default value.

## **How It Works**

### **Presets and Instances**

A preset stores shared appearance and behavior. Each spawned instance has its own persistent UUID, position, inventory, conversation state, and respawn deadline. Editing a preset updates its spawned copies while preserving the identities used by integrations.

The **Manage Instances** menu provides shortcuts for teleporting to an NPC, moving it and its respawn point, or removing it with confirmation. Shift-right-clicking a spawned NPC opens its preset editor directly.

### **Behaviors and Routes**

Behavior sequences contain ordered actions attached to an event. NPCs can speak, wait, walk, fight, mine, harvest, work with containers, ask questions, invoke custom events, or hand control to the optional AI layer. Routes combine world waypoints with their own arrival actions.

Mining, harvesting, and container transfers emit cancellable Bukkit events so claim, region, and logging plugins can approve, reject, or record the world change.

### **AI Behavior**

AI context is configured per preset through **Identity**, **Personality & Behavior**, **Goal / Role**, and **Knowledge / Information**. An `AI Trigger` behavior action invokes the model for the surrounding event. Nearby chat can also be enabled independently.

Conversation memory can be private per player or shared by everyone speaking to an instance. Optional long-term memory stores up to 45 validated facts per preset. While a request is active, a small thinking indicator appears above participating NPCs; queued interactions resume after the request or cooldown completes.

## **Commands**

| Command | Description |
|---------|-------------|
| `/bf` or `/blockfolk` | Open the NPC preset browser |
| `/bf create` | Start preset creation and enter the name in chat |
| `/bf create <name>` | Create a preset with the supplied name |
| `/bf npc <name>` | Open a preset editor; names are tab-completed |
| `/bf npc <name> spawn` | Spawn a persistent instance of a preset |
| `/bf npc <name> duplicate` | Duplicate a preset with ` (copy)` appended to its name |
| `/bf routes` | Open the route manager |

## **Permissions**

| Permission | Description | Default |
|------------|-------------|---------|
| `blockfolk.admin` | Create, edit, spawn, and manage Blockfolk NPCs | `op` |

## **Documentation**

The full administrator guide is available at [cobbleworks.github.io/Blockfolk-NPC-Plugin](https://cobbleworks.github.io/Blockfolk-NPC-Plugin/). Its source is kept in [`docs/`](docs/index.md).

To preview it locally:

```bash
cd docs
npm ci
npm run docs:dev
```

## **Building from Source**

**Requirements:** Java 25 and Maven 3.9+

```bash
git clone https://github.com/Cobbleworks/Blockfolk-NPC-Plugin.git
cd Blockfolk-NPC-Plugin
mvn clean verify
```

The plugin jar is written to `target/Blockfolk.jar`.

## **License**

This project is licensed under the **MIT License**. See [LICENSE](LICENSE) for details.

## **Screenshots**

<table>
  <tr>
    <th>Blockfolk - NPC Editor</th>
    <th>Blockfolk - Idle Walking Action</th>
  </tr>
  <tr>
    <td><a href="https://github.com/Cobbleworks/Blockfolk-NPC-Plugin/raw/main/images/screenshot-npc-editor.png"><img src="https://github.com/Cobbleworks/Blockfolk-NPC-Plugin/raw/main/images/screenshot-npc-editor.png" alt="Blockfolk NPC editor" width="450"></a></td>
    <td><a href="https://github.com/Cobbleworks/Blockfolk-NPC-Plugin/raw/main/images/screenshot-idle-walk-action.png"><img src="https://github.com/Cobbleworks/Blockfolk-NPC-Plugin/raw/main/images/screenshot-idle-walk-action.png" alt="Configuring an NPC idle walking action" width="450"></a></td>
  </tr>
  <tr>
    <th>Blockfolk - AI Item Pickup</th>
    <th>Blockfolk - Default Inventory</th>
  </tr>
  <tr>
    <td><a href="https://github.com/Cobbleworks/Blockfolk-NPC-Plugin/raw/main/images/screenshot-ai-item-pickup.png"><img src="https://github.com/Cobbleworks/Blockfolk-NPC-Plugin/raw/main/images/screenshot-ai-item-pickup.png" alt="An NPC following a request to pick up an item" width="450"></a></td>
    <td><a href="https://github.com/Cobbleworks/Blockfolk-NPC-Plugin/raw/main/images/screenshot-default-inventory.png"><img src="https://github.com/Cobbleworks/Blockfolk-NPC-Plugin/raw/main/images/screenshot-default-inventory.png" alt="Configuring an NPC default inventory" width="450"></a></td>
  </tr>
  <tr>
    <th>Blockfolk - Container Withdrawal</th>
    <th>Blockfolk - AI Personality Response</th>
  </tr>
  <tr>
    <td><a href="https://github.com/Cobbleworks/Blockfolk-NPC-Plugin/raw/main/images/screenshot-container-withdrawal.png"><img src="https://github.com/Cobbleworks/Blockfolk-NPC-Plugin/raw/main/images/screenshot-container-withdrawal.png" alt="An NPC withdrawing items from a chest" width="450"></a></td>
    <td><a href="https://github.com/Cobbleworks/Blockfolk-NPC-Plugin/raw/main/images/screenshot-ai-personality-response.png"><img src="https://github.com/Cobbleworks/Blockfolk-NPC-Plugin/raw/main/images/screenshot-ai-personality-response.png" alt="An NPC responding according to its personality" width="450"></a></td>
  </tr>
</table>
