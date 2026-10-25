package dev.adil.cpvp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class InventoryUtil {
    private InventoryUtil() {}
    public static int hotbarSlot(Minecraft c, Item item) {
        if(c.player==null)return -1;
        for(int i=0;i<9;i++)if(c.player.getInventory().getItem(i).is(item))return i;
        return -1;
    }
    public static boolean selectItem(Minecraft c, Item item) {
        if(c.player==null||c.gameMode==null)return false;
        LocalPlayer p=c.player; int hot=hotbarSlot(c,item);
        if(hot>=0){p.getInventory().setSelectedSlot(hot);return true;}
        if(p.containerMenu!=p.inventoryMenu)return false;
        int selected=p.getInventory().getSelectedSlot();
        for(Slot slot:p.inventoryMenu.slots)if(slot.index>=9&&slot.index<=35&&slot.getItem().is(item)){
            c.gameMode.handleContainerInput(p.inventoryMenu.containerId,slot.index,selected,ContainerInput.SWAP,p);return true;
        }
        return false;
    }
    public static boolean moveItemToHotbarSlot(Minecraft c,Item item,int target) {
        if(c.player==null||c.gameMode==null||target<0||target>8)return false;
        LocalPlayer p=c.player;if(p.containerMenu!=p.inventoryMenu)return false;
        if(p.getInventory().getItem(target).is(item))return true;
        int targetMenuSlot=36+target;
        for(Slot slot:p.inventoryMenu.slots)if(slot.index>=9&&slot.index<=44&&slot.index!=targetMenuSlot&&slot.getItem().is(item)){
            c.gameMode.handleContainerInput(p.inventoryMenu.containerId,slot.index,target,ContainerInput.SWAP,p);return true;
        }
        return false;
    }
    public static boolean moveItemToOffhand(Minecraft c,Item item) {
        if(c.player==null||c.gameMode==null)return false;
        LocalPlayer p=c.player;if(p.containerMenu!=p.inventoryMenu)return false;
        for(Slot slot:p.inventoryMenu.slots)if(slot.index>=9&&slot.index<=44&&slot.getItem().is(item)){
            c.gameMode.handleContainerInput(p.inventoryMenu.containerId,slot.index,
                net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND,ContainerInput.SWAP,p);return true;
        }
        return false;
    }
    public static boolean interactCrosshair(Minecraft c,InteractionHand hand) {
        if(c.player==null||c.gameMode==null||!(c.hitResult instanceof BlockHitResult hit))return false;
        c.gameMode.useItemOn(c.player,hand,hit);c.player.swing(hand);return true;
    }
    public static boolean interactBlock(Minecraft c,BlockPos pos,Direction face,InteractionHand hand) {
        if(c.player==null||c.gameMode==null)return false;
        double x=pos.getX()+.5,y=pos.getY()+.5,z=pos.getZ()+.5;
        if(face==Direction.UP)y=pos.getY()+1;else if(face==Direction.DOWN)y=pos.getY();
        else if(face==Direction.EAST)x=pos.getX()+1;else if(face==Direction.WEST)x=pos.getX();
        else if(face==Direction.SOUTH)z=pos.getZ()+1;else if(face==Direction.NORTH)z=pos.getZ();
        BlockHitResult hit=new BlockHitResult(new Vec3(x,y,z),face,pos,false);
        c.gameMode.useItemOn(c.player,hand,hit);c.player.swing(hand);return true;
    }
}
