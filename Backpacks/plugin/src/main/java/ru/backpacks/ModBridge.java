package ru.backpacks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client mod bridge. The mod never sends commands: it sends custom payloads on
 * {@code backpacks:hello} (handshake), {@code backpacks:open} (the open keybind),
 * {@code backpacks:unequip}, {@code backpacks:slot} (LMB/RMB on the inventory
 * backpack slot) and {@code backpacks:key} with the name of the bound key.
 * Paper delivers unknown payloads to plugins registered on that channel.
 *
 * <p>The server answers with {@code backpacks:sync} (worn tiers for the 3D back
 * models). The backpack window itself is a normal chest inventory, so every client
 * gets native Minecraft click, drag and shortcut handling.</p>
 */
public final class ModBridge implements PluginMessageListener {
    public static final String CHANNEL_HELLO = "backpacks:hello";
    public static final String CHANNEL_OPEN = "backpacks:open";
    public static final String CHANNEL_KEY = "backpacks:key";
    public static final String CHANNEL_TOGGLE = "backpacks:toggle";
    public static final String CHANNEL_UNEQUIP = "backpacks:unequip";
    public static final String CHANNEL_SLOT = "backpacks:slot";
    public static final String CHANNEL_SYNC = "backpacks:sync";

    /** Key names come from the client, never trust them as MiniMessage input. */
    private static final int MAX_KEY_LENGTH = 32;

    private final BackpacksPlugin plugin;
    private final Set<UUID> modded = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> keyNames = new ConcurrentHashMap<>();

