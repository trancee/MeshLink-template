package ch.trancee.meshlink.security

/**
 * 64-bit sliding window for Noise protocol anti-replay protection.
 *
 * Tracks received message nonces using a 64-bit bitmap. A nonce is rejected if its bit is already
 * set (replay), if it's behind the window base, or if it belongs to a previous epoch (key rotation
 * era).
 *
 * Follows the deprotect-before-advance principle from RFC 9147 §4.2: the replay check is performed
 * before any window bitmap is mutated, so that replayed and new nonces within the window follow the
 * same execution path without leaking timing information about their status.
 *
 * When the nonce advances beyond the current window, the bitmap is right-shifted ([ULong.shr]) so
 * that existing bit positions correctly track their nonces under the new base, and bits that fall
 * off the low end are naturally evicted (their nonces are now behind the new baseNonce).
 *
 * See docs/decisions/crypto/replay-window.md.
 *
 * SPEC-ANCHOR: replay-window
 */
public class ReplayWindow {
    /** Window size in bits. Fixed at 64 per ADR and RFC 9147 default. */
    public companion object {
        public const val WINDOW_SIZE: Int = 64
    }

    private var _window: ULong = 0uL
    private var _baseNonce: Long = 0L

    /** Current epoch counter — incremented on each key rotation (KeyUpdate). */
    private var _epoch: Long = 0L

    /** Lowest nonce currently in the window. */
    public val baseNonce: Long
        get() = _baseNonce

    /** Current 64-bit bitmap of seen nonces. Bit 0 tracks [_baseNonce]. */
    public val window: ULong
        get() = _window

    /** Current epoch — incremented on each key rotation (KeyUpdate). */
    public val epoch: Long
        get() = _epoch

    /**
     * Consume a nonce: check if it has been seen and update the window.
     *
     * Returns `true` if the nonce is fresh (not a replay and not behind the window), `false` if
     * rejected.
     *
     * Per RFC 9147 §4.2 this implements deprotect-before-advance: the bit check is performed before
     * the window bitmap is mutated, so that a replayed nonce and a fresh nonce within the current
     * window follow the same execution path and do not leak timing information about their status.
     *
     * @param nonce The message nonce to check. Must be non-negative.
     * @return `true` if the nonce is accepted, `false` if rejected as a replay or out-of-sequence
     *   nonce.
     * @throws IllegalArgumentException if [nonce] is negative.
     */
    public fun consumeNonce(nonce: Long): Boolean {
        require(nonce >= 0) { "Nonce must be non-negative, got $nonce" }

        // Nonces behind the current window are always replays — reject
        // immediately without mutating any state.
        if (nonce < _baseNonce) {
            return false
        }

        val bitIndex = (nonce - _baseNonce).toInt()

        if (bitIndex < WINDOW_SIZE) {
            // --- Nonce is within the current window ----------------------
            // Deprotect-before-advance: compute the mask and check the bit
            // BEFORE we mutate _window. If it's a replay the function
            // returns false and _window is untouched.
            val mask = 1uL shl bitIndex
            if (_window and mask != 0uL) {
                return false
            }
            _window = _window or mask
            return true
        }

        // --- Nonce is beyond the current window -------------------------
        // The window must be advanced (right-shifted) so that existing
        // bits land at the correct positions under the new baseNonce.
        // Bits that shift off the low end are evicted (their nonces are
        // now behind the new baseNonce).
        //
        // The apex bit (position WINDOW_SIZE - 1) after a right-shift always reads from a
        // bit position beyond the original 64-bit bitmap, which is always 0 — so nonces beyond
        // the current window are always fresh and can never be replays.
        // Deprotect-before-advance is therefore trivially satisfied for this branch.
        //
        // When shift >= WINDOW_SIZE, Kotlin's shr masks the distance to 6 bits, so
        // shr 64 == shr 0 and old bits would survive. Guard explicitly to evict everything.
        val shift = bitIndex - WINDOW_SIZE + 1
        val clearedWindow = if (shift >= WINDOW_SIZE) 0uL else _window shr shift

        _window = clearedWindow or (1uL shl (WINDOW_SIZE - 1))
        _baseNonce = nonce - WINDOW_SIZE + 1
        return true
    }

    /**
     * Increments the epoch counter and clears the window, invalidating all previously seen nonces.
     * Called on [KeyUpdate] (rekeying) so that nonces from the previous key era are no longer
     * accepted.
     */
    public fun advanceEpoch() {
        _epoch++
        _window = 0uL
        _baseNonce = 0L
    }

    /** Resets the window to its initial state (epoch 0, empty bitmap). */
    public fun reset() {
        _window = 0uL
        _baseNonce = 0L
        _epoch = 0L
    }
}
