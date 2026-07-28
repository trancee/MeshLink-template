# MeshLink Crypto Layer: Consolidated Design Decisions

**Status:** Locked — 2026-07-20

Consolidates five crypto-layer decisions:

1. **E2E Handshake Pattern**: Noise IX (link: XX/IK)
2. **NX Fallback**: Full public key verification + mitigations
3. **Noise Session State Machine**: Timeouts, retries, migration, rekeying
4. **Key Rotation Protocol**: Explicit announcement + seqno reset
5. **E2E Handshake Routing Over Mesh**: IX frames routed via mesh

All layers use the same primitives: X25519 DH, HKDF-SHA256, ChaCha20-Poly1305.

---

## 1. E2E Handshake Pattern: Noise IX (Link Layer: Noise XX/IK)

### 1.1 Context

MeshLink has two encryption layers:

1. **Hop-by-hop link encryption** between adjacent mesh nodes (relays forward without reading).
2. **End-to-end encryption** between message origin and final destination, carried inside link frames.

### 1.2 Decision

| Layer | Pattern | Protocol Name |
|---|---|---|
| Hop-by-hop link (first contact) | Noise XX | `Noise_XX_25519_ChaChaPoly_SHA256` |
| Hop-by-hop link (post-TOFU reconnect) | Noise IK | `Noise_IK_25519_ChaChaPoly_SHA256` |
| End-to-end (origin → destination) | Noise IX | `Noise_IX_25519_ChaChaPoly_SHA256` |

Both layers use the same primitives (X25519 DH, HKDF-SHA256, ChaCha20-Poly1305) and the same fail-closed crypto contract.

### 1.3 Why IX for End-to-End

1. **Key-knowledge asymmetry:** Origin knows destination's key (gossiped); IX uses `es = DH(e, rs)` in message 1.
2. **Proactive 0-RTT authentication:** Origin binds handshake to known destination key in message 1.
3. **Destination pins origin's identity:** IX transmits origin's static key (`s`, encrypted under `es`) in message 1.
4. **Key-rotation robustness:** IX re-sends destination's current static key in message 2.

**IX Handshake Flow:**

```text
Noise_IX_25519_ChaChaPoly_SHA256
  -> e, s, es      (origin: ephemeral, static, DH(e, rs))
  <- e, ee, se, s  (destination: ephemeral, DH(e,e), DH(s,e), static)
```

---

## 2. NX Fallback with Full Public Key Verification

### 2.1 Context

When destination's public key is unknown, Noise IX cannot proceed. Noise NX provides source authentication level 0 but enables DoS via unauthenticated handshake initiation.

### 2.2 Decision

Use `Noise_NX_25519_ChaChaPoly_SHA256` when destination key is unknown, with security mitigations.

**Key Change:** NX handshake payload carries the **full 64-byte concatenated public key** (Ed25519Pub || X25519Pub), not the truncated 12-byte `PeerFingerprint`.

### 2.3 When NX Fallback Triggers

1. **Cold start discovery:** New peer discovered but key gossip not yet propagated
2. **Key rotation lag:** Peer rotated key but announcement not received
3. **Network partition:** Key unavailable due to mesh partition

**Not triggered by:** Direct attack, key compromise, or misconfiguration.

### 2.4 Security Mitigations

| Threat | Mitigation | Rationale |
|---|---|---|
| DoS via unauthenticated handshakes | Rate limit: max 3 NX attempts/min per destination | Prevents resource exhaustion |
| Resource exhaustion | 10s timeout vs 30s for IX | Limits handshake window |
| Wrong-peer handshake | Full public key verification in payload | Validates identity claim |
| Silent degradation | Diagnostic flag: `fallback_used = true` | Observability |
| NX replay attacks | 32-bit nonce in payload, checked before key verification | Prevents message replay |

### 2.5 Public Key Verification

NX payload includes full 64-byte concatenated public key:

| Field | Size | Description |
|---|---|---|
| Ed25519PublicKey | 32 bytes | Identity/signing key |
| X25519PublicKey | 32 bytes | DH key for Noise handshakes |
| **Total** | **64 bytes** | Verified byte-for-byte |

