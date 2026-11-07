package dev.adil.cpvp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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
    private static final int DOUBLE_ANCHOR_DETONATIONS = 2;

    private static final TickGate FAST = new TickGate();
    private static final TickGate AUTO = new TickGate();
    private static final TickGate HIT = new TickGate();
    private static final TickGate MACRO = new TickGate();
    private static final TickGate MACRO_STAGE = new TickGate();
    private static final TickGate SAFE = new TickGate();
    private static final TickGate DOUBLE = new TickGate();
    private static final TickGate DOUBLE_STAGE = new TickGate();
    private static ClientLevel activeLevel;

    private enum SafeAnchorPhase { WAIT_ANCHOR, CHARGE, WAIT_CHARGE, COVER, WAIT_COVER, RESTORE_VIEW, DETONATE, WAIT_DETONATION }
    private static SafeAnchorPhase safeAnchorPhase = SafeAnchorPhase.WAIT_ANCHOR;
    private static BlockPos safeAnchorPos;
    private static BlockPos safeAnchorCover;
    private static int safeAnchorCoverAttempts;
    private static int safeAnchorDetonationWait;
    private static int safeAnchorDetonationAttempts;
    private static int safeAnchorChargeWait;
    private static boolean safeAnchorChargeSent;
    private static int safeAnchorCoverWait;
    private static float safeAnchorOriginalYaw;
    private static float safeAnchorOriginalPitch;
    private static boolean safeAnchorViewSaved;

    private static int doubleAnchorPass;
    private static boolean doubleAnchorDone;
    private enum DoubleAnchorPhase {
        WAIT_FIRST_ANCHOR, CHARGE, WAIT_CHARGE, DETONATE, WAIT_EXPLOSION,
        PLACE_NEXT, WAIT_NEXT_ANCHOR, DONE
    }
    private static DoubleAnchorPhase doubleAnchorPhase = DoubleAnchorPhase.WAIT_FIRST_ANCHOR;
    private static BlockPos doubleAnchorPos;
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
            activeLevel = null;
            resetWorldState();
            return;
        }

        // Never carry a multi-step interaction into another dimension/world instance.
        if (activeLevel != c.level) {
            activeLevel = c.level;
            resetWorldState();
        }
        // Keep multi-step anchor phases while a GUI is open, but do not interact through it.
        if (c.gui.screen() != null) {
            resetHoldState();
            return;
        }

        boolean useHeld = c.options.keyUse.isDown();

        if (!useHeld) {
            resetHoldState();
        }

        // Fast Crystal is independent from Hit Crystal: on a block hit it places,
        // while Hit Crystal can still attack an entity hit in the same tick.
        if (s.fastCrystalEnabled && !s.autoCrystalEnabled
                && (!s.fastCrystalOnlyWhileHolding || useHeld)
                && FAST.ready() && fastCrystal(c)) {
            FAST.start(s.fastCrystalMinDelayTicks, s.fastCrystalMaxDelayTicks);
        }

        if (!useHeld) {
            // Safe Anchor and Double Anchor continue after the initial manual placement,
            // even when the player releases right-click.
            processAnchorModules(c, s);
            return;
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
            boolean actionReady = DOUBLE.ready();
            boolean stageReady = DOUBLE_STAGE.ready();
            if (actionReady && stageReady && doubleAnchor(c, s)) {
                DOUBLE.start(s.doubleAnchorMinDelayTicks, s.doubleAnchorMaxDelayTicks);
                DOUBLE_STAGE.start(s.doubleAnchorMinStageDelayTicks, s.doubleAnchorMaxStageDelayTicks);
            }
        } else if (s.anchorMacroEnabled && c.options.keyUse.isDown()) {
            boolean actionReady = MACRO.ready();
            boolean stageReady = MACRO_STAGE.ready();
            if (actionReady && stageReady && anchorMacro(c, s)) {
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

    /** True while the player selected or is actively using a protected item. */
    private static boolean isProtectedCrystalItem(Minecraft c) {
        // Protect only the items that must keep right-click priority. Do not block
        // unrelated item use such as bows, food, or other tools.
        return isFastCrystalPriorityItem(c.player.getMainHandItem())
                || isFastCrystalPriorityItem(c.player.getOffhandItem())
                || (c.player.isUsingItem() && isFastCrystalPriorityItem(c.player.getUseItem()));
    }

    /** Auto Crystal keeps its slot-switching cycle but never interrupts Gap/Shield/Pearl use. */
    private static boolean autoCrystal(Minecraft c, CpvpConfig.Settings s) {
        if (isProtectedCrystalItem(c)) return false;

        double range = s.autoCrystalRange;
        if (c.hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof EndCrystal crystal) {
            return attackCrystal(c, crystal, range, s.autoCrystalStopAboveTarget);
        }

        EndCrystal target = findNearestVisibleCrystal(c, range, s.autoCrystalStopAboveTarget);
        if (target != null) {
            if (c.hitResult instanceof EntityHitResult entityHit
                    && entityHit.getEntity() == target) {
                return attackCrystal(c, target, range, s.autoCrystalStopAboveTarget);
            }
            // Rotate the visible client camera gradually; attack only after vanilla's
            // crosshair result actually identifies this crystal.
            turnToward(c, target.getBoundingBox().getCenter());
            return false;
        }

        BlockHitResult hit = blockHit(c);
        if (hit == null || !isCrystalBase(c, hit.getBlockPos())) return false;
        BlockPos base = hit.getBlockPos();
        if (s.autoCrystalStopAboveTarget
                && isAtOrAboveSurface(c, base.getY() + 1.0)) return false;
        if (c.player.distanceToSqr(Vec3.atCenterOf(base)) > range * range) return false;
        if (crystalAbove(c, base)) return false;

        int crystalSlot = findHotbar(c, Items.END_CRYSTAL);
        if (crystalSlot < 0) return false;
        select(c, crystalSlot);
        // Count the interaction attempt even if the server rejects it. The outer TickGate
        // then prevents a rejected placement from being resent on every client tick.
        c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, hit);
        return true;
    }

    /** Hit Crystal only attacks an End Crystal directly under the crosshair. */
    private static boolean hitCrystal(Minecraft c, CpvpConfig.Settings s) {
        if (isProtectedCrystalItem(c)) return false;
        if (!(c.hitResult instanceof EntityHitResult entityHit)
                || !(entityHit.getEntity() instanceof EndCrystal crystal)) {
            return false;
        }
        return attackCrystal(c, crystal, s.hitCrystalRange, s.hitCrystalStopAboveTarget);
    }

    private static boolean attackCrystal(Minecraft c, EndCrystal crystal, double range, boolean stopAbove) {
        if (!crystal.isAlive()) return false;
        if (stopAbove && isAtOrAboveCrystalBase(c, crystal)) return false;
        if (c.player.distanceToSqr(crystal) > range * range) return false;
        c.gameMode.attack(c.player, crystal);
        c.player.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    private static EndCrystal findNearestVisibleCrystal(
            Minecraft c, double range, boolean stopAbove) {
        AABB searchBox = c.player.getBoundingBox().inflate(range);
        List<EndCrystal> crystals = c.level.getEntitiesOfClass(
                EndCrystal.class, searchBox, EndCrystal::isAlive);
        EndCrystal best = null;
        double bestDistance = Double.MAX_VALUE;

        for (EndCrystal crystal : crystals) {
            if (stopAbove && isAtOrAboveCrystalBase(c, crystal)) continue;
            double distance = c.player.distanceToSqr(crystal);
            if (distance > range * range || distance >= bestDistance) continue;
            if (!hasLineOfSight(c, crystal.getBoundingBox().getCenter())) continue;

            best = crystal;
            bestDistance = distance;
        }
        return best;
    }

    private static boolean hasLineOfSight(Minecraft c, Vec3 target) {
        HitResult obstruction = c.level.clip(new ClipContext(
                c.player.getEyePosition(), target,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, c.player));
        return obstruction.getType() != HitResult.Type.BLOCK;
    }

    private static boolean crystalAbove(Minecraft c, BlockPos base) {
        AABB box = new AABB(base.above());
        return !c.level.getEntitiesOfClass(EndCrystal.class, box, EndCrystal::isAlive).isEmpty();
    }

    /** Treat the obsidian/bedrock top surface as the crystal's height reference. */
    private static boolean isAtOrAboveCrystalBase(Minecraft c, EndCrystal crystal) {
        BlockPos base = crystal.blockPosition().below();
        if (!isCrystalBase(c, base)) {
            base = crystal.blockPosition();
        }
        return isCrystalBase(c, base)
                && isAtOrAboveSurface(c, base.getY() + 1.0);
    }

    /** Stop when the player's feet are at the base's top level or higher. */
    private static boolean isAtOrAboveSurface(Minecraft c, double surfaceY) {
        return c.player.getY() >= surfaceY - ABOVE_MARGIN;
    }

    // ----------------------------------------------------------------- anchors

    /** Charge a manually placed anchor once and detonate with the chosen slot. */
    private static boolean anchorMacro(Minecraft c, CpvpConfig.Settings s) {
        // Do not steal right-click while a priority item is held or being used.
        if (isProtectedCrystalItem(c)) return false;

        // Only interact with the anchor already under the player's crosshair.
        // Do not rotate the view or synthesize a center hit.
        BlockHitResult hit = blockHit(c);
        if (hit == null) return false;
        BlockPos pos = hit.getBlockPos();
        if (!c.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR)) return false;

        int charges = c.level.getBlockState(pos).getValue(RespawnAnchorBlock.CHARGE);
        if (charges < 1) {
            if (!s.anchorMacroAutoCharge) return false;
            int glowstoneSlot = findHotbar(c, Items.GLOWSTONE);
            if (glowstoneSlot < 0) return false;
            select(c, glowstoneSlot);
            c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, hit);
            return true;
        }

        if (!selectDetonator(c, s.anchorDetonatorSlot)) return false;
        c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, hit);
        return true;
    }

    /**
     * Safe Anchor charges a manually placed anchor with glowstone, optionally places
     * one glowstone block at the fixed cover position, then looks back and detonates
     * with the configured totem slot.
     */
    private static boolean safeAnchor(Minecraft c, CpvpConfig.Settings s) {
        if (safeAnchorPhase == SafeAnchorPhase.WAIT_ANCHOR) {
            BlockPos aimedAnchor = lookedAtAnchor(c);
            if (aimedAnchor == null) aimedAnchor = findNearbyUnchargedAnchor(c);
            if (aimedAnchor == null) return false;
            safeAnchorPos = aimedAnchor;
            safeAnchorCover = coverPosition(c.player.blockPosition(), aimedAnchor);
            safeAnchorOriginalYaw = c.player.getYRot();
            safeAnchorOriginalPitch = c.player.getXRot();
            safeAnchorViewSaved = true;
            safeAnchorPhase = SafeAnchorPhase.CHARGE;
            safeAnchorCoverAttempts = 0;
            safeAnchorDetonationWait = 0;
            safeAnchorDetonationAttempts = 0;
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
                    safeAnchorChargeWait = 0;
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
                if (++safeAnchorChargeWait >= 40) {
                    c.player.sendSystemMessage(Component.literal(
                            "[Cpvp] Safe Anchor stopped: Glowstone is missing or the anchor cannot be reached."));
                    resetSafeAnchorProgress();
                }
                return false;
            }
            case WAIT_CHARGE -> {
                int charges = c.level.getBlockState(safeAnchorPos)
                        .getValue(RespawnAnchorBlock.CHARGE);
                if (charges >= 1) {
                    safeAnchorChargeWait = 0;
                    safeAnchorPhase = SafeAnchorPhase.COVER;
                    return false;
                }
                if (++safeAnchorChargeWait >= 12) {
                    c.player.sendSystemMessage(Component.literal(
                            "[Cpvp] Safe Anchor stopped: Glowstone charge was not confirmed."));
                    resetSafeAnchorProgress();
                }
                return false;
            }
            case COVER -> {
                double distance = c.player.position().distanceTo(Vec3.atCenterOf(safeAnchorPos));
                boolean shouldProtect = s.safeAnchorProtectSelf
                        && distance < s.safeAnchorSelfDistance;

                if (!shouldProtect
                        || hasSolidCoverBetween(c, c.player.blockPosition(), safeAnchorPos)) {
                    safeAnchorPhase = SafeAnchorPhase.DETONATE;
                    return false;
                }

                if (safeAnchorCoverAttempts >= 1) {
                    safeAnchorPhase = SafeAnchorPhase.WAIT_COVER;
                    return false;
                }

                int glowstoneSlot = findHotbar(c, Items.GLOWSTONE);
                if (glowstoneSlot < 0) {
                    safeAnchorPhase = SafeAnchorPhase.DETONATE;
                    return false;
                }

                // Recalculate from the player's current position every tick. The player
                // may have moved since the anchor was first detected.
                BlockPos candidate = findSafeAnchorCoverPosition(c, safeAnchorPos);
                if (candidate == null) {
                    if (++safeAnchorCoverWait >= 20) {
                        safeAnchorPhase = safeAnchorViewSaved
                                ? SafeAnchorPhase.RESTORE_VIEW : SafeAnchorPhase.DETONATE;
                    }
                    return false;
                }
                safeAnchorCover = candidate;

                BlockHitResult placeTarget = findVisiblePlacementHit(c, safeAnchorCover);
                if (placeTarget == null) {
                    if (++safeAnchorCoverWait >= 20) {
                        safeAnchorPhase = SafeAnchorPhase.DETONATE;
                    }
                    return false;
                }

                BlockHitResult currentHit = blockHit(c);
                if (!placesInto(currentHit, safeAnchorCover)) {
                    turnToward(c, placeTarget.getLocation());
                    if (++safeAnchorCoverWait >= 20) {
                        safeAnchorPhase = SafeAnchorPhase.DETONATE;
                    }
                    return false;
                }

                select(c, glowstoneSlot);
                // Treat this as one placement attempt even when the server declines it.
                // WAIT_COVER verifies the authoritative block state before proceeding.
                c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, currentHit);
                safeAnchorCoverAttempts++;
                safeAnchorCoverWait = 0;
                safeAnchorPhase = SafeAnchorPhase.WAIT_COVER;
                return true;
            }
            case WAIT_COVER -> {
                if (safeAnchorCover != null
                        && c.level.getBlockState(safeAnchorCover).is(Blocks.GLOWSTONE)) {
                    safeAnchorPhase = safeAnchorViewSaved
                            ? SafeAnchorPhase.RESTORE_VIEW : SafeAnchorPhase.DETONATE;
                    return false;
                }
                if (++safeAnchorCoverWait >= 10) {
                    safeAnchorPhase = safeAnchorViewSaved
                            ? SafeAnchorPhase.RESTORE_VIEW : SafeAnchorPhase.DETONATE;
                }
                return false;
            }
            case RESTORE_VIEW -> {
                if (!safeAnchorViewSaved
                        || turnTowardRotation(c, safeAnchorOriginalYaw, safeAnchorOriginalPitch)) {
                    safeAnchorViewSaved = false;
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

                BlockPos avoidedCell = safeAnchorCover != null
                        && c.level.getBlockState(safeAnchorCover).is(Blocks.GLOWSTONE)
                        ? safeAnchorCover : null;
                if (detonateAnchorWithTotem(c, safeAnchorPos, s.safeAnchorTotemSlot, avoidedCell)) {
                    safeAnchorDetonationAttempts++;
                    safeAnchorPhase = SafeAnchorPhase.WAIT_DETONATION;
                    safeAnchorDetonationWait = 0;
                    return true;
                }
                if (++safeAnchorDetonationWait >= 40) {
                    c.player.sendSystemMessage(Component.literal(
                            "[Cpvp] Safe Anchor stopped: no usable Totem or visible anchor face."));
                    resetSafeAnchorProgress();
                }
                return false;
            }
            case WAIT_DETONATION -> {
                if (!c.level.getBlockState(safeAnchorPos).is(Blocks.RESPAWN_ANCHOR)) {
                    resetSafeAnchorProgress();
                    return false;
                }
                if (++safeAnchorDetonationWait >= 8) {
                    if (safeAnchorDetonationAttempts >= 3) {
                        c.player.sendSystemMessage(Component.literal(
                                "[Cpvp] Safe Anchor stopped: detonation was not confirmed after 3 attempts."));
                        resetSafeAnchorProgress();
                        return false;
                    }
                    safeAnchorPhase = SafeAnchorPhase.DETONATE;
                    safeAnchorDetonationWait = 0;
                }
                return false;
            }
        }
        return false;
    }

    /**
     * The player places the first anchor manually. Each later phase waits for the actual
     * crosshair ray before interacting and reuses the original anchor cell after an explosion.
     */
    private static boolean doubleAnchor(Minecraft c, CpvpConfig.Settings s) {
        if (doubleAnchorDone || doubleAnchorPhase == DoubleAnchorPhase.DONE) {
            // A failed run remains paused while its anchor is still present. Once that
            // anchor is gone, allow the enabled module to start on the next manual anchor.
            if (doubleAnchorPos != null
                    && !c.level.getBlockState(doubleAnchorPos).is(Blocks.RESPAWN_ANCHOR)) {
                resetDoubleAnchorProgress();
            } else {
                return false;
            }
        }

        switch (doubleAnchorPhase) {
            case WAIT_FIRST_ANCHOR -> {
                BlockPos first = lookedAtAnchor(c);
                if (first == null) return false;
                doubleAnchorPos = first;
                doubleAnchorPass = 0;
                doubleAnchorChargeAttempts = 0;
                doubleAnchorDetonationAttempts = 0;
                doubleAnchorPlacementAttempts = 0;
                doubleAnchorWaitTicks = 0;

                // The first anchor is already placed manually. It may be floating;
                // do not require or cache a neighbouring support block.
                doubleAnchorPhase = DoubleAnchorPhase.CHARGE;
                return false;
            }
            case CHARGE -> {
                if (doubleAnchorPos == null
                        || !c.level.getBlockState(doubleAnchorPos).is(Blocks.RESPAWN_ANCHOR)) {
                    doubleAnchorPhase = DoubleAnchorPhase.WAIT_FIRST_ANCHOR;
                    doubleAnchorPos = null;
                    doubleAnchorPass = 0;
                    doubleAnchorChargeAttempts = 0;
                    doubleAnchorDetonationAttempts = 0;
                    doubleAnchorPlacementAttempts = 0;
                    doubleAnchorWaitTicks = 0;
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
                if (++doubleAnchorWaitTicks >= 40) {
                    failDoubleAnchor(c,
                            "Cannot charge: keep the anchor reachable and put Glowstone in the hotbar.");
                }
                return false;
            }
            case WAIT_CHARGE -> {
                if (doubleAnchorPos == null
                        || !c.level.getBlockState(doubleAnchorPos).is(Blocks.RESPAWN_ANCHOR)) {
                    failDoubleAnchor(c, "Anchor disappeared before its Glowstone charge was confirmed.");
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
                    failDoubleAnchor(c, "Glowstone charge was not confirmed; stopped to avoid repeated charging.");
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

                // Use the configured Respawn Anchor slot for every detonation.
                // The replacement anchor follows the same manual sequence as the first.
                int detonatorSlot = findDoubleAnchorSlot(c, s);
                if (detonatorSlot < 0) {
                    failDoubleAnchor(c, "Respawn Anchor stack not found in the hotbar.");
                    return false;
                }

                BlockHitResult currentHit = blockHit(c);
                if (currentHit == null || !currentHit.getBlockPos().equals(doubleAnchorPos)) {
                    BlockHitResult aimHit = visibleAnchorHit(c, doubleAnchorPos);
                    if (aimHit == null) {
                        if (++doubleAnchorWaitTicks >= 40) {
                            failDoubleAnchor(c, "No clear, reachable view of the charged anchor.");
                        }
                        return false;
                    }
                    turnToward(c, aimHit.getLocation());
                    if (++doubleAnchorWaitTicks >= 40) {
                        failDoubleAnchor(c, "Could not aim at the charged anchor.");
                    }
                    return false;
                }

                select(c, detonatorSlot);
                var detonateResult = c.gameMode.useItemOn(
                        c.player, InteractionHand.MAIN_HAND, currentHit);
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
                    if (doubleAnchorPass >= DOUBLE_ANCHOR_DETONATIONS) {
                        // The two detonations are complete. Re-arm the module so the
                        // next manually placed anchor starts a fresh two-detonation run.
                        resetDoubleAnchorProgress();
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
                    if (++doubleAnchorWaitTicks >= 40) {
                        failDoubleAnchor(c, "The original anchor cell is not empty after the explosion.");
                    }
                    return false;
                }

                int anchorSlot = findDoubleAnchorSlot(c, s);
                if (anchorSlot < 0) {
                    failDoubleAnchor(c, "Respawn Anchor stack not found in the hotbar.");
                    return false;
                }

                // Re-place directly into the now-empty original cell. This deliberately
                // sends an air-cell BlockHitResult instead of looking for a solid neighbour.
                BlockHitResult airHit = airPlacementHit(c, doubleAnchorPos);
                if (airHit == null) {
                    if (++doubleAnchorWaitTicks >= 40) {
                        failDoubleAnchor(c, "The empty anchor cell is out of reach or obstructed.");
                    }
                    return false;
                }

                select(c, anchorSlot);
                var placementResult = c.gameMode.useItemOn(
                        c.player, InteractionHand.MAIN_HAND, airHit);
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
                    doubleAnchorDetonationAttempts = 0;
                    doubleAnchorPlacementAttempts = 0;
                    return false;
                }
                if (++doubleAnchorWaitTicks >= 20) {
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


    private static boolean placesInto(BlockHitResult hit, BlockPos target) {
        return hit != null && target != null
                && hit.getBlockPos().relative(hit.getDirection()).equals(target);
    }

    private static BlockHitResult placementHit(BlockPos support, Direction face) {
        Vec3 point = Vec3.atCenterOf(support).add(
                face.getStepX() * 0.5,
                face.getStepY() * 0.5,
                face.getStepZ() * 0.5);
        return new BlockHitResult(point, face, support, false);
    }

    private static int findDoubleAnchorSlot(Minecraft c, CpvpConfig.Settings s) {
        int preferred = s.doubleAnchorAnchorSlot;
        if (preferred >= 0 && preferred < 9
                && c.player.getInventory().getItem(preferred).is(Items.RESPAWN_ANCHOR)) {
            return preferred;
        }
        return findHotbar(c, Items.RESPAWN_ANCHOR);
    }

    /**
     * Build an interaction hit at the empty target cell itself. Unlike ordinary
     * placement this does not depend on a neighbouring solid block face.
     * The normal reach and line-of-sight checks are retained before sending it.
     */
    private static BlockHitResult airPlacementHit(Minecraft c, BlockPos target) {
        if (target == null || !c.level.getBlockState(target).canBeReplaced()) return null;

        Vec3 eye = c.player.getEyePosition();
        Vec3 location = Vec3.atCenterOf(target);
        if (eye.distanceToSqr(location) > 4.5 * 4.5) return null;

        HitResult obstruction = c.level.clip(new ClipContext(
                eye, location, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, c.player));
        if (obstruction.getType() == HitResult.Type.BLOCK
                && !((BlockHitResult) obstruction).getBlockPos().equals(target)) {
            return null;
        }

        return new BlockHitResult(location, Direction.UP, target, false);
    }

    private static boolean chargeAnchor(Minecraft c, BlockPos pos) {
        int glowstone = findHotbar(c, Items.GLOWSTONE);
        if (glowstone < 0) return false;

        BlockHitResult hit = blockHit(c);
        if (hit == null || !hit.getBlockPos().equals(pos)) {
            BlockHitResult target = visibleAnchorHit(c, pos);
            if (target != null) turnToward(c, target.getLocation());
            return false;
        }

        select(c, glowstone);
        // The caller enters WAIT_CHARGE and checks the world state before continuing.
        c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, hit);
        return true;
    }

    private static boolean detonateAnchorWithTotem(
            Minecraft c, BlockPos pos, int preferredSlot, BlockPos avoidedCell) {
        int totemSlot = -1;
        if (preferredSlot >= 0 && preferredSlot < 9
                && c.player.getInventory().getItem(preferredSlot).is(Items.TOTEM_OF_UNDYING)) {
            totemSlot = preferredSlot;
        } else {
            totemSlot = findHotbar(c, Items.TOTEM_OF_UNDYING);
        }
        if (totemSlot < 0) return false;

        BlockHitResult hit = blockHit(c);
        if (hit == null || !hit.getBlockPos().equals(pos)) {
            BlockHitResult target = visibleAnchorHitAvoiding(c, pos, avoidedCell);
            if (target != null) turnToward(c, target.getLocation());
            return false;
        }

        select(c, totemSlot);
        // The caller enters WAIT_DETONATION and verifies that the anchor disappeared.
        c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, hit);
        return true;
    }

    private static void turnToward(Minecraft c, Vec3 target) {
        Vec3 delta = target.subtract(c.player.getEyePosition());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));

        float yawDelta = wrapDegrees(targetYaw - c.player.getYRot());
        float pitchDelta = targetPitch - c.player.getXRot();
        c.player.setYRot(c.player.getYRot() + clampAngle(yawDelta, 14.0f));
        c.player.setXRot(Math.max(-90.0f, Math.min(90.0f,
                c.player.getXRot() + clampAngle(pitchDelta, 10.0f))));
    }

    private static boolean turnTowardRotation(Minecraft c, float targetYaw, float targetPitch) {
        float yawDelta = wrapDegrees(targetYaw - c.player.getYRot());
        float pitchDelta = targetPitch - c.player.getXRot();
        if (Math.abs(yawDelta) <= 1.0f && Math.abs(pitchDelta) <= 1.0f) {
            c.player.setYRot(targetYaw);
            c.player.setXRot(targetPitch);
            return true;
        }
        c.player.setYRot(c.player.getYRot() + clampAngle(yawDelta, 14.0f));
        c.player.setXRot(Math.max(-90.0f, Math.min(90.0f,
                c.player.getXRot() + clampAngle(pitchDelta, 10.0f))));
        return false;
    }

    private static float clampAngle(float value, float maxStep) {
        return Math.max(-maxStep, Math.min(maxStep, value));
    }

    private static float wrapDegrees(float value) {
        value %= 360.0f;
        if (value >= 180.0f) value -= 360.0f;
        if (value < -180.0f) value += 360.0f;
        return value;
    }

    /**
     * Finds a nearby uncharged anchor when movement makes the crosshair miss it
     * immediately after placement. Only visible anchors within normal reach qualify.
     */
    private static BlockPos findNearbyUnchargedAnchor(Minecraft c) {
        BlockPos origin = c.player.blockPosition();
        Vec3 eye = c.player.getEyePosition();
        BlockPos best = null;
        double bestDistance = 3.5 * 3.5;

        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    BlockPos candidate = origin.offset(x, y, z);
                    if (!c.level.getBlockState(candidate).is(Blocks.RESPAWN_ANCHOR)
                            || c.level.getBlockState(candidate).getValue(RespawnAnchorBlock.CHARGE) > 0) {
                        continue;
                    }
                    double distance = eye.distanceToSqr(Vec3.atCenterOf(candidate));
                    if (distance > bestDistance) continue;
                    if (visibleAnchorHit(c, candidate) == null) continue;
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    /**
     * Pick a replaceable, reachable cell between the player's current position and
     * the anchor. Recomputed while moving so the cover does not use a stale position.
     */
    private static BlockPos findSafeAnchorCoverPosition(Minecraft c, BlockPos anchor) {
        Vec3 from = c.player.getEyePosition();
        Vec3 to = Vec3.atCenterOf(anchor);
        Vec3 segment = to.subtract(from);
        double lengthSquared = segment.lengthSqr();
        if (lengthSquared < 1.0) return null;

        BlockPos origin = c.player.blockPosition();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        AABB playerBox = c.player.getBoundingBox();

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    BlockPos candidate = origin.offset(x, y, z);
                    if (candidate.equals(anchor)
                            || !c.level.getBlockState(candidate).canBeReplaced()
                            || playerBox.intersects(new AABB(candidate))) {
                        continue;
                    }

                    Vec3 center = Vec3.atCenterOf(candidate);
                    double t = center.subtract(from).dot(segment) / lengthSquared;
                    if (t <= 0.02 || t >= 0.92) continue;
                    Vec3 nearest = from.add(segment.scale(t));
                    double offLine = center.distanceToSqr(nearest);
                    if (offLine > 1.15 * 1.15) continue;
                    if (findVisiblePlacementHit(c, candidate) == null) continue;

                    double score = offLine + t * 0.15;
                    if (score < bestScore) {
                        best = candidate;
                        bestScore = score;
                    }
                }
            }
        }
        return best;
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

    /** Real vanilla-style ray hit on any exposed anchor face, bounded by normal reach. */
    private static BlockHitResult visibleAnchorHit(Minecraft c, BlockPos pos) {
        return visibleAnchorHitAvoiding(c, pos, null);
    }

    /**
     * Find an exposed anchor face without relying on a ray that crosses the future cover
     * cell. This prevents Safe Anchor from placing glowstone that makes the anchor impossible
     * to click normally afterward.
     */
    private static BlockHitResult visibleAnchorHitAvoiding(
            Minecraft c, BlockPos pos, BlockPos avoidedCell) {
        Vec3 eye = c.player.getEyePosition();
        double x = pos.getX(), y = pos.getY(), z = pos.getZ();
        Vec3[] aimPoints = {
                new Vec3(x + 0.5, y + 0.5, z + 0.5),
                new Vec3(x + 0.5, y + 0.95, z + 0.5),
                new Vec3(x + 0.5, y + 0.05, z + 0.5),
                new Vec3(x + 0.05, y + 0.5, z + 0.5),
                new Vec3(x + 0.95, y + 0.5, z + 0.5),
                new Vec3(x + 0.5, y + 0.5, z + 0.05),
                new Vec3(x + 0.5, y + 0.5, z + 0.95)
        };

        for (Vec3 target : aimPoints) {
            if (eye.distanceToSqr(target) > 4.5 * 4.5) continue;
            if (avoidedCell != null && rayCrossesCell(eye, target, avoidedCell)) continue;
            BlockHitResult hit = c.level.clip(new ClipContext(
                    eye, target, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, c.player));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) {
                return hit;
            }
        }
        return null;
    }

    private static boolean rayCrossesCell(Vec3 from, Vec3 to, BlockPos cell) {
        double distance = from.distanceTo(to);
        int steps = Math.max(2, (int) Math.ceil(distance * 8.0));
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            BlockPos point = BlockPos.containing(
                    from.x + (to.x - from.x) * t,
                    from.y + (to.y - from.y) * t,
                    from.z + (to.z - from.z) * t);
            if (point.equals(cell)) return true;
        }
        return false;
    }

    /** Verify that the requested support face is the first real block hit by the ray. */
    private static BlockHitResult visiblePlacementHit(
            Minecraft c, BlockPos support, Direction face) {
        if (support == null || face == null || !c.level.getBlockState(support).isSolid()) return null;
        BlockHitResult candidate = placementHit(support, face);
        Vec3 eye = c.player.getEyePosition();
        if (eye.distanceToSqr(candidate.getLocation()) > 4.5 * 4.5) return null;
        Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        // Clip slightly inside the support block. Extending along the face normal
        // would leave the endpoint outside the block, so the ray never crosses the face.
        Vec3 end = candidate.getLocation().add(
                -normal.x * 0.02, -normal.y * 0.02, -normal.z * 0.02);
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

    /** Reset every retained action/state when the client world session changes. */
    private static void resetWorldState() {
        FAST.reset();
        AUTO.reset();
        HIT.reset();
        MACRO.reset();
        MACRO_STAGE.reset();
        SAFE.reset();
        DOUBLE.reset();
        DOUBLE_STAGE.reset();
        resetSafeAnchorProgress();
        resetDoubleAnchorProgress();
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
        safeAnchorDetonationAttempts = 0;
        safeAnchorChargeWait = 0;
        safeAnchorChargeSent = false;
        safeAnchorCoverWait = 0;
        safeAnchorViewSaved = false;
        safeAnchorOriginalYaw = 0.0f;
        safeAnchorOriginalPitch = 0.0f;
        SAFE.reset();
    }

    /** A fresh Double Anchor sequence starts when the module is turned off or world changes. */
    private static void resetDoubleAnchorProgress() {
        doubleAnchorPass = 0;
        doubleAnchorDone = false;
        doubleAnchorPhase = DoubleAnchorPhase.WAIT_FIRST_ANCHOR;
        doubleAnchorPos = null;
        doubleAnchorWaitTicks = 0;
        doubleAnchorChargeAttempts = 0;
        doubleAnchorDetonationAttempts = 0;
        doubleAnchorPlacementAttempts = 0;
        DOUBLE.reset();
        DOUBLE_STAGE.reset();
    }
}
