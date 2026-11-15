package ru.backpacks.mod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

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
     * Shift + open key: take the worn backpack off and put it into the inventory.
     * The server already understands {@code backpacks:unequip} since 1.0.0.
     */
    public record Unequip() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Unequip> TYPE = payloadType("unequip");
        public static final StreamCodec<FriendlyByteBuf, Unequip> CODEC =
                StreamCodec.unit(new Unequip());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Sent once after joining so the server can mark this client as modded and
     * start syncing the 3D back models. Older mod builds send this payload with
     * no body, so the server treats a missing body as protocol 0.
     */
    public record Hello(int protocol) implements CustomPacketPayload {
        /** Bumped when the client/server payload contract changes. */
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
}
