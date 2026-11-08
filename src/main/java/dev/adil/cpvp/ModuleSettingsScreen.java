package dev.adil.cpvp;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

public final class ModuleSettingsScreen extends Screen {
    private static final int MAX_BOX_W = 440;
    private static final int MIN_BOX_W = 360;
    private static final int NORMAL_ROW_H = 34;
    private static final int RANGE_ROW_H = 76;

    private final CpvpModule module;
    private int x, y, boxW, boxH;
    private boolean listeningForBind;
    private int draggingRow = -1;
    private int draggingBound = -1;

    public ModuleSettingsScreen(CpvpModule module) {
        super(Component.literal(module.title + " settings"));
        this.module = module;
    }

    private String[] labels() {
        return switch (module) {
            case FAST_CRYSTAL -> new String[] { "Toggle key", "Delay range", "Only while holding use" };
            case AUTO_CRYSTAL -> new String[] { "Toggle key", "Target range", "Delay range", "Stop above target" };
            case HIT_CRYSTAL -> new String[] { "Toggle key", "Hit range", "Delay range", "Stop above target" };
            case AUTO_TOTEM -> new String[] { "Toggle key", "Mode", "Backup hotbar slot" };
            case ANCHOR_MACRO -> new String[] { "Toggle key", "Action delay", "Stage delay", "Auto-charge", "Detonator hotbar slot" };
            case SAFE_ANCHOR -> new String[] { "Toggle key", "Action delay", "Cover within distance", "Place glowstone cover", "Detonation hotbar slot" };
            case DOUBLE_ANCHOR -> new String[] { "Toggle key", "Action delay", "Stage delay", "Anchor hotbar slot" };
            case TOTEM_SWAP -> new String[] { "Toggle key", "Swap delay" };
        };
    }

    private boolean isBindRow(int row) { return row == 0; }

    private boolean isRangeRow(int row) {
        return switch (module) {
            case FAST_CRYSTAL -> row == 1;
            case AUTO_CRYSTAL, HIT_CRYSTAL -> row == 2;
            case AUTO_TOTEM -> false;
            case ANCHOR_MACRO -> row == 1 || row == 2;
            case SAFE_ANCHOR -> row == 1;
            case DOUBLE_ANCHOR -> row == 1 || row == 2;
            case TOTEM_SWAP -> row == 1;
        };
    }

    private boolean isToggleRow(int row) {
        return switch (module) {
            case FAST_CRYSTAL -> row == 2;
            case AUTO_CRYSTAL, HIT_CRYSTAL -> row == 3;
            case AUTO_TOTEM -> row == 1;
            case ANCHOR_MACRO -> row == 3;
            case SAFE_ANCHOR -> row == 3;
            case DOUBLE_ANCHOR -> false;
            case TOTEM_SWAP -> false;
        };
    }

    private String value(int row) {
        CpvpConfig.Settings s = CpvpConfig.settings();
        if (isBindRow(row)) {
            return listeningForBind ? "PRESS A KEY OR MOUSE BUTTON..." : keyName(module.bind(s));
        }
        if (isRangeRow(row)) {
            int[] bounds = delayBounds(row);
            return bounds[0] + " - " + bounds[1] + " ticks";
        }
        return switch (module) {
            case FAST_CRYSTAL -> onOff(s.fastCrystalOnlyWhileHolding);
            case AUTO_CRYSTAL -> row == 1 ? fmt(s.autoCrystalRange) + " blocks" : onOff(s.autoCrystalStopAboveTarget);
            case HIT_CRYSTAL -> row == 1 ? fmt(s.hitCrystalRange) + " blocks" : onOff(s.hitCrystalStopAboveTarget);
            case AUTO_TOTEM -> row == 1 ? s.autoTotemMode : Integer.toString(s.totemBackupSlot + 1);
            case ANCHOR_MACRO -> row == 3 ? onOff(s.anchorMacroAutoCharge) : Integer.toString(s.anchorDetonatorSlot + 1);
            case SAFE_ANCHOR -> row == 2 ? fmt(s.safeAnchorSelfDistance) + " blocks"
                    : row == 3 ? onOff(s.safeAnchorProtectSelf) : Integer.toString(s.safeAnchorTotemSlot + 1);
            case DOUBLE_ANCHOR -> Integer.toString(s.doubleAnchorAnchorSlot + 1);
            case TOTEM_SWAP -> boundsText(s.totemSwapMinDelayTicks, s.totemSwapMaxDelayTicks);
        };
    }