Verification: `received_ed25519 == expected_ed25519 AND received_x25519 == expected_x25519`

**Entropy:** 255 bits (Ed25519) + 255 bits (X25519) = 510 bits effective security.

### 2.6 Diagnostic Contract

```yaml
handshake:
  protocol: "Noise_NX_25519_ChaChaPoly_SHA256"
  fallback_used: true
  full_public_key_verified: true
  rate_limit_attempts: 1
  nonce_replay_detected: false
```

---

## 3. Noise Session State Machine

### 3.1 Session States

```kotlin
enum class NoiseSessionState {
    DISCONNECTED, HANDSHAKING_XX, HANDSHAKING_IK,
    HANDSHAKING_IX, HANDSHAKING_NX,
    ESTABLISHED, REKEYING, FAILED(reason: NoiseFailureReason)
}
```

**Layers:** `HOP_BY_HOP` (link-layer, XX/IK) and `END_TO_END` (IX/NX).  
**Roles:** `INITIATOR` / `RESPONDER`.

### 3.2 State Transitions

#### 3.2.1 Peer Layer (HOP_BY_HOP)

```text
DISCONNECTED → HANDSHAKING_XX: no TrustRecord exists
DISCONNECTED → HANDSHAKING_IK: TrustRecord exists (post-TOFU reconnect)
HANDSHAKING_XX/IK → ESTABLISHED: handshakeComplete(pinned = true)
HANDSHAKING_XX/IK → FAILED: timeout / malformed / keyMismatch / transportClosed
ESTABLISHED → REKEYING: keyRotationTriggered() OR rekeyThresholdReached()
ESTABLISHED → DISCONNECTED: transportClosed()
REKEYING → ESTABLISHED: rekeyComplete()
REKEYING → FAILED: rekeyRejected() OR timeout
FAILED → DISCONNECTED: cleanup()
```

#### 3.2.2 Mesh Layer (END_TO_END)

```text
DISCONNECTED → HANDSHAKING_IX: destination key known
DISCONNECTED → HANDSHAKING_NX: destination key unknown (fallback)
HANDSHAKING_IX/NX → ESTABLISHED: handshakeComplete()
HANDSHAKING_IX/NX → FAILED: timeout / malformed / keyMismatch / rateLimit
ESTABLISHED → REKEYING: keyRotationTriggered()
ESTABLISHED → DISCONNECTED: sessionExpired() OR explicitClose()
REKEYING → ESTABLISHED: rekeyComplete()
REKEYING → FAILED: rekeyRejected() OR timeout
FAILED → DISCONNECTED: cleanup()
```

### 3.3 Timeouts & Retries

| Transition | Timeout | Action on Expiry |
|---|---|---|
| DISCONNECTED → HANDSHAKING_XX/IK | 10s | `FAILED(HANDSHAKE_TIMEOUT)` |
| DISCONNECTED → HANDSHAKING_IX | 10s | `FAILED(HANDSHAKE_TIMEOUT)` |
| DISCONNECTED → HANDSHAKING_NX | 10s | `FAILED(HANDSHAKE_TIMEOUT)` |
| ESTABLISHED → REKEYING | 30s | `FAILED(HANDSHAKE_TIMEOUT)` |
| REKEYING → ESTABLISHED | 30s | `FAILED(HANDSHAKE_TIMEOUT)` |

| Pattern | Max Retries | Backoff |
|---|---|---|
| XX (initiator) | 3 | 1s, 2s, 4s |
| IK (initiator) | 2 | 1s, 2s |
| IX (initiator) | 3 | 1s, 2s, 4s |
| NX (initiator) | 3/min (rate limited) | 1s, 2s, 4s |
| Responder (all) | No retry | Fail immediately |

### 3.4 Concurrency & Transport Migration

1. **One session per peer per layer** — at most one `HOP_BY_HOP` and one `END_TO_END` session per `PeerIdentity`.
2. **Handshake serialization** — new handshake attempts for an in-progress peer/layer are queued or rejected.
3. **Transport migration** — when L2CAP CoC becomes available, the `HOP_BY_HOP` session migrates: `transport` updates, no handshake restart, encryption keys continue unchanged. If L2CAP fails, fall back to GATT.

