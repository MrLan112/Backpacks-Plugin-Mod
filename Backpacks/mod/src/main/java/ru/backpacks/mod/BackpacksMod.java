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
        PayloadTypeRegistry.serverboundPlay().register(BackpackSignals.Unequip.TYPE, BackpackSignals.Unequip.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BackpackSignals.Hello.TYPE, BackpackSignals.Hello.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BackpackSignals.Key.TYPE, BackpackSignals.Key.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BackpackSignals.Slot.TYPE, BackpackSignals.Slot.CODEC);
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
                // Shift + open key takes the worn backpack off (restored 1.x shortcut).
                boolean shift = client.options.keyShift.isDown();
                send(shift ? new BackpackSignals.Unequip() : new BackpackSignals.Open());
            }
            sendKey();
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            sentBinding = null;
            send(new BackpackSignals.Hello(BackpackSignals.Hello.CURRENT));
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
