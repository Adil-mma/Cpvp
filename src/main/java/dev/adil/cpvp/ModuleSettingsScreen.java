package dev.adil.cpvp;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ModuleSettingsScreen extends Screen {
    private final CpvpModule module;
    private static final int BOX_W = 430;
    private static final int ROW_H = 38;
    private int x, y;

    public ModuleSettingsScreen(CpvpModule module) {
        super(Component.literal(module.title + " settings"));
        this.module = module;
    }

    private String[] rows() {
        CpvpConfig.Settings s = CpvpConfig.settings();
        return switch (module) {
            case FAST_CRYSTAL -> new String[] {
                    "Repeat delay: " + s.fastCrystalDelayMs + " ms",
                    "Only while holding use: " + onOff(s.fastCrystalOnlyWhileHolding)
            };
            case AUTO_CRYSTAL -> new String[] {
                    "Target range: " + fmt(s.autoCrystalRange) + " blocks",
                    "Action delay: " + s.autoCrystalDelayMs + " ms"
            };
            case HIT_CRYSTAL -> new String[] {
                    "Hit range: " + fmt(s.hitCrystalRange) + " blocks",
                    "Hit delay: " + s.hitCrystalDelayMs + " ms"
            };
            case AUTO_TOTEM -> new String[] {
                    "Mode: " + s.autoTotemMode,
                    "Backup hotbar slot: " + (s.totemBackupSlot + 1)
            };
            case ANCHOR_MACRO -> new String[] {
                    "Macro delay: " + s.anchorMacroDelayMs + " ms",
                    "Auto-charge: " + onOff(s.anchorMacroAutoCharge)
            };
            case SAFE_ANCHOR -> new String[] {
                    "Minimum self distance: " + fmt(s.safeAnchorSelfDistance) + " blocks",
                    "Protect self: " + onOff(s.safeAnchorProtectSelf)
            };
            case DOUBLE_ANCHOR -> new String[] {
                    "Repeat delay: " + s.doubleAnchorDelayMs + " ms",
                    "Macro passes: " + s.doubleAnchorPasses
            };
        };
    }

    private static String onOff(boolean v) { return v ? "ON" : "OFF"; }
    private static String fmt(double v) { return String.format(java.util.Locale.ROOT, "%.1f", v); }

    @Override
    protected void init() {
        x = (width - BOX_W) / 2;
        y = Math.max(24, (height - 240) / 2);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        x = (width - BOX_W) / 2;
        y = Math.max(24, (height - 240) / 2);
        g.fill(0, 0, width, height, 0xC91A1C21);
        g.fill(x, y, x + BOX_W, y + 224, 0xF0101218);
        g.outline(x, y, BOX_W, 224, 0xFF555B6A);
        g.fill(x + 1, y + 1, x + BOX_W / 2, y + 3, 0xFF55D7F5);
        g.fill(x + BOX_W / 2, y + 1, x + BOX_W - 1, y + 3, 0xFFB56BFF);
        g.text(font, Component.literal(module.title.toUpperCase(java.util.Locale.ROOT) + " SETTINGS"),
                x + 16, y + 16, 0xFF9BB6FF, true);

        String[] labels = rows();
        for (int i = 0; i < labels.length; i++) {
            int ry = y + 42 + i * ROW_H;
            boolean hovered = mx >= x + 14 && mx <= x + BOX_W - 14
                    && my >= ry && my < ry + ROW_H - 4;
            g.fill(x + 14, ry, x + BOX_W - 14, ry + ROW_H - 4, hovered ? 0xFF282D38 : 0xFF090B0F);
            g.outline(x + 14, ry, BOX_W - 28, ROW_H - 4, 0xFF303542);
            g.text(font, Component.literal(labels[i]), x + 26, ry + 12, 0xFFE0D6C9, false);
            g.text(font, Component.literal("‹  ›"), x + BOX_W - 44, ry + 12, 0xFF97AFFF, false);
        }
        g.text(font, Component.literal("Left click: increase / toggle    Right click: decrease"),
                x + 16, y + 128, 0xFFAEB4C0, false);
        g.text(font, Component.literal("ESC / Done"), x + 16, y + 203, 0xFFB9BEC8, false);
        super.extractRenderState(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && mx >= x + 14 && mx <= x + BOX_W - 14
                && my >= y + 188 && my <= y + 220) {
            back();
            return true;
        }
        int row = -1;
        for (int i = 0; i < 2; i++) {
            int ry = y + 42 + i * ROW_H;
            if (mx >= x + 14 && mx <= x + BOX_W - 14 && my >= ry && my < ry + ROW_H - 4) {
                row = i;
                break;
            }
        }
        if (row >= 0 && (button == 0 || button == 1)) {
            changeSetting(row, button == 0 ? 1 : -1);
            CpvpConfig.save();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void changeSetting(int row, int direction) {
        CpvpConfig.Settings s = CpvpConfig.settings();
        switch (module) {
            case FAST_CRYSTAL -> {
                if (row == 0) s.fastCrystalDelayMs = wrap(s.fastCrystalDelayMs + direction * 5, 0, 500);
                else s.fastCrystalOnlyWhileHolding = !s.fastCrystalOnlyWhileHolding;
            }
            case AUTO_CRYSTAL -> {
                if (row == 0) s.autoCrystalRange = clamp(s.autoCrystalRange + direction * 0.5, 1, 6);
                else s.autoCrystalDelayMs = wrap(s.autoCrystalDelayMs + direction * 25, 0, 1000);
            }
            case HIT_CRYSTAL -> {
                if (row == 0) s.hitCrystalRange = clamp(s.hitCrystalRange + direction * 0.5, 1, 8);
                else s.hitCrystalDelayMs = wrap(s.hitCrystalDelayMs + direction * 5, 0, 500);
            }
            case AUTO_TOTEM -> {
                if (row == 0) s.autoTotemMode = s.autoTotemMode.equals("AUTO") ? "MANUAL" : "AUTO";
                else s.totemBackupSlot = (s.totemBackupSlot + direction + 9) % 9;
            }
            case ANCHOR_MACRO -> {
                if (row == 0) s.anchorMacroDelayMs = wrap(s.anchorMacroDelayMs + direction * 25, 0, 1000);
                else s.anchorMacroAutoCharge = !s.anchorMacroAutoCharge;
            }
            case SAFE_ANCHOR -> {
                if (row == 0) s.safeAnchorSelfDistance = clamp(s.safeAnchorSelfDistance + direction * 0.5, 2, 8);
                else s.safeAnchorProtectSelf = !s.safeAnchorProtectSelf;
            }
            case DOUBLE_ANCHOR -> {
                if (row == 0) s.doubleAnchorDelayMs = wrap(s.doubleAnchorDelayMs + direction * 10, 0, 500);
                else s.doubleAnchorPasses = wrap(s.doubleAnchorPasses + direction, 2, 4);
            }
        }
    }

    private static int wrap(int v, int min, int max) {
        int n = v;
        if (n < min) n = max;
        if (n > max) n = min;
        return n;
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
