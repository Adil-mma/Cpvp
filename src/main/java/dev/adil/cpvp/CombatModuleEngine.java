package dev.adil.cpvp;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
 *
 * <p>Every module is paced by a {@link TickGate} (0-5 tick min/max range). The gate delay only
 * starts after an action was really sent, so a module that has nothing to do reacts on the
 * very next tick. All actions re-check the current world state before sending input.
 */
public final class CombatModuleEngine {
    /** Player feet must be this far above the crystal/base surface to count as "above". */
    private static final double ABOVE_MARGIN = 0.05;

    private static final TickGate FAST = new TickGate();
    private static final TickGate AUTO = new TickGate();
    private static final TickGate HIT = new TickGate();
    private static final TickGate MACRO = new TickGate();
    private static final TickGate MACRO_STAGE = new TickGate();
    private static final TickGate SAFE = new TickGate();
    private static final TickGate DOUBLE = new TickGate();
    private static final TickGate DOUBLE_STAGE = new TickGate();

    private static int doubleAnchorPass;
    private static boolean doubleAnchorDone;
    /** Set by {@link #findAnchorOrPlace} when it sent a placement this tick. */
    private static boolean placedAnchorThisTick;

    private CombatModuleEngine() {}

    public static void tick(Minecraft c) {
        CpvpConfig.Settings s = CpvpConfig.settings();

        // Disabling Double Anchor starts a fresh pass counter next time it is enabled.
        if (!s.doubleAnchorEnabled) resetDoubleAnchorProgress();

        if (c.player == null || c.level == null || c.gameMode == null || c.player.isSpectator()) {
            resetHoldState();
            resetDoubleAnchorProgress();
            return;
        }
        // Never act through a GUI. Keep the Double Anchor pass count so opening
        // inventory to select/place the next anchor does not restart the sequence.
        if (c.gui.screen() != null) {
            resetHoldState();
            return;
        }

        boolean useHeld = c.options.keyUse.isDown();

        if (!useHeld) {
            // Release resets per-action timers, but not Double Anchor's number of
            // completed anchors. The user must release use to select/place the next.
            resetHoldState();
            if (s.fastCrystalEnabled && !s.fastCrystalOnlyWhileHolding && FAST.ready()
                    && fastCrystal(c)) {
                FAST.start(s.fastCrystalMinDelayTicks, s.fastCrystalMaxDelayTicks);
            }
            return;
        }

        if (s.fastCrystalEnabled && !s.autoCrystalEnabled && !s.hitCrystalEnabled
                && FAST.ready() && fastCrystal(c)) {
            FAST.start(s.fastCrystalMinDelayTicks, s.fastCrystalMaxDelayTicks);
        }

        if (s.autoCrystalEnabled) {
            int min = s.autoCrystalMinDelayTicks;
            int max = s.autoCrystalMaxDelayTicks;
            if (s.fastCrystalEnabled) {
                min = Math.min(min, s.fastCrystalMinDelayTicks);
                max = Math.min(max, s.fastCrystalMaxDelayTicks);
                if (min > max) min = max;
            }
            if (AUTO.ready() && autoCrystal(c, s)) AUTO.start(min, max);
        } else if (s.hitCrystalEnabled) {
            if (HIT.ready() && hitCrystal(c, s)) {
                HIT.start(s.hitCrystalMinDelayTicks, s.hitCrystalMaxDelayTicks);
            }
        }

        // Anchor modes are mutually exclusive. Double Anchor can retain its
        // multi-anchor pass count while use is released to manually place the next one.
        if (s.safeAnchorEnabled) {
            if (SAFE.ready() && safeAnchor(c, s)) {
                SAFE.start(s.safeAnchorMinDelayTicks, s.safeAnchorMaxDelayTicks);
            }
        } else if (s.doubleAnchorEnabled) {
            boolean main = DOUBLE.ready();
            boolean stage = DOUBLE_STAGE.ready();
            if (main && stage && doubleAnchor(c, s)) {
                DOUBLE.start(s.doubleAnchorMinDelayTicks, s.doubleAnchorMaxDelayTicks);
                DOUBLE_STAGE.start(s.doubleAnchorMinStageDelayTicks, s.doubleAnchorMaxStageDelayTicks);
            }
        } else if (s.anchorMacroEnabled) {
            boolean main = MACRO.ready();
            boolean stage = MACRO_STAGE.ready();
            if (main && stage && anchorMacro(c, s)) {
                MACRO.start(s.anchorMacroMinDelayTicks, s.anchorMacroMaxDelayTicks);
                MACRO_STAGE.start(s.anchorMacroMinStageDelayTicks, s.anchorMacroMaxStageDelayTicks);
            }
        }
    }

