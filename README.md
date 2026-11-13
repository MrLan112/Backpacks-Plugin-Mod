# Backpacks

Wearable, upgradeable backpacks for **Purpur/Paper 26.3**, with an optional **Fabric 26.3** client mod that puts a real 3D backpack on the player's back.

**Documentation:** [How to use — English](how%20to%20use%20EN/README.md) · [Как пользоваться — Русский](how%20to%20use%20RU/README.md) · [Releases](https://github.com/MrLan112/Backpacks-Plugin-Mod/releases)

- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Controls](#controls)
- [Commands & permissions](#commands--permissions)
- [Tiers](#tiers)
- [Configuration](#configuration)
- [Data safety](#data-safety)
- [Building from source](#building-from-source)
- [Project layout](#project-layout)
- [License](#license)
- [Русский](#русский)

## Features

- **17 tiers** — from the 9-slot *Linen Pouch* to the 256-slot *Ultimate Backpack*.
- **Upgrade chain** — every tier is crafted from the previous backpack plus materials; contents and the storage UUID survive the upgrade (the recipe book shows all 17 recipes).
- **Bulk cells** — starting with tier 9, one GUI cell can hold up to **27 stacks** of the same item.
- **Wearable** — the backpack sits in a dedicated slot, not in a chest. Equip and unequip without commands:
  - right-click a backpack in your inventory,
  - click the backpack slot in the survival inventory (mod),
  - **Shift + key** or **Shift + F** to take it off.
- **3D model** — with the client mod each tier has its own body/accent colours and a backpack model on the player's back (follows sneaking, swimming and elytra).
- **No commands from the mod** — the client talks to the server through custom payloads (`backpacks:open`, `backpacks:toggle`, `backpacks:unequip`, `backpacks:hello`, `backpacks:key`); the server answers with `backpacks:sync`.
- **SQLite storage** — contents are stored per backpack UUID, not inside the item, and survive upgrades, death (configurable) and server restarts.
- **RU + EN** — all messages and item lore are localised; the lore shows the key that is actually bound in the client controls.
- **Vanilla fallback** — the plugin is fully playable without the client mod (the model is only available with the mod).

## Requirements

| Part | Version |
| --- | --- |
| Server | Purpur or Paper **26.3** (`api-version: '26.3'`) |
| Java | **25** |
| Client mod (optional) | Fabric Loader **0.20.1+**, Fabric API **0.166.2+26.3**, Java **25**, Minecraft **26.3** |

## Installation

### Server

1. Download `Backpacks-1.0.0.jar` from [Releases](https://github.com/MrLan112/Backpacks-Plugin-Mod/releases) and put it into `plugins/`.
2. Start the server. On first start the plugin creates:
   - `plugins/Backpacks/config.yml`
   - `plugins/Backpacks/messages.yml`
   - `plugins/Backpacks/backpacks.db`
3. Give yourself a backpack: `/bp give <player> 1` (admin).

### Client (for the 3D model, inventory slot and hotkey)

1. Install Fabric Loader 0.20.1+ for Minecraft 26.3 and Fabric API.
2. Download `backpacks-mod-1.0.0.jar` from [Releases](https://github.com/MrLan112/Backpacks-Plugin-Mod/releases) and put it into `.minecraft/mods/`.
3. Launch the game and join the server.

Without the mod the plugin still works (commands, inventory right-click, `F`/Shift+F hotkeys), but the backpack-specific item model and the 3D back model are not available on a vanilla client.

## Controls

| Action | Without the mod | With the mod |
| --- | --- | --- |
| Open the equipped backpack | `F` (configurable hotkey), right-click with an empty hand, `/bp` | Open key (default **B**), right-click the backpack slot, `F`, right-click empty hand |
| Open a backpack in hand | Right-click the backpack | Right-click the backpack |
| Equip | Right-click a backpack in your own inventory, or **Shift + right-click** a backpack in hand, or `/bp equip` | **Left-click** the backpack slot |
| Unequip | **Shift + F**, or **Shift + right-click** with an empty hand, or `/bp unequip` | **Shift + open key**, or left-click the backpack slot |
| Swap to another backpack | **Shift + right-click** another backpack in hand | **Shift + right-click** another backpack in hand |

The open key can be changed in **Options → Controls → Backpacks**. The item lore and messages always show the key that is actually bound.

Backpacks cannot be put on a horse (legacy stacks) or placed in an item frame, and right-clicking a jukebox with a backpack opens the backpack instead of playing the disc.

## Commands & permissions

| Command | Permission | Description |
| --- | --- | --- |
| `/bp` | `backpacks.use` | Open the equipped or held backpack |
| `/bp recipes` | `backpacks.use` | Show all 17 recipes |
| `/bp equip` | `backpacks.use` | Equip the backpack in your hand |
| `/bp unequip` | `backpacks.use` | Unequip the backpack |
| `/bp give <player> <tier> [amount]` | `backpacks.admin` | Give a backpack (tier 1–17 or id) |
| `/bp remove` | `backpacks.admin` | Delete the held backpack and its contents |
| `/bp reload` | `backpacks.admin` | Reload config and messages |

Aliases: `/backpack`, `/bp`, `/rukzak`, `/рюкзак`.

| Permission | Default | Description |
| --- | --- | --- |
| `backpacks.use` | everyone | Use backpacks |
| `backpacks.admin` | OP | Give, remove and reload |
| `backpacks.modbypass` | OP | Join without the client mod when `require-mod: true` |

## Tiers

Tiers 9–17 hold up to **27 stacks per cell**; lower tiers hold one stack per cell. Every tier after the first also requires the previous tier backpack in the recipe (not listed again in the materials column).

| # | Tier (RU / EN) | Slots | Upgrade materials |
| --- | --- | --- | --- |
| 1 | Льняной мешочек / Linen Pouch | 9 | 5 string, 2 leather |
| 2 | Кожаный ранец / Leather Satchel | 15 | 4 leather, 2 string |
| 3 | Медный рюкзак / Copper Pack | 21 | 6 copper ingot, 2 leather |
| 4 | Железный рюкзак / Iron Pack | 27 | 6 iron ingot, 2 leather |
| 5 | Золотой рюкзак / Golden Pack | 33 | 6 gold ingot, 2 leather |
| 6 | Рюкзак редстоуна / Redstone Pack | 39 | 6 redstone, 2 gold ingot |
| 7 | Лазурный рюкзак / Lapis Pack | 45 | 6 lapis lazuli, 2 gold ingot |
| 8 | Аметистовый рюкзак / Amethyst Pack | 51 | 6 amethyst shard, 2 gold ingot |
| 9 | Изумрудный рюкзак / Emerald Pack | 57 | 6 emerald, 2 gold ingot |
| 10 | Кварцевый рюкзак / Quartz Pack | 63 | 6 quartz, 2 gold ingot |
| 11 | Обсидиановый рюкзак / Obsidian Pack | 69 | 6 obsidian, 2 diamond |
| 12 | Алмазный рюкзак / Diamond Pack | 75 | 6 diamond, 2 gold ingot |
| 13 | Пламенный рюкзак / Blaze Pack | 81 | 5 blaze rod, 3 magma cream |
| 14 | Эндер-рюкзак / Ender Pack | 90 | 5 ender pearl, 3 chorus fruit |
| 15 | Незеритовый рюкзак / Netherite Pack | 99 | 4 netherite ingot, 4 echo shard |
| 16 | Драконий рюкзак / Dragon Pack | 108 | 3 dragon breath, 3 netherite ingot, 2 ender pearl |
| 17 | Абсолютный рюкзак / Ultimate Backpack | 256 | 2 nether star, 2 end crystal, 2 netherite ingot, 1 dragon breath, 1 ender pearl |

Recipes are shapeless: put the required materials and the previous backpack anywhere in a 3×3 grid. Extra materials of the same types stay in the grid.

## Configuration

`plugins/Backpacks/config.yml`:

```yaml
language: ru            # ru or en

equipment:
  keep-on-death: true   # keep the equipped backpack after death

storage:
  file: backpacks.db

hotkey: SWAP_HANDS      # F without the mod; NONE disables it
open-empty-hand: true   # right-click with an empty hand opens the equipped backpack
require-mod: false      # kick players who do not have the client mod

gui:
  filler: GRAY_STAINED_GLASS_PANE
  sound-open: BLOCK_CHEST_OPEN
  sound-close: BLOCK_CHEST_CLOSE
  sound-click: UI_BUTTON_CLICK
  sound-upgrade: BLOCK_ANVIL_USE

autosave-seconds: 30    # 0 = save only on close
```

Messages and item lore are in `plugins/Backpacks/messages.yml` (`ru` and `en` sections). The file has a `messages-version`; an outdated file is backed up as `messages.yml.<timestamp>.bak` and replaced, so new keys are never missing.

## Data safety

Backpack contents are stored in SQLite (`plugins/Backpacks/backpacks.db`) keyed by the UUID carried by the item. The item itself only carries the tier and that UUID, so upgrading the tier never loses items.

- Saves happen **1 second after the last GUI click**, on GUI close, on player quit, every 30 seconds and synchronously on server shutdown.
- Opening a backpack waits for pending writes, so closing and immediately reopening never rolls items back.
- If the database is unavailable or the stored data cannot be decoded, the GUI does **not** open: otherwise closing an empty GUI would overwrite the real contents.
- Two players cannot open the same storage at the same time (protects against creative copies and old shared stacks).
- SQLite runs in WAL mode with `synchronous=FULL`; a hard crash can lose at most about one second of GUI changes.

What can still destroy the **item** (the contents stay in the database, but the item is gone): lava, fire, the void, cactus, `/clear`, clearing plugins, `/bp remove`, or dying in lava/void with `keep-on-death: false`. Make periodic backups of `backpacks.db` (preferably while the server is stopped, or together with `backpacks.db-wal` and `backpacks.db-shm`).

## Building from source

You need **JDK 25** and a network connection for the first Gradle run.

```bash
git clone https://github.com/MrLan112/Backpacks-Plugin-Mod.git
cd Backpacks-Plugin-Mod/Backpacks

# Windows
gradlew.bat build

# Linux / macOS
./gradlew build
```

Output:

- `plugin/build/libs/Backpacks-1.0.0.jar` — server plugin (SQLite driver shaded in)
- `mod/build/libs/backpacks-mod-1.0.0.jar` — Fabric client mod

The Gradle wrapper downloads Gradle 9.9.1 automatically. The mod's models, item definitions and textures can be regenerated with:

```bash
java tools/GenPack.java mod/src/main/resources
```

## Project layout

```
.
├── Backpacks/
│   ├── plugin/          # Purpur/Paper plugin (Java 25, Paper API 26.3)
│   ├── mod/             # Fabric client mod (Minecraft 26.3, Fabric API)
│   ├── tools/GenPack.java
│   ├── gradlew / gradlew.bat
│   └── README.md        # detailed developer notes (Russian)
├── how to use EN/       # player & admin guide (English)
├── how to use RU/       # руководство для игроков и админов (Russian)
├── LICENSE
└── README.md
```

## License

[MIT](LICENSE).

---

## Русский

Носимые улучшаемые рюкзаки для **Purpur/Paper 26.3** с клиентским модом **Fabric 26.3**, который рисует 3D-рюкзак на спине.

- **17 тиров**: от «Льняного мешочка» на 9 ячеек до «Абсолютного рюкзака» на 256 ячеек.
- **Цепочка крафта**: каждый тир крафтится из рюкзака предыдущего тира и материалов; содержимое и UUID сохраняются при улучшении. Все рецепты видны в книге рецептов.
- **Ячейки-стаки**: с 9-го тира одна ячейка вмещает до **27 стаков** одного предмета.
- **Надевание без команд**: ПКМ по рюкзаку в инвентаре, ЛКМ по слоту рюкзака (с модом), снятие — **Shift+клавиша** или **Shift+F**.
- **Хранение**: SQLite по UUID предмета, содержимое не теряется при улучшении и перезапуске; есть защита от перезаписи и повреждения данных.
- **RU + EN**: сообщения и лор предметов, клавиша в лоре берётся из настроек клиента.
- **Без мода** плагин полностью работает, но 3D-модель и слот в инвентаре доступны только с модом.

**Подробные инструкции:** [Как пользоваться — Русский](how%20to%20use%20RU/README.md) · [How to use — English](how%20to%20use%20EN/README.md)

**Установка:** `Backpacks-1.0.0.jar` → `plugins/`, `backpacks-mod-1.0.0.jar` → `mods/` клиента (Fabric).

**Сборка из исходников:** нужен JDK 25; в папке `Backpacks` выполните `gradlew.bat build` (Windows) или `./gradlew build`.
