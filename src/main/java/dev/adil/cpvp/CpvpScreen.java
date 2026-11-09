package dev.adil.cpvp;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Two-panel menu laid out after the reference screenshot: a rounded header with a cyan to purple
 * gradient border and glow, dark rounded rows with centred beige labels. All sizes scale from the
 * panel width (reference: 417 px wide, 44 px header, 40 px rows on a 47.5 px pitch).
 *
 * <p>Left click toggles a module, right click opens its settings. An enabled module is shown
 * with a green frame, green label and a green dot, a disabled one looks exactly like the reference.
 */
public final class CpvpScreen extends Screen {
    private static final CpvpModule[] LEFT = {
            CpvpModule.FAST_CRYSTAL, CpvpModule.AUTO_CRYSTAL,
            CpvpModule.HIT_CRYSTAL, CpvpModule.AUTO_TOTEM
    };
    private static final CpvpModule[] RIGHT = {
            CpvpModule.ANCHOR_MACRO, CpvpModule.SAFE_ANCHOR,
            CpvpModule.DOUBLE_ANCHOR, CpvpModule.TOTEM_SWAP
    };

    // Colours sampled from the reference image.
    private static final int DIM = 0xA0232428;
    private static final int BODY = 0xFF2B2C30;
    private static final int BODY_EDGE = 0xFF45474E;
    private static final int HEADER_BG = 0xFF202226;
    private static final int ROW_BG = 0xFF151515;
    private static final int ROW_HOVER = 0xFF202022;
    private static final int ROW_ON_BG = 0xFF14211A;
    private static final int ROW_ON_EDGE = 0xFF3F9468;
    private static final int TEXT = 0xFFCDBBA0;
    private static final int TEXT_ON = 0xFFA9E8C2;
    private static final int TITLE = 0xFF86A2E8;
    private static final int GRAD_FROM = 0xFF55B9E6;
    private static final int GRAD_TO = 0xFFA06EE0;

    private int panelW, headerH, rowH, rowStep, rowPad, bottomPad, leftX, rightX, topY, margin, corner;

    private final GuiFx fx = new GuiFx();

    public CpvpScreen() { super(Component.literal("Cpvp")); }

    private void layout() {
        panelW = Math.max(150, Math.min(300, Math.round(width * 0.296f)));
        float f = panelW / 417f;
        headerH = Math.max(22, Math.round(44 * f));
        rowH = Math.max(16, Math.round(40 * f));
        rowStep = rowH + Math.max(3, Math.round(7.5f * f));
        rowPad = Math.max(4, Math.round(13 * f));
        bottomPad = Math.max(3, Math.round(7 * f));
        margin = Math.max(4, Math.round(10 * f));
        corner = panelW >= 220 ? 3 : 2;
        int gap = Math.max(8, Math.round(22 * f));
        leftX = (width - (panelW * 2 + gap)) / 2;
        rightX = leftX + panelW + gap;
        topY = Math.max(8, Math.round(height * 0.19f));
    }

    @Override
    protected void init() { layout(); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        layout();
        g.fill(0, 0, width, height, fx.fade(DIM));
        fx.begin(g, width / 2f, topY + headerH + 60);
        drawPanel(g, leftX, "CRISTAL AURA", LEFT, mx, my);
        drawPanel(g, rightX, "ANCHOR AURA", RIGHT, mx, my);
        fx.end(g);

        CpvpModule hovered = moduleAt(mx, my);
        if (fx.hoverReady(hovered == null ? -1 : hovered.ordinal()) && hovered != null) {
            GuiFx.tooltip(g, font, GuiFx.module(hovered), mx, my, width, height);
        }
        super.extractRenderState(g, mx, my, pt);
    }

    private int bodyHeight(int rows) { return rowPad + rows * rowStep - (rowStep - rowH) + bottomPad; }

