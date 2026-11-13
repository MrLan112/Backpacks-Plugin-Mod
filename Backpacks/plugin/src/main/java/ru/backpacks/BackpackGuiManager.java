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
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side chest GUI. 45 content cells per page, the last row is buttons.
 * Contents live in memory while the GUI is open and are persisted to SQLite.
 *
 * <p>Starting with tier 9 every cell can hold up to 27 stacks of the same item
 * ({@link Tier#stacksPerCell()}). Such cells are rendered as one stack with the
 * total amount in the lore; clicks are handled manually, so one cell can hold
 * more items than a vanilla stack.
 */
public final class BackpackGuiManager implements Listener {
    public static final int SIZE = 54;
    public static final int CONTENT = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;
    private static final ItemStack EMPTY = new ItemStack(Material.AIR);
    /** Safety limit for shift-taking a huge cell into the player inventory. */
    private static final int MAX_TAKE_CHUNKS = 4096;

    private final BackpacksPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    /** Storage UUID -> the session that currently has it open (guards duplicate items). */
    private final Map<UUID, Session> openById = new HashMap<>();
    private ItemStack filler;
    private final NamespacedKey extraKey;
    private BukkitTask autosave;

    public BackpackGuiManager(BackpacksPlugin plugin) {
        this.plugin = plugin;
        this.filler = fillerItem();
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
        filler = fillerItem();
        long seconds = plugin.getConfig().getLong("autosave-seconds", 30);
        if (seconds > 0) {
            autosave = Bukkit.getScheduler().runTaskTimer(plugin, this::saveAll, seconds * 20L, seconds * 20L);
        }
    }

    private ItemStack fillerItem() {
        String configured = plugin.getConfig().getString("gui.filler", "GRAY_STAINED_GLASS_PANE");
        Material material = configured == null ? null : Material.matchMaterial(configured);
        if (material == null || material.isAir()) {
            material = Material.GRAY_STAINED_GLASS_PANE;
        }
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> meta.displayName(Component.empty()));
        return stack;
    }

    private ItemStack fillerCopy() {
        return filler.clone();
    }

    private boolean isBackpack(ItemStack stack) {
        return plugin.items().isBackpack(stack);
    }

    // ------------------------------------------------------------------ open

    /** Opens a backpack item. When {@code equipped} is true the item is stored on the player. */
    public boolean open(Player player, ItemStack item, boolean equipped) {
        return open(player, item, equipped, EquipmentSlot.HAND);
    }

    /**
     * Opens a backpack item taken from {@code source}. The migrated stack is written back
     * into that slot, so opening from the offhand never duplicates or eats the main hand.
     */
    public boolean open(Player player, ItemStack item, boolean equipped, EquipmentSlot source) {
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
        } else if (source == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(item);
        } else {
            player.getInventory().setItemInMainHand(item);
        }

        Session session = new Session(player, id, tier, equipped, contents);
        session.inventory = Bukkit.createInventory(session, SIZE, plugin.msg().component("gui-title",
                "tier", tier.name(plugin.msg().russian()),
                "size", tier.capacity()));
        sessions.put(player.getUniqueId(), session);
        openById.put(id, session);
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

        for (int slot = 0; slot < CONTENT; slot++) {
            int index = session.page * CONTENT + slot;
            if (index >= session.tier.capacity()) {
                inventory.setItem(slot, fillerCopy());
                continue;
            }
            Cell cell = session.contents[index];
            inventory.setItem(slot, cell == null ? null : display(cell));
        }

        for (int slot = CONTENT; slot < SIZE; slot++) {
            inventory.setItem(slot, fillerCopy());
        }

        if (session.page > 0) {
            inventory.setItem(SLOT_PREV, button(plugin.msg().component("prev")));
        }
        if (session.page < session.tier.pages() - 1) {
            inventory.setItem(SLOT_NEXT, button(plugin.msg().component("next")));
        }
        inventory.setItem(SLOT_INFO, info(session));
    }

    private ItemStack button(Component name) {
        ItemStack stack = new ItemStack(Material.ARROW);
        stack.editMeta(meta -> meta.displayName(name));
        return stack;
    }

    private ItemStack info(Session session) {
        ItemStack stack = plugin.items().preview(session.tier);
        stack.editMeta(meta -> {
            List<Component> lore = meta.lore();
            lore = lore == null ? new ArrayList<>() : new ArrayList<>(lore);
            lore.add(Component.empty());
            lore.add(plugin.msg().component("page",
                    "page", session.page + 1,
                    "pages", session.tier.pages()));
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
        for (int slot = 0; slot < CONTENT; slot++) {
            int index = session.page * CONTENT + slot;
            if (index >= session.tier.capacity()) {
                continue;
            }
            ItemStack stack = session.inventory.getItem(slot);
            session.contents[index] = cellOf(stack);
        }
    }

    private byte[] serialize(Session session) {
        ItemStack[] array = new ItemStack[session.contents.length];
        for (int i = 0; i < session.contents.length; i++) {
            Cell cell = session.contents[i];
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
        if (session != null) {
            release(session);
            persist(session);
            player.closeInventory();
        }
    }

    // ----------------------------------------------------------- bulk clicks

    /**
     * Manual handling for tiers with multi-stack cells.
     * LMB: take/put one full stack, RMB: one item, Shift+LMB: take everything.
     */
    private void bulkClick(Session session, InventoryClickEvent event) {
        Player player = session.player;
        Inventory top = session.inventory;
        Inventory clicked = event.getClickedInventory();

        if (clicked != top) {
            if (!event.isShiftClick()) {
                // Display stacks are copies: a vanilla double-click would collect (duplicate) them.
                if (event.getClick() == ClickType.DOUBLE_CLICK) {
                    event.setCancelled(true);
                }
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
            clicked.setItem(event.getSlot(), left <= 0 ? null : current.asQuantity(left));
            render(session);
            return;
        }

        event.setCancelled(true);
        ItemStack cursor = event.getCursor();
        if (isBackpack(cursor)) {
            return;
        }
        int slot = event.getSlot();
        if (slot >= CONTENT) {
            if (slot == SLOT_PREV) {
                turnPage(session, -1);
            } else if (slot == SLOT_NEXT) {
                turnPage(session, 1);
            }
            return;
        }

        int index = session.page * CONTENT + slot;
        if (index >= session.tier.capacity()) {
            return;
        }

        ClickType click = event.getClick();
        if (click.isCreativeAction() || click.isKeyboardClick()
                || click == ClickType.SWAP_OFFHAND
                || click == ClickType.DROP || click == ClickType.CONTROL_DROP
                || click == ClickType.DOUBLE_CLICK || click == ClickType.UNKNOWN) {
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
        if (cell == null) {
            Cell created = new Cell();
            created.proto = cursor.clone();
            created.proto.setAmount(1);
            int room = Math.min(created.limit(session.tier), cursor.getAmount());
            int moved = amount < 0 ? room : Math.min(1, room);
            if (moved <= 0) {
                return;
            }
            created.count = moved;
            session.contents[index] = created;
            player.setItemOnCursor(cursor.getAmount() - moved <= 0
                    ? new ItemStack(Material.AIR)
                    : cursor.asQuantity(cursor.getAmount() - moved));
            return;
        }

        if (!cell.proto.isSimilar(cursor)) {
            return;
        }
        int room = cell.limit(session.tier) - cell.count;
        if (room <= 0) {
            return;
        }
        int moved = amount < 0 ? Math.min(room, cursor.getAmount()) : 1;
        if (moved <= 0) {
            return;
        }
        cell.count += moved;
        player.setItemOnCursor(cursor.getAmount() - moved <= 0
                ? new ItemStack(Material.AIR)
                : cursor.asQuantity(cursor.getAmount() - moved));
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
     * Shift-click in the player inventory: fills matching cells of the current page first,
     * then empty ones.
     *
     * @return how many items did not fit.
     */
    private int insert(Session session, ItemStack stack) {
        int left = stack.getAmount();
        int max = Math.max(1, stack.getType().getMaxStackSize());
        int limit = max * session.tier.stacksPerCell();

        for (int slot = 0; slot < CONTENT && left > 0; slot++) {
            int index = session.page * CONTENT + slot;
            if (index >= session.tier.capacity()) {
                break;
            }
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

        for (int slot = 0; slot < CONTENT && left > 0; slot++) {
            int index = session.page * CONTENT + slot;
            if (index >= session.tier.capacity()) {
                break;
            }
            if (session.contents[index] != null) {
                continue;
            }
            Cell cell = new Cell();
            cell.proto = stack.clone();
            cell.proto.setAmount(1);
            int moved = Math.min(limit, left);
            cell.count = moved;
            session.contents[index] = cell;
            left -= moved;
        }
        return left;
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

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        if (isBackpack(cursor)) {
            event.setCancelled(true);
            return;
        }
        if (event.isShiftClick() && isBackpack(current)) {
            event.setCancelled(true);
            return;
        }
        if (event.getClick() == ClickType.NUMBER_KEY) {
            ItemStack hotbar = player.getInventory().getItem(event.getHotbarButton());
            if (isBackpack(hotbar)) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND && isBackpack(player.getInventory().getItemInOffHand())) {
            event.setCancelled(true);
            return;
        }

        if (event.getClickedInventory() != top) {
            return;
        }

        int slot = event.getSlot();
        if (slot >= CONTENT) {
            event.setCancelled(true);
            if (slot == SLOT_PREV) {
                turnPage(session, -1);
            } else if (slot == SLOT_NEXT) {
                turnPage(session, 1);
            }
            return;
        }

        int index = session.page * CONTENT + slot;
        if (index >= session.tier.capacity()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Session session) || session.inventory == null) {
            return;
        }
        if (isBackpack(event.getOldCursor())) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player) || player != session.player) {
            return;
        }
        int topSize = top.getSize();
        markDirty(session);
        for (int raw : event.getRawSlots()) {
            if (raw >= topSize) {
                continue;
            }
            if (session.tier.stacksPerCell() > 1) {
                event.setCancelled(true);
                return;
            }
            if (raw >= CONTENT) {
                event.setCancelled(true);
                return;
            }
            int index = session.page * CONTENT + raw;
            if (index >= session.tier.capacity()) {
                event.setCancelled(true);
                return;
            }
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
