package dev.adil.cpvp;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Client-side tick engine for the Crystal/Anchor modules.
 * All actions are rate-limited and re-check the current world state before sending input.
 */
public final class CombatModuleEngine {
    private static long lastFastCrystal, lastAutoCrystal, lastHitCrystal;
    private static long lastAnchorMacro, lastSafeAnchor, lastDoubleAnchor;
    private static BlockPos anchorSequencePos;
    private static int anchorSequenceStage;
    private static int doubleAnchorPass;
    private static long lastAnchorStage;

    private CombatModuleEngine() {}

    public static void tick(Minecraft c) {
        if (c.player == null || c.level == null || c.gameMode == null
                || c.player.isSpectator() || c.player.isUsingItem() && !c.options.keyUse.isDown()) {
            resetSequences();
            return;
        }

        CpvpConfig.Settings s = CpvpConfig.settings();
        boolean useHeld = c.options.keyUse.isDown();
        if (!useHeld) {
            resetSequences();
            return;
        }

        long now = System.currentTimeMillis();
        if (s.fastCrystalEnabled && ready(now, lastFastCrystal, s.fastCrystalDelayMs)) {
            if (!s.fastCrystalOnlyWhileHolding || useHeld) {
                fastCrystal(c);
                lastFastCrystal = now;
            }
        }
        if (s.autoCrystalEnabled && ready(now, lastAutoCrystal, s.autoCrystalDelayMs)) {
            autoCrystal(c, s);
            lastAutoCrystal = now;
        }
        if (s.hitCrystalEnabled && ready(now, lastHitCrystal, s.hitCrystalDelayMs)) {
            hitCrystal(c, s);
            lastHitCrystal = now;
        }
        if (s.anchorMacroEnabled && ready(now, lastAnchorMacro, s.anchorMacroDelayMs)) {
            anchorMacro(c, s);
            lastAnchorMacro = now;
        }
        if (s.safeAnchorEnabled && ready(now, lastSafeAnchor, s.safeAnchorDelayMs)) {
            safeAnchor(c, s);
            lastSafeAnchor = now;
        }
        if (s.doubleAnchorEnabled && ready(now, lastDoubleAnchor, s.doubleAnchorDelayMs)) {
            doubleAnchor(c, s);
            lastDoubleAnchor = now;
        }
    }

    private static boolean ready(long now, long last, int delayMs) {
        return now - last >= Math.max(0, delayMs);
    }

