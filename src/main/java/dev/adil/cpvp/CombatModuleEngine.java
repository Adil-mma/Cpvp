package dev.adil.cpvp;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
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

    private enum SafeAnchorPhase { WAIT_ANCHOR, CHARGE, WAIT_CHARGE, COVER, WAIT_COVER, DETONATE, WAIT_DETONATION }
    private static SafeAnchorPhase safeAnchorPhase = SafeAnchorPhase.WAIT_ANCHOR;
    private static BlockPos safeAnchorPos;
    private static BlockPos safeAnchorCover;
    private static int safeAnchorCoverAttempts;
    private static int safeAnchorDetonationWait;
    private static int safeAnchorChargeWait;
    private static boolean safeAnchorChargeSent;
    private static int safeAnchorCoverWait;
    private static float safeAnchorPreviousYaw;
    private static float safeAnchorPreviousPitch;

    private static int doubleAnchorPass;
    private static boolean doubleAnchorDone;
    private enum DoubleAnchorPhase {
        WAIT_FIRST_ANCHOR, CHARGE, WAIT_CHARGE, DETONATE, WAIT_EXPLOSION,
        PLACE_NEXT, WAIT_NEXT_ANCHOR, DONE
    }
    private static DoubleAnchorPhase doubleAnchorPhase = DoubleAnchorPhase.WAIT_FIRST_ANCHOR;
    private static BlockPos doubleAnchorPos;
    private static BlockPos doubleAnchorSupport;
    private static Direction doubleAnchorSupportFace;
    private static int doubleAnchorWaitTicks;
    private static int doubleAnchorChargeAttempts;
    private static int doubleAnchorDetonationAttempts;
    private static int doubleAnchorPlacementAttempts;

    private CombatModuleEngine() {}

    public static void tick(Minecraft c) {
        CpvpConfig.Settings s = CpvpConfig.settings();

        // Turning a module off clears its retained state so the next activation starts cleanly.
        if (!s.safeAnchorEnabled) resetSafeAnchorProgress();
        if (!s.doubleAnchorEnabled) resetDoubleAnchorProgress();

        if (c.player == null || c.level == null || c.gameMode == null || c.player.isSpectator()) {
            resetHoldState();
            resetSafeAnchorProgress();
            resetDoubleAnchorProgress();
            return;
        }
        // Keep multi-step anchor phases while a GUI is open, but do not interact through it.
        if (c.gui.screen() != null) {
            resetHoldState();
            return;
        }

        boolean useHeld = c.options.keyUse.isDown();

        if (!useHeld) {
            resetHoldState();
            if (s.fastCrystalEnabled && !s.fastCrystalOnlyWhileHolding && FAST.ready()
                    && fastCrystal(c)) {
                FAST.start(s.fastCrystalMinDelayTicks, s.fastCrystalMaxDelayTicks);
            }

            // Safe Anchor and Double Anchor are state machines: they must continue after
            // the initial manual anchor placement even when the player releases right-click.
            processAnchorModules(c, s);
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

        processAnchorModules(c, s);
    }

    private static void processAnchorModules(Minecraft c, CpvpConfig.Settings s) {
        if (s.safeAnchorEnabled) {
            if (SAFE.ready() && safeAnchor(c, s)) {
                SAFE.start(s.safeAnchorMinDelayTicks, s.safeAnchorMaxDelayTicks);
            }
        } else if (s.doubleAnchorEnabled) {
            if (DOUBLE.ready() && DOUBLE_STAGE.ready() && doubleAnchor(c, s)) {
                DOUBLE.start(s.doubleAnchorMinDelayTicks, s.doubleAnchorMaxDelayTicks);
                DOUBLE_STAGE.start(s.doubleAnchorMinStageDelayTicks, s.doubleAnchorMaxStageDelayTicks);
            }
        } else if (s.anchorMacroEnabled && c.options.keyUse.isDown()) {
            if (MACRO.ready() && MACRO_STAGE.ready() && anchorMacro(c, s)) {
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
     * Safe Anchor charges a manually placed anchor with glowstone, optionally places
     * one glowstone block at the fixed cover position, then looks back and detonates
     * with the configured totem slot.
     */
    private static boolean safeAnchor(Minecraft c, CpvpConfig.Settings s) {
        if (safeAnchorPhase == SafeAnchorPhase.WAIT_ANCHOR) {
            BlockPos aimedAnchor = lookedAtAnchor(c);
            if (aimedAnchor == null) return false;
            safeAnchorPos = aimedAnchor;
            safeAnchorCover = coverPosition(c.player.blockPosition(), aimedAnchor);
            safeAnchorPhase = SafeAnchorPhase.CHARGE;
            safeAnchorCoverAttempts = 0;
            safeAnchorDetonationWait = 0;
            safeAnchorChargeWait = 0;
            safeAnchorChargeSent = false;
            safeAnchorCoverWait = 0;
        }

        if (safeAnchorPos == null
                || !c.level.getBlockState(safeAnchorPos).is(Blocks.RESPAWN_ANCHOR)) {
            resetSafeAnchorProgress();
            return false;
        }

        switch (safeAnchorPhase) {
            case WAIT_ANCHOR -> {
                return false;
            }
            case CHARGE -> {
                int charges = c.level.getBlockState(safeAnchorPos)
                        .getValue(RespawnAnchorBlock.CHARGE);
                if (charges >= 1) {
                    safeAnchorPhase = SafeAnchorPhase.COVER;
                    return false;
                }
                if (safeAnchorChargeSent) {
                    safeAnchorPhase = SafeAnchorPhase.WAIT_CHARGE;
                    return false;
                }
                if (chargeAnchor(c, safeAnchorPos)) {
                    safeAnchorChargeSent = true;
                    safeAnchorChargeWait = 0;
                    safeAnchorPhase = SafeAnchorPhase.WAIT_CHARGE;
                    return true;
                }
                return false;
            }
            case WAIT_CHARGE -> {
                int charges = c.level.getBlockState(safeAnchorPos)
                        .getValue(RespawnAnchorBlock.CHARGE);
                if (charges >= 1) {
                    safeAnchorPhase = SafeAnchorPhase.COVER;
                    return false;
                }
                if (++safeAnchorChargeWait >= 12) {
                    c.player.sendSystemMessage(Component.literal(
                            "[Cpvp] Safe Anchor stopped: glowstone charge was not confirmed."));
                    resetSafeAnchorProgress();
                }
                return false;
            }
            case COVER -> {
                double distance = c.player.position().distanceTo(Vec3.atCenterOf(safeAnchorPos));
                boolean shouldProtect = s.safeAnchorProtectSelf
                        && distance < s.safeAnchorSelfDistance;

                if (!shouldProtect
                        || hasSolidCoverBetween(c, c.player.blockPosition(), safeAnchorPos)
                        || (safeAnchorCover != null
                            && c.level.getBlockState(safeAnchorCover).isSolid())) {
                    safeAnchorPhase = SafeAnchorPhase.DETONATE;
                    return false;
                }

                // Do not substitute obsidian or another block: the cover item is Glowstone.
                int glowstoneSlot = findHotbar(c, Items.GLOWSTONE);
                if (safeAnchorCover == null
                        || !c.level.getBlockState(safeAnchorCover).canBeReplaced()
                        || c.player.getBoundingBox().intersects(new AABB(safeAnchorCover))
                        || glowstoneSlot < 0
                        || safeAnchorCoverAttempts >= 1) {
                    // No valid placement: do not rotate the camera. Continue to the anchor.
                    safeAnchorPhase = SafeAnchorPhase.DETONATE;
                    return false;
                }

                BlockHitResult placeHit = findVisiblePlacementHit(c, safeAnchorCover);
                if (placeHit == null) {
                    // No reachable support face for this exact cell: do not turn.
                    safeAnchorPhase = SafeAnchorPhase.DETONATE;
                    return false;
                }

                safeAnchorPreviousYaw = c.player.getYRot();
                safeAnchorPreviousPitch = c.player.getXRot();
                select(c, glowstoneSlot);
                lookAt(c, placeHit.getLocation());
                var result = c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, placeHit);
                if (!result.consumesAction()) {
                    // The game refused the placement locally: restore the view immediately.
                    c.player.setYRot(safeAnchorPreviousYaw);
                    c.player.setXRot(safeAnchorPreviousPitch);
                    safeAnchorPhase = SafeAnchorPhase.DETONATE;
                    return false;
                }
                safeAnchorCoverAttempts++;
                safeAnchorCoverWait = 0;
                safeAnchorPhase = SafeAnchorPhase.WAIT_COVER;
                return true;
            }
            case WAIT_COVER -> {
                if (safeAnchorCover != null
                        && c.level.getBlockState(safeAnchorCover).is(Blocks.GLOWSTONE)) {
                    safeAnchorPhase = SafeAnchorPhase.DETONATE;
                    return false;
                }
                if (++safeAnchorCoverWait >= 6) {
                    // Server correction/no placement: undo the temporary camera turn.
                    c.player.setYRot(safeAnchorPreviousYaw);
                    c.player.setXRot(safeAnchorPreviousPitch);
                    safeAnchorPhase = SafeAnchorPhase.DETONATE;
                }
                return false;
            }
            case DETONATE -> {
                int charges = c.level.getBlockState(safeAnchorPos)
                        .getValue(RespawnAnchorBlock.CHARGE);
                if (charges < 1) {
                    safeAnchorPhase = SafeAnchorPhase.CHARGE;
                    return false;
                }
                if (!detonateAnchorWithTotem(c, safeAnchorPos, s.safeAnchorTotemSlot)) return false;
                safeAnchorPhase = SafeAnchorPhase.WAIT_DETONATION;
                safeAnchorDetonationWait = 0;
                return true;
            }
            case WAIT_DETONATION -> {
                if (!c.level.getBlockState(safeAnchorPos).is(Blocks.RESPAWN_ANCHOR)) {
                    resetSafeAnchorProgress();
                    return false;
                }
                if (++safeAnchorDetonationWait >= 8) {
                    safeAnchorPhase = SafeAnchorPhase.DETONATE;
                    safeAnchorDetonationWait = 0;
                }
                return false;
            }
        }
        return false;
    }

    /**
     * The player places the first anchor manually. The module charges it, clicks it while
     * holding the Respawn Anchor stack, waits for the explosion, then places the next anchor
     * in the exact same cell. Every network-dependent phase has a bounded retry count.
     */
    private static boolean doubleAnchor(Minecraft c, CpvpConfig.Settings s) {
        if (doubleAnchorDone || doubleAnchorPhase == DoubleAnchorPhase.DONE) return false;

        switch (doubleAnchorPhase) {
            case WAIT_FIRST_ANCHOR -> {
                BlockPos first = lookedAtAnchor(c);
                if (first == null) return false;
                doubleAnchorPos = first;
                BlockHitResult supportHit = findPlacementHit(c, first);
                if (supportHit == null) {
                    failDoubleAnchor(c, "No solid support face found under/around the first anchor.");
                    return false;
                }
                doubleAnchorSupport = supportHit.getBlockPos();
                doubleAnchorSupportFace = supportHit.getDirection();
                doubleAnchorWaitTicks = 0;
                doubleAnchorDetonationAttempts = 0;
                doubleAnchorPlacementAttempts = 0;
                doubleAnchorPhase = DoubleAnchorPhase.CHARGE;
                return false;
            }
            case CHARGE -> {
                if (doubleAnchorPos == null
                        || !c.level.getBlockState(doubleAnchorPos).is(Blocks.RESPAWN_ANCHOR)) {
                    doubleAnchorPhase = DoubleAnchorPhase.WAIT_FIRST_ANCHOR;
                    doubleAnchorPos = null;
                    return false;
                }
                int charges = c.level.getBlockState(doubleAnchorPos)
                        .getValue(RespawnAnchorBlock.CHARGE);
                if (charges >= 1) {
                    doubleAnchorWaitTicks = 0;
                    doubleAnchorChargeAttempts = 0;
                    doubleAnchorDetonationAttempts = 0;
                    doubleAnchorPhase = DoubleAnchorPhase.DETONATE;
                    return false;
                }
                if (doubleAnchorChargeAttempts >= 1) {
                    doubleAnchorPhase = DoubleAnchorPhase.WAIT_CHARGE;
                    doubleAnchorWaitTicks = 0;
                    return false;
                }
                if (chargeAnchor(c, doubleAnchorPos)) {
                    doubleAnchorChargeAttempts++;
                    doubleAnchorWaitTicks = 0;
                    doubleAnchorPhase = DoubleAnchorPhase.WAIT_CHARGE;
                    return true;
                }
                return false;
            }
            case WAIT_CHARGE -> {
                if (doubleAnchorPos == null
                        || !c.level.getBlockState(doubleAnchorPos).is(Blocks.RESPAWN_ANCHOR)) {
                    failDoubleAnchor(c, "Anchor disappeared before its glowstone charge was confirmed.");
                    return false;
                }
                int charges = c.level.getBlockState(doubleAnchorPos)
                        .getValue(RespawnAnchorBlock.CHARGE);
                if (charges >= 1) {
                    doubleAnchorWaitTicks = 0;
                    doubleAnchorDetonationAttempts = 0;
                    doubleAnchorPhase = DoubleAnchorPhase.DETONATE;
                    return false;
                }
                if (++doubleAnchorWaitTicks >= 12) {
                    failDoubleAnchor(c, "Glowstone charge was not confirmed; stopped to avoid charging repeatedly.");
                }
                return false;
            }
            case DETONATE -> {
                if (doubleAnchorPos == null
                        || !c.level.getBlockState(doubleAnchorPos).is(Blocks.RESPAWN_ANCHOR)) {
                    doubleAnchorPhase = DoubleAnchorPhase.WAIT_EXPLOSION;
                    doubleAnchorWaitTicks = 0;
                    return false;
                }
                int charges = c.level.getBlockState(doubleAnchorPos)
                        .getValue(RespawnAnchorBlock.CHARGE);
                if (charges < 1) {
                    doubleAnchorPhase = DoubleAnchorPhase.CHARGE;
                    return false;
                }
                int anchorSlot = findDoubleAnchorSlot(c, s);
                if (anchorSlot < 0) {
                    failDoubleAnchor(c, "Respawn Anchor stack not found in the hotbar.");
                    return false;
                }

                BlockHitResult detonateHit = visibleAnchorHit(c, doubleAnchorPos);
                if (detonateHit == null) {
                    if (++doubleAnchorWaitTicks >= 12) {
                        failDoubleAnchor(c, "No clear, reachable view of the charged anchor.");
                    }
                    return false;
                }
                select(c, anchorSlot);
                lookAt(c, detonateHit.getLocation());
                var detonateResult = c.gameMode.useItemOn(
                        c.player, InteractionHand.MAIN_HAND, detonateHit);
                doubleAnchorDetonationAttempts++;
                if (!detonateResult.consumesAction()) {
                    if (doubleAnchorDetonationAttempts >= 3) {
                        failDoubleAnchor(c, "The anchor interaction was rejected 3 times.");
                    }
                    return true;
                }
                doubleAnchorPhase = DoubleAnchorPhase.WAIT_EXPLOSION;
                doubleAnchorWaitTicks = 0;
                return true;
            }
            case WAIT_EXPLOSION -> {
                if (doubleAnchorPos == null
                        || !c.level.getBlockState(doubleAnchorPos).is(Blocks.RESPAWN_ANCHOR)) {
                    doubleAnchorPass++;
                    doubleAnchorWaitTicks = 0;
                    doubleAnchorDetonationAttempts = 0;
                    if (doubleAnchorPass >= s.doubleAnchorPasses) {
                        doubleAnchorDone = true;
                        doubleAnchorPhase = DoubleAnchorPhase.DONE;
                    } else {
                        doubleAnchorPhase = DoubleAnchorPhase.PLACE_NEXT;
                    }
                    return false;
                }

                if (++doubleAnchorWaitTicks >= 12) {
                    if (doubleAnchorDetonationAttempts < 3) {
                        doubleAnchorPhase = DoubleAnchorPhase.DETONATE;
                        doubleAnchorWaitTicks = 0;
                    } else {
                        failDoubleAnchor(c, "Anchor did not explode after 3 attempts. Check reach/server rules.");
                    }
                }
                return false;
            }
            case PLACE_NEXT -> {
                if (doubleAnchorPos == null) return false;
                if (!c.level.getBlockState(doubleAnchorPos).canBeReplaced()) {
                    if (++doubleAnchorWaitTicks >= 12) {
                        failDoubleAnchor(c, "The original anchor cell is not empty after the explosion.");
                    }
                    return false;
                }

                // Prefer the exact support face used for the original anchor. If the
                // explosion removed it, discover another real face that still places into
                // the original cell.
                BlockHitResult placeHit = null;
                if (doubleAnchorSupport != null && doubleAnchorSupportFace != null) {
                    placeHit = visiblePlacementHit(c, doubleAnchorSupport, doubleAnchorSupportFace);
                }
                if (placeHit == null) {
                    placeHit = findVisiblePlacementHit(c, doubleAnchorPos);
                    if (placeHit != null) {
                        doubleAnchorSupport = placeHit.getBlockPos();
                        doubleAnchorSupportFace = placeHit.getDirection();
                    }
                }
                if (placeHit == null) {
                    if (++doubleAnchorWaitTicks >= 12) {
                        failDoubleAnchor(c, "Cannot reach a surviving solid face for the same anchor cell.");
                    }
                    return false;
                }

                int anchorSlot = findDoubleAnchorSlot(c, s);
                if (anchorSlot < 0) {
                    failDoubleAnchor(c, "Respawn Anchor stack not found in the hotbar.");
                    return false;
                }

                select(c, anchorSlot);
                // Turn the actual view toward the exact support face before placing.
                lookAt(c, placeHit.getLocation());
                var placementResult = c.gameMode.useItemOn(
                        c.player, InteractionHand.MAIN_HAND, placeHit);
                doubleAnchorPlacementAttempts++;
                if (!placementResult.consumesAction()) {
                    if (doubleAnchorPlacementAttempts >= 3) {
                        failDoubleAnchor(c, "The next anchor placement was rejected 3 times.");
                    }
                    return true;
                }
                doubleAnchorPhase = DoubleAnchorPhase.WAIT_NEXT_ANCHOR;
                doubleAnchorWaitTicks = 0;
                return true;
            }
            case WAIT_NEXT_ANCHOR -> {
                if (doubleAnchorPos != null
                        && c.level.getBlockState(doubleAnchorPos).is(Blocks.RESPAWN_ANCHOR)) {
                    doubleAnchorPhase = DoubleAnchorPhase.CHARGE;
                    doubleAnchorWaitTicks = 0;
                    doubleAnchorChargeAttempts = 0;
                    doubleAnchorPlacementAttempts = 0;
                    return false;
                }
                if (++doubleAnchorWaitTicks >= 12) {
                    if (doubleAnchorPlacementAttempts < 3) {
                        doubleAnchorPhase = DoubleAnchorPhase.PLACE_NEXT;
                        doubleAnchorWaitTicks = 0;
                    } else {
                        failDoubleAnchor(c, "The next anchor was not placed after 3 attempts.");
                    }
                }
                return false;
            }
            case DONE -> {
                return false;
            }
        }
        return false;
    }

    private static void failDoubleAnchor(Minecraft c, String reason) {
        if (c.player != null) {
            c.player.sendSystemMessage(Component.literal("[Cpvp] Double Anchor stopped: " + reason));
        }
        doubleAnchorDone = true;
        doubleAnchorPhase = DoubleAnchorPhase.DONE;
    }


    private static BlockHitResult placementHit(BlockPos support, Direction face) {
        Vec3 point = Vec3.atCenterOf(support).add(
                face.getStepX() * 0.5,
                face.getStepY() * 0.5,
                face.getStepZ() * 0.5);
        return new BlockHitResult(point, face, support, false);
    }

    /** Find a real solid neighbour face that places a block into the requested target cell. */
    private static BlockHitResult findPlacementHit(Minecraft c, BlockPos target) {
        // The target may be either an existing anchor (support discovery) or an empty
        // replacement cell (placement); callers validate replaceability when required.
        if (target == null) return null;
        Direction[] order = {
                Direction.DOWN, Direction.NORTH, Direction.SOUTH,
                Direction.WEST, Direction.EAST, Direction.UP
        };
        for (Direction offset : order) {
            BlockPos support = target.relative(offset);
            if (!c.level.getBlockState(support).isSolid()) continue;
            Direction face = offset.getOpposite();
            return placementHit(support, face);
        }
        return null;
    }

    private static int findDoubleAnchorSlot(Minecraft c, CpvpConfig.Settings s) {
        int preferred = s.doubleAnchorAnchorSlot;
        if (preferred >= 0 && preferred < 9
                && c.player.getInventory().getItem(preferred).is(Items.RESPAWN_ANCHOR)) {
            return preferred;
        }
        return findHotbar(c, Items.RESPAWN_ANCHOR);
    }

    private static boolean chargeAnchor(Minecraft c, BlockPos pos) {
        int glowstone = findHotbar(c, Items.GLOWSTONE);
        if (glowstone < 0) return false;
        BlockHitResult hit = visibleAnchorHit(c, pos);
        if (hit == null) return false;
        select(c, glowstone);
        lookAt(c, hit.getLocation());
        return c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, hit).consumesAction();
    }

    private static boolean detonateAnchorWithTotem(Minecraft c, BlockPos pos, int preferredSlot) {
        if (preferredSlot < 0 || preferredSlot > 8) return false;
        if (!c.player.getInventory().getItem(preferredSlot).is(Items.TOTEM_OF_UNDYING)) return false;
        BlockHitResult hit = visibleAnchorHit(c, pos);
        if (hit == null) return false;
        select(c, preferredSlot);
        lookAt(c, hit.getLocation());
        return c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, hit).consumesAction();
    }

    private static boolean detonateAnchor(Minecraft c, BlockPos pos, int preferredSlot) {
        if (!selectDetonator(c, preferredSlot)) return false;
        BlockHitResult hit = visibleAnchorHit(c, pos);
        if (hit == null) return false;
        lookAt(c, hit.getLocation());
        return c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, hit).consumesAction();
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

    /** Real vanilla-style ray hit on the requested anchor, bounded by normal reach. */
    private static BlockHitResult visibleAnchorHit(Minecraft c, BlockPos pos) {
        Vec3 eye = c.player.getEyePosition();
        Vec3 target = Vec3.atCenterOf(pos);
        if (eye.distanceToSqr(target) > 4.5 * 4.5) return null;
        BlockHitResult hit = c.level.clip(new ClipContext(
                eye, target, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, c.player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)
                ? hit : null;
    }

    /** Verify that the requested support face is the first real block hit by the ray. */
    private static BlockHitResult visiblePlacementHit(
            Minecraft c, BlockPos support, Direction face) {
        if (support == null || face == null || !c.level.getBlockState(support).isSolid()) return null;
        BlockHitResult candidate = placementHit(support, face);
        Vec3 eye = c.player.getEyePosition();
        if (eye.distanceToSqr(candidate.getLocation()) > 4.5 * 4.5) return null;
        Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        Vec3 end = candidate.getLocation().add(
                normal.x * 0.02, normal.y * 0.02, normal.z * 0.02);
        BlockHitResult actual = c.level.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, c.player));
        return actual.getType() == HitResult.Type.BLOCK
                && actual.getBlockPos().equals(support)
                && actual.getDirection() == face ? actual : null;
    }

    /** Find any visible support face that places into the exact requested cell. */
    private static BlockHitResult findVisiblePlacementHit(Minecraft c, BlockPos target) {
        if (target == null) return null;
        Direction[] order = {
                Direction.DOWN, Direction.NORTH, Direction.SOUTH,
                Direction.WEST, Direction.EAST, Direction.UP
        };
        for (Direction offset : order) {
            BlockPos support = target.relative(offset);
            Direction face = offset.getOpposite();
            BlockHitResult hit = visiblePlacementHit(c, support, face);
            if (hit != null) return hit;
        }
        return null;
    }

    /** Rotate the real player view toward a world-space target before sending an interaction. */
    private static void lookAt(Minecraft c, Vec3 target) {
        Vec3 delta = target.subtract(c.player.getEyePosition());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        c.player.setYRot(yaw);
        c.player.setXRot(pitch);
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

    /** Resets hold-only module timers on release or GUI changes. */
    private static void resetHoldState() {
        AUTO.reset();
        HIT.reset();
        MACRO.reset();
        MACRO_STAGE.reset();
    }

    private static void resetSafeAnchorProgress() {
        safeAnchorPhase = SafeAnchorPhase.WAIT_ANCHOR;
        safeAnchorPos = null;
        safeAnchorCover = null;
        safeAnchorCoverAttempts = 0;
        safeAnchorDetonationWait = 0;
        safeAnchorChargeWait = 0;
        safeAnchorChargeSent = false;
        safeAnchorCoverWait = 0;
        SAFE.reset();
    }

    /** A fresh Double Anchor sequence starts when the module is turned off or world changes. */
    private static void resetDoubleAnchorProgress() {
        doubleAnchorPass = 0;
        doubleAnchorDone = false;
        doubleAnchorPhase = DoubleAnchorPhase.WAIT_FIRST_ANCHOR;
        doubleAnchorPos = null;
        doubleAnchorSupport = null;
        doubleAnchorSupportFace = null;
        doubleAnchorWaitTicks = 0;
        doubleAnchorChargeAttempts = 0;
        doubleAnchorDetonationAttempts = 0;
        doubleAnchorPlacementAttempts = 0;
        DOUBLE.reset();
        DOUBLE_STAGE.reset();
    }
}
