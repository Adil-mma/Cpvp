package dev.adil.cpvp;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Temporarily swaps from the selected Totem of Undying to a hotbar sword
 * when the attack button is pressed, then restores the original totem slot.
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
                // The player changed their hotbar selection manually. Do not
                // override their choice when the temporary swap timer expires.
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

        // Trigger on the press edge, not every client tick while the button is held.
        if (attackDown && !wasAttackDown && restoreAt < 0) {
            int selected = player.getInventory().getSelectedSlot();
            if (player.getInventory().getItem(selected).is(Items.TOTEM_OF_UNDYING)) {
                int foundSword = findBestSword(c);
                if (foundSword >= 0) {
                    originalSlot = selected;
                    swordSlot = foundSword;
                    player.getInventory().setSelectedSlot(foundSword);

                    int min = Math.max(0, Math.min(5, s.totemSwapMinDelayTicks));
                    int max = Math.max(min, Math.min(5, s.totemSwapMaxDelayTicks));
                    int delay = ThreadLocalRandom.current().nextInt(min, max + 1);
                    // Restoration is checked on the next client tick, so even a
                    // configured 0-tick delay permits the current attack tick.
                    restoreAt = ticks + delay;
                }
            }
        }
        wasAttackDown = attackDown;
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
