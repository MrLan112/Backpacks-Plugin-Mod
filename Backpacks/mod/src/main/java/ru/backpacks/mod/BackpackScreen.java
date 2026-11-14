package ru.backpacks.mod;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/**
 * One-screen backpack GUI for modded clients. The server owns the contents, the
 * cursor and every click result; this screen only renders the latest snapshot and
 * forwards clicks as {@code backpacks:gui_click} payloads.
 *
 * <p>The whole window is scaled down when a large tier (up to 256 slots) would not
 * fit on the current GUI scale, so there are never pages.</p>
 */
public final class BackpackScreen extends Screen {
    private static final Identifier SLOT_SPRITE =
            Identifier.fromNamespaceAndPath("minecraft", "container/slot");

    private static final int COLS = 9;
    private static final int SLOT = 18;
    private static final int PAD = 7;
    private static final int TOP = 7;
    /** Two title lines: tier name and capacity/close hint. */
    private static final int TITLE_H = 25;
    private static final int INV_GAP = 10;
    private static final int HOTBAR_GAP = 4;
    private static final int BOTTOM = 8;
    private static final int GUI_W = COLS * SLOT + PAD * 2;

    private final int sessionId;
    private final int tier;
    private final int bodyColor;
    private final int accentColor;
    private final Component heading;

    private int capacity;
    private List<BackpackSignals.GuiItem> cells = List.of();
    private List<BackpackSignals.GuiItem> inventory = List.of();
    private BackpackSignals.GuiItem cursor = BackpackSignals.GuiItem.EMPTY;

    private int rows;
    private int gridY;
    private int playerY;
    private int hotbarY;
    private int guiH;

    private float scale = 1.0f;
    private float originX;
    private float originY;
    private boolean closed;

    private BackpackScreen(BackpackSignals.GuiOpen payload) {
        super(Component.empty());
        this.sessionId = payload.sessionId();
        this.tier = payload.tier();
        this.bodyColor = payload.bodyColor();
        this.accentColor = payload.accentColor();
        this.heading = Component.literal(payload.title()).withStyle(ChatFormatting.GOLD);
        this.capacity = Math.max(1, payload.capacity());
        computeSize();
    }

    // ------------------------------------------------------------- lifecycle

