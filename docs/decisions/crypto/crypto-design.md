# MeshLink Crypto Layer: Consolidated Design Decisions

**Status:** Locked — 2026-07-20

Consolidates five crypto-layer decisions:

1. **E2E Handshake Pattern**: Noise IX (link: XX/IK)
2. **NX Fallback**: Full public key verification + mitigations
3. **Noise Session State Machine**: Timeouts, retries, migration, rekeying
4. **Key Rotation Protocol**: Explicit announcement + seqno reset
5. **E2E Handshake Routing Over Mesh**: IX frames routed via mesh

All layers use the same primitives: X25519 DH, HKDF-SHA256, ChaCha20-Poly1305.

> **Specification content** (tables, state machines, wire formats, parameter values) lives in [SPEC.md §5, §7](../../../SPEC.md). This ADR captures only the *rationale*.

---

## 1. E2E Handshake Pattern: Noise IX (Link Layer: Noise XX/IK)

### Context E2E Handshake Pattern

MeshLink has two encryption layers:

1. **Hop-by-hop link encryption** between adjacent mesh nodes (relays forward without reading)
2. **End-to-end encryption** between message origin and final destination, carried inside link frames

### Decision E2E Handshake Pattern

| Layer | Pattern | Rationale |
|---|---|---|
| Hop-by-hop (first contact) | Noise XX | Mutual authentication for initial TOFU — both parties pin each other |
| Hop-by-hop (post-TOFU reconnect) | Noise IK | Proactive mutual auth + 0-RTT when both hold pinned keys (1 RTT vs XX's 1.5) |
| End-to-end | Noise IX | Origin knows destination key (gossiped); IX uses `es = DH(e, rs)` in msg 1 |

### Why IX for E2E

1. **Key-knowledge asymmetry**: Origin knows destination's key; IX binds handshake to known key in message 1 (`es = DH(e, rs)`)
2. **Proactive 0-RTT authentication**: Origin authenticates to destination in first message without waiting for response
3. **Destination pins origin's identity**: IX transmits origin's static key (`s`, encrypted under `es`) in message 1
4. **Key-rotation robustness**: IX re-sends destination's current static key in message 2

> **IX Flow** (specified in SPEC.md §5.1): `-> e, s, es` / `<- e, ee, se, s`

---

## 2. NX Fallback with Full Public Key Verification

### Context NX Fallback

When destination's public key is unknown, Noise IX cannot proceed. Noise NX provides source authentication level 0 but enables DoS via unauthenticated handshake initiation.

### Decision NX Fallback

Use `Noise_NX_25519_ChaChaPoly_SHA256` when destination key is unknown, **with security mitigations**.

**Key design choice**: NX handshake payload carries the **full 64-byte concatenated public key** (Ed25519Pub \|\| X25519Pub), not the truncated 12-byte `PeerFingerprint`.

### Why Full Public Key in Payload

- `PeerFingerprint` (96-bit truncated SHA-256) has insufficient entropy for identity verification
- Full 64-byte key provides 510 bits effective security (255 + 255)
- Byte-for-byte verification eliminates collision/preimage concerns
- Payload size increase (64 vs 12 bytes) is negligible vs handshake overhead

### When NX Fallback Triggers

1. Cold start discovery (key gossip not yet propagated)
2. Key rotation lag (peer rotated, announcement not received)
3. Network partition (key unavailable due to mesh partition)

**Not triggered by**: Direct attack, key compromise, misconfiguration

### Security Mitigations (Rationale)

| Threat | Mitigation | Why This Works |
|---|---|---|
| DoS via unauthenticated handshakes | Rate limit: 3 attempts/min/destination | Bounds resource exhaustion |
| Resource exhaustion | 10s timeout (vs 30s for IX) | Limits handshake window |
| Wrong-peer handshake | Full public key verification in payload | Validates identity claim cryptographically |
| Silent degradation | Diagnostic flag `fallback_used = true` | Observability without breaking flow |
| NX replay attacks | 32-bit nonce in payload, checked pre-verification | Prevents message replay |

---

## 3. Noise Session State Machine

### Design Principles

- **One session per peer per layer**: At most one `HOP_BY_HOP` and one `END_TO_END` session per `PeerIdentity`
- **Handshake serialization**: New attempts for in-progress peer/layer are queued or rejected
- **Transport migration**: L2CAP CoC availability triggers migration — `transport` updates, no handshake restart, encryption keys continue unchanged. Fallback to GATT on L2CAP failure.

### Timeout & Retry Rationale

| Pattern | Max Retries | Backoff | Reasoning |
|---|---|---|---|
| XX (initiator) | 3 | 1s, 2s, 4s | First contact needs resilience; exponential backoff |
| IK (initiator) | 2 | 1s, 2s | Reconnect with pinned keys should be faster |
| IX (initiator) | 3 | 1s, 2s, 4s | E2E may traverse multiple hops |
| NX (initiator) | 3/min (rate limited) | 1s, 2s, 4s | Rate limit is the primary DoS control |
| Responder (all) | 0 | — | Responders never retry; fail fast to avoid state buildup |

**Rekeying**: Triggered at 2^64−1 messages, 3-day timer, or remote `KeyRotationAnnouncement`. Grace period (default 1h) retains old keys for in-flight sessions.

---

## 4. Key Rotation Protocol

### Design Goals

- **Explicit announcement**: No silent key changes — every rotation is a signed, verifiable event
- **SeqNo reset to 1**: Signals "new crypto era" to routing layer; distinguishes rotation from normal seqno progression
- **Grace period differentiation**: Planned rotations allow overlap; security-event rotations are immediate
- **Active session continuity**: Existing Noise sessions continue with current traffic keys; rotation affects only new sessions

### Why Old Key Signs New Key

- Proves continuity of identity (rotation, not replacement)
- Enables neighbors to verify without TOFU re-pinning
- Sig covers `identityKey || handshakeKey || seqNo(1) || reason` — binds both keys to the rotation event

### Grace Period Rationale

| Rotation Type | Grace Period | Security vs Availability Trade-off |
|---|---|---|
| PERIODIC/MANUAL | 1 hour (configurable) | Allows in-flight sessions to complete; old key still trusted |
| SECURITY_EVENT | 0 (immediate) | Suspected compromise → no window for attacker to use old key |

### Propagation Deadlines

- Direct neighbors: < 1s (single hop, link-layer delivery)
- 2-hop: < 3s (within routing convergence budget)
- Beyond: digest resync handles eventual consistency

---

## 5. E2E Handshake Routing Over Mesh

### Core Principle

When destination is not a direct neighbor or key is unknown, route the E2E handshake through the mesh using the existing routing layer. Relays forward without inspecting E2E payload.

### Security Model

- **Relay confidentiality**: Relays decrypt only link-layer encryption; E2E payload remains opaque
- **No trust in relays**: Compromised relay cannot decrypt E2E content, only observe routing metadata
- **Return path symmetry**: Destination responds via reverse route established by routing layer

### Why Not Separate E2E Routing Protocol

- Reuses existing distance-vector routing (Babel-inspired)
- No additional round trips for route discovery
- Route metrics (RSSI, flags) apply equally to E2E handshake frames
- Simpler implementation, smaller attack surface

---

## Related

- [Routing Design](../routing/routing-design.md) — Routing layer carries E2E handshake frames
- [Transport: MTU](../transport/mtu-negotiation.md) — Bearer selection affects handshake fragmentation
- [SPEC.md §5, §7](../../../SPEC.md) — Full specification (state machines, wire formats, parameters)
