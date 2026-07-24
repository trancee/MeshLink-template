# NX Fallback with PeerKey Verification

## Status: Proposed

## Context

When an origin peer lacks the destination's public key for E2E handshake, Noise IX cannot proceed. The NX pattern provides:

- Source authentication level 0 (no authentication)
- Enables DoS via unauthenticated handshake initiation

However, both origin and destination share the discovery `PeerKey` (12-byte truncated public key hash). This provides a verification mechanism.

## Decision: NX Fallback with PeerKey Verification and Mitigations

Use `Noise_NX_25519_ChaChaPoly_SHA256` when destination key is unknown, with security mitigations.

### Threat Model for Key Unavailability

NX fallback is triggered only when:

1. **Cold start discovery**: New peer discovered but key gossip not yet propagated
2. **Key rotation lag**: Peer rotated key but announcement not received
3. **Network partition**: Key unavailable due to mesh partition

**Not triggered by:** Direct attack, key compromise, or misconfiguration.

### Security Mitigations

| Threat | Mitigation | Rationale |
|--------|------------|-----------|
| **DoS via unauthenticated handshakes** | Rate limit: max 3 NX attempts/min per destination | Prevents resource exhaustion |
| **Resource exhaustion** | 10s timeout vs 30s for IX | Limits handshake window |
| **Wrong-peer handshake** | PeerKey verification in payload | Validates identity claim |
| **Silent degradation** | Diagnostic flag: `e2e_handshake_used_fallback = true` | Observability |
| **NX replay attacks** | 32-bit nonce in payload, checked before PeerKey | Prevents message replay |

### PeerKey Collision Analysis

**PeerKey** = first 12 bytes of SHA-256(publicKey)

| Metric | Value |
|--------|-------|
| Entropy | 96 bits |
| Collision probability (birthday) | 2^-48 ≈ 1 in 281 trillion |
| Practical attack feasibility | Infeasible with current technology |

**Mitigation:** Even with collision, attacker cannot:

- Present valid signature from origin's perspective
- Complete full handshake without correct ephemeral keys

### Protocol Details

**When to use NX fallback:**

- Destination key not in TrustStore
- Discovery `PeerKey` available (shared context)
- Within rate limit and timeout constraints

**Verification sequence:**

1. Parse NX_Msg1 payload
2. Extract PeerKey and nonce
3. Verify nonce hasn't been seen (replay protection)
4. Complete NX handshake
5. Verify received static key's PeerKey matches payload PeerKey
6. Reject if mismatch

### Wire Protocol

```kotlin
// PeerKey verification in handshake payload
data class HandshakePayload(
  val peerKey: PeerKey,             // 12-byte discovery hint (truncated key hash)
  val nonce: UInt32,                 // Replay protection - checked first
  val content: ByteArray             // Actual payload
)

// Rate limiting state
data class NxFallbackState(
  val attempts: Int = 0,
  val lastAttempt: Instant = Instant.MIN,
  val seenNonces: Set<UInt32> = emptySet()
)

// Rate limiting
private val nxAttemptsPerDestination = ConcurrentHashMap<PeerId, NxFallbackState>()

suspend fun canInitiateNxfallback(destination: PeerId, nonce: UInt32): Boolean {
  val state = nxAttemptsPerDestination.getOrPut(destination) { NxFallbackState() }
  val now = Clock.System.now()
  
  // Reset counter if minute elapsed
  val freshState = if (now - state.lastAttempt > Duration.minutes(1)) {
    NxFallbackState(nonce = 0, lastAttempt = now)
  } else state
  
  if (freshState.attempts >= 3 || freshState.seenNonces.contains(nonce)) {
    return false
  }
  
  nxAttemptsPerDestination[destination] = freshState.copy(
    attempts = freshState.attempts + 1,
    seenNonces = freshState.seenNonces + nonce
  )
  return true
}

// Timeout configuration
val NX_FALLBACK_TIMEOUT_MS = 10_000
val IX_TIMEOUT_MS = 30_000
```

### Handshake Flow

```text
┌────────┐                              ┌────────┐
│ Origin │                              │Destination│
│ (no key)│                              │(key known)│
└────────┘                              └────────┘

Phase 1: NX Handshake (MeshEnvelope routed)
Origin --(MeshEnvelope)--> Relay(s) --> Destination
Msg1: -> e, s, es (NX pattern, no responder static known)
Msg2: <- e, ee, se, s, es 

Phase 2: PeerKey Verification (after NX completes)
- Origin includes PeerKey + nonce in Msg1 payload
- Destination verifies: Hash(received_static) == PeerKey
- Checks nonce not previously seen
- Mismatch or replay = reject

Phase 3: Transport
-> encryptedContent (IX transport keys established)
```

### Testing Requirements

- `NXFallbackPeerKeyVerifyTest`: verify PeerKey mismatch causes rejection
- `NXFallbackRateLimitTest`: verify 3rd attempt succeeds, 4th fails
- `NXFallbackTimeoutTest`: verify 10s timeout expires correctly
- `NXFallbackReplayTest`: verify nonce replay is rejected
- `NXTransportSecurityTest`: verify final transport keys are secure (Wycheproof vectors)

### Diagnostic Contract

Per handshake session:

```yaml
e2e_handshake:
  protocol: "Noise_NX_25519_ChaChaPoly_SHA256"
  fallback_used: true
  peer_key_verified: true
  rate_limit_attempts: 1
  nonce_replay_detected: false
```

## Related

- `docs/decisions/crypto/e2e-routing-over-mesh.md`
- `docs/decisions/crypto/e2e-handshake-pattern.md`
- Noise Protocol Framework §7.7 (payload security: NX has Source=0, Dest=3)
