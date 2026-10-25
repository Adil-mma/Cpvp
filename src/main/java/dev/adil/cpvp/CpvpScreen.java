package dev.adil.cpvp;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CpvpScreen extends Screen {
    public CpvpScreen() { super(Component.literal("Cpvp")); }

    @Override
    protected void init() {
        int w = 300, h = 150;
        int left = (width - w) / 2, top = (height - h) / 2;
        var s = CpvpConfig.settings();

        addRenderableWidget(Button.builder(label(s.autoTotemEnabled), b -> {
            s.autoTotemEnabled = !s.autoTotemEnabled;
            CpvpConfig.save();
            b.setMessage(label(s.autoTotemEnabled));
        }).bounds(left + 12, top + 42, 276, 22).build());

        addRenderableWidget(Button.builder(Component.literal("Backup hotbar slot: " + (s.totemBackupSlot + 1)), b -> {
            s.totemBackupSlot = (s.totemBackupSlot + 1) % 9;
            CpvpConfig.save();
            b.setMessage(Component.literal("Backup hotbar slot: " + (s.totemBackupSlot + 1)));
        }).bounds(left + 12, top + 70, 276, 22).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(left + 12, top + 102, 276, 22).build());
    }

    private static Component label(boolean enabled) {
        return Component.literal("Auto Totem: " + (enabled ? "ON" : "OFF"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        int w = 300, h = 150, l = (width - w) / 2, t = (height - h) / 2;
        g.fill(0, 0, width, height, 0xB0000000);
        g.fill(l, t, l + w, t + h, 0xF0121720);
        g.outline(l, t, w, h, 0xFF4AA3FF);
        g.text(font, Component.literal("CPVP • Inventory Utility"), l + 12, t + 14, 0xFF75C7FF, true);
        super.extractRenderState(g, mx, my, pt);
    }
}
