package com.safeanchor;

/**
 * All states of the SafeAnchor one-shot sequence.
 *
 * <p>Normal flow:
 * <pre>
 * IDLE -&gt; PLACE_ANCHOR -&gt; INSERT_GLOWSTONE -&gt; PLACE_PROTECTION_BLOCK
 *      -&gt; SWITCH_TO_DETONATION -&gt; DETONATE -&gt; SWITCH_TO_SAFETY -&gt; FINISHED -&gt; IDLE
 * </pre>
 * Any state can transition to {@code CANCELLED} (which resolves back to
 * {@code IDLE} on the next tick) when a safety check fails.
 */
public enum SafeAnchorState {
    /** Resting state, no sequence running. */
    IDLE,
    /** Select the anchor slot and place the Respawn Anchor at the aimed block. */
    PLACE_ANCHOR,
    /** Insert glowstone into the placed anchor to charge it. */
    INSERT_GLOWSTONE,
    /** Place (or verify) the protection block between player and anchor. */
    PLACE_PROTECTION_BLOCK,
    /** Switch to a slot that cannot charge the anchor (empty hand preferred). */
    SWITCH_TO_DETONATION,
    /** Trigger the charged anchor. */
    DETONATE,
    /** Switch back to the configured safety item (e.g. totem). */
    SWITCH_TO_SAFETY,
    /** Sequence completed successfully (transient, resolves to IDLE). */
    FINISHED,
    /** Sequence was aborted (transient, resolves to IDLE). */
    CANCELLED;

    /**
     * @return true while a sequence is actively stepping through states.
     */
    public boolean isRunningState() {
        return this != IDLE && this != FINISHED && this != CANCELLED;
    }
}
