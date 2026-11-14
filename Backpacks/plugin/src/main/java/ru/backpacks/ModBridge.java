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
 * {@code backpacks:hello} (handshake + protocol version), {@code backpacks:open}
 * (the open keybind), {@code backpacks:slot} (LMB/RMB on the inventory backpack slot),
 * {@code backpacks:gui_click} / {@code backpacks:gui_closed} (one-screen GUI) and
 * {@code backpacks:key} with the name of the bound key.
 * Paper delivers unknown payloads to plugins registered on that channel.
 *
 * <p>The server answers with {@code backpacks:sync} (worn tiers for nametags/back
 * models), {@code backpacks:gui_open}, {@code backpacks:gui_data} and
 * {@code backpacks:gui_close}.</p>
 */
public final class ModBridge implements PluginMessageListener {
    public static final String CHANNEL_HELLO = "backpacks:hello";
    public static final String CHANNEL_OPEN = "backpacks:open";
    public static final String CHANNEL_KEY = "backpacks:key";
    public static final String CHANNEL_TOGGLE = "backpacks:toggle";
    public static final String CHANNEL_UNEQUIP = "backpacks:unequip";
    public static final String CHANNEL_SLOT = "backpacks:slot";
    public static final String CHANNEL_GUI_CLICK = "backpacks:gui_click";
    public static final String CHANNEL_GUI_KEY = "backpacks:gui_key";
    public static final String CHANNEL_GUI_CLOSED = "backpacks:gui_closed";
    public static final String CHANNEL_GUI_REQUEST = "backpacks:gui_request";
    public static final String CHANNEL_SYNC = "backpacks:sync";
    public static final String CHANNEL_GUI_OPEN = "backpacks:gui_open";
    public static final String CHANNEL_GUI_DATA = "backpacks:gui_data";
    public static final String CHANNEL_GUI_CLOSE = "backpacks:gui_close";

    /** Client protocol 1 = one-screen custom GUI. Older clients send an empty hello. */
    public static final int PROTOCOL_GUI = 1;

    /** Key names come from the client, never trust them as MiniMessage input. */
    private static final int MAX_KEY_LENGTH = 32;

    private final BackpacksPlugin plugin;
    private final Set<UUID> modded = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> keyNames = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> protocols = new ConcurrentHashMap<>();

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
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL_GUI_CLICK, this);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL_GUI_KEY, this);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL_GUI_CLOSED, this);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL_GUI_REQUEST, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL_SYNC);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL_GUI_OPEN);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL_GUI_DATA);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL_GUI_CLOSE);
    }

    public void unregister() {
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL_HELLO, this);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL_OPEN, this);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL_KEY, this);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL_TOGGLE, this);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL_UNEQUIP, this);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL_SLOT, this);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL_GUI_CLICK, this);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL_GUI_KEY, this);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL_GUI_CLOSED, this);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL_GUI_REQUEST, this);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL_SYNC);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL_GUI_OPEN);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL_GUI_DATA);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL_GUI_CLOSE);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        switch (channel) {
            case CHANNEL_HELLO -> {
                // The old mod sends an empty hello: it only gets the chest GUI.
                mark(player, message.length >= 4 ? readInt(message, 0) : 0);
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
            case CHANNEL_GUI_CLICK -> {
                int sessionId = readInt(message, 0);
                int area = readInt(message, 4);
                int slot = readInt(message, 8);
                int button = readInt(message, 12);
                boolean shift = message.length > 16 && message[16] != 0;
                boolean control = message.length > 17 && message[17] != 0;
                boolean doubleClick = message.length > 18 && message[18] != 0;
                Bukkit.getScheduler().runTask(plugin, () -> plugin.gui().handleGuiClick(
                        player, sessionId, area, slot, button, shift, control, doubleClick));
            }
            case CHANNEL_GUI_KEY -> {
                int sessionId = readInt(message, 0);
                int area = readInt(message, 4);
                int slot = readInt(message, 8);
                int hotbar = readInt(message, 12);
                int action = readInt(message, 16);
                boolean shift = message.length > 20 && message[20] != 0;
                boolean control = message.length > 21 && message[21] != 0;
                Bukkit.getScheduler().runTask(plugin, () -> plugin.gui().handleGuiKey(
                        player, sessionId, area, slot, hotbar, action, shift, control));
            }
            case CHANNEL_GUI_CLOSED -> {
                int sessionId = readInt(message, 0);
                Bukkit.getScheduler().runTask(plugin,
                        () -> plugin.gui().handleGuiClosed(player, sessionId));
            }
            case CHANNEL_GUI_REQUEST -> {
                int sessionId = readInt(message, 0);
                Bukkit.getScheduler().runTask(plugin,
                        () -> plugin.gui().handleGuiRequest(player, sessionId));
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
        mark(player, protocols.getOrDefault(player.getUniqueId(), 0));
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

    /** True when the client understands the one-screen GUI payloads. */
    public boolean supportsCustomGui(Player player) {
        return protocols.getOrDefault(player.getUniqueId(), 0) >= PROTOCOL_GUI;
    }

    // ------------------------------------------------------------------ output

    public void sendGuiOpen(Player player, int sessionId, int tier, int capacity,
                            int bodyColor, int accentColor, String title) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(64);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(sessionId);
            out.writeInt(tier);
            out.writeInt(capacity);
            out.writeInt(bodyColor);
            out.writeInt(accentColor);
            writeString(out, title);
        } catch (IOException ignored) {
            // ByteArrayOutputStream never throws.
        }
        send(player, CHANNEL_GUI_OPEN, bytes.toByteArray());
    }

    public void sendGuiData(Player player, byte[] data) {
        send(player, CHANNEL_GUI_DATA, data);
    }

    public void sendGuiClose(Player player, int sessionId) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(4);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(sessionId);
        } catch (IOException ignored) {
            // ByteArrayOutputStream never throws.
        }
        send(player, CHANNEL_GUI_CLOSE, bytes.toByteArray());
    }

    private void send(Player player, String channel, byte[] data) {
        if (player.isOnline() && isModded(player)) {
            player.sendPluginMessage(plugin, channel, data);
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
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
        mark(player, protocols.getOrDefault(player.getUniqueId(), 0));
    }

    public void mark(Player player, int protocol) {
        modded.add(player.getUniqueId());
        protocols.put(player.getUniqueId(), protocol);
    }

    public boolean isModded(Player player) {
        return modded.contains(player.getUniqueId());
    }

    public void forget(Player player) {
        modded.remove(player.getUniqueId());
        keyNames.remove(player.getUniqueId());
        protocols.remove(player.getUniqueId());
        // Clients keep entity render data until told otherwise; entity ids can be reused.
        byte[] data = syncData(player.getEntityId(), null);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer != player && isModded(viewer)) {
                viewer.sendPluginMessage(plugin, CHANNEL_SYNC, data);
            }
        }
    }
}
