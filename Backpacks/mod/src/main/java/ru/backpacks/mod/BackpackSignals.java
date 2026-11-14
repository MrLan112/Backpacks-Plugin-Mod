package ru.backpacks.mod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom payloads shared with the server plugin. The mod never types commands:
 * Paper delivers unknown payloads to the plugin channel with the same id.
 *
 * <p>Client to server payloads use the normal Minecraft codecs. Server to client
 * payloads are written by {@code ModBridge} with {@code DataOutputStream}: strings
 * are an {@code int} byte length followed by UTF-8 bytes, so they must not be read
 * with {@link FriendlyByteBuf#readUtf()}.</p>
 */
public final class BackpackSignals {
    private BackpackSignals() {
    }

    /**
     * {@code CustomPacketPayload.createType(String)} prefixes the argument with
     * {@code minecraft:}, so the identifier is built explicitly here.
     */
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("backpacks", path));
    }

    /** Keybind pressed: open the equipped (or held) backpack. */
    public record Open() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Open> TYPE = payloadType("open");
        public static final StreamCodec<FriendlyByteBuf, Open> CODEC = StreamCodec.unit(new Open());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Sent once after joining. Older mod builds send this payload with no body,
     * so the server treats a missing body as protocol 0.
     */
    public record Hello(int protocol) implements CustomPacketPayload {
        /** 1 = the one-screen backpack GUI is supported. */
        public static final int CURRENT = 1;
        public static final CustomPacketPayload.Type<Hello> TYPE = payloadType("hello");
        public static final StreamCodec<FriendlyByteBuf, Hello> CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeInt(payload.protocol()),
                buf -> new Hello(buf.readInt()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: entity id -> worn tier and colours (tier 0 = no backpack). */
    public record Sync(int entityId, int tier, int bodyColor, int accentColor)
            implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Sync> TYPE = payloadType("sync");
        public static final StreamCodec<FriendlyByteBuf, Sync> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeInt(payload.entityId());
                    buf.writeInt(payload.tier());
                    buf.writeInt(payload.bodyColor());
                    buf.writeInt(payload.accentColor());
                },
                buf -> new Sync(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * The current display name of the open key binding. The server inserts it into
     * messages instead of hardcoding "B", so a rebound key is shown correctly.
     */
    public record Key(String name) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Key> TYPE = payloadType("key");
        public static final StreamCodec<FriendlyByteBuf, Key> CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeBytes(payload.name().getBytes(StandardCharsets.UTF_8)),
                buf -> {
                    byte[] bytes = new byte[buf.readableBytes()];
                    buf.readBytes(bytes);
                    return new Key(new String(bytes, StandardCharsets.UTF_8));
                });

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Click on the backpack slot in the survival inventory.
     * {@code button}: 0 = LMB take/put, 1 = RMB open.
     */
    public record Slot(int button) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Slot> TYPE = payloadType("slot");
        public static final StreamCodec<FriendlyByteBuf, Slot> CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeInt(payload.button()),
                buf -> new Slot(buf.readInt()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: open the custom one-screen GUI. */
    public record GuiOpen(int sessionId, int tier, int capacity, int bodyColor, int accentColor,
                          String title) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<GuiOpen> TYPE = payloadType("gui_open");
        public static final StreamCodec<FriendlyByteBuf, GuiOpen> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeInt(payload.sessionId());
                    buf.writeInt(payload.tier());
                    buf.writeInt(payload.capacity());
                    buf.writeInt(payload.bodyColor());
                    buf.writeInt(payload.accentColor());
                    writeServerString(buf, payload.title());
                },
                buf -> new GuiOpen(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                        buf.readInt(), readServerString(buf)));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * One item as rendered by the client. Only the components needed for the icon
     * and tooltip are sent; the full stack stays on the server.
     */
    public record GuiItem(String id, int count, int maxStack, int damage, String name,
                          List<String> lore, boolean glint, String model) {
        public static final GuiItem EMPTY =
                new GuiItem("", 0, 0, 0, "", List.of(), false, "");

        public boolean isEmpty() {
            return id.isEmpty() || count <= 0;
        }
    }

    /** Server to client: full state of the open custom GUI. */
    public record GuiData(int sessionId, int capacity, GuiItem cursor, List<GuiItem> cells,
                          List<GuiItem> inventory) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<GuiData> TYPE = payloadType("gui_data");
        public static final StreamCodec<FriendlyByteBuf, GuiData> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeInt(payload.sessionId());
                    buf.writeInt(payload.capacity());
                    writeItem(buf, payload.cursor());
                    for (GuiItem item : payload.cells()) {
                        writeItem(buf, item);
                    }
                    for (GuiItem item : payload.inventory()) {
                        writeItem(buf, item);
                    }
                },
                buf -> {
                    int sessionId = buf.readInt();
                    int capacity = buf.readInt();
                    if (capacity < 0 || capacity > 4096) {
                        throw new IllegalArgumentException("Invalid backpack capacity " + capacity);
                    }
                    GuiItem cursor = readItem(buf);
                    List<GuiItem> cells = new ArrayList<>(capacity);
                    for (int i = 0; i < capacity; i++) {
                        cells.add(readItem(buf));
                    }
                    List<GuiItem> inventory = new ArrayList<>(36);
                    for (int i = 0; i < 36; i++) {
                        inventory.add(readItem(buf));
                    }
                    return new GuiData(sessionId, capacity, cursor, cells, inventory);
                });

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: close the custom GUI without a client-side click. */
    public record GuiClose(int sessionId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<GuiClose> TYPE = payloadType("gui_close");
        public static final StreamCodec<FriendlyByteBuf, GuiClose> CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeInt(payload.sessionId()),
                buf -> new GuiClose(buf.readInt()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Click inside the custom GUI. {@code area}: 0 = backpack grid,
     * 1 = player inventory, 2 = outside the window. {@code button}: 0 = LMB,
     * 1 = RMB, 2 = MMB (pick item).
     */
    public record GuiClick(int sessionId, int area, int slot, int button,
                           boolean shift) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<GuiClick> TYPE = payloadType("gui_click");
        public static final StreamCodec<FriendlyByteBuf, GuiClick> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeInt(payload.sessionId());
                    buf.writeInt(payload.area());
                    buf.writeInt(payload.slot());
                    buf.writeInt(payload.button());
                    buf.writeBoolean(payload.shift());
                },
                buf -> new GuiClick(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                        buf.readBoolean()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The player closed the custom GUI (E, ESC or another screen opened). */
    public record GuiClosed(int sessionId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<GuiClosed> TYPE = payloadType("gui_closed");
        public static final StreamCodec<FriendlyByteBuf, GuiClosed> CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeInt(payload.sessionId()),
                buf -> new GuiClosed(buf.readInt()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------ server strings

    private static void writeServerString(FriendlyByteBuf buf, String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readServerString(FriendlyByteBuf buf) {
        int length = buf.readInt();
        if (length < 0 || length > 32767) {
            throw new IllegalArgumentException("Invalid string length " + length);
        }
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeItem(FriendlyByteBuf buf, GuiItem item) {
        if (item == null || item.isEmpty()) {
            buf.writeBoolean(false);
            return;
        }
        buf.writeBoolean(true);
        buf.writeInt(item.count());
        buf.writeInt(item.maxStack());
        buf.writeInt(item.damage());
        buf.writeBoolean(item.glint());
        writeServerString(buf, item.id());
        writeServerString(buf, item.name());
        buf.writeInt(item.lore().size());
        for (String line : item.lore()) {
            writeServerString(buf, line);
        }
        writeServerString(buf, item.model());
    }

    private static GuiItem readItem(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return GuiItem.EMPTY;
        }
        int count = buf.readInt();
        int maxStack = buf.readInt();
        int damage = buf.readInt();
        boolean glint = buf.readBoolean();
        String id = readServerString(buf);
        String name = readServerString(buf);
        int lines = buf.readInt();
        if (lines < 0 || lines > 256) {
            throw new IllegalArgumentException("Invalid lore size " + lines);
        }
        List<String> lore = new ArrayList<>(lines);
        for (int i = 0; i < lines; i++) {
            lore.add(readServerString(buf));
        }
        String model = readServerString(buf);
        return new GuiItem(id, count, maxStack, damage, name, lore, glint, model);
    }
}
