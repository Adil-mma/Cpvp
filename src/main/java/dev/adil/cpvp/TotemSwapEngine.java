package dev.adil.cpvp;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility swap: when attack is pressed while the main hand contains a totem,
 * briefly select a sword, then restore the user's previous totem slot.
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
        if (restoreAt >= 0 && ticks >= restoreAt) {
            if (player.getInventory().getSelectedSlot() == swordSlot && originalSlot >= 0) {
                player.getInventory().setSelectedSlot(originalSlot);
            }
            clearPending();
        }

        boolean attackDown = c.options.keyAttack.isDown();
        CpvpConfig.Settings s = CpvpConfig.settings();
        if (!s.totemSwapEnabled || c.gui.screen() != null) {
            wasAttackDown = attackDown;
            return;
        }

        if (attackDown && !wasAttackDown && restoreAt < 0) {
            int selected = player.getInventory().getSelectedSlot();
            if (player.getInventory().getItem(selected).is(Items.TOTEM_OF_UNDYING)) {
                int foundSword = findSword(c);
                if (foundSword >= 0) {
                    originalSlot = selected;
                    swordSlot = foundSword;
                    player.getInventory().setSelectedSlot(foundSword);
                    int min = s.totemSwapMinDelayTicks;
                    int max = s.totemSwapMaxDelayTicks;
                    int delay = ThreadLocalRandom.current().nextInt(min, max + 1);
                    restoreAt = ticks + Math.max(1, delay);
                }
            }
        }
        wasAttackDown = attackDown;
    }

    private static int findSword(Minecraft c) {
        for (int slot = 0; slot < 9; slot++) {
            if (c.player.getInventory().getItem(slot).getItem() instanceof SwordItem) return slot;
        }
        return -1;
    }

    private static void clearPending() {
        restoreAt = -1;
        originalSlot = -1;
        swordSlot = -1;
    }
}
