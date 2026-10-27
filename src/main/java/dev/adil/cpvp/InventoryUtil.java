package dev.adil.cpvp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;

public final class InventoryUtil {
    private InventoryUtil() {}

    public static boolean moveItemToHotbarSlot(Minecraft c, Item item, int target) {
        if (c.player == null || c.gameMode == null || target < 0 || target > 8) return false;
        LocalPlayer p = c.player;
        if (p.containerMenu != p.inventoryMenu) return false;
        int targetMenuSlot = 36 + target;
        if (p.inventoryMenu.slots.get(targetMenuSlot).getItem().is(item)) return true;

        for (int slotId = 9; slotId <= 44 && slotId < p.inventoryMenu.slots.size(); slotId++) {
            if (slotId == targetMenuSlot) continue;
            Slot slot = p.inventoryMenu.slots.get(slotId);
            if (slot.getItem().is(item)) {
                c.gameMode.handleContainerInput(p.inventoryMenu.containerId, slotId, target,
                        ContainerInput.SWAP, p);
                return true;
            }
        }
        return false;
    }

    public static boolean moveItemToOffhand(Minecraft c, Item item) {
        if (c.player == null || c.gameMode == null) return false;
        LocalPlayer p = c.player;
        if (p.containerMenu != p.inventoryMenu) return false;
        for (int slotId = 9; slotId <= 44 && slotId < p.inventoryMenu.slots.size(); slotId++) {
            Slot slot = p.inventoryMenu.slots.get(slotId);
            if (slot.getItem().is(item)) {
                c.gameMode.handleContainerInput(p.inventoryMenu.containerId, slotId,
                        net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND, ContainerInput.SWAP, p);
                return true;
            }
        }
        return false;
    }
}