    /**
     * Warns when Auto Totem has no active protection and its configured backup slot
     * does not currently contain a totem. The warning is shown on the slot row itself.
     */
    private boolean isBackupSlotWarning(int row) {
        if (module != CpvpModule.AUTO_TOTEM || row != 2
                || minecraft == null || minecraft.player == null) {
            return false;
        }

        var player = minecraft.player;
        int backupSlot = CpvpConfig.settings().totemBackupSlot;
        return player.getOffhandItem().isEmpty()
                && !player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
                && !player.getInventory().getItem(backupSlot).is(Items.TOTEM_OF_UNDYING);
    }

    private int[] delayBounds(int row) {
        CpvpConfig.Settings s = CpvpConfig.settings();
        return switch (module) {
            case FAST_CRYSTAL -> new int[] { s.fastCrystalMinDelayTicks, s.fastCrystalMaxDelayTicks };
            case AUTO_CRYSTAL -> new int[] { s.autoCrystalMinDelayTicks, s.autoCrystalMaxDelayTicks };
            case HIT_CRYSTAL -> new int[] { s.hitCrystalMinDelayTicks, s.hitCrystalMaxDelayTicks };
            case AUTO_TOTEM -> new int[] { 1, 2 };
            case ANCHOR_MACRO -> row == 1
                    ? new int[] { s.anchorMacroMinDelayTicks, s.anchorMacroMaxDelayTicks }
                    : new int[] { s.anchorMacroMinStageDelayTicks, s.anchorMacroMaxStageDelayTicks };
            case SAFE_ANCHOR -> new int[] { s.safeAnchorMinDelayTicks, s.safeAnchorMaxDelayTicks };
            case DOUBLE_ANCHOR -> row == 1
                    ? new int[] { s.doubleAnchorMinDelayTicks, s.doubleAnchorMaxDelayTicks }
                    : new int[] { s.doubleAnchorMinStageDelayTicks, s.doubleAnchorMaxStageDelayTicks };
            case TOTEM_SWAP -> new int[] { s.totemSwapMinDelayTicks, s.totemSwapMaxDelayTicks };
        };
    }

    private static String boundsText(int min, int max) { return min + " - " + max + " ticks"; }

    private static String keyName(int bind) {
        if (bind == -1) return "UNBOUND";
        if (bind <= -2) return "MOUSE " + (-bind - 1);
        if (bind >= GLFW.GLFW_KEY_F1 && bind <= GLFW.GLFW_KEY_F25) {
            return "F" + (bind - GLFW.GLFW_KEY_F1 + 1);
        }
        return switch (bind) {
            case GLFW.GLFW_KEY_ESCAPE -> "ESC";
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> "ENTER";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_BACKSPACE -> "BACKSPACE";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "L-SHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "R-SHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "L-CTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "R-CTRL";
            case GLFW.GLFW_KEY_LEFT_ALT -> "L-ALT";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "R-ALT";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_DOWN -> "DOWN";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            case GLFW.GLFW_KEY_DELETE -> "DELETE";
            case GLFW.GLFW_KEY_INSERT -> "INSERT";
            case GLFW.GLFW_KEY_HOME -> "HOME";
            case GLFW.GLFW_KEY_END -> "END";
            case GLFW.GLFW_KEY_PAGE_UP -> "PAGE UP";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "PAGE DOWN";
            default -> {
                String name = GLFW.glfwGetKeyName(bind, 0);
                yield name == null ? "KEY " + bind : name.toUpperCase(java.util.Locale.ROOT);
            }
        };
    }

    private static String onOff(boolean v) { return v ? "ON" : "OFF"; }
    private static String fmt(double v) { return String.format(java.util.Locale.ROOT, "%.1f", v); }

    private int rowHeight(int row) { return isRangeRow(row) ? RANGE_ROW_H : NORMAL_ROW_H; }

    private int rowsHeight() {
        int total = 0;
        for (int i = 0; i < labels().length; i++) total += rowHeight(i);
        return total;
    }

    private int contentHeight() { return 46 + rowsHeight() + 36; }

    private void layout() {
        boxW = Math.max(MIN_BOX_W, Math.min(MAX_BOX_W, width - 20));
        boxH = contentHeight();
        x = (width - boxW) / 2;
        y = Math.max(8, (height - boxH) / 2);
    }

    private int rowY(int row) {
        int at = y + 38;
        for (int i = 0; i < row; i++) at += rowHeight(i);
        return at;
    }