    public static void open(BackpackSignals.GuiOpen payload) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen current = minecraft.gui.screen();
        if (current instanceof BackpackScreen screen && screen.sessionId == payload.sessionId()) {
            return;
        }
        minecraft.setScreenAndShow(new BackpackScreen(payload));
    }

    public static void applyData(BackpackSignals.GuiData data) {
        Screen current = Minecraft.getInstance().gui.screen();
        if (current instanceof BackpackScreen screen && screen.sessionId == data.sessionId()) {
            screen.apply(data);
        }
    }

    /** The server closed the GUI (logout, death, another backpack opened). */
    public static void close(int sessionId) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen current = minecraft.gui.screen();
        if (current instanceof BackpackScreen screen && screen.sessionId == sessionId) {
            screen.closed = true;
            minecraft.setScreenAndShow(null);
        }
    }

    private void apply(BackpackSignals.GuiData data) {
        if (data.capacity() != capacity) {
            capacity = Math.max(1, data.capacity());
            computeSize();
            layout();
        }
        cells = data.cells();
        inventory = data.inventory();
        cursor = data.cursor();
    }

    private void computeSize() {
        rows = Math.max(1, (capacity + COLS - 1) / COLS);
        gridY = TOP + TITLE_H;
        playerY = gridY + rows * SLOT + INV_GAP;
        hotbarY = playerY + 3 * SLOT + HOTBAR_GAP;
        guiH = hotbarY + SLOT + BOTTOM;
    }

    @Override
    protected void init() {
        layout();
    }

    private void layout() {
        float byWidth = (width - 8.0f) / GUI_W;
        float byHeight = (height - 8.0f) / guiH;
        scale = Math.min(1.0f, Math.min(byWidth, byHeight));
        // Below this the interface is unreadable; the panel may slightly overflow.
        scale = Math.max(0.3f, scale);
        originX = (width - GUI_W * scale) / 2.0f;
        originY = (height - guiH * scale) / 2.0f;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractTransparentBackground(graphics);
    }

    // -------------------------------------------------------------- rendering

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        layout();
        Hover hover = hover(mouseX, mouseY);
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(originX, originY);
        pose.scale(scale, scale);

        drawPanel(graphics);
        drawTitle(graphics);

        for (int index = 0; index < rows * COLS; index++) {
            int column = index % COLS;
            int row = index / COLS;
            BackpackSignals.GuiItem item = index < capacity && index < cells.size()
                    ? cells.get(index) : BackpackSignals.GuiItem.EMPTY;
            boolean highlighted = hover.area() == 0 && hover.slot() == index;
            drawSlot(graphics, PAD + column * SLOT, gridY + row * SLOT, item, highlighted, true);
        }

        for (int index = 0; index < 27; index++) {
            int column = index % COLS;
            int row = index / COLS;
            int slot = 9 + index;
            BackpackSignals.GuiItem item = slot < inventory.size()
                    ? inventory.get(slot) : BackpackSignals.GuiItem.EMPTY;
            boolean highlighted = hover.area() == 1 && hover.slot() == slot;
            drawSlot(graphics, PAD + column * SLOT, playerY + row * SLOT, item, highlighted, false);
        }
        for (int column = 0; column < COLS; column++) {
            BackpackSignals.GuiItem item = column < inventory.size()
                    ? inventory.get(column) : BackpackSignals.GuiItem.EMPTY;
            boolean highlighted = hover.area() == 1 && hover.slot() == column;
            drawSlot(graphics, PAD + column * SLOT, hotbarY, item, highlighted, false);
        }

        pose.popMatrix();

        if (!cursor.isEmpty()) {
            ItemStack stack = stackOf(cursor, false);
            int x = mouseX - 8;
            int y = mouseY - 8;
            graphics.item(stack, x, y);
            graphics.itemDecorations(font, stack, x, y);
        }

        BackpackSignals.GuiItem hovered = hoveredItem(hover);
        if (cursor.isEmpty() && hovered != null && !hovered.isEmpty()) {
            graphics.setTooltipForNextFrame(font, stackOf(hovered, hover.area() == 0), mouseX, mouseY);
        }
    }

    private void drawPanel(GuiGraphicsExtractor graphics) {
        int border = 0xFF000000 | (bodyColor & 0xFFFFFF);
        graphics.fill(0, 0, GUI_W, guiH, 0xF0141414);
        graphics.fill(0, 0, GUI_W, 1, border);
        graphics.fill(0, guiH - 1, GUI_W, guiH, border);
        graphics.fill(0, 0, 1, guiH, border);
        graphics.fill(GUI_W - 1, 0, GUI_W, guiH, border);
        graphics.fill(PAD, gridY - 4, GUI_W - PAD, gridY - 3, 0xFF2E2E2E);
        graphics.fill(PAD, playerY - 6, GUI_W - PAD, playerY - 5, 0xFF2E2E2E);
    }

    private void drawTitle(GuiGraphicsExtractor graphics) {
        Component fitted = heading;
        if (font.width(heading) > GUI_W - 12) {
            fitted = Component.literal(font.plainSubstrByWidth(heading.getString(), GUI_W - 20) + "…")
                    .withStyle(ChatFormatting.GOLD);
        }
        graphics.centeredText(font, fitted, GUI_W / 2, TOP, 0xFFFFFF);
        Component subtitle = Component.translatable("screen.backpacks.gui.subtitle", capacity,
                minecraft == null ? Component.literal("E")
                        : minecraft.options.keyInventory.getTranslatedKeyMessage());
        graphics.centeredText(font, subtitle, GUI_W / 2, TOP + 12, 0xFF9A9A9A);
        if (tier > 0) {
            String label = "T" + tier;
            graphics.text(font, label, GUI_W - PAD - font.width(label), TOP, 0xFF000000 | accentColor);
        }
    }

    private void drawSlot(GuiGraphicsExtractor graphics, int x, int y,
                          BackpackSignals.GuiItem item, boolean highlighted, boolean backpackCell) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, x, y, SLOT, SLOT);
        if (highlighted) {
            graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0x40FFFFFF);
        }
        if (item == null || item.isEmpty()) {
            return;
        }
        ItemStack stack = stackOf(item, backpackCell);
        stack.setCount(Math.min(item.count(), Math.max(1, item.maxStack())));
        graphics.item(stack, x + 1, y + 1);
        if (item.count() > item.maxStack()) {
            drawTotal(graphics, item.count(), x, y);
        } else {
            graphics.itemDecorations(font, stack, x + 1, y + 1);
        }
    }

    private void drawTotal(GuiGraphicsExtractor graphics, int count, int x, int y) {
        String text = String.valueOf(count);
        float textScale = 0.7f;
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x + SLOT - 2 - font.width(text) * textScale, y + SLOT - 10);
        pose.scale(textScale, textScale);
        graphics.text(font, text, 0, 0, 0xFFFFFFFF, true);
        pose.popMatrix();
    }

    private BackpackSignals.GuiItem hoveredItem(Hover hover) {
        if (hover.area() == 0) {
            return hover.slot() < cells.size() ? cells.get(hover.slot()) : null;
        }
        if (hover.area() == 1) {
            return hover.slot() < inventory.size() ? inventory.get(hover.slot()) : null;
        }
        return null;
    }

    /** Builds a client-side stack from the components sent by the server. */
    private static ItemStack stackOf(BackpackSignals.GuiItem item, boolean backpackCell) {
        if (item == null || item.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Identifier id = Identifier.tryParse(item.id());
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Item value = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (value == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(value);
        stack.setCount(Math.max(1, Math.min(item.count(), Math.max(1, item.maxStack()))));
        if (!item.name().isEmpty()) {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(item.name()));
        }
        List<Component> lore = new ArrayList<>(item.lore().size() + 2);
        for (String line : item.lore()) {
            lore.add(Component.literal(line));
        }
        if (backpackCell) {
            if (item.count() > item.maxStack()) {
                lore.add(Component.translatable("screen.backpacks.gui.total", item.count())
                        .withStyle(ChatFormatting.GRAY));
            }
            lore.add(Component.translatable("screen.backpacks.gui.pick")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (!lore.isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(lore));
        }
        if (item.glint()) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        if (item.damage() > 0) {
            stack.set(DataComponents.DAMAGE, item.damage());
        }
        if (!item.model().isEmpty()) {
            Identifier model = Identifier.tryParse(item.model());
            if (model != null) {
                stack.set(DataComponents.ITEM_MODEL, model);
            }
        }
        return stack;
    }

    // ------------------------------------------------------------------ input

    private record Hover(int area, int slot) {
        private static final Hover NONE = new Hover(-1, -1);
    }

    private Hover hover(int mouseX, int mouseY) {
        double localX = (mouseX - originX) / scale;
        double localY = (mouseY - originY) / scale;
        if (localX < PAD || localX >= PAD + COLS * SLOT) {
            return Hover.NONE;
        }
        int column = (int) ((localX - PAD) / SLOT);
        if (localY >= gridY && localY < gridY + rows * SLOT) {
            int row = (int) ((localY - gridY) / SLOT);
            int index = row * COLS + column;
            return index < capacity ? new Hover(0, index) : Hover.NONE;
        }
        if (localY >= playerY && localY < playerY + 3 * SLOT) {
            int row = (int) ((localY - playerY) / SLOT);
            return new Hover(1, 9 + row * COLS + column);
        }
        if (localY >= hotbarY && localY < hotbarY + SLOT) {
            return new Hover(1, column);
        }
        return Hover.NONE;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int button = event.button();
        if (button < 0 || button > 2) {
            return super.mouseClicked(event, doubleClick);
        }
        Hover hover = hover((int) event.x(), (int) event.y());
        if (hover.area() < 0) {
            if (cursor.isEmpty()) {
                return true;
            }
            send(new BackpackSignals.GuiClick(sessionId, 2, 0, button, event.hasShiftDown()));
            return true;
        }
        send(new BackpackSignals.GuiClick(sessionId, hover.area(), hover.slot(), button,
                event.hasShiftDown()));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() || minecraft.options.keyInventory.matches(event)) {
            close(true);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        if (!closed) {
            closed = true;
            send(new BackpackSignals.GuiClosed(sessionId));
        }
        super.removed();
    }

    private void close(boolean notifyServer) {
        if (closed) {
            return;
        }
        closed = true;
        if (notifyServer) {
            send(new BackpackSignals.GuiClosed(sessionId));
        }
        minecraft.setScreenAndShow(null);
    }

    private void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (minecraft.getConnection() != null) {
            ClientPlayNetworking.send(payload);
        }
    }
}
