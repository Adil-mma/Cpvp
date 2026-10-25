package dev.adil.cpvp;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.BlockHitResult;
import java.util.Comparator;

public final class CombatEngine {
    private static int fastDelay, crystalDelay, anchorDelay;
    private CombatEngine() {}

    public static void tick(Minecraft c) {
        if(c.player==null||c.level==null||c.gameMode==null||c.gui.screen()!=null)return;
        var cfg=CpvpConfig.settings();
        if(fastDelay>0)fastDelay--;
        if(cfg.fastCrystalEnabled&&fastDelay==0&&c.options.keyUse.isDown()&&
           c.player.getMainHandItem().is(Items.END_CRYSTAL)&&c.hitResult instanceof BlockHitResult hit){
            c.gameMode.useItemOn(c.player,InteractionHand.MAIN_HAND,hit);
            c.player.swing(InteractionHand.MAIN_HAND);
            fastDelay=cfg.fastCrystalDelayTicks+1;
        }
        if(crystalDelay>0)crystalDelay--;
        if(cfg.autoCrystalEnabled&&crystalDelay==0)autoCrystal(c);
        if(anchorDelay>0)anchorDelay--;
        if(cfg.autoAnchorEnabled&&anchorDelay==0)autoAnchor(c);
    }

    private static Player target(Minecraft c) {
        var self=c.player; double max=CpvpConfig.settings().targetRange;
        return c.level.players().stream().filter(p->p!=self&&p.isAlive()&&!p.isSpectator())
            .filter(p->self.distanceTo(p)<=max)
            .min(Comparator.comparingDouble(self::distanceTo)).orElse(null);
    }

    private static void autoCrystal(Minecraft c) {
        var cfg=CpvpConfig.settings(); Player t=target(c);
        if(t==null)return;
        var crystals=c.level.getEntitiesOfClass(EndCrystal.class,c.player.getBoundingBox().inflate(5));
        for(var crystal:crystals)if(crystal.distanceTo(t)<6){
            c.gameMode.attack(c.player,crystal);c.player.swing(InteractionHand.MAIN_HAND);
        }
        if(!InventoryUtil.selectItem(c,Items.END_CRYSTAL))return;
        BlockPos origin=t.blockPosition(), best=null; double score=Double.MAX_VALUE;
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)for(int dy=-1;dy<=1;dy++){
            BlockPos p=origin.offset(dx,dy,dz);
            if(!c.level.getBlockState(p).is(Blocks.OBSIDIAN)&&!c.level.getBlockState(p).is(Blocks.BEDROCK))continue;
            if(!c.level.getBlockState(p.above()).isAir()||!c.level.getBlockState(p.above(2)).isAir())continue;
            double d=t.distanceToSqr(p.getX()+.5,p.getY()+1,p.getZ()+.5);
            if(d<score&&d<=25&&c.player.distanceToSqr(p.getX()+.5,p.getY()+1,p.getZ()+.5)<=20.25){best=p;score=d;}
        }
        if(best!=null&&InventoryUtil.interactBlock(c,best,Direction.UP,InteractionHand.MAIN_HAND))
            crystalDelay=cfg.autoCrystalDelayTicks+1;
    }

    private static void autoAnchor(Minecraft c) {
        var cfg=CpvpConfig.settings();
        if(c.level.dimensionType().respawnAnchorWorks())return;
        Player t=target(c);if(t==null)return;
        BlockPos origin=t.blockPosition(), anchor=null;double score=Double.MAX_VALUE;
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)for(int dy=-1;dy<=1;dy++){
            BlockPos p=origin.offset(dx,dy,dz);
            if(!c.level.getBlockState(p).is(Blocks.RESPAWN_ANCHOR))continue;
            double d=p.distToCenterSqr(origin.getX()+.5,origin.getY()+.5,origin.getZ()+.5);
            if(d<score){score=d;anchor=p;}
        }
        if(anchor!=null){
            int charges=c.level.getBlockState(anchor).getValue(RespawnAnchorBlock.CHARGE);
            if(charges<4){
                if(InventoryUtil.selectItem(c,Items.GLOWSTONE)&&InventoryUtil.interactBlock(c,anchor,Direction.UP,InteractionHand.MAIN_HAND))
                    anchorDelay=cfg.autoAnchorDelayTicks+1;
                return;
            }
            double selfDistance=c.player.distanceToSqr(anchor.getX()+.5,anchor.getY()+.5,anchor.getZ()+.5);
            if(cfg.safeAnchorEnabled&&selfDistance<cfg.safeAnchorDistance*cfg.safeAnchorDistance)return;
            if(InventoryUtil.interactBlock(c,anchor,Direction.UP,InteractionHand.MAIN_HAND))anchorDelay=cfg.autoAnchorDelayTicks+1;
            return;
        }
        if(!InventoryUtil.selectItem(c,Items.RESPAWN_ANCHOR))return;
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
            if(dx==0&&dz==0)continue;
            BlockPos place=origin.offset(dx,0,dz);
            if(!c.level.getBlockState(place).isAir()||!c.level.getBlockState(place.above()).isAir())continue;
            BlockPos support=place.below();
            if(!c.level.getBlockState(support).isFaceSturdy(c.level,support,Direction.UP))continue;
            if(t.distanceToSqr(place.getX()+.5,place.getY()+.5,place.getZ()+.5)>20.25)continue;
            if(InventoryUtil.interactBlock(c,support,Direction.UP,InteractionHand.MAIN_HAND)){anchorDelay=cfg.autoAnchorDelayTicks+1;return;}
        }
    }

    public static void runMacro(Minecraft c) {
        if(c.player==null||c.level==null||c.gameMode==null||c.gui.screen()!=null)return;
        var cfg=CpvpConfig.settings();
        if(!cfg.macroEnabled||!(c.hitResult instanceof BlockHitResult hit))return;
        BlockPos p=hit.getBlockPos();
        if(cfg.macroMode==0){
            if((c.level.getBlockState(p).is(Blocks.OBSIDIAN)||c.level.getBlockState(p).is(Blocks.BEDROCK))&&
               c.level.getBlockState(p.above()).isAir()&&c.level.getBlockState(p.above(2)).isAir()&&
               InventoryUtil.selectItem(c,Items.END_CRYSTAL))InventoryUtil.interactCrosshair(c,InteractionHand.MAIN_HAND);
        }else{
            if(c.level.getBlockState(p).is(Blocks.RESPAWN_ANCHOR)){
                int charge=c.level.getBlockState(p).getValue(RespawnAnchorBlock.CHARGE);
                if(charge<4&&InventoryUtil.selectItem(c,Items.GLOWSTONE))InventoryUtil.interactCrosshair(c,InteractionHand.MAIN_HAND);
                else if(charge==4&&(!cfg.safeAnchorEnabled||c.player.distanceToSqr(p.getX()+.5,p.getY()+.5,p.getZ()+.5)>=cfg.safeAnchorDistance*cfg.safeAnchorDistance))
                    InventoryUtil.interactCrosshair(c,InteractionHand.MAIN_HAND);
            }else if(InventoryUtil.selectItem(c,Items.RESPAWN_ANCHOR))InventoryUtil.interactCrosshair(c,InteractionHand.MAIN_HAND);
        }
    }
}
