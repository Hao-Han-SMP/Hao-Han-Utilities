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

Hao Han Utilities is a Paper/Purpur `1.21.11` plugin featuring:

- **Carry:** pick up functional blocks, passive creatures, or other players and move them somewhere else.
- **Dog Fetch:** charge and throw sticks for your tamed dogs to fetch and bring back to you.
- **Phantom Suppression:** cancel Phantom spawns and remove existing Phantoms from loaded worlds.
- **54-slot EnderChest:** expands EnderChest inventory to 54 slots (6 rows) with automatic 27-slot legacy item migration.
- **Torch Ignition:** set entities on fire when attacking them with a Torch (Normal, Soul, or Redstone Torch) in your main hand.
- **Concrete Mixer:** use water cauldrons to turn concrete powder into matching concrete.

The plugin is completely server-side and requires no client mod or resource pack.

## How to Carry

### Pick something up

1. Make sure both hands are empty.
2. Hold the carry activation key (Sprint/`Ctrl` by default).
3. Right-click the block, creature, or player you want to carry.

If either hand contains an item or the target block is unsupported, the plugin leaves the interaction untouched and sends no message, so vanilla placement or interaction continues normally.

Carry mode is enabled by default for each player. Use `/hhu toggle` to enable or disable it; while disabled, the plugin does not intercept that player's right-click interactions. The activation control can be changed with `/hhu bind sprint` or `/hhu bind sneak`. The binding follows the client's Sprint/Sneak control, including custom client key mappings.

If the mode is disabled while an object is already being carried, the player can still place that object safely; the disabled state applies to future pickups.

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

### Players

Hold the carry activation key with both hands empty, then right-click another player to carry them. The carried player uses Minecraft's native sitting pose, can look around normally, and can press their Sneak key (`Shift` by default) to dismount just like riding an entity.

Players who disable carry mode with `/hhu toggle off` cannot be carried by others. If they disable it while being carried, the plugin dismounts them immediately.

### SoulAnchor

When the server has the `SoulAnchor plugin` installed, players can carry a complete Soul Anchor while preserving:

- Its anchor UUID.
- Owner and name.
- Shared-player list.

Only the anchor's owner can pick it up and move it.

SoulAnchor support is optional; Hao Han Utilities works normally without it.

## Dog Fetch

- **Excited Waiting:** When you hold a **Stick** in your hand, nearby standing tamed dogs immediately notice you, sprint close to you, and excitedly circle around you with panting sounds and playful hops while waiting for you to throw the stick. *(Sitting dogs will stay seated and will not approach you)*.
- **Charge Throwing:** Hold **Shift (Sneak) + Right-Click** with the stick to start charging your throw. Throw distance and velocity scale according to how long you charge.
- **Tiny White Particle Trajectory:** Only when charging, a real-time predictive trajectory appears using tiny, subtle white dust particles along with a power meter on the Action Bar.
- **Release to Throw:** Release Right-Click or release Shift to launch the stick with the charged force.
- **Fetch & Return:** Your nearest standing dog sprints after the stick, picks it up (visibly held in its mouth), and brings it right back to your inventory with happy barking and heart particles.
- If the dog is commanded to sit or you change worlds, the dog safely drops the stick.

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

## 54-slot EnderChest

- Intercepts EnderChest interactions and opens a 54-slot GUI (6 rows).
- Automatically migrates existing items from the player's vanilla 27-slot EnderChest upon first open.
- Syncs the first 27 slots back to vanilla EnderChest for full compatibility with `/enderchest` commands or third-party plugins.
- Safely persisted in player Persistent Data Containers (PDC).

## Torch Ignition

- Sets target entities on fire when hit with a torch in main hand.
- **Normal Torch:** sets target on fire for 3 seconds.
- **Soul Torch:** sets target on fire for 4 seconds (with soul fire particles).
- **Redstone Torch:** sets target on fire for 1 second.
- Fully respects land protection claims (WorldGuard, GriefPrevention, v.v.) and non-PVP zones.

## Installation

1. Build or download `HaoHanUtilities-3.1.0.jar`.
2. Copy it into the server's `plugins/` directory.
3. Restart the server.
4. Review `plugins/HaoHanUtilities/config.yml`.

Requirements:

- Paper or Purpur `1.21.11`.
- Java `21`.
- Do not use Bukkit `/reload` when testing carry transactions or recovery.

## Quick Configuration

```yaml
debug: false

placement:
  maximum-distance: 5.0

carrying:
  # State for players who have never used the toggle command.
  enabled-by-default: true
  # Default pickup modifier: sprint or sneak.
  default-activation-key: sprint
  # Speed while carrying a normal object or an empty container.
  movement-speed-multiplier: 0.75
  # Speed at a full container; item load is interpolated between both values.
  full-container-movement-speed-multiplier: 0.35

entities:
  enabled: true

players:
  enabled: true

dog-fetch:
  enabled: true
  search-radius: 32.0
  min-throw-velocity: 0.55
  max-throw-velocity: 1.85
  max-charge-ticks: 24
  run-speed-multiplier: 1.55
  return-speed-multiplier: 1.40
  show-stick-in-mouth: true
  require-permission: false
  excited-wait:
    enabled: true
    speed-multiplier: 1.45
    circle-radius: 2.6

phantom-suppression:
  enabled: true
  remove-existing: true

ender-chest:
  enabled: true
  size: 54

torch-fire:
  enabled: true
  duration-seconds:
    torch: 3
    soul-torch: 4
    redstone-torch: 1
  consume-torch: false

concrete-mixer:
  enabled: true
  require-permission: false
  lower-water-level: true
  effects:
    enabled: true
    splash:
      particles:
        enabled: true
      sound:
        enabled: true
        name: ENTITY_GENERIC_SPLASH
        volume: 0.75
        pitch: 1.0
    transform:
      particles:
        enabled: true
      sound:
        enabled: true
        name: BLOCK_FIRE_EXTINGUISH
        volume: 0.65
        pitch: 1.25
```

## Commands

| Command | Description |
| --- | --- |
| `/hhu info` | Show the plugin version and status. |
| `/hhu toggle [on\|off]` | Toggle personal carry mode; omit the argument to invert its state. |
| `/hhu bind <sprint\|sneak>` | Select the carry activation control; the physical key is changed in Controls. |
| `/hhu reload` | Reload config/messages and clean loaded Phantoms. |
| `/hhu status <player>` | Show a player's active carry transaction. |
| `/hhu inspect <carryId>` | Show a carry transaction details. |
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
build/libs/HaoHanUtilities-3.1.0.jar
```
