<div align="center">

# Hao Han Utilities

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-62B47A?style=for-the-badge&logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Paper](https://img.shields.io/badge/Paper-API-222222?style=for-the-badge&logo=paper&logoColor=white)](https://papermc.io/)
[![Purpur](https://img.shields.io/badge/Purpur-Compatible-8A4FFF?style=for-the-badge)](https://purpurmc.org/)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Gradle](https://img.shields.io/badge/Gradle-8.x-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org/)
[![SQLite](https://img.shields.io/badge/SQLite-WAL-003B57?style=for-the-badge&logo=sqlite&logoColor=white)](https://sqlite.org/)

Language: [Tiếng Việt](README.md) | English

</div>

## Overview

Hao Han Utilities is a Paper/Purpur `1.21.11` plugin focused on two features:

- **Carry:** pick up functional blocks or passive creatures, move them, and place them somewhere else.
- **Phantom Suppression:** cancel Phantom spawns and remove existing Phantoms from loaded worlds.

The plugin is completely server-side and requires no client mod or resource pack.

## How to Carry

### Pick something up

1. Make sure both hands are empty.
2. Hold the sprint key (`Ctrl` by default).
3. Right-click the block or creature you want to carry.

If either hand contains an item or the target block is unsupported, the plugin leaves the interaction untouched and sends no message, so vanilla placement or interaction continues normally.

A player can carry one object at a time. The more items a container holds, the slower the player moves while carrying it.

### Place it down

1. Look at the destination.
2. Right-click a block face.
3. The carried object is placed against the selected face.

### Creatures

Supported passive creatures preserve data such as:

- Health, age, and variant.
- Custom names.
- Equipment, inventories, and Persistent Data Containers.

### SoulAnchor

When the server has the `SoulAnchor plugin` installed, players can carry a complete Soul Anchor while preserving:

- Its anchor UUID.
- Owner and name.
- Shared-player list.

Only the anchor's owner can pick it up and move it.

SoulAnchor support is optional; Hao Han Utilities works normally without it.

## Supported Blocks

Default supported blocks include:

- Chests, trapped chests, barrels, and shulker boxes.
- Furnaces, blast furnaces, smokers, and brewing stands.
- Hoppers, dispensers, droppers, and crafters.
- Chiseled bookshelves, decorated pots, jukeboxes, beehives, and bee nests.
- Crafting, smithing, stonecutting, cartography, loom, grindstone, and enchanting tables.

The list can be changed in `plugins/HaoHanUtilities/config.yml`.

## Data Safety

- Block inventories and state are stored using Minecraft/Paper snapshots.
- Every carry operation is journaled in SQLite as `PREPARED → CARRIED → PLACING → PLACED/RESTORED`.
- If the server crashes or a player disconnects while carrying something, the state can be loaded from the database.
- The database is stored at `plugins/HaoHanUtilities/carry-blocks.db`.

## Installation

1. Build or download `HaoHanUtilities-2.0.0.jar`.
2. Copy it into the server's `plugins/` directory.
3. Restart the server.
4. Review `plugins/HaoHanUtilities/config.yml`.

Requirements:

- Paper or Purpur `1.21.11`.
- Java `21`.
- Do not use Bukkit `/reload` when testing carry transactions or recovery.

## Quick Configuration

```yaml
carrying:
  # Speed while carrying a normal object or an empty container.
  movement-speed-multiplier: 0.75
  # Speed at a full container; item load is interpolated between both values.
  full-container-movement-speed-multiplier: 0.35

entities:
  enabled: true

phantom-suppression:
  enabled: true
  remove-existing: true
```

## Commands

| Command | Description |
| --- | --- |
| `/hhu info` | Show the plugin version and status. |
| `/hhu reload` | Reload config/messages and clean loaded Phantoms. |
| `/hhu status <player>` | Show a player's active carry transaction. |
| `/hhu inspect <carryId>` | Inspect a carry transaction. |
| `/hhu recover <player> original` | Restore an object to its original location. |
| `/hhu recover <player> here` | Restore an object at the admin's targeted location. |

Aliases: `/haohanutilities`, `/hhu`, `/carryblocks`, `/carryblock`, `/cb`.

## Build

Windows:

```powershell
.\gradlew.bat clean test build
```

Linux/macOS:

```bash
./gradlew clean test build
```

The deployable JAR is written to:

```text
build/libs/HaoHanUtilities-2.0.0.jar
```
