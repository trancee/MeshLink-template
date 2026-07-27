# Noise Replay Window Size

**Status:** Locked — 2026-07-27

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
// Per Noise session, track received nonces
class ReplayWindow {
    private var window: ULong = 0u  // 64-bit bitmap
    private var baseNonce: Long = 0L  // Window start (lowest nonce in window)

    /**
     * Check if a nonce has been seen and update the window.
     * Returns true if the nonce is new (not a replay), false if replay detected.
     */
    fun checkNonce(nonce: Long): Boolean {
        if (nonce < baseNonce) {
            // Nonce is behind the window — replay
            return false
        }
        if (nonce >= baseNonce + 64) {
            // Nonce is beyond the window — advance window
            val shift = (nonce - baseNonce - 63).toInt()
            window = window shl shift
            baseNonce = nonce - 63
        }
        val bitIndex = (nonce - baseNonce).toInt()
        val mask = 1uL shl bitIndex
        if (window and mask != 0uL) {
            // Bit already set — replay
            return false
        }
        window = window or mask
        return true
    }
}
```

### Failure Mode

If a replay is detected, the receiving peer MUST:

1. Drop the frame
2. Emit a `DecryptFailureReason.REPLAY_DETECTED` diagnostic event
3. NOT respond (silent drop — no alert that could be used for amplification attacks)

### When to Revisit

- If the BLE mesh supports session lifetimes longer than 2 minutes with sustained message rates exceeding 64 nonces/minute
- If a future security analysis shows that 64 bits is insufficient for the expected threat model
- If RFC 9147 is updated with a larger default window size

## Related

- [DTLS 1.3 Spec (RFC 9147)](../../../docs/rfcs/replay/rfc9147.txt)
- [Crypto Design ADR](crypto-design.md) — fail-closed rules
- [Noise Protocol Framework Skill](../../../.agents/skills/noise-protocol-framework/SKILL.md)
