# Backpacks — how to use

A backpack is a wearable item with its own inventory. It is equipped on the player, opened
with a key and upgraded by crafting without losing its contents. There are 17 tiers, from
the 9-slot *Linen Pouch* to the 256-slot *Ultimate Backpack*.

- Русская версия: [Как пользоваться — Русский](../how%20to%20use%20RU/README.md)
- Short overview and tier table: [README.md](../README.md)
- Downloads: [Releases](https://github.com/MrLan112/Backpacks-Plugin-Mod/releases)

---

## 1. Requirements

| Part | Version |
| --- | --- |
| Server | Purpur or Paper **26.3** |
| Java | **25** |
| Client mod (optional) | Fabric Loader **0.20.1+**, Fabric API **0.166.2+26.3**, Minecraft **26.3** |

The plugin works without the mod: backpacks can be opened, equipped and crafted. The mod
adds a 3D backpack on the player's back, a dedicated inventory slot, a one-screen window and
its own open key. Every tier has a detailed model (side pockets, metal corner caps,
compression straps, emblem, bedroll, pads and a lock — the higher the tier, the more parts).

## 2. Installation

### Server (admin)

1. Put `Backpacks-2.0.1.jar` into `plugins/`.
2. Start the server. On first start it creates:
   - `plugins/Backpacks/config.yml` — settings;
   - `plugins/Backpacks/messages.yml` — RU/EN texts;
   - `plugins/Backpacks/backpacks.db` — backpack contents storage.
3. Give the first backpack: `/bp give <player> 1`.

### Client (for the 3D model and the inventory slot)

1. Install Fabric Loader 0.20.1+ for Minecraft 26.3 and Fabric API.
2. Put `backpacks-mod-2.0.1.jar` into `mods/`.
3. Launch the game and join the server.

The mod is client-side only: without the server plugin it does nothing.

## 3. First steps

1. **Get a backpack.** Ask an admin (`/bp give <name> 1`) or craft a *Linen Pouch*:
   5 string + 2 leather, anywhere in a 3×3 grid.
2. **Equip it.** Left-click the backpack slot in the survival inventory (mod), right-click a
   backpack in your own inventory, Shift+right-click a backpack in hand, or use `/bp equip`.
3. **Open it.** With the mod press the open key (default **B**) or right-click the backpack
   slot; without the mod right-click with an empty hand. `/bp` and right-clicking a backpack
   in hand also work.
4. **Store your items** and close the window (E or ESC). Everything is saved automatically.
5. **Upgrade** the backpack by crafting — the items inside stay in place.

## 4. Controls

| Action | Without the mod | With the mod |
| --- | --- | --- |
| Open the equipped backpack | Right-click with an empty hand, `/bp` | Open key (default **B**), right-click the backpack slot, right-click with an empty hand, `/bp` |
| Open a backpack in hand | Right-click the backpack | Right-click the backpack |
| Equip | Right-click a backpack in your own inventory, Shift+right-click in hand, `/bp equip` | Left-click the backpack slot, right-click a backpack in your inventory |
| Unequip | Shift+right-click with an empty hand, `/bp unequip` | Left-click the backpack slot, **Shift + open key**, Shift+right-click with an empty hand, `/bp unequip` |
| Take the worn backpack to the cursor | — | Left-click the backpack slot with an empty cursor |
| Put a backpack from the cursor into the slot | — | Left-click the slot with a backpack on the cursor (replaces the worn one) |
| Take an item from the backpack into your hand | — | **Middle-click** it in the backpack window (a stack, or everything left when it is smaller); pick-block (MMB) in the world also finds items inside the equipped backpack |

The open key can be changed in **Options → Controls → Backpacks**
(`key.backpacks.open`). The item lore and messages always show the key that is actually
bound.

`F` opens the backpack only when `hotkey: SWAP_HANDS` is set in the config. The default is
`NONE`: the mod key, the backpack slot and `/bp` already cover every case, and `F` stays a
vanilla offhand swap.

Limits: a backpack cannot be placed in an item frame or equipped on a horse, and
right-clicking a jukebox with a backpack opens the backpack instead of playing the disc.

## 5. The backpack window

- With the mod the whole backpack opens **on one screen without pages**: a chest-style
  window, and large tiers (up to 256 cells) are scaled down to fit. Without the mod the
  regular chest window with pages is used.
- Capacity is counted in **cells**. A cell holds one stack of an item, and from tier 9
  onward up to **27 stacks** of the same item.
- **LMB** — take/put a stack, **RMB** — one item, **Shift+LMB** on a cell — take everything
  from it, **Shift+LMB** on an item in your inventory — put it into the backpack.
- Chest shortcuts work too: **number keys 1–9** swap a cell with that hotbar slot, **Q**
  drops one item, **Ctrl+Q** drops the stack, **double-click** collects matching items,
  and **dragging** distributes items.
- **MMB** on a cell — take a stack from the backpack into your hand, or everything left
  when there is less than a stack. The same works in the world: pick-block (MMB) on a
  block or mob finds the item in the equipped backpack.
- A backpack cannot be placed inside another backpack.
- The window shows the tier name, the number of cells, the total item count in a bulk cell
  and click hints; the cursor is rendered from server snapshots, so an item is never lost
  on logout or death.

## 6. Crafting and upgrading

All recipes are **shapeless**: put the materials and the previous tier backpack anywhere
in a 3×3 grid. Extra materials of the same type stay in the grid.

- The first tier is crafted from scratch.
- Every next tier requires the **previous tier backpack** plus materials.
- Upgrading keeps the contents and the storage UUID: the items stay inside, only the
  tier and capacity change.
- Show all 17 recipes in game: `/bp recipes` (they are also in the recipe book).

Examples:

| Tier | Recipe |
| --- | --- |
| 1. Linen Pouch | 5 string + 2 leather |
| 2. Leather Satchel | Linen Pouch + 4 leather + 2 string |
| 3. Copper Pack | Leather Satchel + 6 copper ingots + 2 leather |
| … | … |
| 17. Ultimate Backpack | Dragon Pack + 2 nether stars + 2 end crystals + 2 netherite ingots + 1 dragon breath + 1 ender pearl |

Full tier and material table: [README.md → Tiers](../README.md#tiers).

## 7. Commands and permissions

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
| `backpacks.modbypass` | OP | Join without the mod when `require-mod: true` |

## 8. Configuration (`config.yml`)

```yaml
language: ru            # ru or en

equipment:
  keep-on-death: true   # keep the equipped backpack after death

storage:
  file: backpacks.db

hotkey: NONE            # SWAP_HANDS = F without the mod; NONE disables the key
open-empty-hand: true   # right-click with an empty hand opens the equipped backpack
require-mod: false      # kick players who do not have the client mod

gui:
  single-screen: true   # one screen without pages for modded clients (vanilla keeps the chest GUI)
  filler: GRAY_STAINED_GLASS_PANE
  sound-open: BLOCK_CHEST_OPEN
  sound-close: BLOCK_CHEST_CLOSE
  sound-click: UI_BUTTON_CLICK
  sound-upgrade: BLOCK_ANVIL_USE

autosave-seconds: 30    # 0 = save only on close
```

Messages and item lore live in `messages.yml` (`ru` and `en` sections). The file has a
`messages-version`: an outdated file is backed up as `messages.yml.<timestamp>.bak` and
replaced, so new keys are never missing.

## 9. Data safety

Items are stored **not inside the item** but in `plugins/Backpacks/backpacks.db` (SQLite),
keyed by the UUID carried by the item. The item only holds the tier and that UUID, so an
upgrade never loses items.

- Saves happen: 1 second after the last GUI click, on GUI close, on player quit, every
  30 seconds and synchronously on server shutdown.
- Opening waits for pending writes, so closing and immediately reopening never rolls
  anything back.
- If the database is unavailable or the data cannot be decoded, the backpack **does not
  open**: otherwise closing an empty window would overwrite the real contents.
- Two players cannot open the same backpack at the same time (protects against item
  copies).
- SQLite runs in WAL mode with `synchronous=FULL`: confirmed writes survive a power loss.
  A hard server crash can lose at most about one second of changes after the last click.

**Back up `backpacks.db` periodically** — preferably while the server is stopped; on a
running server copy `backpacks.db-wal` and `backpacks.db-shm` as well.

**Can I delete the `.jar` and keep the plugin folder?** Yes. All data lives in
`plugins/Backpacks/` (`config.yml`, `messages.yml`, `backpacks.db`), not in the jar. Delete
only `Backpacks-*.jar` and install the new jar — the folder and every stored backpack stay.
Deleting the whole `plugins/Backpacks` folder deletes the settings and all backpack
contents; if you do not need them, the folder can be removed too.

What can destroy the **item** (contents stay in the database, but without the item they
cannot be reached): lava, fire, the void, cactus, `/clear`, clearing plugins,
`/bp remove`, or dying in lava/void with `keep-on-death: false`.

## 10. FAQ

**The backpack does not open and says "Database error".**
`backpacks.db` is unavailable or damaged. Check permissions on `plugins/Backpacks`, disk
space and the server log. The plugin deliberately refuses to open an empty window so that
items are not lost.

**"This backpack is already open by another player".**
One storage cannot be edited by two players at once. Close it for the second player (or
this is a creative copy of the item — use a single instance).

**"No room in your inventory — the backpack stays equipped".**
Free a slot and unequip again. A backpack is never dropped on the ground when unequipped.

**No 3D model / no inventory slot.**
The client mod or Fabric API is missing. Without the mod the backpack looks like a regular
item but works fully.

**The key does not open the backpack.**
Check the bind in **Options → Controls → Backpacks**. Without the mod, `F` is used only
when `hotkey: SWAP_HANDS` (default `NONE`), and the backpack must be equipped. With the mod,
right-clicking the backpack slot and `/bp` also work.

**Where did my items go after an upgrade?**
They are inside: upgrading only changes the tier and capacity. Open the backpack — with the
mod all cells are visible at once, without pages.

**Why does the backpack no longer glow?**
The glint was removed on purpose: the backpack is not enchanted, and the tier is shown by
the name gradient and the model colours.

**How do I move backpacks to another server?**
Move `backpacks.db` together with the items (or give new ones). Contents are tied to the
UUID in the database, not to the player name.
