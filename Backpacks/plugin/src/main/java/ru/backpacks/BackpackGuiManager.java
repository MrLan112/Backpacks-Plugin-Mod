package ru.backpacks;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.DragType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side backpack window. It is a real chest inventory, so every client —
 * modded or vanilla — gets native Minecraft slots: left/right click, shift-click,
 * number keys, Q/Ctrl+Q, double-click and dragging work exactly like a chest.
 * Backpacks up to 45 slots open in a chest sized to their capacity; larger tiers
 * use 45-cell pages with prev/page/next arrows in the last row. Contents live in
 * memory while the window is open and are persisted to SQLite.
 *
 * <p>Starting with tier 9 every cell can hold up to 27 stacks of the same item
 * ({@link Tier#stacksPerCell()}). Such cells are rendered as one stack with the
 * total amount in the lore; clicks are handled manually, so one cell can hold
 * more items than a vanilla stack.
 */
public final class BackpackGuiManager implements Listener {
    /** Content cells per page (5 chest rows). */
    public static final int CONTENT = 45;
    /** Normal (single-stack) tiers use plain chest slots, so they need no page row. */
    private static final int NAV_ROW = CONTENT;
    private static final ItemStack EMPTY = new ItemStack(Material.AIR);
    /** Safety limit for shift-taking a huge cell into the player inventory. */
    private static final int MAX_TAKE_CHUNKS = 4096;

    private final BackpacksPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    /** Storage UUID -> the session that currently has it open (guards duplicate items). */
    private final Map<UUID, Session> openById = new HashMap<>();
    private final NamespacedKey extraKey;
    private BukkitTask autosave;

    public BackpackGuiManager(BackpacksPlugin plugin) {
        this.plugin = plugin;
        this.extraKey = new NamespacedKey(plugin, "cell_extra");
    }

    public void start() {
        reload();
    }

    public void reload() {
        if (autosave != null) {
            autosave.cancel();
            autosave = null;
        }
        long seconds = plugin.getConfig().getLong("autosave-seconds", 30);
        if (seconds > 0) {
            autosave = Bukkit.getScheduler().runTaskTimer(plugin, this::saveAll, seconds * 20L, seconds * 20L);
        }
    }

    private boolean isBackpack(ItemStack stack) {
        return plugin.items().isBackpack(stack);
    }

    // ------------------------------------------------------------------ open

    /** Opens a backpack item. When {@code equipped} is true the item is stored on the player. */
    public boolean open(Player player, ItemStack item, boolean equipped) {
        return open(player, item, equipped, EquipmentSlot.HAND, -1);
    }

    /**
     * Opens a backpack item taken from {@code source}. The migrated stack is written back
     * into that slot, so opening from the offhand never duplicates or eats the main hand.
     */
    public boolean open(Player player, ItemStack item, boolean equipped, EquipmentSlot source) {
        return open(player, item, equipped, source, -1);
    }

    /** Opens a backpack that stays in inventory slot {@code inventorySlot}. */
    public boolean openFromInventory(Player player, ItemStack item, int inventorySlot) {
        return open(player, item, false, EquipmentSlot.HAND, inventorySlot);
    }

    private boolean open(Player player, ItemStack item, boolean equipped, EquipmentSlot source,
                         int inventorySlot) {
        Tier tier = plugin.items().tierOf(item);
        if (tier == null) {
            plugin.msg().send(player, "no-backpack");
            return false;
        }
        // Without a working database the GUI would keep items in memory only and drop
        // them on close, so refuse instead of pretending everything is fine.
        if (!plugin.store().ready()) {
            plugin.msg().send(player, "storage-error");
            return false;
        }

        item = plugin.items().migrate(item, plugin.modBridge().displayKey(player));
        UUID id = plugin.items().ensureId(item);

        // Two copies of one backpack (creative clones, old shared stacks) must not edit
        // the same storage at once: the last one closed would overwrite the other.
        Session holder = openById.get(id);
        if (holder != null && holder.player != player) {
            plugin.msg().send(player, "backpack-in-use");
            return false;
        }

        // Save and close the player's previous GUI first, so opening another backpack
        // loads the newest contents instead of stale database data.
        Session existing = sessions.get(player.getUniqueId());
        if (existing != null && existing.id.equals(id)) {
            // The same backpack is already open. Keep the live session: its last changes
            // may still be queued for SQLite, so reloading it now could roll them back.
            return true;
        }
        if (existing != null) {
            sessions.remove(player.getUniqueId());
            release(existing);
            persist(existing);
            player.closeInventory();
        }

        // Writes are queued (closing the previous GUI, the debounced save, autosave).
        // Wait for them, otherwise this read could return older contents and the next
        // save would roll the player's items back.
        if (!plugin.store().flush(3000L)) {
            plugin.msg().send(player, "storage-error");
            return false;
        }

        BackpackStore.Contents stored = plugin.store().loadContents(id);
        if (stored.failed()) {
            plugin.msg().send(player, "storage-error");
            return false;
        }

        Cell[] contents = new Cell[tier.capacity()];
        if (stored.data() != null && stored.data().length > 0) {
            try {
                ItemStack[] loaded = ItemStack.deserializeItemsFromBytes(stored.data());
                for (int i = 0; i < loaded.length; i++) {
                    Cell cell = cellOf(loaded[i]);
                    if (cell == null) {
                        continue;
                    }
                    if (i < contents.length) {
                        contents[i] = cell;
                    } else {
                        giveCell(player, cell);
                    }
                }
            } catch (RuntimeException exception) {
                // Do not open an empty GUI: closing it would erase the stored contents.
                plugin.getLogger().severe("Could not decode backpack data " + id
                        + ": " + exception.getMessage());
                plugin.msg().send(player, "storage-error");
                return false;
            }
        }

        if (equipped) {
            plugin.items().setEquipped(player, item);
        } else if (inventorySlot >= 0) {
            player.getInventory().setItem(inventorySlot, item);
        } else if (source == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(item);
        } else {
            player.getInventory().setItemInMainHand(item);
        }

        Session session = new Session(player, id, tier, equipped, contents);
        sessions.put(player.getUniqueId(), session);
        openById.put(id, session);

        // Every client (with or without the mod) gets the normal chest-style window.
        // Vanilla then owns clicks, shift-clicks, number keys, Q, double-click and drag,
        // so the mechanics behave exactly like a chest; large tiers use page arrows.
        session.inventory = Bukkit.createInventory(session, session.size,
                plugin.msg().component("gui-title", "tier", tier.name(plugin.msg().russian())));
        render(session);
        player.openInventory(session.inventory);
        sound(player, "sound-open", 0.7f, 1.0f);
        return true;
    }

    public boolean openEquipped(Player player) {
        ItemStack item = plugin.items().equipped(player);
        if (item == null) {
            plugin.msg().send(player, "no-backpack");
            return false;
        }
        return open(player, item, true);
    }

    /** RMB on the backpack slot: the worn backpack first, otherwise one from the inventory. */
    public void openFromSlot(Player player) {
        if (plugin.items().equipped(player) != null) {
            openEquipped(player);
            return;
        }
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (plugin.items().isBackpack(item)) {
                openFromInventory(player, item, slot);
                return;
            }
        }
        plugin.msg().send(player, "no-backpack");
    }

    /**
     * LMB on the backpack slot. An empty cursor takes the worn backpack to the cursor
     * (or equips the first backpack from the inventory); a backpack on the cursor is
     * put into the slot, replacing the worn one.
     */
    public void slotTakePut(Player player) {
        ItemStack cursor = player.getItemOnCursor();
        boolean cursorEmpty = cursor == null || cursor.getType().isAir();
        ItemStack worn = plugin.items().equipped(player);

        if (cursorEmpty) {
            if (worn != null) {
                unequipToCursor(player);
                return;
            }
            equipFromInventory(player);
            return;
        }

        Tier tier = plugin.items().tierOf(cursor);
        if (tier == null) {
            plugin.msg().send(player, "slot-cursor-not-backpack");
            return;
        }

        closeSilently(player);
        ItemStack one = equipCopy(player, cursor);
        int left = cursor.getAmount() - 1;
        ItemStack rest = left <= 0 ? new ItemStack(Material.AIR) : cursor.asQuantity(left);
        if (worn != null) {
            if (rest.getType().isAir()) {
                rest = worn;
            } else {
                give(player, worn);
            }
        }
        player.setItemOnCursor(rest);
        plugin.items().setEquipped(player, one);
        plugin.msg().send(player, "equipped",
                "tier", tier.name(plugin.msg().russian()),
                "key", plugin.modBridge().displayKey(player));
        sound(player, "sound-click", 0.7f, 1.1f);
        plugin.modBridge().broadcastSync(player);
        player.updateInventory();
    }

    // ----------------------------------------------------------------- cells

    /** One GUI cell: a prototype stack plus the total amount of that item. */
    private static final class Cell {
        private ItemStack proto;
        private int count;

        private int maxStack() {
            return proto == null ? 64 : Math.max(1, proto.getType().getMaxStackSize());
        }

        private int limit(Tier tier) {
            return maxStack() * tier.stacksPerCell();
        }
    }

    private Cell cellOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        int extra = 0;
        if (stack.hasItemMeta()) {
            Integer stored = stack.getItemMeta().getPersistentDataContainer()
                    .get(extraKey, PersistentDataType.INTEGER);
            if (stored != null) {
                extra = Math.max(0, stored);
                stack.editMeta(meta -> meta.getPersistentDataContainer().remove(extraKey));
            }
        }
        Cell cell = new Cell();
        cell.proto = stack.clone();
        cell.proto.setAmount(1);
        cell.count = stack.getAmount() + extra;
        return cell;
    }

    private ItemStack display(Cell cell) {
        ItemStack stack = cell.proto.clone();
        int max = cell.maxStack();
        stack.setAmount(Math.min(cell.count, max));
        if (cell.count > max) {
            int total = cell.count;
            int stacks = (cell.count + max - 1) / max;
            stack.editMeta(meta -> {
                List<Component> lore = meta.lore();
                lore = lore == null ? new ArrayList<>() : new ArrayList<>(lore);
                lore.add(plugin.msg().component("cell-count", "amount", total, "stacks", stacks));
                meta.lore(lore);
            });
        }
        return stack;
    }

    private void giveCell(Player player, Cell cell) {
        int max = cell.maxStack();
        int left = cell.count;
        while (left > 0) {
            ItemStack chunk = cell.proto.clone();
            int amount = Math.min(left, max);
            chunk.setAmount(amount);
            give(player, chunk);
            left -= amount;
        }
    }

    // ---------------------------------------------------------------- render

    private void render(Session session) {
        Inventory inventory = session.inventory;
        inventory.clear();

        int base = session.page * CONTENT;
        int count = Math.min(CONTENT, session.tier.capacity() - base);
        for (int slot = 0; slot < count; slot++) {
            Cell cell = session.contents[base + slot];
            inventory.setItem(slot, cell == null ? null : display(cell));
        }

        if (session.paged) {
            if (session.page > 0) {
                inventory.setItem(NAV_ROW, button(plugin.msg().component("prev")));
            }
            inventory.setItem(NAV_ROW + 4, pageItem(session));
            if (session.page < session.tier.pages() - 1) {
                inventory.setItem(NAV_ROW + 8, button(plugin.msg().component("next")));
            }
        }
    }

    private ItemStack button(Component name) {
        ItemStack stack = new ItemStack(Material.ARROW);
        stack.editMeta(meta -> meta.displayName(name));
        return stack;
    }

    /** Page indicator in the middle of the navigation row. */
    private ItemStack pageItem(Session session) {
        ItemStack stack = new ItemStack(Material.PAPER);
        stack.editMeta(meta -> {
            meta.displayName(plugin.msg().component("page",
                    "page", session.page + 1,
                    "pages", session.tier.pages()));
            List<Component> lore = new ArrayList<>();
            lore.add(plugin.msg().component("capacity", "size", session.tier.capacity()));
            if (session.tier.stacksPerCell() > 1) {
                lore.add(plugin.msg().component("bulk-info", "stacks", session.tier.stacksPerCell()));
                lore.add(plugin.msg().component("bulk-hint"));
            }
            lore.add(plugin.msg().component("close-hint"));
            meta.lore(lore);
        });
        return stack;
    }

    private void sound(Player player, String key, float volume, float pitch) {
        String configured = plugin.getConfig().getString("gui." + key, "");
        if (configured == null || configured.isBlank()) {
            return;
        }
        player.playSound(player.getLocation(), soundKey(configured), volume, pitch);
    }

    private static String soundKey(String configured) {
        if (configured.indexOf('.') >= 0 || configured.indexOf(':') >= 0) {
            return configured;
        }
        return configured.toLowerCase(java.util.Locale.ROOT).replace('_', '.');
    }

    private void turnPage(Session session, int delta) {
        syncPage(session);
        int target = session.page + delta;
        if (target < 0 || target >= session.tier.pages()) {
            return;
        }
        session.page = target;
        render(session);
        sound(session.player, "sound-click", 0.6f, 1.4f);
    }

    // --------------------------------------------------------------- persist

    /**
     * Copies the page back from the inventory. Bulk tiers keep their cells in memory
     * (clicks are handled manually), so the inventory only holds display copies there.
     */
    private void syncPage(Session session) {
        if (session.inventory == null || session.tier.stacksPerCell() > 1) {
            return;
        }
        int base = session.page * CONTENT;
        int count = Math.min(CONTENT, session.tier.capacity() - base);
        for (int slot = 0; slot < count; slot++) {
            session.contents[base + slot] = cellOf(session.inventory.getItem(slot));
        }
    }

    private byte[] serialize(Session session) {
        return serialize(session.contents);
    }

    private byte[] serialize(Cell[] contents) {
        ItemStack[] array = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            Cell cell = contents[i];
            if (cell == null || cell.count <= 0) {
                array[i] = EMPTY;
                continue;
            }
            ItemStack stack = cell.proto.clone();
            int max = cell.maxStack();
            int shown = Math.min(cell.count, max);
            stack.setAmount(shown);
            int extra = cell.count - shown;
            if (extra > 0) {
                stack.editMeta(meta -> meta.getPersistentDataContainer()
                        .set(extraKey, PersistentDataType.INTEGER, extra));
            }
            array[i] = stack;
        }
        return ItemStack.serializeItemsAsBytes(array);
    }

    private void persist(Session session) {
        if (!plugin.store().ready()) {
            return;
        }
        syncPage(session);
        plugin.store().save(session.id, session.tier, session.player.getUniqueId(), serialize(session));
        session.dirty = false;
    }

    /** Registers a debounced write so a crash loses at most the last second of changes. */
    private void markDirty(Session session) {
        session.dirty = true;
        if (session.saveTask != null) {
            session.saveTask.cancel();
        }
        session.saveTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            session.saveTask = null;
            if (!session.dirty || sessions.get(session.player.getUniqueId()) != session) {
                return;
            }
            persist(session);
        }, 20L);
    }

    /** Drops every reference to a closed session (pending save task and UUID lock). */
    private void release(Session session) {
        session.cancelSave();
        openById.remove(session.id, session);
    }

    public void saveAll() {
        for (Session session : new ArrayList<>(sessions.values())) {
            persist(session);
        }
    }

    /** Saves everything synchronously, used on disable. */
    public void saveAllSync() {
        // Drain queued writes first: a stale snapshot must not land after these saves.
        plugin.store().flush();
        for (Session session : new ArrayList<>(sessions.values())) {
            syncPage(session);
            if (plugin.store().ready()) {
                plugin.store().saveNow(session.id, session.tier, session.player.getUniqueId(), serialize(session));
            }
            release(session);
        }
        sessions.clear();
        openById.clear();
    }

    public void handleQuit(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session != null) {
            release(session);
            persist(session);
        }
    }

    /**
     * Closes an open session on death and drops the cursor stack, because the player
     * cannot keep it.
     */
    public void handleDeath(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        release(session);
        persist(session);
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            player.setItemOnCursor(new ItemStack(Material.AIR));
            player.getWorld().dropItemNaturally(player.getLocation(), cursor);
        }
    }

    /** Called when the player dies and the equipped backpack should be dropped. */
    public void dropEquipped(Player player) {
        ItemStack item = plugin.items().equipped(player);
        if (item == null) {
            return;
        }
        plugin.items().clearEquipped(player);
        ItemStack dropped = plugin.items().migrate(item, plugin.modBridge().displayKey(player));
        player.getWorld().dropItemNaturally(player.getLocation(), dropped);
        plugin.modBridge().broadcastSync(player);
    }

    // ---------------------------------------------------------------- equip

    public void equipFromHand(Player player) {
        equipFromHand(player, EquipmentSlot.HAND);
    }

    /** Equips the backpack held in {@code source} (main or off hand). */
    public void equipFromHand(Player player, EquipmentSlot source) {
        ItemStack hand = source == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        Tier tier = plugin.items().tierOf(hand);
        if (tier == null) {
            plugin.msg().send(player, "no-backpack");
            return;
        }

        ItemStack one = equipCopy(player, hand);
        int left = hand.getAmount() - 1;
        ItemStack rest = left <= 0 ? new ItemStack(Material.AIR) : hand.asQuantity(left);
        if (source == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(rest);
        } else {
            player.getInventory().setItemInMainHand(rest);
        }
        finishEquip(player, tier, one);
    }

    /** Equips the first backpack found in the player's main inventory. */
    public boolean equipFromInventory(Player player) {
        return equipFromInventory(player, null);
    }

    /** Equips {@code preferred} when it can be found, otherwise the first backpack. */
    public boolean equipFromInventory(Player player, ItemStack preferred) {
        UUID wanted = preferred == null ? null : plugin.items().idOf(preferred);
        int first = -1;
        int match = -1;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (!plugin.items().isBackpack(item)) {
                continue;
            }
            if (first < 0) {
                first = slot;
            }
            if (wanted != null && wanted.equals(plugin.items().idOf(item))) {
                match = slot;
                break;
            }
        }
        int slot = match >= 0 ? match : first;
        if (slot < 0) {
            plugin.msg().send(player, "cursor-no-backpack");
            return false;
        }
        equipFromSlot(player, slot);
        return true;
    }

    public void equipFromSlot(Player player, int slot) {
        ItemStack item = player.getInventory().getItem(slot);
        Tier tier = plugin.items().tierOf(item);
        if (tier == null) {
            plugin.msg().send(player, "no-backpack");
            return;
        }

        ItemStack one = equipCopy(player, item);
        int left = item.getAmount() - 1;
        player.getInventory().setItem(slot, left <= 0 ? null : item.asQuantity(left));
        finishEquip(player, tier, one);
    }

    public void equipFromCursor(Player player, ItemStack cursor) {
        Tier tier = plugin.items().tierOf(cursor);
        if (tier == null) {
            plugin.msg().send(player, "no-backpack");
            return;
        }

        ItemStack one = equipCopy(player, cursor);
        int left = cursor.getAmount() - 1;
        player.setItemOnCursor(left <= 0 ? new ItemStack(Material.AIR) : cursor.asQuantity(left));
        finishEquip(player, tier, one);
    }

    /**
     * Inventory slot click: equips a backpack from the cursor or from the inventory.
     * When a backpack is already worn the click removes it back into the inventory.
     * No mod is required for the server side of this action.
     */
    public void toggle(Player player) {
        ItemStack cursor = player.getItemOnCursor();
        if (plugin.items().isBackpack(cursor)) {
            equipFromCursor(player, cursor);
            return;
        }
        if (plugin.items().equipped(player) != null) {
            unequip(player);
            return;
        }
        equipFromInventory(player);
    }

    public void unequip(Player player) {
        ItemStack current = plugin.items().equipped(player);
        if (current == null) {
            plugin.msg().send(player, "no-backpack");
            return;
        }
        // Never drop the backpack: an item on the ground can burn or despawn.
        if (!hasStorageRoom(player)) {
            plugin.msg().send(player, "inventory-full-unequip");
            return;
        }
        closeSilently(player);
        plugin.items().clearEquipped(player);
        give(player, current);
        plugin.msg().send(player, "unequipped");
        plugin.modBridge().broadcastSync(player);
        player.updateInventory();
    }

    public void unequipToCursor(Player player) {
        ItemStack current = plugin.items().equipped(player);
        if (current == null) {
            plugin.msg().send(player, "no-backpack");
            return;
        }
        closeSilently(player);
        plugin.items().clearEquipped(player);

        ItemStack cursor = player.getItemOnCursor();
        if (cursor == null || cursor.getType().isAir()) {
            player.setItemOnCursor(current);
        } else {
            give(player, current);
        }
        plugin.msg().send(player, "unequipped");
        plugin.modBridge().broadcastSync(player);
        player.updateInventory();
    }

    private ItemStack equipCopy(Player player, ItemStack source) {
        Tier tier = plugin.items().tierOf(source);
        ItemStack one = source.clone();
        one.setAmount(1);
        UUID id = plugin.items().ensureId(one);
        String key = plugin.modBridge().displayKey(player);
        one = plugin.items().migrate(one, key);
        plugin.items().apply(one, tier, id, key);
        return one;
    }

    private void finishEquip(Player player, Tier tier, ItemStack one) {
        closeSilently(player);
        ItemStack previous = plugin.items().equipped(player);
        plugin.items().setEquipped(player, one);
        if (previous != null) {
            give(player, previous);
        }
        plugin.msg().send(player, "equipped",
                "tier", tier.name(plugin.msg().russian()),
                "key", plugin.modBridge().displayKey(player));
        sound(player, "sound-click", 0.7f, 1.1f);
        plugin.modBridge().broadcastSync(player);
        player.updateInventory();
    }

    /** Deletes the backpack in the main hand together with its contents. */
    public boolean removeHeld(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        Tier tier = plugin.items().tierOf(hand);
        if (tier == null) {
            plugin.msg().send(player, "no-backpack");
            return false;
        }
        UUID id = plugin.items().idOf(hand);
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        if (id != null) {
            plugin.store().delete(id);
        }
        plugin.msg().send(player, "removed");
        return true;
    }

    public void give(Player player, ItemStack stack) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
            plugin.msg().send(player, "inventory-full");
        }
    }

    /** True when the main inventory (hotbar + 27 slots) has a free slot for a backpack. */
    private static boolean hasStorageRoom(Player player) {
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    private void closeSilently(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        release(session);
        persist(session);
        player.closeInventory();
    }

    // ----------------------------------------------------------- bulk clicks

    /**
     * Manual handling for tiers with multi-stack cells. The stacks shown in the window
     * are only copies of the real cells, so vanilla must not touch them; every chest
     * interaction is translated to the cell model instead: LMB/RMB, shift-click,
     * number keys, offhand swap, Q / Ctrl+Q, double-click and dragging.
     */
    private void bulkClick(Session session, InventoryClickEvent event) {
        Player player = session.player;
        Inventory top = session.inventory;
        Inventory clicked = event.getClickedInventory();
        ClickType click = event.getClick();

        if (clicked != top) {
            if (click == ClickType.DOUBLE_CLICK) {
                // Display stacks are copies: vanilla would duplicate them, so the
                // collection is done from the real cells and the player inventory.
                event.setCancelled(true);
                collect(session, player, -1, event.getCurrentItem());
                render(session);
                return;
            }
            if (!event.isShiftClick()) {
                // Ordinary player-inventory click: vanilla handles it, including Q,
                // number keys and moving a backpack between player slots.
                return;
            }
            event.setCancelled(true);
            if (event.getAction() != InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                return;
            }
            ItemStack current = event.getCurrentItem();
            if (current == null || current.getType().isAir() || isBackpack(current)) {
                return;
            }
            int left = insert(session, current);
            event.setCurrentItem(left <= 0 ? null : current.asQuantity(left));
            render(session);
            return;
        }

        event.setCancelled(true);
        ItemStack cursor = event.getCursor();
        if (isBackpack(cursor)) {
            return;
        }
        int slot = event.getSlot();
        int count = Math.min(CONTENT, session.tier.capacity() - session.page * CONTENT);
        if (slot >= count) {
            if (click.isMouseClick() && !event.isShiftClick()) {
                if (slot == NAV_ROW) {
                    turnPage(session, -1);
                } else if (slot == NAV_ROW + 8) {
                    turnPage(session, 1);
                }
            }
            return;
        }

        int index = session.page * CONTENT + slot;

        if (click == ClickType.NUMBER_KEY) {
            swapWithHotbar(session, index, player, event.getHotbarButton());
            player.updateInventory();
            render(session);
            return;
        }
        if (click == ClickType.SWAP_OFFHAND) {
            swapWithHotbar(session, index, player, -1);
            player.updateInventory();
            render(session);
            return;
        }
        if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
            dropFromCell(session, index, player, click == ClickType.CONTROL_DROP);
            render(session);
            return;
        }
        if (click == ClickType.DOUBLE_CLICK) {
            collect(session, player, index, null);
            render(session);
            return;
        }
        if (click.isCreativeAction() || click == ClickType.UNKNOWN) {
            return;
        }

        if (event.isShiftClick()) {
            takeAll(session, index, player);
            render(session);
            return;
        }

        if (cursor == null || cursor.getType().isAir()) {
            take(session, index, click.isRightClick() ? 1 : -1, player);
        } else {
            put(session, index, click.isRightClick() ? 1 : -1, player);
        }
        render(session);
    }

    /**
     * Number keys and the offhand swap key: exchange one displayed stack with the
     * hotbar/offhand stack, merge it into a matching cell, or pick the cell up.
     * {@code hotbar < 0} means the offhand slot.
     */
    private void swapWithHotbar(Session session, int index, Player player, int hotbar) {
        PlayerInventory inventory = player.getInventory();
        ItemStack other = hotbar < 0 ? inventory.getItemInOffHand() : inventory.getItem(hotbar);
        if (isBackpack(other)) {
            return;
        }
        Cell cell = session.contents[index];

        if (cell == null) {
            if (other == null || other.getType().isAir()) {
                return;
            }
            Cell created = new Cell();
            created.proto = other.clone();
            created.proto.setAmount(1);
            int moved = Math.min(other.getAmount(), created.limit(session.tier));
            if (moved <= 0) {
                return;
            }
            created.count = moved;
            session.contents[index] = created;
            setHand(inventory, hotbar, other.getAmount() - moved <= 0
                    ? null : other.asQuantity(other.getAmount() - moved));
            return;
        }

        if (other == null || other.getType().isAir()) {
            int taken = Math.min(cell.count, cell.maxStack());
            ItemStack stack = cell.proto.clone();
            stack.setAmount(taken);
            cell.count -= taken;
            if (cell.count <= 0) {
                session.contents[index] = null;
            }
            setHand(inventory, hotbar, stack);
            return;
        }

        if (cell.proto.isSimilar(other)) {
            int room = cell.limit(session.tier) - cell.count;
            int moved = Math.min(room, other.getAmount());
            cell.count += moved;
            setHand(inventory, hotbar, other.getAmount() - moved <= 0
                    ? null : other.asQuantity(other.getAmount() - moved));
            return;
        }

        // A different item: exchange it for one displayed stack of the cell. Taking
        // that stack out always frees enough room for the hand stack (at most one
        // stack), so this is a true chest-like swap.
        int taken = Math.min(cell.count, cell.maxStack());
        int room = cell.limit(session.tier) - (cell.count - taken);
        if (other.getAmount() > room) {
            return;
        }
        ItemStack stack = cell.proto.clone();
        stack.setAmount(taken);
        cell.count = cell.count - taken + other.getAmount();
        if (cell.count <= 0) {
            session.contents[index] = null;
        }
        setHand(inventory, hotbar, stack);
    }

    private static void setHand(PlayerInventory inventory, int hotbar, ItemStack stack) {
        ItemStack value = stack == null ? new ItemStack(Material.AIR) : stack;
        if (hotbar < 0) {
            inventory.setItemInOffHand(value);
        } else {
            inventory.setItem(hotbar, value);
        }
    }

    /** Q / Ctrl+Q on a cell: throw one item or one displayed stack like a chest slot. */
    private void dropFromCell(Session session, int index, Player player, boolean stack) {
        Cell cell = session.contents[index];
        if (cell == null || cell.count <= 0) {
            return;
        }
        int amount = stack ? Math.min(cell.count, cell.maxStack()) : 1;
        ItemStack dropped = cell.proto.clone();
        dropped.setAmount(amount);
        cell.count -= amount;
        if (cell.count <= 0) {
            session.contents[index] = null;
        }
        player.dropItem(dropped, false, null);
    }

    /**
     * Double-click: collects matching items from every cell and from the player
     * inventory onto the cursor, exactly like a chest. When the cursor is empty the
     * clicked cell (or the clicked player slot) provides the item to collect.
     */
    private void collect(Session session, Player player, int index, ItemStack clicked) {
        ItemStack cursor = player.getItemOnCursor();
        ItemStack proto;
        if (cursor != null && !cursor.getType().isAir()) {
            proto = cursor.clone();
        } else {
            Cell cell = index >= 0 && index < session.contents.length ? session.contents[index] : null;
            ItemStack base = cell != null ? cell.proto : clicked;
            if (base == null || base.getType().isAir()) {
                return;
            }
            proto = base.clone();
        }
        proto.setAmount(1);

        int max = Math.max(1, proto.getType().getMaxStackSize());
        int have = cursor == null || cursor.getType().isAir() ? 0 : cursor.getAmount();
        if (have >= max) {
            return;
        }
        int need = max - have;

        for (int i = 0; i < session.contents.length && need > 0; i++) {
            Cell cell = session.contents[i];
            if (cell == null || !cell.proto.isSimilar(proto)) {
                continue;
            }
            int moved = Math.min(need, cell.count);
            cell.count -= moved;
            need -= moved;
            if (cell.count <= 0) {
                session.contents[i] = null;
            }
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        for (int i = 0; i < storage.length && need > 0; i++) {
            ItemStack stack = storage[i];
            if (stack == null || stack.getType().isAir() || !stack.isSimilar(proto)) {
                continue;
            }
            int moved = Math.min(need, stack.getAmount());
            need -= moved;
            if (stack.getAmount() - moved <= 0) {
                storage[i] = new ItemStack(Material.AIR);
            } else {
                stack.setAmount(stack.getAmount() - moved);
            }
        }
        inventory.setStorageContents(storage);

        ItemStack result = proto.clone();
        result.setAmount(max - need);
        player.setItemOnCursor(result);
        player.updateInventory();
    }

    /**
     * Moves up to {@code amount} items of {@code proto} into one cell.
     *
     * @return how many items were actually stored.
     */
    private int deposit(Session session, int index, ItemStack proto, int amount) {
        if (amount <= 0) {
            return 0;
        }
        Cell cell = session.contents[index];
        if (cell == null) {
            Cell created = new Cell();
            created.proto = proto.clone();
            created.proto.setAmount(1);
            int moved = Math.min(amount, created.limit(session.tier));
            created.count = moved;
            session.contents[index] = created;
            return moved;
        }
        if (!cell.proto.isSimilar(proto)) {
            return 0;
        }
        int room = cell.limit(session.tier) - cell.count;
        int moved = Math.min(amount, room);
        cell.count += moved;
        return moved;
    }

    /** Takes one stack ({@code amount < 0}) or a single item from a cell onto the cursor. */
    private void take(Session session, int index, int amount, Player player) {
        Cell cell = session.contents[index];
        if (cell == null || cell.count <= 0) {
            return;
        }
        int max = cell.maxStack();
        int taken = amount < 0 ? Math.min(cell.count, max) : Math.min(amount, Math.min(cell.count, max));
        if (taken <= 0) {
            return;
        }
        ItemStack stack = cell.proto.clone();
        stack.setAmount(taken);
        player.setItemOnCursor(stack);
        cell.count -= taken;
        if (cell.count <= 0) {
            session.contents[index] = null;
        }
    }

    /** Puts the cursor stack ({@code amount < 0}) or a single item into a cell. */
    private void put(Session session, int index, int amount, Player player) {
        ItemStack cursor = player.getItemOnCursor();
        if (cursor == null || cursor.getType().isAir()) {
            return;
        }
        Cell cell = session.contents[index];

        if (cell == null || cell.proto.isSimilar(cursor)) {
            int moved = deposit(session, index, cursor, amount < 0 ? cursor.getAmount() : 1);
            if (moved <= 0) {
                return;
            }
            player.setItemOnCursor(cursor.getAmount() - moved <= 0
                    ? new ItemStack(Material.AIR)
                    : cursor.asQuantity(cursor.getAmount() - moved));
            return;
        }

        // A different item: swap the cursor stack with one displayed stack. Taking
        // that stack out always leaves room for a full cursor stack.
        int taken = Math.min(cell.count, cell.maxStack());
        int room = cell.limit(session.tier) - (cell.count - taken);
        if (cursor.getAmount() > room) {
            return;
        }
        ItemStack stack = cell.proto.clone();
        stack.setAmount(taken);
        cell.count = cell.count - taken + cursor.getAmount();
        player.setItemOnCursor(stack);
    }

    /** Shift-click on a cell: moves as much as fits into the player inventory. */
    private void takeAll(Session session, int index, Player player) {
        Cell cell = session.contents[index];
        if (cell == null || cell.count <= 0) {
            return;
        }
        int max = cell.maxStack();
        int guard = 0;
        while (cell.count > 0 && guard++ < MAX_TAKE_CHUNKS) {
            int amount = Math.min(cell.count, max);
            ItemStack chunk = cell.proto.clone();
            chunk.setAmount(amount);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(chunk);
            int kept = 0;
            for (ItemStack stack : leftover.values()) {
                kept += stack.getAmount();
            }
            cell.count -= amount - kept;
            if (kept > 0) {
                break;
            }
        }
        if (cell.count <= 0) {
            session.contents[index] = null;
        }
    }

    /**
     * Shift-click in the player inventory: merges into matching cells anywhere in the
     * backpack first, then fills empty cells (starting on the visible page, like a
     * chest that keeps the current view useful).
     *
     * @return how many items did not fit.
     */
    private int insert(Session session, ItemStack stack) {
        int left = stack.getAmount();
        int max = Math.max(1, stack.getType().getMaxStackSize());
        int limit = max * session.tier.stacksPerCell();
        int capacity = session.tier.capacity();

        for (int index = 0; index < capacity && left > 0; index++) {
            Cell cell = session.contents[index];
            if (cell == null || !cell.proto.isSimilar(stack)) {
                continue;
            }
            int room = Math.min(limit, cell.limit(session.tier)) - cell.count;
            int moved = Math.min(room, left);
            if (moved <= 0) {
                continue;
            }
            cell.count += moved;
            left -= moved;
        }

        int start = session.page * CONTENT;
        for (int n = 0; n < capacity && left > 0; n++) {
            int index = (start + n) % capacity;
            if (session.contents[index] != null) {
                continue;
            }
            int moved = deposit(session, index, stack, Math.min(limit, left));
            left -= moved;
        }
        return left;
    }

    /**
     * Vanilla pick-block fallback: middle-click a block/entity and the matching item
     * is taken from the equipped backpack when it is not in the player inventory.
     */
    public boolean pickFromBackpack(Player player, ItemStack wanted) {
        if (wanted == null || wanted.getType().isAir()) {
            return false;
        }
        ItemStack backpack = plugin.items().equipped(player);
        if (backpack == null) {
            return false;
        }
        UUID id = plugin.items().idOf(backpack);
        Tier tier = plugin.items().tierOf(backpack);
        if (id == null || tier == null) {
            return false;
        }

        Session session = openById.get(id);
        if (session != null && session.player == player) {
            int index = findCell(session.contents, wanted);
            if (index < 0) {
                return false;
            }
            Cell cell = session.contents[index];
            int amount = Math.min(cell.count, cell.maxStack());
            ItemStack picked = cell.proto.clone();
            picked.setAmount(amount);
            int moved = addToInventory(player, picked);
            if (moved <= 0) {
                return false;
            }
            cell.count -= moved;
            if (cell.count <= 0) {
                session.contents[index] = null;
            }
            selectHotbar(player, picked);
            markDirty(session);
            player.updateInventory();
            return true;
        }

        if (!plugin.store().ready() || !plugin.store().flush(3000L)) {
            return false;
        }
        BackpackStore.Contents stored = plugin.store().loadContents(id);
        if (stored.failed()) {
            return false;
        }
        Cell[] contents = new Cell[tier.capacity()];
        if (stored.data() != null && stored.data().length > 0) {
            try {
                ItemStack[] loaded = ItemStack.deserializeItemsFromBytes(stored.data());
                for (int i = 0; i < loaded.length && i < contents.length; i++) {
                    contents[i] = cellOf(loaded[i]);
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Could not read backpack " + id + ": "
                        + exception.getMessage());
                return false;
            }
        }
        int index = findCell(contents, wanted);
        if (index < 0) {
            return false;
        }
        Cell cell = contents[index];
        int amount = Math.min(cell.count, cell.maxStack());
        ItemStack picked = cell.proto.clone();
        picked.setAmount(amount);
        int moved = addToInventory(player, picked);
        if (moved <= 0) {
            return false;
        }
        cell.count -= moved;
        if (cell.count <= 0) {
            contents[index] = null;
        }
        plugin.store().save(id, tier, player.getUniqueId(), serialize(contents));
        selectHotbar(player, picked);
        player.updateInventory();
        return true;
    }

    private static int findCell(Cell[] contents, ItemStack wanted) {
        for (int i = 0; i < contents.length; i++) {
            Cell cell = contents[i];
            if (cell != null && cell.count > 0 && cell.proto.isSimilar(wanted)) {
                return i;
            }
        }
        for (int i = 0; i < contents.length; i++) {
            Cell cell = contents[i];
            if (cell != null && cell.count > 0 && cell.proto.getType() == wanted.getType()) {
                return i;
            }
        }
        return -1;
    }

    /** Adds as much of {@code stack} as fits into the 36 storage slots. */
    private static int addToInventory(Player player, ItemStack stack) {
        int total = stack.getAmount();
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        int left = 0;
        for (ItemStack rest : leftover.values()) {
            left += rest.getAmount();
        }
        return total - left;
    }

    /** Selects the first hotbar slot holding a similar stack, so the item lands in the hand. */
    private static void selectHotbar(Player player, ItemStack stack) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir() && item.isSimilar(stack)) {
                inventory.setHeldItemSlot(slot);
                return;
            }
        }
    }

    // ---------------------------------------------------------------- events

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Session session) || session.inventory == null) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player) || player != session.player) {
            return;
        }
        // Any click may move items (top inventory, shift-click or hotbar swap), so the
        // session is written one second later; a crash loses at most that second.
        markDirty(session);
        if (session.tier.stacksPerCell() > 1) {
            bulkClick(session, event);
            return;
        }

        boolean topClick = event.getClickedInventory() == top;
        if (!topClick) {
            if (event.getAction() != InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                // Ordinary player-inventory click: vanilla handles it, including a
                // backpack that is only moved between player slots.
                return;
            }
            event.setCancelled(true);
            ItemStack current = event.getCurrentItem();
            if (current == null || current.getType().isAir() || isBackpack(current)) {
                return;
            }
            // Vanilla would also use slots that do not exist on this page, so the
            // shift-move is done here: matching cells first, then empty cells.
            syncPage(session);
            int left = insert(session, current);
            event.setCurrentItem(left <= 0 ? null : current.asQuantity(left));
            render(session);
            return;
        }

        // A backpack item must never end up inside another backpack.
        if (isBackpack(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        if (event.getClick() == ClickType.NUMBER_KEY
                && isBackpack(player.getInventory().getItem(event.getHotbarButton()))) {
            event.setCancelled(true);
            return;
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND
                && isBackpack(player.getInventory().getItemInOffHand())) {
            event.setCancelled(true);
            return;
        }

        int slot = event.getSlot();
        int count = Math.min(CONTENT, session.tier.capacity() - session.page * CONTENT);
        if (slot < count) {
            // A real content slot: vanilla handles the click like a chest.
            return;
        }
        event.setCancelled(true);
        if (!session.paged) {
            return;
        }
        if (slot == NAV_ROW) {
            turnPage(session, -1);
        } else if (slot == NAV_ROW + 8) {
            turnPage(session, 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Session session) || session.inventory == null) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player) || player != session.player) {
            return;
        }
        int topSize = top.getSize();
        int count = Math.min(CONTENT, session.tier.capacity() - session.page * CONTENT);
        boolean touchesTop = false;
        for (int raw : event.getRawSlots()) {
            if (raw >= 0 && raw < topSize) {
                touchesTop = true;
                break;
            }
        }
        if (!touchesTop) {
            // Dragging inside the player inventory stays completely vanilla.
            return;
        }

        if (session.tier.stacksPerCell() == 1) {
            // Real slots: vanilla distributes the stack, but pages, the navigation
            // row and the unused tail of the last row must stay untouched.
            for (int raw : event.getRawSlots()) {
                if (raw >= 0 && raw < topSize && raw >= count) {
                    event.setCancelled(true);
                    return;
                }
            }
            markDirty(session);
            return;
        }

        // Bulk cells display copies, so the distribution is applied to the cell model.
        event.setCancelled(true);
        ItemStack cursor = event.getOldCursor();
        if (cursor == null || cursor.getType().isAir() || isBackpack(cursor)) {
            return;
        }
        List<Integer> slots = new ArrayList<>();
        for (int raw : event.getRawSlots()) {
            if (raw >= 0 && raw < topSize && raw < count) {
                slots.add(raw);
            }
        }
        if (slots.isEmpty()) {
            return;
        }
        markDirty(session);

        // EVEN = left-drag (split evenly), SINGLE = right-drag (one per slot).
        int per = event.getType() == DragType.EVEN
                ? Math.max(1, cursor.getAmount() / slots.size())
                : 1;
        int left = cursor.getAmount();
        int moved = 0;
        for (int slot : slots) {
            int amount = Math.min(per, left - moved);
            if (amount <= 0) {
                break;
            }
            moved += deposit(session, session.page * CONTENT + slot, cursor, amount);
        }
        if (moved > 0) {
            player.setItemOnCursor(left - moved <= 0
                    ? new ItemStack(Material.AIR)
                    : cursor.asQuantity(left - moved));
            render(session);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Session session)) {
            return;
        }
        if (sessions.remove(session.player.getUniqueId()) == null) {
            return;
        }
        release(session);
        persist(session);
        sound(session.player, "sound-close", 0.6f, 0.9f);
    }

    public static final class Session implements InventoryHolder {
        private final Player player;
        private final UUID id;
        private final Tier tier;
        private final boolean equipped;
        private final Cell[] contents;
        /** True when the tier needs more than one page, so the last row holds the arrows. */
        private final boolean paged;
        private final int size;
        private Inventory inventory;
        private int page;
        private boolean dirty;
        private BukkitTask saveTask;

        private Session(Player player, UUID id, Tier tier, boolean equipped, Cell[] contents) {
            this.player = player;
            this.id = id;
            this.tier = tier;
            this.equipped = equipped;
            this.contents = contents;
            this.paged = tier.pages() > 1;
            // One-page backpacks get exactly the chest rows they need; paged ones use
            // the usual five content rows plus a navigation row.
            this.size = paged ? 54 : Math.max(1, (tier.capacity() + 8) / 9) * 9;
        }

        private void cancelSave() {
            dirty = false;
            if (saveTask != null) {
                saveTask.cancel();
                saveTask = null;
            }
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public UUID id() {
            return id;
        }

        public Tier tier() {
            return tier;
        }

        public boolean equipped() {
            return equipped;
        }
    }
}
