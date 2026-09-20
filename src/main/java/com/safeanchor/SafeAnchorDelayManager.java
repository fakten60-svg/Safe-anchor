package com.safeanchor;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Random per-transition delay manager.
 *
 * <p>A new random delay in {@code [minDelayMs, maxDelayMs]} is rolled exactly
 * once per state transition via {@link #scheduleNextAction(int, int)}. Time is
 * measured with the monotonic {@link System#nanoTime()} clock so system clock
 * changes cannot break the sequence.
 *
 * <p>Note: Minecraft runs at ~20 TPS (about 50 ms per tick), so a 60-70 ms
 * delay is honored on tick granularity, never with millisecond precision.
 */
public class SafeAnchorDelayManager {

    private static final long MS_TO_NANOS = 1_000_000L;

    private int currentDelayMs;
    private long nextActionTimeNanos;
    private boolean waiting;

    /**
     * Rolls one random delay for the upcoming transition and arms the timer.
     */
    public void scheduleNextAction(int minDelayMs, int maxDelayMs) {
        int min = Math.min(minDelayMs, maxDelayMs);
        int max = Math.max(minDelayMs, maxDelayMs);
        if (min < 0) {
            min = 0;
        }
        this.currentDelayMs = (min == max) ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
        this.nextActionTimeNanos = System.nanoTime() + (long) this.currentDelayMs * MS_TO_NANOS;
        this.waiting = true;
    }

    /**
     * @return true when a delay was scheduled and its target time was reached.
     */
    public boolean isReady() {
        return waiting && System.nanoTime() >= nextActionTimeNanos;
    }

    public boolean isWaiting() {
        return waiting;
    }

    public int getCurrentDelayMs() {
        return currentDelayMs;
    }

    /**
     * @return remaining milliseconds until the scheduled action may run.
     */
    public long getRemainingMs() {
        if (!waiting) {
            return 0;
        }
        return Math.max(0, (nextActionTimeNanos - System.nanoTime()) / MS_TO_NANOS);
    }

    /**
     * Clears any pending delay (used on finish/cancel).
     */
    public void reset() {
        currentDelayMs = 0;
        nextActionTimeNanos = 0;
        waiting = false;
    }
}
