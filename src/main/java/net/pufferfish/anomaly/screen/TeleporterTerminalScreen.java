package net.pufferfish.anomaly.screen;

import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import net.pufferfish.anomaly.block.TeleporterTerminalBlockEntity;
import net.pufferfish.anomaly.net.ModPackets;

public class TeleporterTerminalScreen extends HandledScreen<TeleporterTerminalScreenHandler> {
    private TextFieldWidget xField, yField, zField, nameField;
    private ButtonWidget addBtn, renameBtn, deleteBtn, closeBtn;

    private int listIndexTop = 0;   // top-most visible absolute index
    private int selectedLocal = -1; // selected row inside visible window [0..ROWS_VISIBLE-1]
    private static final int ROWS_VISIBLE = 6;

    public TeleporterTerminalScreen(TeleporterTerminalScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 236;
        this.backgroundHeight = 186;
    }

    @Override
    protected void init() {
        super.init();
        int left = (width - backgroundWidth) / 2;
        int top  = (height - backgroundHeight) / 2;

        // CLOSE (X) — 14x14 inside the 20px title bar
        closeBtn = addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> this.close())
                .dimensions(left + backgroundWidth - 18, top + 3, 14, 14).build());

        // --- Coordinate fields (48 wide each, 4px gaps) ---
        // x: [left+10 .. +58], y: [left+62 .. +110], z: [left+114 .. +162]
        xField = addDrawableChild(new TextFieldWidget(textRenderer, left + 10,  top + 24, 48, 18, Text.literal("X")));
        yField = addDrawableChild(new TextFieldWidget(textRenderer, left + 62,  top + 24, 48, 18, Text.literal("Y")));
        zField = addDrawableChild(new TextFieldWidget(textRenderer, left + 114, top + 24, 48, 18, Text.literal("Z")));

        // Name field EXACT length of X+gap+Y+gap+Z = 152
        nameField = addDrawableChild(new TextFieldWidget(textRenderer, left + 10, top + 48, 152, 18, Text.literal("Name")));

        xField.setMaxLength(12); yField.setMaxLength(12); zField.setMaxLength(12);
        xField.setTextPredicate(s -> s.matches("-?\\d*"));
        yField.setTextPredicate(s -> s.matches("-?\\d*"));
        zField.setTextPredicate(s -> s.matches("-?\\d*"));
        nameField.setMaxLength(64);

        // Buttons
        addBtn    = addDrawableChild(ButtonWidget.builder(Text.literal("Add"),     b -> onAdd()).dimensions(left + 168, top + 24, 58, 18).build());
        renameBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Rename"),  b -> onRename()).dimensions(left + 168, top + 48, 58, 18).build());
        deleteBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Delete"),  b -> onDelete()).dimensions(left + 168, top + 72, 58, 18).build());

        // Align UI to currently server-selected row if any
        snapSelectionIntoView();
        setInitialFocus(xField);
    }

    /* ---------- actions ---------- */

    private void onAdd() {
        String xs = xField.getText().trim();
        String ys = yField.getText().trim();
        String zs = zField.getText().trim();
        String name = nameField.getText().trim();
        if (xs.isEmpty() || ys.isEmpty() || zs.isEmpty()) return;
        try {
            int x = Integer.parseInt(xs), y = Integer.parseInt(ys), z = Integer.parseInt(zs);
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(handler.getBePos());
            buf.writeBlockPos(new BlockPos(x, y, z));
            buf.writeString(name, 64);
            ClientPlayNetworking.send(ModPackets.ADD_PAD, buf);
        } catch (NumberFormatException ignored) {}
    }

    private void onRename() {
        int idx = resolveSelectedIndex();
        if (idx < 0) return;
        String name = nameField.getText().trim();
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(handler.getBePos());
        buf.writeVarInt(idx);
        buf.writeString(name, 64);
        ClientPlayNetworking.send(ModPackets.RENAME_PAD, buf);
    }

    /** Delete the currently selected entry only (no selection -> no-op). */
    private void onDelete() {
        List<TeleporterTerminalBlockEntity.PadEntry> pads = handler.getPadEntriesSnapshot();
        if (pads.isEmpty()) return;

        int idx = resolveSelectedIndex();
        if (idx < 0 || idx >= pads.size()) return;

        // Tell server to remove that exact index
        PacketByteBuf rm = PacketByteBufs.create();
        rm.writeBlockPos(handler.getBePos());
        rm.writeVarInt(idx);
        ClientPlayNetworking.send(ModPackets.REMOVE_PAD, rm);

        // Locally: compute a stable next selection (prefer same index, else previous)
        int newSize = pads.size() - 1;
        int newIdx = Math.min(idx, newSize - 1);
        applyLocalSelection(newIdx);

        // Send selection update to server (or -1 if list became empty)
        PacketByteBuf sel = PacketByteBufs.create();
        sel.writeBlockPos(handler.getBePos());
        sel.writeVarInt(newIdx < 0 ? -1 : newIdx);
        ClientPlayNetworking.send(ModPackets.SELECT_PAD, sel);
    }

    /* ---------- selection helpers ---------- */

    private int resolveSelectedIndex() {
        if (selectedLocal < 0) return -1;
        return listIndexTop + selectedLocal;
    }

    private void applyLocalSelection(int absIdx) {
        List<TeleporterTerminalBlockEntity.PadEntry> pads = handler.getPadEntriesSnapshot();
        if (pads.isEmpty() || absIdx < 0 || absIdx >= pads.size()) {
            selectedLocal = -1;
            listIndexTop = 0;
            return;
        }
        if (absIdx < listIndexTop) listIndexTop = absIdx;
        if (absIdx >= listIndexTop + ROWS_VISIBLE) listIndexTop = absIdx - (ROWS_VISIBLE - 1);

        int maxTop = Math.max(0, pads.size() - ROWS_VISIBLE);
        listIndexTop = Math.max(0, Math.min(maxTop, listIndexTop));
        selectedLocal = absIdx - listIndexTop;
    }

    /** Keep server-selected visible and highlighted when opening. */
    private void snapSelectionIntoView() {
        int sel = handler.getSelectedIndex();
        List<TeleporterTerminalBlockEntity.PadEntry> pads = handler.getPadEntriesSnapshot();
        if (sel < 0 || sel >= pads.size()) { selectedLocal = -1; listIndexTop = 0; return; }
        int maxTop = Math.max(0, pads.size() - ROWS_VISIBLE);
        if (sel < listIndexTop) listIndexTop = sel;
        if (sel >= listIndexTop + ROWS_VISIBLE) listIndexTop = sel - (ROWS_VISIBLE - 1);
        listIndexTop = Math.max(0, Math.min(maxTop, listIndexTop));
        selectedLocal = sel - listIndexTop;
    }

    /* ---------- input plumbing ---------- */

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (xField.keyPressed(keyCode, scanCode, modifiers) || xField.isFocused()) return true;
        if (yField.keyPressed(keyCode, scanCode, modifiers) || yField.isFocused()) return true;
        if (zField.keyPressed(keyCode, scanCode, modifiers) || zField.isFocused()) return true;
        if (nameField.keyPressed(keyCode, scanCode, modifiers) || nameField.isFocused()) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (xField.charTyped(chr, modifiers)) return true;
        if (yField.charTyped(chr, modifiers)) return true;
        if (zField.charTyped(chr, modifiers)) return true;
        if (nameField.charTyped(chr, modifiers)) return true;
        return super.charTyped(chr, modifiers);
    }

    // Scroll list with mouse wheel when hovering over it
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int left = (width - backgroundWidth) / 2;
        int top  = (height - backgroundHeight) / 2;
        int listLeft = left + 10;
        int listTop  = top + 88;
        int listRight = left + backgroundWidth - 24;
        int listBottom = listTop + ROWS_VISIBLE * 12;

        if (mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom) {
            int maxTop = Math.max(0, handler.getPadEntriesSnapshot().size() - ROWS_VISIBLE);
            listIndexTop = Math.max(0, Math.min(maxTop, listIndexTop + (amount < 0 ? 1 : -1)));
            // keep selection row consistent if it goes off-screen
            int abs = resolveSelectedIndex();
            if (abs >= 0) selectedLocal = Math.max(0, Math.min(ROWS_VISIBLE - 1, abs - listIndexTop));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    /* ---------- render ---------- */

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        super.render(ctx, mouseX, mouseY, delta);

        int left = (width - backgroundWidth) / 2;
        int top  = (height - backgroundHeight) / 2;

        ctx.drawText(textRenderer, "Teleporter Terminal", left + 8, top + 6, 0xFFFFFF, false);
        ctx.drawText(textRenderer, "Pads:", left + 10, top + 72, 0xFFFFFF, false);

        List<TeleporterTerminalBlockEntity.PadEntry> pads = handler.getPadEntriesSnapshot();
        int serverSel = handler.getSelectedIndex();

        // reflect external server selection change
        int currentAbs = resolveSelectedIndex();
        if (serverSel >= 0 && serverSel != currentAbs && serverSel < pads.size()) {
            applyLocalSelection(serverSel);
        }

        int listLeft = left + 10;
        int listTop  = top + 88;

        for (int row = 0; row < ROWS_VISIBLE; row++) {
            int idx = listIndexTop + row;
            int y = listTop + row * 12;
            int color = 0xA0A0A0;
            if (idx == serverSel) color = 0x00FFAA;     // server-selected
            if (row == selectedLocal) color = 0xFFFF55; // local cursor

            String line;
            if (idx < pads.size()) {
                var e = pads.get(idx);
                String nm = (e.name == null || e.name.isBlank()) ? e.pos.toShortString() : e.name;
                line = "#" + idx + "  " + nm + "  [" + e.pos.toShortString() + "]";
            } else line = "-";

            ctx.drawText(textRenderer, line, listLeft, y, color, false);
        }

        drawMouseoverTooltip(ctx, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        int left = (width - backgroundWidth) / 2;
        int top  = (height - backgroundHeight) / 2;
        ctx.fill(left, top, left + backgroundWidth, top + backgroundHeight, 0xAA101018); // panel
        ctx.fill(left, top, left + backgroundWidth, top + 20, 0xAA202430);               // title bar
    }

    // Suppress HandledScreen's default "Inventory" label behind our "Pads" text.
    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        // Intentionally empty: we render our own titles in render()
    }

    @Override
    public void close() {
        if (client != null && client.player != null) client.player.closeHandledScreen();
        super.close();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = (width - backgroundWidth) / 2;
        int top  = (height - backgroundHeight) / 2;

        if (xField.mouseClicked(mouseX, mouseY, button)) { setFocused(xField); return true; }
        if (yField.mouseClicked(mouseX, mouseY, button)) { setFocused(yField); return true; }
        if (zField.mouseClicked(mouseX, mouseY, button)) { setFocused(zField); return true; }
        if (nameField.mouseClicked(mouseX, mouseY, button)) { setFocused(nameField); return true; }

        // Click in the list selects that entry and sends selection to server immediately
        int listLeft = left + 10;
        int listTop  = top + 88;
        int listRight = left + backgroundWidth - 24;
        int listBottom = listTop + ROWS_VISIBLE * 12;

        if (mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom) {
            int row = Math.max(0, Math.min(ROWS_VISIBLE - 1, (int)((mouseY - listTop) / 12.0)));
            List<TeleporterTerminalBlockEntity.PadEntry> pads = handler.getPadEntriesSnapshot();
            int idx = listIndexTop + row;
            if (idx < pads.size()) {
                applyLocalSelection(idx);
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeBlockPos(handler.getBePos());
                buf.writeVarInt(idx);
                ClientPlayNetworking.send(ModPackets.SELECT_PAD, buf);
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
}
