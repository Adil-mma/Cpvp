package dev.adil.cpvp;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Sword Swap: while the crosshair is on another player, temporarily selects the best
 * hotbar sword when the attack button is pressed with an eligible non-sword item.
 * Crystal, axes and pickaxes are intentionally ignored; the previous slot is restored.
 */
public final class TotemSwapEngine {
    private static boolean wasAttackDown;
    private static long ticks;
    private static long restoreAt = -1;
    private static int originalSlot = -1;
    private static int swordSlot = -1;

    private TotemSwapEngine() {}

    public static void tick(Minecraft c) {
        ticks++;
        if (c.player == null || c.level == null || c.gameMode == null) {
            clearPending();
            wasAttackDown = false;
            return;
        }

        var player = c.player;
        boolean attackDown = c.options.keyAttack.isDown();
        CpvpConfig.Settings s = CpvpConfig.settings();

        if (restoreAt >= 0) {
            int selected = player.getInventory().getSelectedSlot();
            if (selected != swordSlot) {
                // Do not override a hotbar slot the player selected manually.
                clearPending();
            } else if (ticks >= restoreAt) {
                if (originalSlot >= 0) player.getInventory().setSelectedSlot(originalSlot);
                clearPending();
            }
        }

        if (!s.totemSwapEnabled || c.gui.screen() != null) {
            wasAttackDown = attackDown;
            return;
        }

        if (attackDown && !wasAttackDown && restoreAt < 0 && lookingAtAnotherPlayer(c)) {
            int selected = player.getInventory().getSelectedSlot();
            Item held = player.getInventory().getItem(selected).getItem();
            if (!isSwapExempt(held) && swordTier(held) == 0) {
                int foundSword = findBestSword(c);
                if (foundSword >= 0) {
                    originalSlot = selected;
                    swordSlot = foundSword;
                    player.getInventory().setSelectedSlot(foundSword);

                    int min = Math.max(0, Math.min(5, s.totemSwapMinDelayTicks));
                    int max = Math.max(min, Math.min(5, s.totemSwapMaxDelayTicks));
                    int delay = ThreadLocalRandom.current().nextInt(min, max + 1);
                    restoreAt = ticks + delay;
                }
            }
        }
        wasAttackDown = attackDown;
    }

    private static boolean lookingAtAnotherPlayer(Minecraft c) {
        return c.hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof Player target
                && target != c.player
                && target.isAlive();
    }

    private static boolean isSwapExempt(Item item) {
        return item == Items.END_CRYSTAL
                || item == Items.WOODEN_AXE || item == Items.STONE_AXE
                || item == Items.IRON_AXE || item == Items.GOLDEN_AXE
                || item == Items.DIAMOND_AXE || item == Items.NETHERITE_AXE
                || item == Items.WOODEN_PICKAXE || item == Items.STONE_PICKAXE
                || item == Items.IRON_PICKAXE || item == Items.GOLDEN_PICKAXE
                || item == Items.DIAMOND_PICKAXE || item == Items.NETHERITE_PICKAXE;
    }

    private static int findBestSword(Minecraft c) {
        int bestSlot = -1;
        int bestTier = 0;
        for (int slot = 0; slot < 9; slot++) {
            Item item = c.player.getInventory().getItem(slot).getItem();
            int tier = swordTier(item);
            if (tier > bestTier) {
                bestTier = tier;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private static int swordTier(Item item) {
        if (item == Items.NETHERITE_SWORD) return 6;
        if (item == Items.DIAMOND_SWORD) return 5;
        if (item == Items.IRON_SWORD) return 4;
        if (item == Items.STONE_SWORD) return 3;
        if (item == Items.GOLDEN_SWORD) return 2;
        if (item == Items.WOODEN_SWORD) return 1;
        return 0;
    }

    private static void clearPending() {
        restoreAt = -1;
        originalSlot = -1;
        swordSlot = -1;
    }
}
