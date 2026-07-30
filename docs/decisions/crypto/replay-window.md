# Noise Replay Window Size

**Status:** Locked — 2026-07-27 (Updated 2026-07-27 — fixed bitmap shift direction, added epoch support and deprotect-before-advance)

## Context

The Noise protocol provides replay protection via a sliding window mechanism, and RFC 9147 (DTLS 1.3) uses a 64-bit bitmap window for anti-replay. The MeshLink spec references RFC 9147 for replay protection patterns but does not specify the exact window size for the Noise protocol implementation.

## Decision

**Use a 64-bit sliding window (1 bit per message nonce) for replay protection on both the hop-by-hop and end-to-end Noise layers.**

### Rationale

1. **Conservative but bounded**: 64 bits = 64 message nonces tracked. This handles normal mesh traffic (messages are typically seconds apart) while keeping memory usage minimal (8 bytes per session).

2. **DTLS 1.3 precedent**: RFC 9147 §4.2 uses a 64-bit window as the default anti-replay window. This is the standard reference value for similar protocols.

3. **BLE mesh context**: BLE mesh messages are high-latency (100ms-1s per hop), so 64 nonces covers ~1-2 minutes of traffic per session. If a session exceeds this window, the oldest entries are evicted (sliding window advances).

4. **Memory budget**: 8 bytes per active session × 8 peers (typical) = 64 bytes total. Negligible.

### Implementation

```kotlin
public class ReplayWindow {
    public companion object {
        // Window size fixed at 64 per ADR and RFC 9147 default.
        public const val WINDOW_SIZE: Int = 64
    }

    private var _window: ULong = 0uL
    private var _baseNonce: Long = 0L
    private var _epoch: Long = 0L

    /**
     * Check if a nonce has been seen and update the window.
     * Returns true if the nonce is new (not a replay), false if replay detected.
     *
     * Implements RFC 9147 §4.2 "deprotect-before-advance": the replay check
     * is performed before any bitmap mutation, so replayed and fresh nonces
     * within the window follow the same execution path and do not leak timing
     * information.
     *
     * When the nonce falls beyond the current window, the bitmap is right-shifted
     * (ULong.shr) so that existing bit positions correctly map to their nonces
     * under the new baseNonce, and bits shifted off the low end are naturally
     * evicted.
     */
    fun consumeNonce(nonce: Long): Boolean {
        require(nonce >= 0) { "Nonce must be non-negative" }

        // Nonces behind the window are always replays.
        if (nonce < _baseNonce) return false

        val bitIndex = (nonce - _baseNonce).toInt()

        if (bitIndex < WINDOW_SIZE) {
            // Within window — deprotect-before-advance.
            val mask = 1uL shl bitIndex
            if (_window and mask != 0uL) return false   // replay
            _window = _window or mask                    // accept
            return true
        }

        // Beyond window — advance via right-shift, then accept.
        // When shift >= WINDOW_SIZE, Kotlin's shr masks the distance to 6 bits,
        // so shr 64 == shr 0 and old bits would survive. Guard explicitly.
        val shift = bitIndex - WINDOW_SIZE + 1
        val clearedWindow = if (shift >= WINDOW_SIZE) 0uL else _window shr shift
        _window = clearedWindow or (1uL shl (WINDOW_SIZE - 1))
        _baseNonce = nonce - WINDOW_SIZE + 1
        return true
    }

    /** Called on KeyUpdate: increments epoch and clears the bitmap. */
    fun advanceEpoch() {
        _epoch++
        _window = 0uL
        _baseNonce = 0L
    }

    fun reset() {
        _window = 0uL
        _baseNonce = 0L
        _epoch = 0L
    }
}
```

### Critical bug fix (applied during implementation)

The initial specification used `ULong.shl shift` to advance the bitmap, which shifts existing bits to **higher** positions. This is incorrect: when `baseNonce` increases, existing nonces move to **lower** bit positions (e.g., nonce at old position 63, after base advances by 1, moves to new position 62). The correct operation is `ULong.shr shift` (right shift), and the initial specification has been corrected to reflect this. A left-shift caused phantom bits at wrong positions — for example, setting nonce 0 (bit 0) then advancing the window would place a phantom at nonce 2 (bit 1 after shl 1), causing false replay rejections.

### Shift-distance guard (applied during implementation)

When the nonce jumps far beyond the window (`shift >= 64`), Kotlin's `ULong.shr` masks the shift distance to 6 bits (per the JVM/Native `Long.ushr` semantics), so `shr 64 == shr 0` and old bits survive instead of being evicted. The implementation guards explicitly: when `shift >= WINDOW_SIZE`, the entire bitmap is cleared to `0uL` before setting the apex bit.

### Deprotect-before-advance (RFC 9147 §4.2)

The `consumeNonce` function computes the replay check (bit already set) **before** it mutates the bitmap. This means:

- A replayed nonce within the window returns `false` without any bitmap mutation.
- A fresh nonce within the window mutates the bitmap only after the check passes.
- Nonces that fall beyond the window cannot be replays by construction (they were never tracked), so the bitmap is advanced and the bit set atomically.

### Epoch support (spec §7.5 "per-epoch numbering")

Each `advanceEpoch()` call increments an internal epoch counter and clears the bitmap. Nonces from a previous epoch are no longer tracked in the cleared window, so they are accepted as fresh in the new epoch. This matches the Noise protocol's rekey behavior where `KeyUpdate` resets the nonce space.

### Failure Mode

If a replay is detected, the receiving peer MUST:

- Drop the frame
- Emit a `DecryptFailureReason.REPLAY_DETECTED` diagnostic event
- NOT respond (silent drop — no alert that could be used for amplification attacks)

### When to Revisit

- If the BLE mesh supports session lifetimes longer than 2 minutes with sustained message rates exceeding 64 nonces/minute
- If a future security analysis shows that 64 bits is insufficient for the expected threat model
- If RFC 9147 is updated with a larger default window size

## Related

- [DTLS 1.3 Spec (RFC 9147)](../../../docs/rfcs/replay/rfc9147.txt)
- [Crypto Design ADR](crypto-design.md) — fail-closed rules
- [Noise Protocol Framework Skill](../../../.agents/skills/noise-protocol-framework/SKILL.md)
