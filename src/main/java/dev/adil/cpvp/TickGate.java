package dev.adil.cpvp;

import java.util.Random;

/**
 * Tick-based rate limiter with no Minecraft dependencies.
 *
 * <p>A delay of {@code N} ticks means "skip N client ticks after each performed action":
 * 0 acts on every tick, 1 acts every 2nd tick, and so on. When min != max a value inside
 * the inclusive range is rolled <em>once per performed action</em>.
 *
 * <p>Usage per tick: {@code if (gate.ready() && doAction()) gate.start(min, max);}
 * The gate only counts down while it is being queried, so it never advances while a
 * module is idle.
 */
public final class TickGate {
    public static final int MAX_TICKS = 5;

    private final Random random;
    private int remaining;

    public TickGate() { this(new Random()); }

    public TickGate(Random random) { this.random = random; }

    /** True when the action may run this tick; otherwise consumes one tick of the delay. */
    public boolean ready() {
        if (remaining > 0) {
            remaining--;
            return false;
        }
        return true;
    }

    /** Starts the delay after an action was really performed. */
    public void start(int minTicks, int maxTicks) {
        int min = clamp(minTicks);
        int max = Math.max(min, clamp(maxTicks));
        remaining = min == max ? min : min + random.nextInt(max - min + 1);
    }

    public void reset() { remaining = 0; }

    public int remaining() { return remaining; }

    private static int clamp(int v) { return Math.max(0, Math.min(MAX_TICKS, v)); }
}
