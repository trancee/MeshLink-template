# Key Rotation Protocol

## Status: Proposed

## Context

Ed25519/Ed25519 keys can and should be rotated for forward secrecy. The current spec states: "Seqno increments only on cold start (`MeshLink.start()`), not on reconnect." This creates a problem for key rotation:

1. **Rotated key, continuous operation:** Node has new key but no cold start occurred
2. **Gossip race:** Nearby peers may not have received the new key yet
3. **Multi-hop propagation:** Distant peers may have stale key -> stale seqno

If a peer rotates keys without a cold start, its self-sourced seqno remains the same, but the key has changed. Neighbors seeing the same seqno with a different key must recognize this as a key update.

## Decision: Explicit Key Announcement + Seqno Reset

### Security Justification for Key Rotation

**Key rotation addresses:**

1. **Forward secrecy for long-running sessions:** If a session spans key rotation, old key compromise doesn't expose future sessions
2. **Device compromise recovery:** If device is rooted/compromised, new key invalidates attacker's access
3. **Regulatory compliance:** Some security standards require periodic key rotation

**Key rotation does NOT address:**

- Session-level forward secrecy (handled by ephemeral keys)
- Peer identity verification (handled by TOFU)

### Key Rotation Trigger

Key rotation is initiated by:

1. **Periodic timer** (default: every 3 days)
2. **Manual reset** via public API (`meshLink.rotateIdentity()`)
3. **Security event** (compromise detection via diagnostics)

**Configuration (sub-object for grouping):**

```kotlin
meshLinkConfig {
  keyRotation {
    interval = Duration.ofDays(1)    // Override default 3 days
    rotationGracePeriod = Duration.ofHours(1)  // Time to accept old key after planned rotation
    compromiseGracePeriod = Duration.ZERO  // Immediate revocation for security events
  }
}
```

**Default values:**

- `keyRotation.interval`: 3 days (security best practice)
- `keyRotation.rotationGracePeriod`: 1 hour (allow propagation for planned rotations)
- `keyRotation.compromiseGracePeriod`: ZERO (immediate revocation for security events)

### Wire Protocol

Upon key rotation, immediately send a **KeyRotationAnnouncement**:

```flatbuffers
table KeyRotationAnnouncement {
  // The NEW identity key (32 bytes)
  identityKey: CryptoKey;
  
  // Current seqno at time of rotation (forces reset)
  seqNo: uint32;
  
  // Signature of the announcement using OLD private key
  signature: uint8Vector(64); // Ed25519 signature
  
  // Optional: reason for rotation (compact enum)
  reason: uint8;
}
```

### Rotation Flow

```text
┌────────────────┐
│  Key Rotating  │
└────────────────┘
       │
       ▼
┌────────────────┐     ┌────────────────┐
│  New keypair   │────▶│ Sign rotation  │
│ generated      │     │ announce with  │
└────────────────┘     │ OLD private key│
       │               └────────────────┘
       ▼                        │
┌────────────────┐               ▼
│ Store OLD key  │◀───┌──────────────────────┐
│ for verify     │    │ KeyRotationAnnounce- │
└────────────────┘    │ ment with NEW key    │
       │              └──────────────────────┘
       ▼                        │
┌────────────────┐               ▼
│ Broadcast via  │────▶┌────────────────┐
│ Route self-    │     │ Neighbors:     │
│ announcement   │     │ Verify signature │
└────────────────┘     │ with OLD key     │
                       │ Accept new key   │
                       └────────────────┘
                                │
                                ▼
                     ┌────────────────┐
                     │ Update Trust-  │
                     │ Store + reset  │
                     │ seqno to 1     │
                     └────────────────┘
```

### Neighbor Behavior on Key Rotation

```kotlin
// In RouteCoordinator.onPeerConnected()
suspend fun handleKeyRotationAnnouncement(
  peerId: PeerIdentity,
  announcement: KeyRotationAnnouncement
) {
  // Verify signature with OLD known key (must exist for valid rotation)
  val oldKey: CryptoKey = trustStore.getPublicKey(peerId)
      ?: run {
        diagnostics.error("Cannot verify rotation - old key not found for $peerId")
        return
      }
  
  if (!crypto.verify(announcement.signature, announcement.toByteArray(), oldKey)) {
    diagnostics.error("Invalid key rotation signature from $peerId")
    return
  }
  
  // Accept new key
  trustStore.updatePublicKey(peerId, announcement.identityKey)
  
  // Seqno RESET to 1 (new identity)
  routingTable.resetSeqNo(peerId, 1u)
  
  // Update route digest
  routeDigestTracker.invalidatePeer(peerId)
}
```

### Seqno Management During Rotation

**Key insight:** Key rotation changes the **crypto identity**, not the **peer identity**.

- **PeerIdentity** (stable) — remains unchanged across rotations
- **PublicKey** (volatile) — changes on key rotation
- **SeqNo** (route metric) — resets to 1 to signal "new crypto era"

**Why reset seqno:**

1. Prevents replay confusion between key versions
2. Signals route freshness for the new key
3. Matches "new identity" semantics for routing (but not peer tracking)

### Failure Modes and Recovery

| Scenario | Detection | Recovery |
|----------|-----------|----------|
| **Malicious rotation (wrong signature)** | Signature verification fails | Reject, report to diagnostics |
| **Stale key announcement** | Seqno >= current known | Treat as replay, ignore |
| **Missing rotation announcement** | Key mismatch on connection | Fall back to trust reset |
| **Partial mesh propagation** | Some neighbors have key, others don't | Digest mismatch triggers full sync |
| **Old key lost before rotation** | Cannot verify signature | Rotation fails, key unchanged |

### Propagation Deadline

Key rotation announcements must propagate within:

- **Direct neighbors:** < 1 second (immediate)
- **2-hop:** < 3 seconds (route convergence budget)
- **Beyond 2-hop:** May take longer, handled by digest resync

**If propagation exceeds deadline:**

- Originating peer continues using old key
- Neighbor falls back to trust reset on next connection

### Testing Requirements

- `KeyRotationAnnounceTest`: verify signature verification and key adoption
- `KeyRotationSeqnoResetTest`: verify seqno resets to 1, not preserved
- `KeyRotationPropagationTest`: verify gossip reaches mesh within deadlines
- `KeyRotationRollbackTest`: verify old key still accepted for active sessions
- `KeyRotationMultiRotationTest`: verify chain of rotations works correctly
- `WireCompatTest`: verify `KeyRotationAnnouncement` round-trips correctly

### Wire Compatibility

Since no MeshLink release has shipped yet, this is not a breaking change — wire format grows but doesn't break existing fields.

## Related

- `docs/decisions/crypto/e2e-handshake-pattern.md`
- `docs/decisions/routing/destination-sourced-seqno-ihu-removal-digest-resync-design.md`
- `docs/decisions/crypto/vector-policy.md` (signature verification rules)
- `docs/explanation/privacy-pseudonyms.md` (PeerFingerprint in discovery)
