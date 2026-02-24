# DungeonInstances

A Spigot/Paper Minecraft plugin for managing instanced dungeons with party system, clickable invitations, admin commands and in-game scoreboard HUD.

---

## Overview

**DungeonInstances** lets you create dungeon worlds copied from templates, invite players to parties, and display a dynamic scoreboard when inside an instance. The plugin aims for simple administration (create/delete/edit templates, set spawns) and friendly UX (colored messages, clickable buttons to accept/decline invitations).

## Main Features
- Instance creation based on templates (folders under `templates-dungeons/`).
- Party system with clickable invitations (`[Accept]`, `[Decline]`).
- Admin commands: load/edit/save/purge instances, define spawn points.
- Scoreboard visible in instance worlds (`instance_<name>_<uuid>`) displaying:
  - Party members (leader marked with `♛`, you marked with `★`).
  - Directional arrows showing members' relative position.
  - Current health in color (green/yellow/red).
  - Offline members displayed with strikethrough.

## Installation

1. Build:

```bash
cd /path/to/DungeonsInstances
mvn clean package -DskipTests -T 1C
```

Or use the `./build` script if you made it executable.

2. Deploy: copy the generated JAR to your Spigot/Paper server's `plugins/` folder:

```bash
cp "target/Dungeon Instances-1.0.0.jar" /path/to/server/plugins/
```

3. Restart the server.

## Important Folder Structure
- `templates-dungeons/` – template folders (one folder = one dungeon template). If the folder doesn't exist, the plugin automatically creates it and installs the default *manaria* dungeon from its internal archive.
- `plugins/DungeonInstances/spawnPoints.json` – spawn points set via `/dungeon admin setspawn`.

## Commands

All commands are under `/dungeon`.

- `/dungeon instance <template> [difficulty]`
  - Creates an instance from the template with one of these difficulty levels: **Beginner**, **Normal** (default), **Heroic**, **Mythic**.
    Difficulty applies multipliers to creature health, damage and armor.
  - The player (party leader) and members receive a message and are teleported after a few seconds.

- `/dungeon leave`
  - Leaves the instance and restores normal state.

- `/dungeon list`
  - Lists available templates and/or active instances.

- `/dungeon admin <sub>` (requires permission: `dungeon.admin`)
  - `edit <template>` – creates/opens an edit instance for a template.
  - `save <instance>` – saves a modified instance (used for edit templates).
  - `purge <instance>` – deletes and unloads an empty instance.
  - `setspawn <template>` – registers your current position as spawn for this template (saved in `spawnPoints.json`).
  - `alias <alias>` – assigns or shows the loot pool alias for a mob you're looking at; use `none` to clear.
  - `reloadloot` – reloads loot tables from `lootTables.json`.

- `/dungeon party <sub>`
  - `create [name]` – creates a new party. If no name is given, the plugin uses the creator's username (spaces replaced with underscores). Names must be unique — creation fails if the name exists.
  - `invite <player>` – invites a player (sends clickable `[Accept]`/`[Decline]` message).
  - `accept` – accepts the last invitation. Automatically leaves any existing party before joining the new one.
  - `decline` – declines the last invitation.
  - `leave` – leaves the party. If the player is in an instance (`instance_...`), they are teleported to their previous world (if known) or main spawn.
  - `kick <player>` – (leader only) removes a member. If the kicked member is in an instance, they are teleported to their previous world.
  - `disband` – (leader only) dissolves the group — notifies all members, clears pending invitations and teleports members in instances to their previous world.
  - `list` – lists parties/members.
  - `members` – shows party members (Leader/You, online/offline).

Notes:
- Invitations use TextComponent API to provide clickable buttons on the client.
- Admin commands are hidden from players without permission.

## Scoreboard (HUD)

- The scoreboard activates automatically for players in a world whose name starts with `instance_`.
- It displays in the sidebar:
  - The dungeon name (extracted from world name `instance_<name>_<uuid>`).
  - Party members with health status, relative direction, leader/star markers.
  - Uniform separators (header/footer) in consistent color.

## Spawn Points

- Set a spawn per template with:

```
/dungeon admin setspawn <template>
```

- Spawns are persisted in `plugins/DungeonInstances/spawnPoints.json`.

## Templates & Instances

- Place your dungeon templates in `templates-dungeons/<template_name>/`.
- Instances are created by copying and loaded under `instance_<template_name>_<uuid>`.
- Empty instances are automatically unloaded and deleted.

