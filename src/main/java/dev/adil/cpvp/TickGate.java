package dev.adil.cpvp;

import java.util.Random;

/**
 * Tick-based rate limiter with no Minecraft dependencies.
 *
 * <p>A delay of {@code N} ticks is the interval between performed actions at 20 TPS:
 * 0 and 1 both allow the next available client tick, 2 leaves one full tick between
 * actions, and so on. When min != max a value is rolled once per performed action.
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
        int delay = min == max ? min : min + random.nextInt(max - min + 1);
        // ready() is called once per client tick. Subtract one because the next
        // available tick is already one tick after the action that started the gate.
        remaining = Math.max(0, delay - 1);
    }

    public void reset() { remaining = 0; }

    public int remaining() { return remaining; }

    private static int clamp(int v) { return Math.max(0, Math.min(MAX_TICKS, v)); }
}
