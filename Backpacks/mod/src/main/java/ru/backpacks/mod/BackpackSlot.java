package ru.backpacks.mod;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Fake backpack slot in the survival inventory, right next to the player preview
 * (above the offhand slot). The slot itself lives only on the client; clicks send
 * {@code backpacks:slot} and the server owns the result: LMB takes the worn backpack
 * to the cursor or puts a backpack from the cursor into the slot, RMB opens it.
 */
public final class BackpackSlot {
    private static final Identifier SLOT_SPRITE =
            Identifier.fromNamespaceAndPath("minecraft", "container/slot");

    /** Position inside the 176x166 inventory GUI. */
    private static final int SLOT_X = 77;
    private static final int SLOT_Y = 44;
    private static final int SIZE = 16;

    private static Field leftPos;
    private static Field topPos;

    private BackpackSlot() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof InventoryScreen inventory)) {
                return;
            }
            ScreenEvents.afterForeground(screen).register(BackpackSlot::onForeground);
            ScreenMouseEvents.allowMouseClick(screen).register(BackpackSlot::onMouseClick);
        });
    }

    private static void onForeground(Screen screen, GuiGraphicsExtractor graphics,
                                     int mouseX, int mouseY, float partialTick) {
        if (screen instanceof InventoryScreen inventory) {
            render(inventory, graphics, Minecraft.getInstance(), mouseX, mouseY);
        }
    }

    private static boolean onMouseClick(Screen screen, MouseButtonEvent event) {
        return !(screen instanceof InventoryScreen inventory) || click(inventory, event);
    }

    private static int left(InventoryScreen screen) {
        try {
            if (leftPos == null) {
                leftPos = AbstractContainerScreen.class.getDeclaredField("leftPos");
                leftPos.setAccessible(true);
            }
            return leftPos.getInt(screen);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return (screen.width - 176) / 2;
        }
    }

    private static int top(InventoryScreen screen) {
        try {
            if (topPos == null) {
                topPos = AbstractContainerScreen.class.getDeclaredField("topPos");
                topPos.setAccessible(true);
            }
            return topPos.getInt(screen);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return (screen.height - 166) / 2;
        }
    }

    private static void render(InventoryScreen screen, GuiGraphicsExtractor graphics, Minecraft client,
                               int mouseX, int mouseY) {
        int x = left(screen) + SLOT_X;
        int y = top(screen) + SLOT_Y;

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, x, y, SIZE, SIZE);

        int tier = client.player == null ? 0 : BackpackState.tierOf(client.player.getId());
        if (tier > 0) {
            graphics.item(BackpackState.icon(tier), x, y);
        }

        if (mouseX >= x && mouseX < x + SIZE && mouseY >= y && mouseY < y + SIZE) {
            List<Component> lines = new ArrayList<>(4);
            lines.add(Component.translatable("screen.backpacks.slot").withStyle(ChatFormatting.GOLD));
            if (tier > 0) {
                // The tier number is a translation argument: the lang file uses %s.
                lines.add(Component.translatable("screen.backpacks.slot.tier", tier)
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("screen.backpacks.slot.hint")
                        .withStyle(ChatFormatting.DARK_GRAY));
                lines.add(Component.translatable("screen.backpacks.slot.open")
                        .withStyle(ChatFormatting.DARK_GRAY));
            } else {
                lines.add(Component.translatable("screen.backpacks.slot.empty")
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("screen.backpacks.slot.hint")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            graphics.setComponentTooltipForNextFrame(client.font, lines, mouseX, mouseY);
        }
    }

    private static boolean click(InventoryScreen screen, MouseButtonEvent event) {
        int x = left(screen) + SLOT_X;
        int y = top(screen) + SLOT_Y;
        if (event.x() < x || event.x() >= x + SIZE || event.y() < y || event.y() >= y + SIZE) {
            return true;
        }
        if (event.button() == 0 || event.button() == 1) {
            ClientPlayNetworking.send(new BackpackSignals.Slot(event.button()));
            return false;
        }
        return true;
    }
}
