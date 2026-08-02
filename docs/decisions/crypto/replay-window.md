# Noise Replay Window Size

**Status:** Locked — 2026-07-27 (Updated 2026-07-27 — fixed bitmap shift direction, added epoch support and deprotect-before-advance)

> Normative algorithm, parameters, and behavior live in [SPEC.md §7.5](../../../SPEC.md#replay-window) and [ReplayWindow.kt](../../../meshlink/src/commonMain/kotlin/ch/trancee/meshlink/security/ReplayWindow.kt). This record explains the design rationale.

## Why 64-bit sliding window

**Decision:** Use a 64-bit sliding window (1 bit per message nonce) for replay protection on both the hop-by-hop and end-to-end Noise layers.

**Rationale:**

1. **Conservative but bounded**: 64 bits = 64 message nonces tracked. This handles normal mesh traffic (messages typically seconds apart) while keeping memory usage minimal (8 bytes per session).

2. **DTLS 1.3 precedent**: RFC 9147 §4.2 uses a 64-bit window as the default anti-replay window. This is the standard reference value for similar protocols.

3. **BLE mesh context**: BLE mesh messages are high-latency (100ms–1s per hop), so 64 nonces covers ~1–2 minutes of traffic per session. If a session exceeds this window, the oldest entries are evicted (sliding window advances).

4. **Memory budget**: 8 bytes per active session × 8 peers (typical) = 64 bytes total. Negligible.

## Why right-shift (not left-shift) for window advance

**Decision:** When `baseNonce` advances, the bitmap is right-shifted (`ULong.shr`).

**Rationale:** When `baseNonce` increases, existing nonces move to **lower** bit positions. For example, nonce at old position 63, after base advances by 1, moves to new position 62. Left-shift (`shl`) would place phantom bits at wrong positions — setting nonce 0 (bit 0) then advancing would create a phantom at nonce 2 (bit 1 after `shl 1`), causing false replay rejections. Right-shift correctly maps old bits to their new positions; bits shifted off the low end are evicted.

## Why deprotect-before-advance (RFC 9147 §4.2)

**Decision:** The replay check (bit already set) is computed **before** any bitmap mutation.

**Rationale:** This ensures replayed and fresh nonces within the window follow the same execution path and do not leak timing information. A replayed nonce returns `false` without mutation; a fresh nonce mutates only after the check passes. Nonces beyond the window cannot be replays by construction, so the bitmap is advanced and the bit set atomically.

## Why per-epoch numbering with epoch increment on KeyUpdate

**Decision:** Each successful fresh IK renewal increments the local epoch and clears the new epoch's bitmap. Record lookup selects the authenticated epoch before consulting its replay window.

**Rationale:** Epoch separation prevents old-epoch records from being misinterpreted as fresh in the new window. Clearing the bitmap on epoch advance avoids false replay rejections on the first records of the new epoch. Record lookup by authenticated epoch ensures a record's epoch is validated before its nonce is checked.

## Why silent drop on replay detection

**Decision:** On replay detection, the receiving peer drops the frame, emits a `DecryptFailureReason.REPLAY_DETECTED` diagnostic event, and does NOT respond.

**Rationale:** Responding to replays (even with an alert) enables amplification attacks — an attacker could inject a replayed frame and cause the victim to send a response, amplifying traffic. Silent drop denies the attacker any signal. The diagnostic event provides observability for the operator without network amplification.

## When to revisit

- If BLE mesh supports session lifetimes longer than 2 minutes with sustained message rates exceeding 64 nonces/minute
- If future security analysis shows 64 bits is insufficient for the expected threat model
- If RFC 9147 is updated with a larger default window size

## Related

- [DTLS 1.3 Spec (RFC 9147)](../../../docs/rfcs/replay/rfc9147.txt)
- [Crypto Design ADR](crypto-design.md) — fail-closed rules
- [Noise Protocol Framework Skill](../../../.agents/skills/noise-protocol-framework/SKILL.md)
- [SPEC.md §7.5](../../../SPEC.md#replay-window)
- [ReplayWindow.kt](../../../meshlink/src/commonMain/kotlin/ch/trancee/meshlink/security/ReplayWindow.kt)
