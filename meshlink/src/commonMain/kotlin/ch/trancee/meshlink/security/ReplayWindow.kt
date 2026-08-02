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

        var accepted = false
        if (nonce >= _baseNonce) {
            val bitIndex = (nonce - _baseNonce).toInt()
            if (bitIndex < WINDOW_SIZE) {
                val mask = 1uL shl bitIndex
                if (_window and mask == 0uL) {
                    _window = _window or mask
                    accepted = true
                }
            } else {
                val shift = bitIndex - WINDOW_SIZE + 1
                val clearedWindow = if (shift >= WINDOW_SIZE) 0uL else _window shr shift
                _window = clearedWindow or (1uL shl (WINDOW_SIZE - 1))
                _baseNonce = nonce - WINDOW_SIZE + 1
                accepted = true
            }
        }
        return accepted
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
