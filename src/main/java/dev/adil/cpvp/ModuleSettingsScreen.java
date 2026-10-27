package dev.adil.cpvp;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class ModuleSettingsScreen extends Screen {
    private static final int MAX_BOX_W = 360;
    private static final int ROW_H = 30;
    private int x, y, boxW, boxH;

    public ModuleSettingsScreen(CpvpModule module) {
        super(Component.literal(module.title + " settings"));
        this.module = module;
    }

    private final CpvpModule module;

    private String[] labels() {
        return switch (module) {
            case FAST_CRYSTAL -> new String[] { "Repeat delay", "Only while holding use" };
            case AUTO_CRYSTAL -> new String[] { "Target range", "Action delay", "Stop above target" };
            case HIT_CRYSTAL -> new String[] { "Hit range", "Hit delay", "Stop above target" };
            case AUTO_TOTEM -> new String[] { "Mode", "Backup hotbar slot" };
            case ANCHOR_MACRO -> new String[] { "Macro delay", "Stage delay", "Auto-charge", "Detonator hotbar slot" };
            case SAFE_ANCHOR -> new String[] { "Action delay", "Minimum self distance", "Require solid cover" };
            case DOUBLE_ANCHOR -> new String[] { "Repeat delay", "Stage delay", "Macro passes", "Detonator hotbar slot" };
        };
    }

    private String value(int row) {
        CpvpConfig.Settings s = CpvpConfig.settings();
        return switch (module) {
            case FAST_CRYSTAL -> row == 0 ? ticks(s.fastCrystalDelayMs) : onOff(s.fastCrystalOnlyWhileHolding);
            case AUTO_CRYSTAL -> switch (row) {
                case 0 -> fmt(s.autoCrystalRange) + " blocks";
                case 1 -> ticks(s.autoCrystalDelayMs);
                default -> onOff(s.autoCrystalStopAboveTarget);
            };
            case HIT_CRYSTAL -> switch (row) {
                case 0 -> fmt(s.hitCrystalRange) + " blocks";
                case 1 -> ticks(s.hitCrystalDelayMs);
                default -> onOff(s.hitCrystalStopAboveTarget);
            };
            case AUTO_TOTEM -> row == 0 ? s.autoTotemMode : Integer.toString(s.totemBackupSlot + 1);
            case ANCHOR_MACRO -> switch (row) {
                case 0 -> ticks(s.anchorMacroDelayMs);
                case 1 -> ticks(s.anchorStageDelayMs);
                case 2 -> onOff(s.anchorMacroAutoCharge);
                default -> Integer.toString(s.anchorDetonatorSlot + 1);
            };
            case SAFE_ANCHOR -> switch (row) {
                case 0 -> ticks(s.safeAnchorDelayMs);
                case 1 -> fmt(s.safeAnchorSelfDistance) + " blocks";
                default -> onOff(s.safeAnchorProtectSelf);
            };
            case DOUBLE_ANCHOR -> switch (row) {
                case 0 -> ticks(s.doubleAnchorDelayMs);
                case 1 -> ticks(s.anchorStageDelayMs);
                case 2 -> Integer.toString(s.doubleAnchorPasses);
                default -> Integer.toString(s.anchorDetonatorSlot + 1);
            };
        };
    }

    private boolean isToggleRow(int row) {
        return switch (module) {
            case FAST_CRYSTAL -> row == 1;
            case AUTO_CRYSTAL, HIT_CRYSTAL -> row == 2;
            case AUTO_TOTEM -> row == 0;
            case ANCHOR_MACRO -> row == 2;
            case SAFE_ANCHOR -> row == 2;
            case DOUBLE_ANCHOR -> false;
        };
    }

    private static String onOff(boolean v) { return v ? "ON" : "OFF"; }
    private static String fmt(double v) { return String.format(java.util.Locale.ROOT, "%.1f", v); }

    private static int delayTicks(int ms) {
        return Math.max(0, Math.min(10, (ms + 25) / 50));
    }

    private static String ticks(int ms) { return delayTicks(ms) + " / 10 ticks"; }

    private int contentHeight() { return 38 + labels().length * ROW_H + 42; }

    private void layout() {
        boxW = Math.max(160, Math.min(MAX_BOX_W, width - 20));
        boxH = contentHeight();
        x = (width - boxW) / 2;
        y = Math.max(8, (height - boxH) / 2);
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
                x + 14, y + 13, 0xFF9BB6FF, true);

        String[] labels = labels();
        for (int i = 0; i < labels.length; i++) {
            int ry = y + 34 + i * ROW_H;
            boolean hovered = mx >= x + 10 && mx <= x + boxW - 10
                    && my >= ry && my < ry + ROW_H - 2;
            g.fill(x + 10, ry, x + boxW - 10, ry + ROW_H - 2,
                    hovered ? 0xFF282D38 : 0xFF090B0F);
            g.outline(x + 10, ry, boxW - 20, ROW_H - 2, 0xFF303542);
            g.text(font, Component.literal(labels[i]), x + 18, ry + 9, 0xFFE0D6C9, false);

            String v = value(i);
            if (isToggleRow(i)) {
                int color = v.equals("ON") || v.equals("AUTO") ? 0xFF73E0A0
                        : v.equals("OFF") || v.equals("MANUAL") ? 0xFFDBA86A : 0xFF9BB6FF;
                g.text(font, Component.literal(v), x + boxW - 52, ry + 9, color, true);
            } else {
                int controlsX = x + boxW - 112;
                g.text(font, Component.literal("−"), controlsX, ry + 8, 0xFF97AFFF, true);
                g.text(font, Component.literal(v), controlsX + 20, ry + 9, 0xFFE0D6C9, false);
                g.text(font, Component.literal("+"), x + boxW - 29, ry + 8, 0xFF97AFFF, true);
            }
        }

        int footerY = y + 39 + labels.length * ROW_H;
        g.text(font, Component.literal("Click − / + to adjust. Click ON/OFF to toggle."),
                x + 14, footerY, 0xFFAEB4C0, false);
        g.text(font, Component.literal("Delay range: 0–10 ticks   •   ESC: back"),
                x + 14, footerY + 14, 0xFF8ADBA4, false);
        super.extractRenderState(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        if (button == 0 && my >= y + boxH - 27 && my <= y + boxH) {
            back();
            return true;
        }

        String[] names = labels();
        for (int row = 0; row < names.length; row++) {
            int ry = y + 34 + row * ROW_H;
            if (mx < x + 10 || mx > x + boxW - 10 || my < ry || my >= ry + ROW_H - 2) continue;

            if (isToggleRow(row)) {
                changeSetting(row, 1);
            } else {
                int minusX = x + boxW - 116;
                int plusX = x + boxW - 42;
                int direction;
                if (mx >= minusX && mx < minusX + 20) direction = -1;
                else if (mx >= plusX && mx < plusX + 24) direction = 1;
                else direction = button == 1 ? -1 : 1;
                changeSetting(row, direction);
            }
            CpvpConfig.save();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void changeSetting(int row, int direction) {
        CpvpConfig.Settings s = CpvpConfig.settings();
        switch (module) {
            case FAST_CRYSTAL -> {
                if (row == 0) s.fastCrystalDelayMs = adjustDelay(s.fastCrystalDelayMs, direction);
                else s.fastCrystalOnlyWhileHolding = !s.fastCrystalOnlyWhileHolding;
            }
            case AUTO_CRYSTAL -> {
                if (row == 0) s.autoCrystalRange = clamp(s.autoCrystalRange + direction * 0.5, 1, 6);
                else if (row == 1) s.autoCrystalDelayMs = adjustDelay(s.autoCrystalDelayMs, direction);
                else s.autoCrystalStopAboveTarget = !s.autoCrystalStopAboveTarget;
            }
            case HIT_CRYSTAL -> {
                if (row == 0) s.hitCrystalRange = clamp(s.hitCrystalRange + direction * 0.5, 1, 8);
                else if (row == 1) s.hitCrystalDelayMs = adjustDelay(s.hitCrystalDelayMs, direction);
                else s.hitCrystalStopAboveTarget = !s.hitCrystalStopAboveTarget;
            }
            case AUTO_TOTEM -> {
                if (row == 0) s.autoTotemMode = s.autoTotemMode.equals("AUTO") ? "MANUAL" : "AUTO";
                else s.totemBackupSlot = (s.totemBackupSlot + direction + 9) % 9;
            }
            case ANCHOR_MACRO -> {
                if (row == 0) s.anchorMacroDelayMs = adjustDelay(s.anchorMacroDelayMs, direction);
                else if (row == 1) s.anchorStageDelayMs = adjustDelay(s.anchorStageDelayMs, direction);
                else if (row == 2) s.anchorMacroAutoCharge = !s.anchorMacroAutoCharge;
                else s.anchorDetonatorSlot = (s.anchorDetonatorSlot + direction + 9) % 9;
            }
            case SAFE_ANCHOR -> {
                if (row == 0) s.safeAnchorDelayMs = adjustDelay(s.safeAnchorDelayMs, direction);
                else if (row == 1) s.safeAnchorSelfDistance = clamp(s.safeAnchorSelfDistance + direction * 0.5, 2, 8);
                else s.safeAnchorProtectSelf = !s.safeAnchorProtectSelf;
            }
            case DOUBLE_ANCHOR -> {
                if (row == 0) s.doubleAnchorDelayMs = adjustDelay(s.doubleAnchorDelayMs, direction);
                else if (row == 1) s.anchorStageDelayMs = adjustDelay(s.anchorStageDelayMs, direction);
                else if (row == 2) s.doubleAnchorPasses = wrap(s.doubleAnchorPasses + direction, 2, 4);
                else s.anchorDetonatorSlot = (s.anchorDetonatorSlot + direction + 9) % 9;
            }
        }
    }

    private static int adjustDelay(int ms, int direction) {
        return Math.max(0, Math.min(10, delayTicks(ms) + direction)) * 50;
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
