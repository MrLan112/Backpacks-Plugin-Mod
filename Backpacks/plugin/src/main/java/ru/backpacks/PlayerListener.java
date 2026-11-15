package ru.backpacks;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import io.papermc.paper.event.player.PlayerPickItemEvent;

public final class PlayerListener implements Listener {
    private final BackpacksPlugin plugin;

    public PlayerListener(BackpacksPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.recipeBook() != null) {
            plugin.recipeBook().discover(player);
        }

        // Migrates legacy horse-armour backpacks and shows the current key in the lore.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                plugin.items().refreshKeys(player);
            }
        });

        // The client sends its hello as soon as it joins; that packet can arrive before
        // the player is fully added to the server, so the first sync may be lost. Send
        // the worn tier again once the entity exists on the client, and once more as a
        // safety net. Without this a rejoin showed no backpack until the next action.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.modBridge().broadcastSync(player);
                plugin.modBridge().syncAll(player);
            }
        }, 20L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.modBridge().broadcastSync(player);
                plugin.modBridge().syncAll(player);
            }
        }, 60L);

        if (!plugin.getConfig().getBoolean("require-mod", false)) {
            return;
        }
        if (player.hasPermission("backpacks.modbypass")) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !plugin.modBridge().isModded(player)) {
                player.kick(plugin.msg().component("mod-required"));
            }
        }, 100L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.modBridge().broadcastSync(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        plugin.modBridge().broadcastSync(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.gui().handleQuit(event.getPlayer());
        plugin.modBridge().forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        EquipmentSlot handSlot = event.getHand();
        if (handSlot == null) {
            return;
        }
        switch (event.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> {
            }
            default -> {
                return;
            }
        }

        Player player = event.getPlayer();
        if (!player.hasPermission("backpacks.use")) {
            return;
        }

        ItemStack hand = itemIn(player, handSlot);
        if (plugin.items().isBackpack(hand)) {
            event.setCancelled(true);
            if (player.isSneaking()) {
                plugin.gui().equipFromHand(player, handSlot);
            } else {
                plugin.gui().open(player, hand, false, handSlot);
            }
            return;
        }

        // The empty-hand hotkey is main-hand only: otherwise it would trigger twice.
        if (handSlot != EquipmentSlot.HAND) {
            return;
        }
        if (hand == null || hand.getType() == Material.AIR) {
            if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR) {
                return;
            }
            if (player.isSneaking() && plugin.items().equipped(player) != null) {
                event.setCancelled(true);
                plugin.gui().unequip(player);
                return;
            }
            if (plugin.getConfig().getBoolean("open-empty-hand", true)
                    && plugin.items().equipped(player) != null) {
                event.setCancelled(true);
                plugin.gui().openEquipped(player);
            }
        }
    }

    private static ItemStack itemIn(Player player, EquipmentSlot slot) {
        return slot == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
    }

    /**
     * A backpack must never be used as an ordinary item: it cannot be put on a horse
     * (legacy horse-armour stacks) and cannot be placed in an item frame. Both hands are
     * handled, otherwise an offhand backpack could still be placed in a frame.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        EquipmentSlot handSlot = event.getHand();
        if (handSlot == null) {
            return;
        }
        if (!(event.getRightClicked() instanceof AbstractHorse)
                && !(event.getRightClicked() instanceof ItemFrame)) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission("backpacks.use")) {
            return;
        }

        ItemStack hand = itemIn(player, handSlot);
        if (!plugin.items().isBackpack(hand)) {
            return;
        }

        event.setCancelled(true);
        if (player.isSneaking()) {
            plugin.gui().equipFromHand(player, handSlot);
        } else {
            plugin.gui().open(player, hand, false, handSlot);
        }
    }

    /**
     * Right-clicking a backpack in the player's own inventory equips it, no mod needed.
     * The fake backpack slot of the mod sends its own packet, so this is a pure
     * server-side fallback for vanilla clients.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.hasPermission("backpacks.use")) {
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof BackpackGuiManager.Session) {
            return;
        }
        // Only the player's own survival inventory: a right click in a chest or another
        // plugin's GUI must keep its normal "move one item" behaviour.
        if (event.getView().getType() != InventoryType.CRAFTING) {
            return;
        }
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getView().getBottomInventory())) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (current == null || !plugin.items().isBackpack(current)) {
            return;
        }
        if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
            event.setCancelled(true);
            plugin.gui().equipFromInventory(player, current);
        }
    }

    /**
     * Optional keybind: swap-hands (F) opens the equipped backpack. Disabled by default
     * because the client mod and {@code /bp} already open it; keeping it on steals a
     * vanilla key. Unequipping is done by LMB on the backpack slot or Shift+RMB with an
     * empty hand, so there is no hidden Shift+F action anymore.
     */
    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("backpacks.use")) {
            return;
        }
        if (!"SWAP_HANDS".equalsIgnoreCase(plugin.getConfig().getString("hotkey", "NONE"))) {
            return;
        }
        if (plugin.items().isBackpack(player.getInventory().getItemInMainHand())
                || plugin.items().isBackpack(player.getInventory().getItemInOffHand())) {
            return;
        }
        if (plugin.items().equipped(player) == null) {
            return;
        }
        event.setCancelled(true);
        plugin.gui().openEquipped(player);
    }

    /**
     * Middle-click pick block also looks inside the equipped backpack. The vanilla
     * search only checks the 36 inventory slots and reports {@code sourceSlot == -1}
     * when the item is missing, so that is exactly the case handled here.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickItem(PlayerPickItemEvent event) {
        if (event.getSourceSlot() >= 0) {
            // The item is already in the inventory: vanilla handles it (and swaps slots).
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("backpacks.use")) {
            return;
        }
        if (!plugin.gui().pickFromBackpack(player, event.getItem())) {
            return;
        }
        // The stack is already in the inventory and the hotbar is selected; cancel so
        // creative mode does not spawn a second copy in the target slot.
        event.setCancelled(true);
        player.updateInventory();
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        // Finish the open session and drop the cursor stack before the inventory is scattered.
        plugin.gui().handleDeath(event.getEntity());
        if (plugin.getConfig().getBoolean("equipment.keep-on-death", true)) {
            return;
        }
        plugin.gui().dropEquipped(event.getEntity());
    }
}
