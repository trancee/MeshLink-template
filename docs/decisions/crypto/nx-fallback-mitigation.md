# NX Fallback with Full Public Key Verification

## Status: Proposed

## Context

When an origin peer lacks the destination's public key for E2E handshake, Noise IX cannot proceed. The NX pattern provides:

- Source authentication level 0 (no authentication)
- Enables DoS via unauthenticated handshake initiation

However, both origin and destination share the discovery `PeerFingerprint` (12-byte truncated public key hash). This provides a verification mechanism.

## Decision: NX Fallback with Full Public Key Verification and Mitigations

Use `Noise_NX_25519_ChaChaPoly_SHA256` when destination key is unknown, with security mitigations.

**Key Change**: The NX handshake payload carries the **full 64-byte concatenated public key** (Ed25519Pub || X25519Pub), not the truncated 12-byte `PeerFingerprint`. This eliminates the 2^96 preimage attack vector from PeerFingerprint truncation.

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
| **Wrong-peer handshake** | Full public key verification in payload | Validates identity claim |
| **Silent degradation** | Diagnostic flag: `e2e_handshake_used_fallback = true` | Observability |
| **NX replay attacks** | 32-bit nonce in payload, checked before key verification | Prevents message replay |

### Public Key Verification (Replaces PeerFingerprint Collision Analysis)

The NX handshake payload includes the **full 64-byte concatenated public key**:

| Field | Size | Description |
|-------|------|-------------|
| Ed25519PublicKey | 32 bytes | Identity/signing key |
| X25519PublicKey | 32 bytes | DH key for Noise handshakes |
| **Total** | **64 bytes** | Verified byte-for-byte |

Verification: `received_ed25519 == expected_ed25519 AND received_x25519 == expected_x25519`

**Entropy**: 255 bits (Ed25519) + 255 bits (X25519) = 510 bits effective security. No truncation collision risk.

**PeerFingerprint** is still used in discovery advertisements for initial filtering.

### Protocol Details

**When to use NX fallback:**

- Destination key not in TrustStore or route table
- Discovery `PeerFingerprint` available (shared context, for initial filtering)
- Within rate limit and timeout constraints

**Verification sequence:**

1. Parse NX_Msg1 payload
2. Extract **ed25519PublicKey**, **x25519PublicKey**, and nonce
3. Verify nonce hasn't been seen (replay protection)
4. Complete NX handshake
5. Verify received static keys match payload byte-for-byte:
   - `received_ed25519 == ed25519PublicKey`
   - `received_x25519 == x25519PublicKey`
6. Reject if mismatch

### Wire Protocol

```kotlin
// Full public key verification in handshake payload
data class HandshakePayload(
  val ed25519PublicKey: CryptoKey,     // 32 bytes - identity/signing key
  val x25519PublicKey: CryptoKey,      // 32 bytes - DH key for Noise handshakes
  val nonce: UInt32,                    // Replay protection - checked first
  val content: ByteArray                // Actual payload
)

// Rate limiting state
data class FallbackState(
  val attempts: Int = 0,
  val lastAttempt: Instant = Instant.MIN,
  val seenNonces: Set<UInt32> = emptySet()
)

// Rate limiting
private val attemptsPerDestination = ConcurrentHashMap<PeerIdentity, FallbackState>()

suspend fun canInitiateFallback(destination: PeerIdentity, nonce: UInt32): Boolean {
  val state = attemptsPerDestination.getOrPut(destination) { FallbackState() }
  val now = Clock.System.now()
  
  // Reset counter if minute elapsed
  val freshState = if (now - state.lastAttempt > Duration.minutes(1)) {
    FallbackState(nonce = 0, lastAttempt = now)
  } else state
  
  if (freshState.attempts >= 3 || freshState.seenNonces.contains(nonce)) {
    return false
  }
  
  attemptsPerDestination[destination] = freshState.copy(
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
       Payload: ed25519PublicKey (32B) + x25519PublicKey (32B) + nonce (4B)
Msg2: <- e, ee, se, s, es 

Phase 2: Full Public Key Verification (after NX completes)
- Origin includes ed25519Pub + x25519Pub + nonce in Msg1 payload
- Destination verifies: received_static_ed25519 == ed25519PublicKey AND received_static_x25519 == x25519PublicKey
- Checks nonce not previously seen
- Mismatch or replay = reject

Phase 3: Transport
-> encryptedContent (IX transport keys established)
```

### Testing Requirements

- `NXFallbackPublicKeyVerifyTest`: verify full public key mismatch causes rejection
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
  full_public_key_verified: true
  rate_limit_attempts: 1
  nonce_replay_detected: false
```

## Related

- `docs/decisions/crypto/e2e-routing-over-mesh.md`
- `docs/decisions/crypto/e2e-handshake-pattern.md`
- Noise Protocol Framework §7.7 (payload security: NX has Source=0, Dest=3)
