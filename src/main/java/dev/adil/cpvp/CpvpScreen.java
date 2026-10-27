package dev.adil.cpvp;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class CpvpScreen extends Screen {
    private static final int ROW_H = 32;
    private static final int HEADER_H = 36;
    private static final int PANEL_W = 385;
    private static final int GAP = 16;
    private int leftX, rightX, topY;

    public CpvpScreen() { super(Component.literal("Cpvp")); }

    private int panelTop() { return Math.max(18, (height - 460) / 2); }

    @Override
    protected void init() {
        leftX = (width - (PANEL_W * 2 + GAP)) / 2;
        rightX = leftX + PANEL_W + GAP;
        topY = panelTop();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        leftX = (width - (PANEL_W * 2 + GAP)) / 2;
        rightX = leftX + PANEL_W + GAP;
        topY = panelTop();
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
        g.text(font, Component.literal("ON = enabled     OFF = disabled"),
                leftX + 2, topY + HEADER_H + ROW_H * 4 + 12, 0xFFB7BBC4, false);
        g.text(font, Component.literal("LEFT CLICK: toggle     RIGHT CLICK: settings"),
                leftX + 2, topY + HEADER_H + ROW_H * 4 + 24, 0xFF999FAC, false);
        super.extractRenderState(g, mx, my, pt);
    }

    private void drawPanel(GuiGraphicsExtractor g, int x, int y, String title,
                           CpvpModule[] modules, int mx, int my) {
        int h = HEADER_H + modules.length * ROW_H + 10;
        g.fill(x, y, x + PANEL_W, y + h, 0xE9101115);
        g.outline(x, y, PANEL_W, h, 0xFF434751);
        g.fill(x + 1, y + 1, x + PANEL_W / 2, y + 3, 0xFF55D7F5);
        g.fill(x + PANEL_W / 2, y + 1, x + PANEL_W - 1, y + 3, 0xFFB56BFF);
        g.fill(x + 10, y + 7, x + PANEL_W - 10, y + HEADER_H - 2, 0xD80A0D12);
        g.outline(x + 10, y + 7, PANEL_W - 20, HEADER_H - 9, 0xFF323743);
        g.text(font, Component.literal(title), x + 22, y + 18, 0xFF93AEFF, true);

        CpvpConfig.Settings s = CpvpConfig.settings();
        for (int i = 0; i < modules.length; i++) {
            CpvpModule module = modules[i];
            int ry = y + HEADER_H + i * ROW_H;
            boolean hovered = mx >= x + 10 && mx <= x + PANEL_W - 10
                    && my >= ry && my < ry + ROW_H - 3;
            boolean enabled = module.enabled(s);
            boolean running = enabled;
            int bg = hovered ? 0xFF242833 : 0xE908090B;
            int accent = !enabled ? 0xFF30343D : running ? 0xFF3E7B58 : 0xFF8C6B36;
            g.fill(x + 10, ry, x + PANEL_W - 10, ry + ROW_H - 4, bg);
            g.outline(x + 10, ry, PANEL_W - 20, ROW_H - 4, accent);
            if (enabled) g.fill(x + 10, ry + 2, x + 13, ry + ROW_H - 6,
                    running ? 0xFF5CDB8C : 0xFFE0B45D);
            g.text(font, Component.literal(module.title), x + 22, ry + 9,
                    enabled && running ? 0xFFE7F9EC : 0xFFD8CCBA, false);
            String state = enabled ? "ON" : "OFF";
            int stateColor = enabled ? 0xFF63E49A : 0xFF858B96;
            g.text(font, Component.literal(state), x + PANEL_W - 48, ry + 9, stateColor, true);
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
            if (mx >= x + 10 && mx <= x + PANEL_W - 10 && my >= y && my < y + ROW_H - 3) {
                return modules[i];
            }
        }
        return null;
    }
}