    private void drawPanel(GuiGraphicsExtractor g, int x, String title, CpvpModule[] modules, int mx, int my) {
        int bodyTop = topY + headerH - 2;
        int bodyBottom = topY + headerH + bodyHeight(modules.length);
        roundFill(g, x, bodyTop, x + panelW, bodyBottom, corner, BODY_EDGE);
        roundFill(g, x + 1, bodyTop + 1, x + panelW - 1, bodyBottom - 1, corner, BODY);

        drawHeader(g, x, title);

        CpvpConfig.Settings s = CpvpConfig.settings();
        for (int i = 0; i < modules.length; i++) {
            CpvpModule module = modules[i];
            int x1 = x + margin, x2 = x + panelW - margin;
            int y1 = rowTop(i), y2 = y1 + rowH;
            boolean on = module.enabled(s);
            boolean hover = mx >= x1 && mx < x2 && my >= y1 && my < y2;
            if (on) {
                roundFill(g, x1, y1, x2, y2, 2, ROW_ON_EDGE);
                roundFill(g, x1 + 1, y1 + 1, x2 - 1, y2 - 1, 2, hover ? 0xFF1A2B22 : ROW_ON_BG);
                int dot = Math.max(3, rowH / 7);
                g.fill(x1 + 7, y1 + (rowH - dot) / 2, x1 + 7 + dot, y1 + (rowH - dot) / 2 + dot, 0xFF5CE08F);
            } else {
                roundFill(g, x1, y1, x2, y2, 2, hover ? ROW_HOVER : ROW_BG);
            }
            Component label = Component.literal(module.label);
            g.text(font, label, x + (panelW - font.width(label)) / 2, y1 + (rowH - font.lineHeight) / 2 + 1,
                    on ? TEXT_ON : TEXT, false);
        }
    }

    private void drawHeader(GuiGraphicsExtractor g, int x, String title) {
        int x1 = x, x2 = x + panelW, y1 = topY, y2 = topY + headerH, r = corner;

        // soft glow around the header
        roundFill(g, x1 - 2, y1 - 2, x2 + 2, y2 + 2, r + 1, 0x1555B9E6);
        roundFill(g, x1 - 1, y1 - 1, x2 + 1, y2 + 1, r + 1, 0x2A7C8CE6);

        // 1 px border whose colour runs from cyan (left) to purple (right)
        int span = Math.max(1, panelW - 1);
        for (int cx = x1 + r; cx < x2 - r; cx++) {
            int c = lerp(GRAD_FROM, GRAD_TO, (cx - x1) / (float) span);
            g.fill(cx, y1, cx + 1, y1 + 1, c);
            g.fill(cx, y2 - 1, cx + 1, y2, c);
        }
        for (int i = 0; i < r; i++) {
            int inset = r - i;
            int cl = lerp(GRAD_FROM, GRAD_TO, inset / (float) span);
            int cr = lerp(GRAD_FROM, GRAD_TO, (panelW - inset) / (float) span);
            g.fill(x1 + inset - 1, y1 + i, x1 + inset, y1 + i + 1, cl);
            g.fill(x1 + inset - 1, y2 - 1 - i, x1 + inset, y2 - i, cl);
            g.fill(x2 - inset, y1 + i, x2 - inset + 1, y1 + i + 1, cr);
            g.fill(x2 - inset, y2 - 1 - i, x2 - inset + 1, y2 - i, cr);
        }
        g.fill(x1, y1 + r, x1 + 1, y2 - r, GRAD_FROM);
        g.fill(x2 - 1, y1 + r, x2, y2 - r, GRAD_TO);

        roundFill(g, x1 + 1, y1 + 1, x2 - 1, y2 - 1, Math.max(1, r - 1), HEADER_BG);

        Component text = Component.literal(title);
        g.text(font, text, x + (panelW - font.width(text)) / 2, y1 + (headerH - font.lineHeight) / 2 + 1,
                TITLE, false);
    }

    /** Rectangle with chamfered corners of radius {@code r}. */
    private static void roundFill(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
        g.fill(x1, y1 + r, x2, y2 - r, color);
        for (int i = 0; i < r; i++) {
            int inset = r - i;
            g.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
            g.fill(x1 + inset, y2 - 1 - i, x2 - inset, y2 - i, color);
        }
    }

    private static int lerp(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = 0xFF;
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int gr = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }

    private int rowTop(int i) { return topY + headerH + rowPad + i * rowStep; }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        CpvpModule module = moduleAt(event.x(), event.y());
        if (module != null) {
            int button = event.button();
            if (button == 0) {
                CpvpConfig.Settings s = CpvpConfig.settings();
                module.setEnabled(s, !module.enabled(s));
                CpvpConfig.save();
                if (minecraft != null && minecraft.player != null) {
                    minecraft.player.sendSystemMessage(Component.literal(
                            "[Cpvp] " + module.title + ": " + (module.enabled(s) ? "ON" : "OFF")));
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
        CpvpModule found = rowAt(mx, my, leftX, LEFT);
        return found != null ? found : rowAt(mx, my, rightX, RIGHT);
    }

    private CpvpModule rowAt(double mx, double my, int x, CpvpModule[] modules) {
        if (mx < x + margin || mx >= x + panelW - margin) return null;
        for (int i = 0; i < modules.length; i++) {
            int y = rowTop(i);
            if (my >= y && my < y + rowH) return modules[i];
        }
        return null;
    }
}
