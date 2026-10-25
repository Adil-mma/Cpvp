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
        if (p.getInventory().getItem(target).is(item)) return true;
        int targetMenuSlot = 36 + target;
        for (Slot slot : p.inventoryMenu.slots) {
            if (slot.index < 9 || slot.index > 44 || slot.index == targetMenuSlot) continue;
            if (slot.getItem().is(item)) {
                c.gameMode.handleContainerInput(p.inventoryMenu.containerId, slot.index, target,
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
        for (Slot slot : p.inventoryMenu.slots) {
            if (slot.index < 9 || slot.index > 44) continue;
            if (slot.getItem().is(item)) {
                c.gameMode.handleContainerInput(p.inventoryMenu.containerId, slot.index,
                        net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND, ContainerInput.SWAP, p);
                return true;
            }
        }
        return false;
    }
}