    // ---------------------------------------------------------------- crystals

    /** Places a crystal on the obsidian/bedrock under the crosshair. */
    private static boolean fastCrystal(Minecraft c) {
        // Do not steal right-click from a golden apple, pearl, or shield.
        // Check both hands because these items are commonly kept in the offhand.
        if (isFastCrystalPriorityItem(c.player.getMainHandItem())
                || isFastCrystalPriorityItem(c.player.getOffhandItem())
                || (c.player.isUsingItem() && isFastCrystalPriorityItem(c.player.getUseItem()))) {
            return false;
        }
        BlockHitResult hit = blockHit(c);
        if (hit == null || !isCrystalBase(c, hit.getBlockPos())) return false;
        int crystalSlot = findHotbar(c, Items.END_CRYSTAL);
        if (crystalSlot < 0) return false;
        select(c, crystalSlot);
        c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, hit);
        return true;
    }

    private static boolean isFastCrystalPriorityItem(ItemStack stack) {
        return stack.is(Items.GOLDEN_APPLE)
                || stack.is(Items.ENCHANTED_GOLDEN_APPLE)
                || stack.is(Items.ENDER_PEARL)
                || stack.is(Items.SHIELD);
    }

    /** Hold use: breaks the crystal in reach, otherwise places one on obsidian/bedrock. */
    private static boolean autoCrystal(Minecraft c, CpvpConfig.Settings s) {
        double range = s.autoCrystalRange;
        if (c.hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof EndCrystal crystal) {
            return attackCrystal(c, crystal, range, s.autoCrystalStopAboveTarget);
        }

        // A freshly placed crystal is not always the crosshair entity; break nearby ones first.
        if (attackNearestCrystal(c, range, s.autoCrystalStopAboveTarget)) return true;

        BlockHitResult hit = blockHit(c);
        if (hit == null || !isCrystalBase(c, hit.getBlockPos())) return false;
        BlockPos base = hit.getBlockPos();
        // Crystals sit on top of the base block, so compare against the base's top surface.
        if (s.autoCrystalStopAboveTarget && isAbove(c, base.getY() + 1.0)) return false;
        if (c.player.distanceToSqr(Vec3.atCenterOf(base)) > range * range) return false;
        // Do not stack onto a base that already carries a crystal.
        if (crystalAbove(c, base)) return false;

        int crystalSlot = findHotbar(c, Items.END_CRYSTAL);
        if (crystalSlot < 0) return false;
        select(c, crystalSlot);
        c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, hit);
        return true;
    }

    /** Hold use (vanilla places crystals): only breaks crystals. */
    private static boolean hitCrystal(Minecraft c, CpvpConfig.Settings s) {
        double range = s.hitCrystalRange;
        if (c.hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof EndCrystal crystal) {
            return attackCrystal(c, crystal, range, s.hitCrystalStopAboveTarget);
        }
        BlockHitResult block = blockHit(c);
        if (block == null || !isCrystalBase(c, block.getBlockPos())) return false;
        return attackNearestCrystal(c, range, s.hitCrystalStopAboveTarget);
    }

    private static boolean attackCrystal(Minecraft c, EndCrystal crystal, double range, boolean stopAbove) {
        if (!crystal.isAlive()) return false;
        if (stopAbove && isAbove(c, crystal.getY())) return false;
        if (c.player.distanceToSqr(crystal) > range * range) return false;
        c.gameMode.attack(c.player, crystal);
        c.player.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    private static boolean attackNearestCrystal(Minecraft c, double range, boolean stopAbove) {
        AABB box = c.player.getBoundingBox().inflate(range);
        List<EndCrystal> crystals = c.level.getEntitiesOfClass(EndCrystal.class, box, EndCrystal::isAlive);
        EndCrystal best = null;
        double bestDist = Double.MAX_VALUE;
        for (EndCrystal crystal : crystals) {
            if (stopAbove && isAbove(c, crystal.getY())) continue;
            double d = c.player.distanceToSqr(crystal);
            if (d <= range * range && d < bestDist) {
                best = crystal;
                bestDist = d;
            }
        }
        if (best == null) return false;
        c.gameMode.attack(c.player, best);
        c.player.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    private static boolean crystalAbove(Minecraft c, BlockPos base) {
        AABB box = new AABB(base.above());
        return !c.level.getEntitiesOfClass(EndCrystal.class, box, EndCrystal::isAlive).isEmpty();
    }

    /** True when the player's feet are clearly higher than {@code surfaceY}. */
    private static boolean isAbove(Minecraft c, double surfaceY) {
        return c.player.getY() > surfaceY + ABOVE_MARGIN;
    }

    // ----------------------------------------------------------------- anchors

    /** Charge a manually placed anchor once and detonate with the chosen slot. */
    private static boolean anchorMacro(Minecraft c, CpvpConfig.Settings s) {
        // The player places the anchor manually. Cpvp only charges it once and detonates it.
        BlockPos pos = lookedAtAnchor(c);
        if (pos == null) return false;

        int charges = c.level.getBlockState(pos).getValue(RespawnAnchorBlock.CHARGE);
        if (charges < 1) {
            if (!s.anchorMacroAutoCharge) return false;
            return chargeAnchor(c, pos);
        }
        return detonateAnchor(c, pos, s.anchorDetonatorSlot);
    }

    /**
     * Protects the player before detonating a manually placed anchor. If the player is close,
     * it tries to put a glowstone block between the player and anchor and only detonates after
     * the cover is verified. It uses the configured totem hotbar slot.
     */
    private static boolean safeAnchor(Minecraft c, CpvpConfig.Settings s) {
        // Safe Anchor also expects the anchor to have been placed by the player.
        BlockPos pos = lookedAtAnchor(c);
        if (pos == null) return false;

        int charges = c.level.getBlockState(pos).getValue(RespawnAnchorBlock.CHARGE);
        double distance = c.player.position().distanceTo(Vec3.atCenterOf(pos));
        boolean needsCover = s.safeAnchorProtectSelf && distance < s.safeAnchorSelfDistance;

        if (needsCover && !hasSolidCoverBetween(c, c.player.blockPosition(), pos)) {
            BlockPos cover = coverPosition(c.player.blockPosition(), pos);
            if (cover == null || !c.level.getBlockState(cover).canBeReplaced()
                    || !c.level.getBlockState(cover.below()).isSolid()) {
                // Do not detonate without the requested protection block.
                return false;
            }

            int glowstoneSlot = findHotbar(c, Items.GLOWSTONE);
            if (glowstoneSlot < 0) return false;
            int glowstoneCount = c.player.getInventory().getItem(glowstoneSlot).getCount();
            // One glowstone is used for cover and another is needed if the anchor is uncharged.
            int needed = charges < 1 ? 2 : 1;
            if (glowstoneCount < needed) return false;

            select(c, glowstoneSlot);
            BlockPos support = cover.below();
            BlockHitResult placeHit = new BlockHitResult(
                    Vec3.atCenterOf(support).add(0, 0.5, 0),
                    Direction.UP, support, false);
            c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, placeHit);
            return true;
        }

        if (charges < 1) return chargeAnchor(c, pos);
        // Safe Anchor always detonates with the configured totem slot (slot 9 by default).
        return detonateAnchorWithTotem(c, pos, s.safeAnchorTotemSlot);
    }

    /**
     * Runs the configured number of charge -> detonate passes. The player manually places
     * each next anchor; the macro never switches to or places an anchor.
     */
    private static boolean doubleAnchor(Minecraft c, CpvpConfig.Settings s) {
        if (doubleAnchorDone) return false;

        // Each pass starts only after the player has manually placed the next anchor.
        BlockPos pos = lookedAtAnchor(c);
        if (pos == null) return false;

        int charges = c.level.getBlockState(pos).getValue(RespawnAnchorBlock.CHARGE);
        if (charges < 1) return chargeAnchor(c, pos);

        if (!detonateAnchor(c, pos, s.anchorDetonatorSlot)) return false;
        doubleAnchorPass++;
        if (doubleAnchorPass >= s.doubleAnchorPasses) doubleAnchorDone = true;
        return true;
    }

    private static boolean chargeAnchor(Minecraft c, BlockPos pos) {
        int glowstone = findHotbar(c, Items.GLOWSTONE);
        if (glowstone < 0) return false;
        select(c, glowstone);
        c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, anchorHit(pos));
        return true;
    }

    private static boolean detonateAnchorWithTotem(Minecraft c, BlockPos pos, int preferredSlot) {
        if (preferredSlot < 0 || preferredSlot > 8) return false;
        if (!c.player.getInventory().getItem(preferredSlot).is(Items.TOTEM_OF_UNDYING)) return false;
        select(c, preferredSlot);
        c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, anchorHit(pos));
        return true;
    }

    private static boolean detonateAnchor(Minecraft c, BlockPos pos, int preferredSlot) {
        if (!selectDetonator(c, preferredSlot)) return false;
        c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, anchorHit(pos));
        return true;
    }

    /**
     * Returns the anchor position when an anchor is already there. Otherwise sends a placement
     * (setting {@link #placedAnchorThisTick}) and returns null.
     */
    private static BlockPos findAnchorOrPlace(Minecraft c, BlockHitResult aimed) {
        placedAnchorThisTick = false;
        BlockPos aimedPos = aimed.getBlockPos();
        if (c.level.getBlockState(aimedPos).is(Blocks.RESPAWN_ANCHOR)) return aimedPos;
        BlockPos placePos = aimedPos.relative(aimed.getDirection());
        if (c.level.getBlockState(placePos).is(Blocks.RESPAWN_ANCHOR)) return placePos;
        int anchorSlot = findHotbar(c, Items.RESPAWN_ANCHOR);
        if (anchorSlot < 0 || !c.level.getBlockState(placePos).canBeReplaced()) return null;
        select(c, anchorSlot);
        c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, aimed);
        placedAnchorThisTick = true;
        return null;
    }

    private static BlockPos coverPosition(BlockPos player, BlockPos anchor) {
        int dx = anchor.getX() - player.getX();
        int dz = anchor.getZ() - player.getZ();
        if (dx == 0 && dz == 0) return null;
        if (Math.abs(dx) >= Math.abs(dz)) {
            return new BlockPos(player.getX() + Integer.signum(dx), player.getY(), player.getZ());
        }
        return new BlockPos(player.getX(), player.getY(), player.getZ() + Integer.signum(dz));
    }

    private static int findSolidBlock(Minecraft c) {
        for (int i = 0; i < 9; i++) {
            Item item = c.player.getInventory().getItem(i).getItem();
            if (item == Items.GLOWSTONE || item == Items.RESPAWN_ANCHOR || item == Items.TNT) continue;
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

    // ----------------------------------------------------------------- helpers

    private static BlockHitResult blockHit(Minecraft c) {
        return c.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                ? hit : null;
    }

    private static boolean isCrystalBase(Minecraft c, BlockPos pos) {
        return c.level.getBlockState(pos).is(Blocks.OBSIDIAN)
                || c.level.getBlockState(pos).is(Blocks.BEDROCK);
    }

    private static boolean selectDetonator(Minecraft c, int preferredSlot) {
        if (preferredSlot >= 0 && preferredSlot < 9 && isDetonator(c, preferredSlot)) {
            select(c, preferredSlot);
            return true;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (isDetonator(c, slot)) {
                select(c, slot);
                return true;
            }
        }
        return false;
    }

    /** Anything except the charging/placing items works as a detonator (an empty hand too). */
    private static boolean isDetonator(Minecraft c, int slot) {
        var stack = c.player.getInventory().getItem(slot);
        return !stack.is(Items.GLOWSTONE) && !stack.is(Items.RESPAWN_ANCHOR);
    }

    private static int findHotbar(Minecraft c, Item item) {
        for (int i = 0; i < 9; i++) {
            if (c.player.getInventory().getItem(i).is(item)) return i;
        }
        return -1;
    }

    private static void select(Minecraft c, int slot) {
        if (slot >= 0 && slot < 9) c.player.getInventory().setSelectedSlot(slot);
    }

    /** Resets stage timers on release or GUI changes, but preserves Double Anchor progress. */
    private static void resetHoldState() {
        AUTO.reset();
        HIT.reset();
        MACRO.reset();
        MACRO_STAGE.reset();
        SAFE.reset();
        DOUBLE.reset();
        DOUBLE_STAGE.reset();
        placedAnchorThisTick = false;
    }

    /** A fresh Double Anchor sequence starts when the module is turned off or world changes. */
    private static void resetDoubleAnchorProgress() {
        doubleAnchorPass = 0;
        doubleAnchorDone = false;
    }
}