    public ModBridge(BackpacksPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL_HELLO, this);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL_OPEN, this);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL_KEY, this);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL_TOGGLE, this);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL_UNEQUIP, this);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL_SLOT, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL_SYNC);
    }

    public void unregister() {
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL_HELLO, this);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL_OPEN, this);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL_KEY, this);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL_TOGGLE, this);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL_UNEQUIP, this);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL_SLOT, this);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL_SYNC);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        switch (channel) {
            case CHANNEL_HELLO -> {
                // Hello marks the client as modded so it receives the 3D model sync.
                mark(player);
                Bukkit.getScheduler().runTask(plugin, () -> syncAll(player));
                // The hello can arrive before the player is added to the online list,
                // so repeat the sync once the client definitely has the entity.
                Bukkit.getScheduler().runTaskLater(plugin, () -> syncAll(player), 40L);
            }
            case CHANNEL_OPEN -> Bukkit.getScheduler().runTask(plugin, () -> open(player));
            case CHANNEL_KEY -> acceptKey(player, message);
            case CHANNEL_TOGGLE -> Bukkit.getScheduler().runTask(plugin, () -> toggle(player));
            case CHANNEL_UNEQUIP -> Bukkit.getScheduler().runTask(plugin, () -> unequip(player));
            case CHANNEL_SLOT -> {
                int button = readInt(message, 0);
                Bukkit.getScheduler().runTask(plugin, () -> slot(player, button));
            }
            default -> {
                // Unknown channel: nothing to do.
            }
        }
    }

    private void open(Player player) {
        if (!player.isOnline()) {
            return;
        }
        if (!player.hasPermission("backpacks.use")) {
            plugin.msg().send(player, "no-permission");
            return;
        }

        ItemStack equipped = plugin.items().equipped(player);
        if (equipped != null) {
            plugin.gui().openEquipped(player);
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (plugin.items().isBackpack(hand)) {
            plugin.gui().open(player, hand, false);
            return;
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (plugin.items().isBackpack(offhand)) {
            plugin.gui().open(player, offhand, false, org.bukkit.inventory.EquipmentSlot.OFF_HAND);
            return;
        }
        plugin.msg().send(player, "no-backpack");
    }

    /** LMB/RMB on the fake backpack slot in the survival inventory. */
    private void slot(Player player, int button) {
        if (!player.isOnline()) {
            return;
        }
        if (!player.hasPermission("backpacks.use")) {
            plugin.msg().send(player, "no-permission");
            return;
        }
        if (button == 1) {
            plugin.gui().openFromSlot(player);
        } else {
            plugin.gui().slotTakePut(player);
        }
    }

    private void toggle(Player player) {
        if (!player.isOnline()) {
            return;
        }
        if (!player.hasPermission("backpacks.use")) {
            plugin.msg().send(player, "no-permission");
            return;
        }
        plugin.gui().toggle(player);
    }

    private void unequip(Player player) {
        if (!player.isOnline()) {
            return;
        }
        if (!player.hasPermission("backpacks.use")) {
            plugin.msg().send(player, "no-permission");
            return;
        }
        plugin.gui().unequip(player);
    }

    private void acceptKey(Player player, byte[] message) {
        mark(player);
        String raw = new String(message, StandardCharsets.UTF_8);
        StringBuilder safe = new StringBuilder(Math.min(raw.length(), MAX_KEY_LENGTH));
        for (int i = 0; i < raw.length() && safe.length() < MAX_KEY_LENGTH; i++) {
            char current = raw.charAt(i);
            if (current == '<' || current == '>' || current == '{' || current == '}'
                    || current < ' ' || current == 0x7F) {
                continue;
            }
            safe.append(current);
        }
        String name = safe.toString().trim();
        if (name.isEmpty()) {
            keyNames.remove(player.getUniqueId());
        } else {
            keyNames.put(player.getUniqueId(), name);
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                plugin.items().refreshKeys(player);
            }
        });
    }

    /** Display name of the client open key, or a localized "key from controls" fallback. */
    public String displayKey(Player player) {
        String name = keyNames.get(player.getUniqueId());
        return name != null ? name : plugin.msg().raw("key-fallback");
    }

    private static int readInt(byte[] data, int offset) {
        if (data == null || data.length < offset + 4) {
            return -1;
        }
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    // -------------------------------------------------------------------- sync

    /** Sends the current equipped state of {@code subject} to every modded online player. */
    public void broadcastSync(Player subject) {
        if (!subject.isOnline()) {
            return;
        }
        byte[] data = syncData(subject.getEntityId(), tierOf(subject));
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (isModded(viewer)) {
                viewer.sendPluginMessage(plugin, CHANNEL_SYNC, data);
            }
        }
    }

    /** Sends the equipped state of every online player to one modded client. */
    public void syncAll(Player viewer) {
        if (!viewer.isOnline() || !isModded(viewer)) {
            return;
        }
        for (Player subject : Bukkit.getOnlinePlayers()) {
            viewer.sendPluginMessage(plugin, CHANNEL_SYNC,
                    syncData(subject.getEntityId(), tierOf(subject)));
        }
    }

    private Tier tierOf(Player player) {
        ItemStack equipped = plugin.items().equipped(player);
        return equipped == null ? null : plugin.items().tierOf(equipped);
    }

    private static byte[] syncData(int entityId, Tier tier) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(16);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(entityId);
            out.writeInt(tier == null ? 0 : tier.number());
            out.writeInt(tier == null ? 0 : tier.bodyColor());
            out.writeInt(tier == null ? 0 : tier.accentColor());
        } catch (IOException ignored) {
            // ByteArrayOutputStream never throws.
        }
        return bytes.toByteArray();
    }

    public void mark(Player player) {
        modded.add(player.getUniqueId());
    }

    public boolean isModded(Player player) {
        return modded.contains(player.getUniqueId());
    }

    public void forget(Player player) {
        modded.remove(player.getUniqueId());
        keyNames.remove(player.getUniqueId());
        // Clients keep entity render data until told otherwise; entity ids can be reused.
        byte[] data = syncData(player.getEntityId(), null);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer != player && isModded(viewer)) {
                viewer.sendPluginMessage(plugin, CHANNEL_SYNC, data);
            }
        }
    }
}
