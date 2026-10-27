package dev.adil.cpvp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

public final class AutoTotemEngine {
    private static int cooldown;
    private AutoTotemEngine() {}

    public static void tick(Minecraft c) {
        CpvpConfig.Settings cfg = CpvpConfig.settings();
        if (!cfg.autoTotemEnabled || c.player == null || c.gameMode == null) return;
        var p = c.player;
        if (p.isSpectator() || p.containerMenu != p.inventoryMenu) return;

        if (cfg.autoTotemMode.equals("MANUAL")) {
            tickManual(c);
            return;
        }
        if (c.gui.screen() != null) return;
        if (cooldown > 0) { cooldown--; return; }

        if (!p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            if (InventoryUtil.moveItemToOffhand(c, Items.TOTEM_OF_UNDYING)) cooldown = 5;
            return;
        }
        if (!p.getInventory().getItem(cfg.totemBackupSlot).is(Items.TOTEM_OF_UNDYING)
                && InventoryUtil.moveItemToHotbarSlot(c, Items.TOTEM_OF_UNDYING, cfg.totemBackupSlot)) {
            cooldown = 5;
        }
    }

    private static void tickManual(Minecraft c) {
        if (!(c.gui.screen() instanceof InventoryScreen)) return;
        var p = c.player;
        if (p == null || !p.inventoryMenu.getCarried().isEmpty() || cooldown > 0) {
            if (cooldown > 0) cooldown--;
            return;
        }

        var window = c.getWindow();
        double mouseX = c.mouseHandler.xpos() * window.getGuiScaledWidth() / (double) window.getScreenWidth();
        double mouseY = c.mouseHandler.ypos() * window.getGuiScaledHeight() / (double) window.getScreenHeight();
        int left = (window.getGuiScaledWidth() - 176) / 2;
        int top = (window.getGuiScaledHeight() - 166) / 2;

        for (int slotId = 9; slotId <= 44 && slotId < p.inventoryMenu.slots.size(); slotId++) {
            Slot slot = p.inventoryMenu.slots.get(slotId);
            if (!slot.getItem().is(Items.TOTEM_OF_UNDYING)) continue;
            int sx = left + slot.x;
            int sy = top + slot.y;
            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                c.gameMode.handleContainerInput(p.inventoryMenu.containerId, slotId, 0,
                        ContainerInput.PICKUP, p);
                cooldown = 4;
                return;
            }
        }
    }
}
