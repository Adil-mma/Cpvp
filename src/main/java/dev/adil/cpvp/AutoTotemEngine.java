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

        boolean manual = cfg.autoTotemMode.equals("MANUAL");

        // Manual equip only triggers while hovering a totem with the inventory open.
        if (manual && c.gui.screen() instanceof InventoryScreen) {
            if (cooldown > 0) { cooldown--; return; }
            if (tickManual(c)) cooldown = 4;
            return;
        }

        // Both modes run this block whenever no screen is open. Auto additionally
        // equips the offhand itself here; Manual leaves that to the player but still
        // needs this block, since it is the only place the backup slot gets refilled.
        if (c.gui.screen() != null) return;
        if (cooldown > 0) { cooldown--; return; }

        if (!manual && !p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            if (InventoryUtil.moveItemToOffhand(c, Items.TOTEM_OF_UNDYING)) cooldown = 5;
            return;
        }

        // Keep a spare totem stocked in the configured backup hotbar slot in both
        // modes: Auto needs it to refill the offhand after a totem pop, and Manual
        // needs it so there is always a totem ready to hover and swap in.
        if (!p.getInventory().getItem(cfg.totemBackupSlot).is(Items.TOTEM_OF_UNDYING)
                && InventoryUtil.moveItemToHotbarSlot(c, Items.TOTEM_OF_UNDYING, cfg.totemBackupSlot)) {
            cooldown = 5;
        }
    }

    /** Equips the totem under the cursor into the offhand. True if a swap was sent. */
    private static boolean tickManual(Minecraft c) {
        var p = c.player;
        // PICKUP alone leaves the stack attached to the cursor and can strand it there,
        // so this sends the same SWAP input as vanilla's "swap to offhand" key.
        if (p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return false;

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
                c.gameMode.handleContainerInput(p.inventoryMenu.containerId, slotId,
                        net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND,
                        ContainerInput.SWAP, p);
                return true;
            }
        }
        return false;
    }
}
