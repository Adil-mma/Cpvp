package dev.adil.cpvp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Inventory;

/**
 * Auto mode keeps the offhand and backup slot stocked without user input.
 * Manual mode only reacts to a totem currently hovered in the player inventory:
 * it swaps that stack into the offhand first, or into the configured backup
 * hotbar slot when the offhand already contains a totem.
 */
public final class AutoTotemEngine {
    private static int cooldown;
    private static int lastManualSelectedSlot = -1;

    private AutoTotemEngine() {}

    public static void tick(Minecraft c) {
        CpvpConfig.Settings cfg = CpvpConfig.settings();
        if (!cfg.autoTotemEnabled || c.player == null || c.gameMode == null) {
            cooldown = 0;
            return;
        }

        var p = c.player;
        if (p.isSpectator()) return;

        boolean manual = cfg.autoTotemMode.equals("MANUAL");
        if (manual) {
            if (cooldown > 0) {
                cooldown--;
                lastManualSelectedSlot = p.getInventory().getSelectedSlot();
                return;
            }

            // In-game manual behavior: selecting a hotbar slot containing a totem
            // moves that totem into the offhand when the offhand has no totem.
            if (c.gui.screen() == null && p.containerMenu == p.inventoryMenu) {
                int selected = p.getInventory().getSelectedSlot();
                if (selected != lastManualSelectedSlot) {
                    lastManualSelectedSlot = selected;
                    if (!p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)
                            && p.getInventory().getItem(selected).is(Items.TOTEM_OF_UNDYING)) {
                        c.gameMode.handleContainerInput(p.inventoryMenu.containerId,
                                36 + selected, Inventory.SLOT_OFFHAND, ContainerInput.SWAP, p);
                        cooldown = 4;
                    }
                }
                return;
            }

            // Keep the existing inventory-hover workflow intact.
            if (!(c.gui.screen() instanceof InventoryScreen) || p.containerMenu != p.inventoryMenu) {
                return;
            }
            if (tickManual(c, cfg)) cooldown = 4;
            return;
        }
        lastManualSelectedSlot = -1;

        // Auto mode never moves inventory items while another screen/container is open.
        if (c.gui.screen() != null || p.containerMenu != p.inventoryMenu) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (!p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            if (InventoryUtil.moveItemToOffhand(c, Items.TOTEM_OF_UNDYING)) cooldown = 5;
            return;
        }

        if (!p.getInventory().getItem(cfg.totemBackupSlot).is(Items.TOTEM_OF_UNDYING)
                && InventoryUtil.moveItemToHotbarSlot(c, Items.TOTEM_OF_UNDYING, cfg.totemBackupSlot)) {
            cooldown = 5;
        }
    }

    /**
     * The hovered totem is the source of the swap. If the offhand is not a totem,
     * SWAP moves the hovered totem to offhand and leaves the old offhand item in
     * the source slot. If the offhand already has a totem, it moves the hovered
     * extra totem to the configured backup hotbar slot; an occupied destination
     * is exchanged back into the source inventory slot.
     */
    private static boolean tickManual(Minecraft c, CpvpConfig.Settings cfg) {
        var p = c.player;
        var window = c.getWindow();
        if (window == null) return false;

        double mouseX = c.mouseHandler.xpos()
                * window.getGuiScaledWidth() / (double) window.getScreenWidth();
        double mouseY = c.mouseHandler.ypos()
                * window.getGuiScaledHeight() / (double) window.getScreenHeight();

        int left = (window.getGuiScaledWidth() - 176) / 2;
        int top = (window.getGuiScaledHeight() - 166) / 2;

        int hoveredSlotId = -1;
        for (int slotId = 9; slotId <= 44 && slotId < p.inventoryMenu.slots.size(); slotId++) {
            Slot slot = p.inventoryMenu.slots.get(slotId);
            int sx = left + slot.x;
            int sy = top + slot.y;
            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                hoveredSlotId = slotId;
                break;
            }
        }

        if (hoveredSlotId < 0) return false;

        Slot hovered = p.inventoryMenu.slots.get(hoveredSlotId);
        if (!hovered.getItem().is(Items.TOTEM_OF_UNDYING)) return false;

        if (!p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            // Empty or occupied offhand: exchange it with the hovered totem.
            c.gameMode.handleContainerInput(p.inventoryMenu.containerId, hoveredSlotId,
                    Inventory.SLOT_OFFHAND, ContainerInput.SWAP, p);
            return true;
        }

        int backupSlot = cfg.totemBackupSlot;
        int backupMenuSlot = 36 + backupSlot;
        if (hoveredSlotId == backupMenuSlot) return false;

        Slot backup = p.inventoryMenu.slots.get(backupMenuSlot);
        if (backup.getItem().is(Items.TOTEM_OF_UNDYING)) return false;

        // This also swaps an occupied hotbar target with the hovered totem.
        c.gameMode.handleContainerInput(p.inventoryMenu.containerId, hoveredSlotId,
                backupSlot, ContainerInput.SWAP, p);
        return true;
    }
}