    private int resetX() { return x + boxW - 28; }
    private int sliderStartX() { return x + 28; }
    private int sliderEndX() { return x + boxW - 42; }
    private int sliderY(int row) { return rowY(row) + 43; }

    private int sliderX(int value) {
        return sliderStartX() + Math.round((sliderEndX() - sliderStartX()) * value / 5.0f);
    }

    @Override
    protected void init() { layout(); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        layout();
        g.fill(0, 0, width, height, 0xC91A1C21);
        g.fill(x, y, x + boxW, y + boxH, 0xF0101218);
        g.outline(x, y, boxW, boxH, 0xFF555B6A);
        g.fill(x + 1, y + 1, x + boxW / 2, y + 3, 0xFF55D7F5);
        g.fill(x + boxW / 2, y + 1, x + boxW - 1, y + 3, 0xFFB56BFF);
        g.text(font, Component.literal(module.title.toUpperCase(java.util.Locale.ROOT) + " SETTINGS"),
                x + 14, y + 14, 0xFF9BB6FF, true);

        String[] names = labels();
        for (int i = 0; i < names.length; i++) {
            int ry = rowY(i);
            int rh = rowHeight(i);
            boolean hovered = mx >= x + 10 && mx <= x + boxW - 10
                    && my >= ry && my < ry + rh - 2;
            g.fill(x + 10, ry, x + boxW - 10, ry + rh - 2,
                    hovered ? 0xFF282D38 : 0xFF090B0F);
            g.outline(x + 10, ry, boxW - 20, rh - 2,
                    isBackupSlotWarning(i) ? 0xFFE05252 : 0xFF303542);

            if (isRangeRow(i)) {
                int[] bounds = delayBounds(i);
                g.text(font, Component.literal(names[i]), x + 18, ry + 8, 0xFFE0D6C9, false);
                g.text(font, Component.literal(boundsText(bounds[0], bounds[1])),
                        x + boxW - 146, ry + 8, 0xFF9BB6FF, false);
                drawSlider(g, ry, i, bounds[0], bounds[1]);
            } else {
                g.text(font, Component.literal(names[i]), x + 18, ry + 11, 0xFFE0D6C9, false);
                if (isBindRow(i)) {
                    int color = listeningForBind ? 0xFFFFC56D : 0xFF9BB6FF;
                    g.text(font, Component.literal(value(i)), x + 142, ry + 11, color, false);
                } else if (isToggleRow(i)) {
                    String v = value(i);
                    int color = v.equals("ON") ? 0xFF73E0A0 : 0xFFDBA86A;
                    g.text(font, Component.literal(v), x + boxW - 76, ry + 11, color, true);
                } else {
                    int controlsX = x + boxW - 118;
                    g.text(font, Component.literal("−"), controlsX, ry + 10, 0xFF97AFFF, true);
                    g.text(font, Component.literal(value(i)), controlsX + 20, ry + 11,
                            isBackupSlotWarning(i) ? 0xFFFF5555 : 0xFFE0D6C9, false);
                    g.text(font, Component.literal("+"), x + boxW - 49, ry + 10, 0xFF97AFFF, true);
                }
            }

            boolean resetHover = mx >= resetX() - 2 && mx < x + boxW - 10
                    && my >= ry && my < ry + rh - 2;
            g.text(font, Component.literal("↺"), resetX(), ry + (isRangeRow(i) ? 8 : 11),
                    resetHover ? 0xFFFF7777 : 0xFFE0B45D, true);
        }

        int footerY = y + 42 + rowsHeight();
        g.text(font, Component.literal(listeningForBind
                        ? "Press a key. Press ESC to cancel. Click a mouse button to bind it."
                        : "Drag either handle. 0–5 ticks; 1 tick ≈ 50 ms at 20 TPS."),
                x + 14, footerY, listeningForBind ? 0xFFFFC56D : 0xFF8ADBA4, false);
        g.text(font, Component.literal("ESC: back"), x + 14, footerY + 14, 0xFFB9BEC8, false);
        super.extractRenderState(g, mx, my, pt);
    }

