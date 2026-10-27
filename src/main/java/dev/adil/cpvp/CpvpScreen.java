package dev.adil.cpvp;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class CpvpScreen extends Screen {
    private static final int ROW_H = 28;
    private static final int HEADER_H = 32;
    private static final int MAX_PANEL_W = 320;
    private static final int GAP = 10;
    private int leftX, rightX, topY, panelW;

    public CpvpScreen() { super(Component.literal("Cpvp")); }

    private int calculatedPanelWidth() {
        return Math.max(140, Math.min(MAX_PANEL_W, (width - 24 - GAP) / 2));
    }

    private int panelHeight(int rows) { return HEADER_H + rows * ROW_H + 10; }

    private int calculatedTopY() {
        int contentH = panelHeight(4) + 34;
        return Math.max(8, (height - contentH) / 2);
    }

    private void layout() {
        panelW = calculatedPanelWidth();
        leftX = (width - (panelW * 2 + GAP)) / 2;
        rightX = leftX + panelW + GAP;
        topY = calculatedTopY();
    }

    @Override
    protected void init() { layout(); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        layout();
        g.fill(0, 0, width, height, 0xB0232428);

        CpvpModule[] crystals = {
                CpvpModule.FAST_CRYSTAL, CpvpModule.AUTO_CRYSTAL,
                CpvpModule.HIT_CRYSTAL, CpvpModule.AUTO_TOTEM
        };
        CpvpModule[] anchors = {
                CpvpModule.ANCHOR_MACRO, CpvpModule.SAFE_ANCHOR, CpvpModule.DOUBLE_ANCHOR
        };
        drawPanel(g, leftX, topY, "CRISTAL AURA", crystals, mx, my);
        drawPanel(g, rightX, topY, "ANCHOR AURA", anchors, mx, my);

        int footerY = topY + panelHeight(4) + 4;
        g.text(font, Component.literal("ON = enabled   OFF = disabled"),
                leftX + 2, footerY, 0xFFB7BBC4, false);
        g.text(font, Component.literal("LEFT CLICK: toggle   RIGHT CLICK: settings"),
                leftX + 2, footerY + 12, 0xFF999FAC, false);
        super.extractRenderState(g, mx, my, pt);
    }

    private void drawPanel(GuiGraphicsExtractor g, int x, int y, String title,
                           CpvpModule[] modules, int mx, int my) {
        int h = panelHeight(modules.length);
        g.fill(x, y, x + panelW, y + h, 0xE9101115);
        g.outline(x, y, panelW, h, 0xFF434751);
        g.fill(x + 1, y + 1, x + panelW / 2, y + 3, 0xFF55D7F5);
        g.fill(x + panelW / 2, y + 1, x + panelW - 1, y + 3, 0xFFB56BFF);
        g.fill(x + 8, y + 6, x + panelW - 8, y + HEADER_H - 2, 0xD80A0D12);
        g.outline(x + 8, y + 6, panelW - 16, HEADER_H - 8, 0xFF323743);
        g.text(font, Component.literal(title), x + 16, y + 15, 0xFF93AEFF, true);

        CpvpConfig.Settings s = CpvpConfig.settings();
        for (int i = 0; i < modules.length; i++) {
            CpvpModule module = modules[i];
            int ry = y + HEADER_H + i * ROW_H;
            boolean hovered = mx >= x + 8 && mx <= x + panelW - 8
                    && my >= ry && my < ry + ROW_H - 2;
            boolean enabled = module.enabled(s);
            int bg = hovered ? 0xFF242833 : 0xE908090B;
            int accent = enabled ? 0xFF3E7B58 : 0xFF30343D;
            g.fill(x + 8, ry, x + panelW - 8, ry + ROW_H - 2, bg);
            g.outline(x + 8, ry, panelW - 16, ROW_H - 2, accent);
            if (enabled) g.fill(x + 8, ry + 2, x + 11, ry + ROW_H - 4, 0xFF5CDB8C);
            g.text(font, Component.literal(module.title), x + 16, ry + 8,
                    enabled ? 0xFFE7F9EC : 0xFFD8CCBA, false);
            String state = enabled ? "ON" : "OFF";
            int stateColor = enabled ? 0xFF63E49A : 0xFF858B96;
            g.text(font, Component.literal(state), x + panelW - 35, ry + 8, stateColor, true);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        CpvpModule module = moduleAt(mx, my);
        if (module != null) {
            if (button == 0) {
                CpvpConfig.Settings s = CpvpConfig.settings();
                module.setEnabled(s, !module.enabled(s));
                CpvpConfig.save();
                if (module == CpvpModule.AUTO_TOTEM && minecraft != null && minecraft.player != null) {
                    minecraft.player.sendSystemMessage(Component.literal(
                            "[Cpvp] Auto Totem: " + (s.autoTotemEnabled ? "ON" : "OFF")
                                    + " (" + s.autoTotemMode + ")"));
                }
                return true;
            }
            if (button == 1) {
                if (minecraft != null) minecraft.gui.setScreen(new ModuleSettingsScreen(module));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private CpvpModule moduleAt(double mx, double my) {
        CpvpModule[] crystals = {
                CpvpModule.FAST_CRYSTAL, CpvpModule.AUTO_CRYSTAL,
                CpvpModule.HIT_CRYSTAL, CpvpModule.AUTO_TOTEM
        };
        CpvpModule[] anchors = {
                CpvpModule.ANCHOR_MACRO, CpvpModule.SAFE_ANCHOR, CpvpModule.DOUBLE_ANCHOR
        };
        CpvpModule found = rowAt(mx, my, leftX, crystals);
        return found != null ? found : rowAt(mx, my, rightX, anchors);
    }

    private CpvpModule rowAt(double mx, double my, int x, CpvpModule[] modules) {
        for (int i = 0; i < modules.length; i++) {
            int y = topY + HEADER_H + i * ROW_H;
            if (mx >= x + 8 && mx <= x + panelW - 8 && my >= y && my < y + ROW_H - 2) {
                return modules[i];
            }
        }
        return null;
    }
}
