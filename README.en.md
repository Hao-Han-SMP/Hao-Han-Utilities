<div align="center">

# Hao Han Utilities

Server-side utilities for Hao Han SMP: safely carry functional blocks and remove Phantoms from every world.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-62B47A?style=for-the-badge&logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Paper](https://img.shields.io/badge/Paper-API-222222?style=for-the-badge&logo=paper&logoColor=white)](https://papermc.io/)
[![Purpur](https://img.shields.io/badge/Purpur-Compatible-8A4FFF?style=for-the-badge)](https://purpurmc.org/)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Gradle](https://img.shields.io/badge/Gradle-8.x-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org/)
[![SQLite](https://img.shields.io/badge/SQLite-WAL-003B57?style=for-the-badge&logo=sqlite&logoColor=white)](https://sqlite.org/)

Language: [Tiếng Việt](README.md) | English

</div>

## Overview

Hao Han Utilities is a Paper/Purpur `1.21.11` plugin with two modules:

- **Carry Blocks:** sneak with empty hands and right-click to carry a supported container or functional block, then right-click again to place it.
- **Phantom Suppression:** cancels every Phantom spawn and removes existing Phantoms when the plugin, a world, or a chunk loads.

The carried block is never represented by an item. SQLite is the transaction source of truth; `BlockDisplay` is visual only.

## Features

- Durable `PREPARED → CARRIED → PLACING → PLACED/RESTORED` SQLite journal.
- Inventory, item metadata, block PDC, custom name, lock and `BlockData` preservation.
- Furnace/smoker/blast-furnace progress and brewing-stand state preservation.
- Runtime locks, fingerprints, plugin chunk tickets and restored-payload verification.
- Crash, logout, death and plugin-disable recovery without overwriting unknown blocks.
- One shared `BlockDisplay` follow task for every active carrier.
- Double chests are explicitly rejected in this version.
- Standard protection probes plus cancellable custom pickup/place events.
- Global Phantom spawn cancellation and cleanup of already-loaded Phantoms.

Supported blocks include single chests, trapped chests, barrels, furnaces, smokers, blast furnaces, hoppers, dispensers, droppers, brewing stands, crafters, shulker boxes and the functional tables listed in `config.yml`.

## Requirements

- Paper or Purpur `1.21.11`.
- Java `21`.
- No client mod or resource pack.

## Installation

1. Build or download `HaoHanUtilities-1.0.0.jar`.
2. Copy it to the server's `plugins/` directory.
3. Restart the server. Do not use Bukkit `/reload` to test transaction behavior.
4. Review `plugins/HaoHanUtilities/config.yml`.

The transaction database is stored at `plugins/HaoHanUtilities/carry-blocks.db`.

## Commands

| Command | Description |
| --- | --- |
| `/haohanutilities info` | Show the plugin version and modules. |
| `/haohanutilities reload` | Reload configuration/messages and clean loaded Phantoms. |
| `/haohanutilities status <player>` | Show a player's active carry transaction. |
| `/haohanutilities inspect <carryId>` | Inspect transaction metadata. |
| `/haohanutilities recover <player> original` | Restore to the original loaded, empty location. |
| `/haohanutilities recover <player> here` | Restore at the admin's targeted location. |

Aliases: `/hhu`, `/carryblocks`, `/carryblock`, `/cb`.

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `haohanutilities.carry.use` | true | Carry and place supported blocks. |
| `haohanutilities.admin` | op | Use administration and recovery commands. |
| `haohanutilities.bypass.protection` | op | Bypass protection probes; grant only to trusted admins. |

## Build

Windows:

```powershell
.\gradlew.bat clean test shadowJar
```

Linux/macOS:

```bash
./gradlew clean test shadowJar
```

The deployable JAR is written to `build/libs/HaoHanUtilities-1.0.0.jar`.

## Operations

- Do not delete the SQLite database, WAL or SHM files while the server is running.
- Recovery fails closed when a location is occupied; use the status, inspect and recover commands.
- Back up the world and plugin data before production upgrades.
- Protection integrations may cancel the standard probes or listen to `CarryBlockPickupEvent` and `CarryBlockPlaceEvent`.