    private void drawSlider(GuiGraphicsExtractor g, int rowY, int row, int min, int max) {
        int start = sliderStartX();
        int end = sliderEndX();
        int cy = sliderY(row);
        int minX = sliderX(min);
        int maxX = sliderX(max);

        g.fill(start, cy - 5, end, cy + 5, 0xFFD0D8E1);
        g.fill(minX, cy - 4, maxX + 1, cy + 4, 0xFF19AEBB);
        drawCircle(g, minX, cy, 14, 0x3325B7C3);
        drawCircle(g, maxX, cy, 14, 0x3325B7C3);
        drawCircle(g, minX, cy, 11, 0xFFE9EEF4);
        drawCircle(g, maxX, cy, 11, 0xFFE9EEF4);
        drawCircle(g, minX, cy, 8, 0xFF149BA8);
        drawCircle(g, maxX, cy, 8, 0xFF149BA8);
        drawCircle(g, minX, cy, 5, 0xFF13B8C5);
        drawCircle(g, maxX, cy, 5, 0xFF13B8C5);

        for (int value = 0; value <= 5; value++) {
            int tx = sliderX(value);
            g.text(font, Component.literal(Integer.toString(value)), tx - 3, cy + 16, 
                    value >= min && value <= max ? 0xFFCEDBE8 : 0xFF777F8D, false);
        }
    }