### 3.5 Key Rotation (Rekeying)

Triggered by: message count reaches 2^64−1, local key rotation timer (default 3 days), or remote `KeyRotationAnnouncement`.

Process: initiator sends `RekeyMessage` → both sides enter `REKEYING` → on success: new `trafficKey`, `chainingKey`, `messageNonce = 0`. Old keys retained for `gracePeriod` (default 1 hour).

### 3.6 Failure Handling

| Failure | Link Layer | E2E Layer |
|---|---|---|
| `HANDSHAKE_TIMEOUT` | Retry per table | Retry per table |
| `REMOTE_STATIC_KEY_MISMATCH` | `FAILED` → notify TrustStore (TOFU violation) | `FAILED` → routing layer: key unknown |
| `TRANSPORT_CLOSED` | `FAILED` → peer marked DISCONNECTED | `FAILED` → transfer layer: unreachable |
| `REKEY_REJECTED` | Stay ESTABLISHED with old keys | Stay ESTABLISHED with old keys |
| `MAX_RETRIES_EXCEEDED` | `FAILED` → backoff 60s | `FAILED` → backoff 60s |

### 3.7 Diagnostics

Each state transition emits `DiagnosticEvent.NoiseSessionTransition` with fields: `sessionId`, `peerId`, `layer`, `fromState`, `toState`, `role`, `handshakePattern`, `failureReason`, `timestamp`.

---

## 4. Key Rotation Protocol

### 4.1 Trigger

1. Periodic timer (default: **3 days**)
2. Manual API: `meshLink.rotateIdentity()`
3. Security event (compromise detection via diagnostics)

### 4.2 Configuration

```kotlin
meshLinkConfig {
  keyRotation {
    interval = Duration.ofDays(1)           // Override default 3 days
    rotationGracePeriod = Duration.ofHours(1) // Accept old key after planned rotation
    compromiseGracePeriod = Duration.ZERO     // Immediate revocation for security events
  }
}
```

### 4.3 Wire Protocol

```flatbuffers
KeyRotationAnnouncement {
  identityKey: CryptoKey (NEW public key)
  seqNo: UInt (always 1 — new identity)
  signature: ByteArray (Ed25519 signature with OLD private key)
  reason: KeyRotationReason (PERIODIC, MANUAL, SECURITY_EVENT)
}
```

### 4.4 Neighbor Behavior

1. Verify signature with OLD known key (must exist for valid rotation)
2. Accept new key into TrustStore
3. SeqNo resets to 1 (new crypto era)
4. Old key retained for grace period verification

### 4.5 SeqNo Management During Rotation

- **PeerIdentity** (stable) — unchanged across rotations
- **PublicKey** (volatile) — changes on key rotation
- **SeqNo** (route metric) — resets to 1 to signal "new crypto era"

### 4.6 Grace Period

| Rotation Type | Grace Period | Old Key |
|---|---|---|
| PERIODIC or MANUAL | `rotationGracePeriod` (default 1 hour) | Accepted for in-flight sessions |
| SECURITY_EVENT | `compromiseGracePeriod` (default ZERO) | Rejected immediately |

### 4.7 Failure Modes

| Scenario | Detection | Recovery |
|---|---|---|
| Malicious rotation (wrong signature) | Signature verification fails | Reject, report to diagnostics |
| Stale key announcement | SeqNo >= current known | Treat as replay, ignore |
| Missing rotation announcement | Key mismatch on connection | Fall back to trust reset |
| Partial mesh propagation | Some neighbors have key, others don't | Digest mismatch triggers full sync |
| Old key lost before rotation | Cannot verify signature | Rotation fails, key unchanged |

### 4.8 Propagation Deadline

- Direct neighbors: < 1 second
- 2-hop: < 3 seconds (route convergence budget)
- Beyond 2-hop: handled by digest resync

---

## 5. E2E Handshake Routing Over Mesh

### 5.1 Problem

When destination is not a direct neighbor or key is unknown, the E2E Noise IX handshake must be routed through the mesh.