    private static void fastCrystal(Minecraft c) {
        BlockHitResult hit = blockHit(c);
        if (hit == null || !isCrystalBase(c, hit.getBlockPos())) return;
        int crystalSlot = findHotbar(c, Items.END_CRYSTAL);
        if (crystalSlot < 0) return;
        select(c, crystalSlot);
        c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, hit);
    }

    private static void autoCrystal(Minecraft c, CpvpConfig.Settings s) {
        BlockHitResult hit = blockHit(c);
        if (hit == null || !isCrystalBase(c, hit.getBlockPos())) return;
        if (s.autoCrystalStopAboveTarget && c.player.getY() > hit.getBlockPos().getY() + 1.0) return;
        if (c.player.distanceToSqr(Vec3.atCenterOf(hit.getBlockPos())) > s.autoCrystalRange * s.autoCrystalRange) return;

        int crystalSlot = findHotbar(c, Items.END_CRYSTAL);
        if (crystalSlot >= 0) {
            select(c, crystalSlot);
            c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, hit);
        }

        attackNearbyCrystals(c, s.autoCrystalRange);
    }

    private static void hitCrystal(Minecraft c, CpvpConfig.Settings s) {
        if (s.hitCrystalStopAboveTarget && c.player.getY() > c.player.blockPosition().getY() + 0.25) {
            // This module is intentionally conservative while the player is above the target plane.
            HitResult aimed = c.hitResult;
            if (aimed instanceof EntityHitResult eh && eh.getEntity() instanceof EndCrystal crystal
                    && c.player.getY() > crystal.getY() + 1.0) return;
        }
        HitResult hit = c.hitResult;
        if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof EndCrystal crystal) {
            if (c.player.distanceToSqr(crystal) <= s.hitCrystalRange * s.hitCrystalRange) {
                c.gameMode.attack(c.player, crystal);
                c.player.swing(InteractionHand.MAIN_HAND);
            }
            return;
        }
        BlockHitResult block = blockHit(c);
        if (block == null || !isCrystalBase(c, block.getBlockPos())) return;
        if (c.player.distanceToSqr(Vec3.atCenterOf(block.getBlockPos())) > s.hitCrystalRange * s.hitCrystalRange) return;
        attackNearbyCrystals(c, s.hitCrystalRange);
    }

    private static void attackNearbyCrystals(Minecraft c, double range) {
        AABB box = c.player.getBoundingBox().inflate(range);
        List<EndCrystal> crystals = c.level.getEntitiesOfClass(EndCrystal.class, box,
                e -> e.isAlive() && c.player.distanceToSqr(e) <= range * range);
        for (EndCrystal crystal : crystals) {
            c.gameMode.attack(c.player, crystal);
            c.player.swing(InteractionHand.MAIN_HAND);
            break;
        }
    }

    private static void anchorMacro(Minecraft c, CpvpConfig.Settings s) {
        BlockPos pos = lookedAtAnchor(c);
        if (pos == null) {
            anchorSequencePos = null;
            anchorSequenceStage = 0;
            return;
        }
        if (!pos.equals(anchorSequencePos)) {
            anchorSequencePos = pos.immutable();
            anchorSequenceStage = 0;
        }
        if (System.currentTimeMillis() - lastAnchorStage < s.anchorStageDelayMs) return;

        int charges = c.level.getBlockState(pos).getValue(RespawnAnchorBlock.CHARGES);
        if (s.anchorMacroAutoCharge && charges < 4) {
            int glowstone = findHotbar(c, Items.GLOWSTONE);
            if (glowstone < 0) return;
            select(c, glowstone);
            c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, anchorHit(pos));
            lastAnchorStage = System.currentTimeMillis();
            return;
        }
        int detonator = s.anchorDetonatorSlot;
        if (detonator < 0 || detonator > 8) detonator = c.player.getInventory().selected;
        select(c, detonator);
        c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, anchorHit(pos));
        lastAnchorStage = System.currentTimeMillis();
        anchorSequenceStage++;
    }

    private static void safeAnchor(Minecraft c, CpvpConfig.Settings s) {
        BlockPos pos = lookedAtAnchor(c);
        if (pos == null) return;
        if (s.safeAnchorProtectSelf && c.player.distanceToSqr(Vec3.atCenterOf(pos))
                < s.safeAnchorSelfDistance * s.safeAnchorSelfDistance) {
            // Do not detonate when no verified protective placement can be made.
            // A solid block is only considered protective if one already separates the player and anchor.
            if (!hasSolidCoverBetween(c, c.player.blockPosition(), pos)) return;
        }
        int detonator = s.anchorDetonatorSlot;
        if (detonator >= 0 && detonator <= 8) select(c, detonator);
        c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, anchorHit(pos));
    }

    private static void doubleAnchor(Minecraft c, CpvpConfig.Settings s) {
        BlockPos pos = lookedAtAnchor(c);
        if (pos == null) {
            doubleAnchorPass = 0;
            return;
        }
        if (System.currentTimeMillis() - lastAnchorStage < s.anchorStageDelayMs) return;
        int charges = c.level.getBlockState(pos).getValue(RespawnAnchorBlock.CHARGES);
        if (charges < 4) {
            int glowstone = findHotbar(c, Items.GLOWSTONE);
            if (glowstone < 0) return;
            select(c, glowstone);
            c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, anchorHit(pos));
        } else {
            int detonator = s.anchorDetonatorSlot;
            if (detonator >= 0 && detonator <= 8) select(c, detonator);
            c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, anchorHit(pos));
            doubleAnchorPass++;
            if (doubleAnchorPass >= 2) doubleAnchorPass = 0;
        }
        lastAnchorStage = System.currentTimeMillis();
    }

    private static boolean hasSolidCoverBetween(Minecraft c, BlockPos player, BlockPos anchor) {
        Vec3 from = Vec3.atCenterOf(player);
        Vec3 to = Vec3.atCenterOf(anchor);
        double distance = from.distanceTo(to);
        int steps = Math.max(1, (int) Math.ceil(distance * 3));
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            BlockPos p = BlockPos.containing(
                    from.x + (to.x - from.x) * t,
                    from.y + (to.y - from.y) * t,
                    from.z + (to.z - from.z) * t);
            if (!p.equals(player) && !p.equals(anchor) && c.level.getBlockState(p).isSolid()) return true;
        }
        return false;
    }

    private static BlockPos lookedAtAnchor(Minecraft c) {
        BlockHitResult hit = blockHit(c);
        if (hit == null) return null;
        BlockPos pos = hit.getBlockPos();
        return c.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR) ? pos : null;
    }

    private static BlockHitResult anchorHit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    private static BlockHitResult blockHit(Minecraft c) {
        return c.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                ? hit : null;
    }

    private static boolean isCrystalBase(Minecraft c, BlockPos pos) {
        return c.level.getBlockState(pos).is(Blocks.OBSIDIAN)
                || c.level.getBlockState(pos).is(Blocks.BEDROCK);
    }

    private static int findHotbar(Minecraft c, Item item) {
        for (int i = 0; i < 9; i++) {
            if (c.player.getInventory().getItem(i).is(item)) return i;
        }
        return -1;
    }

    private static void select(Minecraft c, int slot) {
        if (slot >= 0 && slot < 9) c.player.getInventory().selected = slot;
    }

    private static void resetSequences() {
        anchorSequencePos = null;
        anchorSequenceStage = 0;
        doubleAnchorPass = 0;
    }
}
