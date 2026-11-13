package ru.backpacks.mod;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class BackpacksMod implements ClientModInitializer {
    private static KeyMapping openKey;
    private static String sentBinding;

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.serverboundPlay().register(BackpackSignals.Open.TYPE, BackpackSignals.Open.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BackpackSignals.Hello.TYPE, BackpackSignals.Hello.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BackpackSignals.Key.TYPE, BackpackSignals.Key.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BackpackSignals.Toggle.TYPE, BackpackSignals.Toggle.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BackpackSignals.Unequip.TYPE, BackpackSignals.Unequip.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BackpackSignals.Sync.TYPE, BackpackSignals.Sync.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(BackpackSignals.Sync.TYPE,
                (payload, context) -> BackpackState.set(payload.entityId(), payload.tier(),
                        payload.bodyColor(), payload.accentColor()));

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("backpacks", "backpacks"));

        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.backpacks.open",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_B,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                if (client.player != null && client.player.isShiftKeyDown()) {
                    send(new BackpackSignals.Unequip());
                } else {
                    send(new BackpackSignals.Open());
                }
            }
            sendKey();
        });

        // Lets the server know the client has the mod (used by require-mod only),
        // then tells it which key is actually bound to opening the backpack.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            sentBinding = null;
            send(new BackpackSignals.Hello());
            sendKey();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            BackpackState.clear();
            sentBinding = null;
        });

        BackpackSlot.register();
        BackpackLayer.register();
    }

    private static void sendKey() {
        if (openKey == null || Minecraft.getInstance().getConnection() == null) {
            return;
        }
        String binding = openKey.saveString();
        if (binding.equals(sentBinding)) {
            return;
        }
        sentBinding = binding;
        // An unbound key is sent as an empty name so the server shows its own
        // localized "the key set in controls" fallback instead of "Unknown".
        String name = openKey.isUnbound() ? "" : openKey.getTranslatedKeyMessage().getString();
        send(new BackpackSignals.Key(name));
    }

    private static void send(CustomPacketPayload payload) {
        if (Minecraft.getInstance().getConnection() == null) {
            return;
        }
        ClientPlayNetworking.send(payload);
    }
}