### 5.2 Core Principle

When the destination's key is unknown or the destination is not a direct neighbor, route the E2E handshake through the mesh to the destination, using the existing routing layer.

### 5.3 Handshake Flow

```text
Phase 1: Link Setup (Noise_XX_25519_ChaChaPoly_SHA256)
Origin --(GATT/L2CAP)--> Relay(s) --> Destination

Phase 2: E2E Handshake Routing
Origin wraps IX_Msg1 in a RoutingFrame:
  RoutingFrame {
    destination: destination.peerIdentity,
    payload: IX_Msg1_encrypted,
    hopLimit: UByte
  }

Relay(s) decrypt hop layer → re-encrypt → forward without inspecting E2E payload

Phase 3: Destination responds with IX_Msg2 wrapped for return path

Phase 4: Origin now has E2E traffic keys
```

**Security:** Relays cannot read E2E content; only link-layer encryption at each hop.

### 5.4 Routing Logic

```kotlin
suspend fun sendE2EHandshake(destination: PeerIdentity, content: ByteArray): Result<Unit> {
  val destKey = trustStore.getPublicKey(destination)
  return when {
    destKey != null && routingTable.isDirectNeighbor(destination) ->
      noiseIX.initiateHandshake(destination, destKey, content)
    destKey != null ->
      routeHandshakeOverMesh(destination, content)
    else ->
      requestIdentityGossip(destination)
        .awaitHandshakeCapability()
        .also { routeHandshakeOverMesh(destination, content) }
  }
}
```

### 5.5 Relay Behavior

Relays MUST NOT inspect E2E payloads:

```kotlin
if (frame.destination != localPeerIdentity) {
  val nextHop = routingTable.getNextHop(frame.destination)
  hopSession.send(nextHop, frame.serialized)
  return // Do not inspect E2E content
}
```

### 5.6 Destination Behavior

```kotlin
if (frame.destination == localPeerIdentity) {
  when (parseE2EPayload(frame.payload)) {
    is IX_Msg1 -> {
      noiseIX.processHandshakeMessage(parseHandshake(), originator)
      respondWithIX_Msg2()
    }
    is EncryptedContent -> processE2ETransfer(frame.payload)
  }
}
```

### 5.7 Failure Modes

| Scenario | Detection | Recovery |
|---|---|---|
| No route to destination | `routingTable.getNextHop()` returns null | Fail with `TransferError.NoRoute` |
| Intermediate peer offline | Timeout on hop session | Use alternate route or fail |
| Destination key mismatch | Verify key hash before IX_Msg2 | Fail with `TrustError.KeyMismatch` |
| Replay attack | Nonce check in Noise state | Fail closed (standard Noise behavior) |

---

## Testing

### Handshake Pattern

- `NoiseHandshakeTest`: IX establishes bidirectional E2E traffic keys; origin proactively authenticated.
- Fail-closed: malformed/all-zero X25519 shared secret fails IX handshake at X25519/HKDF step.
- Multi-node harness: E2E session between non-adjacent peers establishes over relayed path.

### NX Fallback

- Full public key verification in payload
- Rate limiting: 3 attempts/min
- 10s timeout
- 32-bit nonce replay protection
- Diagnostic flag: `fallback_used = true`

### Session State Machine

- Timeouts and retries per table
- Transport migration (GATT → L2CAP)
- Rekeying with grace period
- Concurrent handshake serialization

### Key Rotation

- Signature verification and key adoption
- SeqNo resets to 1
- Grace period acceptance for active sessions
- Wire compat: `KeyRotationAnnouncement` round-trips

### E2E Routing Over Mesh

- `E2EHandshakeOverMeshTest`: IX handshake routes correctly through 2+ hops
- `RelayConfidentialityTest`: Relays cannot decrypt E2E payloads
- `KeyUnknownFallbackTest`: Graceful handling of missing public keys
- `ReplayAttackTest`: Anti-replay window on E2E layer

---

## Related

- [Routing Design](../routing/routing-design.md)
- [Transport: MTU](../transport/mtu-negotiation.md)