## Permissions

- `dungeon.admin` – access to admin commands (`/dungeon admin ...`).

Other commands have no specific permissions by default (you can add checks if needed).

## Troubleshooting

- If an instance fails to load, check write permissions on the world container folder (template folder copy).
- If the scoreboard doesn't appear: ensure you're in an `instance_...` world and belong to a party.
- If offline player names display incorrectly, the plugin uses `Bukkit.getOfflinePlayer(UUID).getName()` as fallback.

## Custom Loot Tables

DungeonInstances includes a loot table system per dungeon/difficulty. Each mob can be marked with a **pool alias** that determines the loot table used when the mob dies in an instance. The alias is stored in NBT (PersistentDataContainer) and persists across saves/loads.

### Configuration

The config file is at `plugins/DungeonInstances/lootTables.json`. On first load, an empty `{}` is created so admins can populate it manually. Expected structure:

```json
{
  "<template_name>": {
    "<difficulty>": {
      "<alias>": {
        "iterations": <number of rolls>,
        "loots": [
          {
            "item": "minecraft:diamond_sword",
            "nbt": { "item_name": "Legendary Sword" },
            "count": 1,
            "chance": 0.1
          },
          {
            "item": "minecraft:iron_nugget",
            "nbt": { "item_name": "Shield" },
            "count": 1,
            "chance": 0.4
          }
        ]
      }
    }
  }
}
```

Complete example for template `manaria` with `default` alias across all four difficulties:

```json
{
  "manaria": {
    "BEGINNER": {
      "default": {
        "iterations": 2,
        "loots": [
          {"item":"minecraft:emerald","nbt":{},"count":1,"chance":0.2},
          {"item":"minecraft:gold_nugget","nbt":{},"count":2,"chance":0.5}
        ]
      }
    },
    "NORMAL": {
      "default": {
        "iterations": 3,
        "loots": [
          {"item":"minecraft:emerald","nbt":{},"count":1,"chance":0.4},
          {"item":"minecraft:gold_ingot","nbt":{},"count":1,"chance":0.25},
          {"item":"minecraft:iron_nugget","nbt":{},"count":3,"chance":0.6}
        ]
      }
    },
    "HEROIC": {
      "default": {
        "iterations": 4,
        "loots": [
          {"item":"minecraft:diamond","nbt":{},"count":1,"chance":0.05},
          {"item":"minecraft:gold_ingot","nbt":{},"count":2,"chance":0.4},
          {"item":"minecraft:iron_ingot","nbt":{},"count":5,"chance":0.8}
        ]
      }
    },
    "MYTHIC": {
      "default": {
        "iterations": 5,
        "loots": [
          {"item":"minecraft:nether_star","nbt":{},"count":1,"chance":0.01},
          {"item":"minecraft:diamond","nbt":{},"count":2,"chance":0.2},
          {"item":"minecraft:gold_ingot","nbt":{},"count":4,"chance":0.5}
        ]
      }
    }
  }
}
```

In this example, increasing difficulty raises both `iterations` and drop chances; adjust these values to suit your needs.

### Loot Table Fields

- `iterations` – number of times the pool is visited; each iteration randomly picks an item from `loots`.
- `loots` – array of possible entries. For each entry, `chance` is compared to a random value (`0.0`–`1.0`); if the roll succeeds, the item is added to drops.
- `item` – Bukkit/Spigot material ID (`DIAMOND_SWORD`, `IRON_NUGGET`, etc.).
- `nbt` – free mapping for item name (`item_name`), lore, etc. Only simple keys are supported currently (see source code).
- `count` – quantity of items to generate when the entry is selected.

### Assigning Aliases

While editing a template, look at a mob and run:

```
/dungeon admin alias <alias>
```

The plugin shows the current alias if no argument is given. To clear the alias, use `none`:

```
/dungeon admin alias none
```

The alias is saved with the mob and automatically restored in instances.

### Reloading Configuration

After editing `lootTables.json` on disk, reload changes in-game with:

```
/dungeon admin reloadloot
```

### In-Game Behavior

When a mob with an alias dies in an instance, vanilla drops are removed and replaced with items calculated from the matching pool (template + difficulty + alias). If no table is found, vanilla behavior is unchanged.

## Customization & Suggestions

- You can modify the scoreboard update frequency (`DungeonScoreboardManager`) and message formats in the code.
- Possible improvements: multi-language support, config via `config.yml`, options to hide certain HUD elements.
