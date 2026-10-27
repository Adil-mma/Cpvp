package dev.adil.cpvp;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
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
        if (c.player == null || c.level == null || c.gameMode == null || c.player.isSpectator()) {
            resetSequences();
            return;
        }

        CpvpConfig.Settings s = CpvpConfig.settings();
        boolean useHeld = c.options.keyUse.isDown();
        long now = System.currentTimeMillis();
        if (!useHeld) {
            resetSequences();
            if (s.fastCrystalEnabled && !s.fastCrystalOnlyWhileHolding
                    && ready(now, lastFastCrystal, s.fastCrystalDelayMs)) {
                fastCrystal(c);
                lastFastCrystal = now;
            }
            return;
        }
        if (s.fastCrystalEnabled && !s.autoCrystalEnabled && !s.hitCrystalEnabled
                && ready(now, lastFastCrystal, s.fastCrystalDelayMs)) {
            if (!s.fastCrystalOnlyWhileHolding || useHeld) {
                fastCrystal(c);
                lastFastCrystal = now;
            }
        }
        int autoDelay = s.fastCrystalEnabled
                ? Math.min(s.autoCrystalDelayMs, s.fastCrystalDelayMs) : s.autoCrystalDelayMs;
        if (s.autoCrystalEnabled && ready(now, lastAutoCrystal, autoDelay)) {
            autoCrystal(c, s);
            lastAutoCrystal = now;
        }
        else if (s.hitCrystalEnabled && ready(now, lastHitCrystal, s.hitCrystalDelayMs)) {
            hitCrystal(c, s);
            lastHitCrystal = now;
        }
        // Anchor automation modes are mutually exclusive; safety mode has priority
        // if an older config file contains more than one enabled anchor module.
        if (s.safeAnchorEnabled && ready(now, lastSafeAnchor, s.safeAnchorDelayMs)) {
            safeAnchor(c, s);
            lastSafeAnchor = now;
        } else if (s.doubleAnchorEnabled && ready(now, lastDoubleAnchor, s.doubleAnchorDelayMs)) {
            doubleAnchor(c, s);
            lastDoubleAnchor = now;
        } else if (s.anchorMacroEnabled && ready(now, lastAnchorMacro, s.anchorMacroDelayMs)) {
            anchorMacro(c, s);
            lastAnchorMacro = now;
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
        if (s.autoCrystalStopAboveTarget && c.player.blockPosition().getY() > hit.getBlockPos().getY()) return;
        if (c.player.distanceToSqr(Vec3.atCenterOf(hit.getBlockPos())) > s.autoCrystalRange * s.autoCrystalRange) return;

        int crystalSlot = findHotbar(c, Items.END_CRYSTAL);
        if (crystalSlot >= 0) {
            select(c, crystalSlot);
            c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, hit);
        }

        attackNearbyCrystals(c, s.autoCrystalRange);
    }

    private static void hitCrystal(Minecraft c, CpvpConfig.Settings s) {
        HitResult hit = c.hitResult;
        if (s.hitCrystalStopAboveTarget && hit instanceof EntityHitResult eh
                && eh.getEntity() instanceof EndCrystal crystal
                && c.player.blockPosition().getY() > crystal.blockPosition().getY()) return;
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
        BlockHitResult aimed = blockHit(c);
        if (aimed == null) return;
        BlockPos pos = findAnchorOrPlace(c, aimed);
        if (pos == null) return;
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
        select(c, s.anchorDetonatorSlot);
        c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, anchorHit(pos));
        lastAnchorStage = System.currentTimeMillis();
        anchorSequenceStage++;
    }

    private static void safeAnchor(Minecraft c, CpvpConfig.Settings s) {
        BlockPos pos = lookedAtAnchor(c);
        if (pos == null) return;
        if (s.safeAnchorProtectSelf && !hasSolidCoverBetween(c, c.player.blockPosition(), pos)) {
            BlockPos cover = coverPosition(c.player.blockPosition(), pos);
            if (cover != null && c.level.getBlockState(cover).isAir()
                    && c.level.getBlockState(cover.below()).isSolid()) {
                int blockSlot = findSolidBlock(c);
                if (blockSlot >= 0) {
                    select(c, blockSlot);
                    BlockPos support = cover.below();
                    BlockHitResult placeHit = new BlockHitResult(
                            Vec3.atCenterOf(support).add(0, 0.5, 0),
                            Direction.UP, support, false);
                    c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, placeHit);
                    return;
                }
            }
            // Never detonate if the protection block could not be placed and verified.
            return;
        }
        if (System.currentTimeMillis() - lastAnchorStage < s.anchorStageDelayMs) return;
        select(c, s.anchorDetonatorSlot);
        c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, anchorHit(pos));
        lastAnchorStage = System.currentTimeMillis();
    }

    private static void doubleAnchor(Minecraft c, CpvpConfig.Settings s) {
        BlockHitResult aimed = blockHit(c);
        if (aimed == null) {
            doubleAnchorPass = 0;
            return;
        }
        BlockPos pos = findAnchorOrPlace(c, aimed);
        if (pos == null) return;
        if (System.currentTimeMillis() - lastAnchorStage < s.anchorStageDelayMs) return;

        int charges = c.level.getBlockState(pos).getValue(RespawnAnchorBlock.CHARGES);
        if (charges < 4) {
            int glowstone = findHotbar(c, Items.GLOWSTONE);
            if (glowstone < 0) return;
            select(c, glowstone);
            c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, anchorHit(pos));
        } else {
            select(c, s.anchorDetonatorSlot);
            c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, anchorHit(pos));
            doubleAnchorPass++;
            if (doubleAnchorPass >= s.doubleAnchorPasses) doubleAnchorPass = 0;
            // After the anchor is detonated, the next held-use tick can place a fresh one
            // at the same adjacent position. Server state is re-checked before each pass.
            anchorSequencePos = pos.immutable();
        }
        lastAnchorStage = System.currentTimeMillis();
    }

    private static BlockPos findAnchorOrPlace(Minecraft c, BlockHitResult aimed) {
        BlockPos aimedPos = aimed.getBlockPos();
        if (c.level.getBlockState(aimedPos).is(Blocks.RESPAWN_ANCHOR)) return aimedPos;
        BlockPos placePos = aimedPos.relative(aimed.getDirection());
        if (c.level.getBlockState(placePos).is(Blocks.RESPAWN_ANCHOR)) return placePos;
        int anchorSlot = findHotbar(c, Items.RESPAWN_ANCHOR);
        if (anchorSlot < 0 || !c.level.getBlockState(placePos).canBeReplaced()) return null;
        select(c, anchorSlot);
        c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, aimed);
        return null;
    }

    private static BlockPos coverPosition(BlockPos player, BlockPos anchor) {
        int dx = anchor.getX() - player.getX();
        int dy = anchor.getY() - player.getY();
        int dz = anchor.getZ() - player.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return new BlockPos(player.getX() + Integer.signum(dx), player.getY(), player.getZ());
        }
        return new BlockPos(player.getX(), player.getY(), player.getZ() + Integer.signum(dz));
    }

    private static int findSolidBlock(Minecraft c) {
        for (int i = 0; i < 9; i++) {
            Item item = c.player.getInventory().getItem(i).getItem();
            if (item instanceof BlockItem blockItem
                    && blockItem.getBlock().defaultBlockState().isSolid()) return i;
        }
        return -1;
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
