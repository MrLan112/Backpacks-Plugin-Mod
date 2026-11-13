package ru.backpacks.mod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Client to server signals. The mod never types commands: Paper delivers unknown
 * payloads to the plugin channel with the same id (see {@code ModBridge}).
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

    /** Sent once after joining, used by the optional {@code require-mod} check. */
    public record Hello() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Hello> TYPE = payloadType("hello");
        public static final StreamCodec<FriendlyByteBuf, Hello> CODEC = StreamCodec.unit(new Hello());

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

    /** Click on the backpack slot in the survival inventory. */
    public record Toggle() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Toggle> TYPE = payloadType("toggle");
        public static final StreamCodec<FriendlyByteBuf, Toggle> CODEC = StreamCodec.unit(new Toggle());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Shift + the open key: remove the equipped backpack. */
    public record Unequip() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Unequip> TYPE = payloadType("unequip");
        public static final StreamCodec<FriendlyByteBuf, Unequip> CODEC = StreamCodec.unit(new Unequip());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server to client: entity {@code entityId} wears a backpack of {@code tier} (0 = none).
     * The two colours are the tier body/accent RGB values, so the client does not need its
     * own copy of the tier table.
     */
    public record Sync(int entityId, int tier, int bodyColor, int accentColor) implements CustomPacketPayload {
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
}
