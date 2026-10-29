package dev.adil.cpvp;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class ModuleSettingsScreen extends Screen {
    private static final int MAX_BOX_W = 440;
    private static final int MIN_BOX_W = 360;
    private static final int ROW_H = 30;
    private final CpvpModule module;
    private int x, y, boxW, boxH;
    private boolean listeningForBind;

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
            case ANCHOR_MACRO -> new String[] { "Toggle key", "Delay range", "Stage delay range", "Auto-charge", "Detonator hotbar slot" };
            case SAFE_ANCHOR -> new String[] { "Toggle key", "Delay range", "Cover within distance", "Require solid cover" };
            case DOUBLE_ANCHOR -> new String[] { "Toggle key", "Delay range", "Stage delay range", "Macro passes", "Detonator hotbar slot" };
            case TOTEM_SWAP -> new String[] { "Toggle key", "Swap delay range" };
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
            case SAFE_ANCHOR -> row == 2 ? fmt(s.safeAnchorSelfDistance) + " blocks" : onOff(s.safeAnchorProtectSelf);
            case DOUBLE_ANCHOR -> row == 3 ? Integer.toString(s.doubleAnchorPasses) : Integer.toString(s.anchorDetonatorSlot + 1);
            case TOTEM_SWAP -> "1 - 2 ticks";
        };
    }

    private int[] delayBounds(int row) {
        CpvpConfig.Settings s = CpvpConfig.settings();
        return switch (module) {
            case FAST_CRYSTAL -> new int[] { s.fastCrystalMinDelayTicks, s.fastCrystalMaxDelayTicks };
            case AUTO_CRYSTAL -> new int[] { s.autoCrystalMinDelayTicks, s.autoCrystalMaxDelayTicks };
            case HIT_CRYSTAL -> new int[] { s.hitCrystalMinDelayTicks, s.hitCrystalMaxDelayTicks };
            case AUTO_TOTEM -> new int[] {1, 2};
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

    private int contentHeight() { return 42 + labels().length * ROW_H + 36; }

    private void layout() {
        boxW = Math.max(MIN_BOX_W, Math.min(MAX_BOX_W, width - 20));
        boxH = contentHeight();
        x = (width - boxW) / 2;
        y = Math.max(8, (height - boxH) / 2);
    }

    private int rowY(int row) { return y + 34 + row * ROW_H; }
    private int resetX() { return x + boxW - 30; }

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
                x + 14, y + 13, 0xFF9BB6FF, true);

        String[] names = labels();
        for (int i = 0; i < names.length; i++) {
            int ry = rowY(i);
            boolean hovered = mx >= x + 10 && mx <= x + boxW - 10
                    && my >= ry && my < ry + ROW_H - 2;
            g.fill(x + 10, ry, x + boxW - 10, ry + ROW_H - 2,
                    hovered ? 0xFF282D38 : 0xFF090B0F);
            g.outline(x + 10, ry, boxW - 20, ROW_H - 2, 0xFF303542);
            g.text(font, Component.literal(names[i]), x + 18, ry + 10, 0xFFE0D6C9, false);

            if (isBindRow(i)) {
                int color = listeningForBind ? 0xFFFFC56D : 0xFF9BB6FF;
                g.text(font, Component.literal(value(i)), x + 142, ry + 10, color, false);
            } else if (isRangeRow(i)) {
                int[] bounds = delayBounds(i);
                g.text(font, Component.literal("Min"), x + 137, ry + 10, 0xFF9BB6FF, false);
                g.text(font, Component.literal("−"), x + 166, ry + 9, 0xFF97AFFF, true);
                g.text(font, Component.literal(Integer.toString(bounds[0])), x + 184, ry + 10, 0xFFE0D6C9, false);
                g.text(font, Component.literal("+"), x + 202, ry + 9, 0xFF97AFFF, true);
                g.text(font, Component.literal("Max"), x + 225, ry + 10, 0xFF9BB6FF, false);
                g.text(font, Component.literal("−"), x + 257, ry + 9, 0xFF97AFFF, true);
                g.text(font, Component.literal(Integer.toString(bounds[1])), x + 275, ry + 10, 0xFFE0D6C9, false);
                g.text(font, Component.literal("+"), x + 293, ry + 9, 0xFF97AFFF, true);
            } else if (isToggleRow(i)) {
                String v = value(i);
                int color = v.equals("ON") ? 0xFF73E0A0 : 0xFFDBA86A;
                g.text(font, Component.literal(v), x + boxW - 76, ry + 10, color, true);
            } else {
                int controlsX = x + boxW - 118;
                g.text(font, Component.literal("−"), controlsX, ry + 9, 0xFF97AFFF, true);
                g.text(font, Component.literal(value(i)), controlsX + 20, ry + 10, 0xFFE0D6C9, false);
                g.text(font, Component.literal("+"), x + boxW - 49, ry + 9, 0xFF97AFFF, true);
            }

            boolean resetHover = mx >= resetX() - 2 && mx < x + boxW - 10
                    && my >= ry && my < ry + ROW_H - 2;
            g.text(font, Component.literal("↺"), resetX(), ry + 9,
                    resetHover ? 0xFFFF7777 : 0xFFE0B45D, true);
        }

        int footerY = y + 38 + names.length * ROW_H;
        g.text(font, Component.literal(listeningForBind
                        ? "Press a key. Press ESC to cancel. Click a mouse button to bind it."
                        : "Each row has its own reset arrow. Delay ranges: 0–5 ticks."),
                x + 14, footerY, listeningForBind ? 0xFFFFC56D : 0xFF8ADBA4, false);
        g.text(font, Component.literal("ESC: back"), x + 14, footerY + 14, 0xFFB9BEC8, false);
        super.extractRenderState(g, mx, my, pt);
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
            if (mx < x + 10 || mx > x + boxW - 10 || my < ry || my >= ry + ROW_H - 2) continue;

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
                int bound = (mx >= x + 220) ? 1 : 0;
                int direction;
                if (mx >= x + (bound == 0 ? 198 : 289)) direction = 1;
                else if (mx >= x + (bound == 0 ? 162 : 253)) direction = -1;
                else direction = button == 1 ? -1 : 1;
                changeRange(row, bound, direction);
            } else if (isToggleRow(row)) {
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

    private void changeRange(int row, int bound, int direction) {
        CpvpConfig.Settings s = CpvpConfig.settings();
        int[] values = delayBounds(row);
        int next = Math.max(0, Math.min(5, values[bound] + direction));
        if (bound == 0) {
            values[0] = next;
            if (values[0] > values[1]) values[1] = values[0];
        } else {
            values[1] = next;
            if (values[1] < values[0]) values[0] = values[1];
        }
        setDelayBounds(row, values[0], values[1]);
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
            setDelayBounds(row, 1, 2);
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
                else s.safeAnchorProtectSelf = true;
            }
            case DOUBLE_ANCHOR -> {
                if (row == 3) s.doubleAnchorPasses = 2;
                else s.anchorDetonatorSlot = 0;
            }
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
            }
            case DOUBLE_ANCHOR -> {
                if (row == 3) s.doubleAnchorPasses = wrap(s.doubleAnchorPasses + direction, 2, 4);
                else if (row == 4) s.anchorDetonatorSlot = (s.anchorDetonatorSlot + direction + 9) % 9;
            }
            case TOTEM_SWAP -> { }
        }
    }

    private static int wrap(int v, int min, int max) {
        if (v < min) return max;
        if (v > max) return min;
        return v;
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
