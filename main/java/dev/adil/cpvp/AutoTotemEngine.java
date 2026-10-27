package dev.adil.cpvp;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;
public final class AutoTotemEngine {
    private static int cooldown;
    private AutoTotemEngine() {}
    public static void tick(Minecraft c) {
        var cfg=CpvpConfig.settings();
        if(!cfg.autoTotemEnabled||c.player==null||c.gameMode==null||c.gui.screen()!=null)return;
        var p=c.player;
        if(p.isSpectator()||p.containerMenu!=p.inventoryMenu)return;
        if(cooldown>0){cooldown--;return;}
        if(!p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)){
            if(InventoryUtil.moveItemToOffhand(c,Items.TOTEM_OF_UNDYING))cooldown=5;return;
        }
        if(!p.getInventory().getItem(cfg.totemBackupSlot).is(Items.TOTEM_OF_UNDYING)&&
            InventoryUtil.moveItemToHotbarSlot(c,Items.TOTEM_OF_UNDYING,cfg.totemBackupSlot))cooldown=5;
    }
}