    private static void drawCircle(GuiGraphicsExtractor g, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.floor(Math.sqrt(radius * radius - dy * dy));
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listeningForBind) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                listeningForBind = false;
                return true;
            }
            if (event.key() >= 0 && event.key() <= GLFW.GLFW_KEY_LAST) {
                module.setBind(CpvpConfig.settings(), event.key());
                CpvpConfig.save();
                listeningForBind = false;
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int button = event.button();

        if (listeningForBind && mx < resetX() - 2) {
            module.setBind(CpvpConfig.settings(), -event.button() - 2);
            CpvpConfig.save();
            listeningForBind = false;
            return true;
        }

        String[] names = labels();
        for (int row = 0; row < names.length; row++) {
            int ry = rowY(row);
            int rh = rowHeight(row);
            if (mx < x + 10 || mx > x + boxW - 10 || my < ry || my >= ry + rh - 2) continue;

            if (mx >= resetX() - 2) {
                resetSetting(row);
                listeningForBind = false;
                CpvpConfig.save();
                return true;
            }

            if (isBindRow(row)) {
                listeningForBind = true;
                return true;
            }

            if (isRangeRow(row)) {
                int[] bounds = delayBounds(row);
                int minX = sliderX(bounds[0]);
                int maxX = sliderX(bounds[1]);
                draggingBound = Math.abs(mx - minX) <= Math.abs(mx - maxX) ? 0 : 1;
                draggingRow = row;
                updateSliderFromMouse(row, draggingBound, mx);
                return true;
            }

            if (isToggleRow(row)) {
                changeSetting(row, 1);
            } else {
                int controlsX = x + boxW - 122;
                int plusX = x + boxW - 56;
                int direction;
                if (mx >= plusX && mx < plusX + 24) direction = 1;
                else if (mx >= controlsX - 4 && mx < controlsX + 18) direction = -1;
                else direction = button == 1 ? -1 : 1;
                changeSetting(row, direction);
            }

            CpvpConfig.save();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (draggingRow >= 0 && draggingBound >= 0) {
            updateSliderFromMouse(draggingRow, draggingBound, event.x());
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingRow >= 0) {
            draggingRow = -1;
            draggingBound = -1;
            return true;
        }
        return super.mouseReleased(event);
    }

    private void updateSliderFromMouse(int row, int bound, double mouseX) {
        double start = sliderStartX();
        double end = sliderEndX();
        int next = (int) Math.round(Math.max(0, Math.min(1, (mouseX - start) / (end - start))) * 5.0);
        int[] values = delayBounds(row);
        if (bound == 0) values[0] = Math.min(next, values[1]);
        else values[1] = Math.max(next, values[0]);
        setDelayBounds(row, values[0], values[1]);
        CpvpConfig.save();
    }

    private void setDelayBounds(int row, int min, int max) {
        CpvpConfig.Settings s = CpvpConfig.settings();
        switch (module) {
            case FAST_CRYSTAL -> { s.fastCrystalMinDelayTicks = min; s.fastCrystalMaxDelayTicks = max; }
            case AUTO_CRYSTAL -> { s.autoCrystalMinDelayTicks = min; s.autoCrystalMaxDelayTicks = max; }
            case HIT_CRYSTAL -> { s.hitCrystalMinDelayTicks = min; s.hitCrystalMaxDelayTicks = max; }
            case ANCHOR_MACRO -> {
                if (row == 1) { s.anchorMacroMinDelayTicks = min; s.anchorMacroMaxDelayTicks = max; }
                else { s.anchorMacroMinStageDelayTicks = min; s.anchorMacroMaxStageDelayTicks = max; }
            }
            case SAFE_ANCHOR -> { s.safeAnchorMinDelayTicks = min; s.safeAnchorMaxDelayTicks = max; }
            case DOUBLE_ANCHOR -> {
                if (row == 1) { s.doubleAnchorMinDelayTicks = min; s.doubleAnchorMaxDelayTicks = max; }
                else { s.doubleAnchorMinStageDelayTicks = min; s.doubleAnchorMaxStageDelayTicks = max; }
            }
            case TOTEM_SWAP -> { s.totemSwapMinDelayTicks = min; s.totemSwapMaxDelayTicks = max; }
            case AUTO_TOTEM -> { }
        }
    }

    private void resetSetting(int row) {
        CpvpConfig.Settings s = CpvpConfig.settings();
        if (isBindRow(row)) {
            module.setBind(s, -1);
            return;
        }
        if (isRangeRow(row)) {
            if (module == CpvpModule.HIT_CRYSTAL) setDelayBounds(row, 0, 1);
            else setDelayBounds(row, 1, 2);
            return;
        }

        switch (module) {
            case FAST_CRYSTAL -> s.fastCrystalOnlyWhileHolding = true;
            case AUTO_CRYSTAL -> {
                if (row == 1) s.autoCrystalRange = 4.0;
                else s.autoCrystalStopAboveTarget = true;
            }
            case HIT_CRYSTAL -> {
                if (row == 1) s.hitCrystalRange = 4.0;
                else s.hitCrystalStopAboveTarget = true;
            }
            case AUTO_TOTEM -> {
                if (row == 1) s.autoTotemMode = "AUTO";
                else s.totemBackupSlot = 8;
            }
            case ANCHOR_MACRO -> {
                if (row == 3) s.anchorMacroAutoCharge = true;
                else s.anchorDetonatorSlot = 0;
            }
            case SAFE_ANCHOR -> {
                if (row == 2) s.safeAnchorSelfDistance = 5.0;
                else if (row == 3) s.safeAnchorProtectSelf = true;
                else s.safeAnchorTotemSlot = 8;
            }
            case DOUBLE_ANCHOR -> s.doubleAnchorAnchorSlot = 8;
            case TOTEM_SWAP -> { }
        }
    }

    private void changeSetting(int row, int direction) {
        CpvpConfig.Settings s = CpvpConfig.settings();
        switch (module) {
            case FAST_CRYSTAL -> { if (row == 2) s.fastCrystalOnlyWhileHolding = !s.fastCrystalOnlyWhileHolding; }
            case AUTO_CRYSTAL -> {
                if (row == 1) s.autoCrystalRange = clamp(s.autoCrystalRange + direction * 0.5, 1, 6);
                else if (row == 3) s.autoCrystalStopAboveTarget = !s.autoCrystalStopAboveTarget;
            }
            case HIT_CRYSTAL -> {
                if (row == 1) s.hitCrystalRange = clamp(s.hitCrystalRange + direction * 0.5, 1, 8);
                else if (row == 3) s.hitCrystalStopAboveTarget = !s.hitCrystalStopAboveTarget;
            }
            case AUTO_TOTEM -> {
                if (row == 1) s.autoTotemMode = s.autoTotemMode.equals("AUTO") ? "MANUAL" : "AUTO";
                else if (row == 2) s.totemBackupSlot = (s.totemBackupSlot + direction + 9) % 9;
            }
            case ANCHOR_MACRO -> {
                if (row == 3) s.anchorMacroAutoCharge = !s.anchorMacroAutoCharge;
                else if (row == 4) s.anchorDetonatorSlot = (s.anchorDetonatorSlot + direction + 9) % 9;
            }
            case SAFE_ANCHOR -> {
                if (row == 2) s.safeAnchorSelfDistance = clamp(s.safeAnchorSelfDistance + direction * 0.5, 2, 8);
                else if (row == 3) s.safeAnchorProtectSelf = !s.safeAnchorProtectSelf;
                else if (row == 4) s.safeAnchorTotemSlot = (s.safeAnchorTotemSlot + direction + 9) % 9;
            }
            case DOUBLE_ANCHOR -> {
                if (row == 3) s.doubleAnchorAnchorSlot = (s.doubleAnchorAnchorSlot + direction + 9) % 9;
            }
            case TOTEM_SWAP -> { }
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private void back() {
        if (minecraft != null) minecraft.gui.setScreen(new CpvpScreen());
    }

    @Override
    public void onClose() { back(); }
}
