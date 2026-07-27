package ch.trancee.meshlink.security

/**
 * 64-bit sliding window for Noise protocol anti-replay protection.
 *
 * Tracks received message nonces using a 64-bit bitmap. A nonce is rejected if its bit is already
 * set (replay) or if it's behind the window base.
 *
 * See docs/decisions/crypto/replay-window.md.
 *
 * SPEC-ANCHOR: replay-window
 */
public class ReplayWindow {
    private var _window: ULong = 0uL
    private var _baseNonce: Long = 0L

    /** Lowest nonce currently in the window. */
    public val baseNonce: Long
        get() = _baseNonce

    /** Current 64-bit bitmap of seen nonces. */
    public val window: ULong
        get() = _window

    /**
     * Check if a nonce has been seen and update the window. Returns true if the nonce is new (not a
     * replay), false if replay detected.
     */
    public fun checkNonce(nonce: Long): Boolean {
        if (nonce < _baseNonce) {
            return false
        }
        if (nonce >= _baseNonce + 64) {
            val shift = (nonce - _baseNonce - 63).toInt()
            _window = _window shl shift
            _baseNonce = nonce - 63
        }
        val bitIndex = (nonce - _baseNonce).toInt()
        val mask = 1uL shl bitIndex
        if (_window and mask != 0uL) {
            return false
        }
        _window = _window or mask
        return true
    }

    /** Resets the window to its initial state. */
    public fun reset() {
        _window = 0uL
        _baseNonce = 0L
    }
}
