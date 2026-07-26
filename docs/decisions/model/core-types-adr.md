# MeshLink Data Model — Design Decisions

## Status: Locked — 2026-07-20

This ADR captures the design rationale for core data types. For complete type definitions, see [SPEC.md](../../../SPEC.md#3-core-data-models).

---

## Peer Identity Model

### PeerIdentity is stable/random, not derived

**Initial design** derived PeerIdentity from public key: `PeerIdentity = SHA-256(publicKey).first(16)`. This created a critical flaw:

1. **Key rotation changes public key** → therefore changes PeerIdentity
2. **Neighbors can't look up old key** → TrustStore indexed by PeerIdentity becomes stale
3. **KeyRotationAnnouncement breaks** → Cannot verify with "old key by PeerIdentity"

**Solution**: Generate stable PeerIdentity ONCE at install time (16-byte random). This ensures:

- Peer identity persists across key rotations
- TrustStore lookups succeed for old keys
- Key rotation announcements validate properly

### PeerFingerprint is truncated discovery hint

12-byte SHA-256(Ed25519Pub || X25519Pub). Used in discovery advertisements only. Both keys required; derivation fails if either missing. Ed25519 first because it's the identity anchor; X25519 second because DH key may rotate independently.

### CryptoKey distinguishes signing from DH keys

```kotlin
// See SPEC.md §3.1 for complete definitions
sealed interface CryptoKey {
  val keyType: KeyType        // ED25519 or X25519
  val diagnosticId: String    // NEVER the raw key
  internal val keyBytes: ByteArray
}
```

`diagnosticId` prevents key material leaking into logs. Raw access is `internal` only.

---

## Routing Model

### RouteEntry structure

```kotlin
// See SPEC.md §3.3 for complete definitions
data class RouteEntry(
  val destination: PeerIdentity,
  val nextHop: PeerIdentity?,
  val seqNo: SeqNo,        // Destination-sourced, wrapped for safe comparison
  val metric: UInt,        // RSSI + flags; feasibility computed dynamically
  val identityKey: IdentityKey?, // Learned via route updates
  val expiresAt: Instant
)
```

**Key decisions**:

- `metric` composite = `(flags shl 8) or rssiNormalized` per [`link-quality-metric.md`](../routing/link-quality-metric.md)
- `isFeasible` computed dynamically via Babel feasibility condition, not stored
- `identityKey` enables E2E IX handshake; see [`e2e-handshake-pattern.md`](../crypto/e2e-handshake-pattern.md)

### SeqNo wraps UInt with signed comparison

RFC 8966 §3.7 requires signed interpretation for seqno comparison. `(this - other).toInt() > 0` handles wrap at 2^32.

---

## Transfer Model

### Scoreboard uses immutable dynamic bitfield

```kotlin
// See SPEC.md §3.4 for complete definitions
class Scoreboard(totalChunks: UInt) {
  fun markReceived(chunkIndex: Int): Scoreboard  // Returns new instance
  fun markMissing(chunkIndex: Int): Scoreboard
  fun isReceived(chunkIndex: Int): Boolean
  fun missingChunks(): List<Int>
  fun toByteArray(): ByteArray  // For wire serialization
}
```

**Why immutable**: Enables thread-safe sharing between protocol-layer and test assertions.

**Why dynamic**: Small transfers (10 chunks) use 1 byte; large transfers (1000 chunks) use 125 bytes. Memory scales with transfer size.

### TransferState transitions

| Current | Event | Next |
|---------|-------|------|
| — | Created | IN_PROGRESS |
| IN_PROGRESS | All chunks received | COMPLETED |
| IN_PROGRESS | Error/cancel/trust failure | FAILED |
| IN_PROGRESS | Route lost | WAITING_FOR_ROUTE |
| WAITING_FOR_ROUTE | Route found | IN_PROGRESS |
| WAITING_FOR_ROUTE | Retry budget exhausted | TIMED_OUT |
| IN_PROGRESS | Chunk missing | RETRYING |
| RETRYING | Retransmission done | IN_PROGRESS |
| RETRYING | Retry budget exhausted | FAILED |

Transition logic lives in `TransferCoordinator.kt`. Scoreboard completeness is checked before COMPLETED transition.

---

## Configuration Model

### PowerTier maps to concrete BLE parameters

| Tier | Scan | Adv | Conn | Concurrent | Chunk | Retries | Budget |
|------|------|-----|------|------------|-------|---------|--------|
| HIGH | 20% | 100ms | 7.5-15ms | 8 | 512B | 10 | 60s |
| MEDIUM | 10% | 500ms | 15-30ms | 4 | 256B | 5 | 30s |
| LOW | 5% | 1000ms | 30-60ms | 2 | 128B | 3 | 15s |

Defaults in `MeshLinkSettings` match MEDIUM tier. EU region clamps adv interval floor to 300ms.

### RegulatoryRegion adds explicit clamping

- `DEFAULT`: Rely on platform BLE stack behavior
- `EU`: Clamp adv interval ≥300ms, scan duty ≤70%

Clamping happens in shared policy code, not platform-specific wrappers.

---

## Diagnostic Events

All events are defined in `DiagnosticEvent.kt` (see SPEC.md §11.3). Event types map to layer concerns:

| Layer | Event Types |
|-------|-------------|
| route | `RouteDecryptFailureEvent`, `RouteDigestMismatchEvent` |
| transport | `TransportFallbackEvent` |
| transfer | `TransferDataPlaneBearerEvent`, `TransferSessionTransitionEvent`, `TransferFailureEvent` |
| power | `PowerTierEffectiveEvent` |
| handshake | `HandshakeEvent` |
| key_rotation | `KeyRotationEvent` |
| noise | `NoiseSessionTransitionEvent` |

Events are machine-observable: consumed by `eventCallback` in settings or logged when `emitToLog = true`.

---

## Testing Matrix

| Type | Test Class | Verifies |
|------|------------|----------|
| SeqNo | `SeqNoTest` | Wrap-around comparison |
| PeerIdentity/Fingerprint | `PeerIdentityTest` | Generation, truncation |
| Scoreboard | `ScoreboardTest` | Bitfield operations |
| RouteEntry | `RouteEntryTest` | Seqno/metric handling |
| PowerTier | `PowerTierTest` | Parameter mapping |
| RoutingPolicy | `RoutingPolicyTest` | Settings validation |
| TransferFailureReason | `TransferFailureReasonTest` | Sealed type coverage |

All types require 100% line/branch coverage in `:meshlink`.

---

## Related

- [SPEC.md Core Models](../../../SPEC.md#3-core-data-models)
- [Link Quality Metric](../routing/link-quality-metric.md)
- [Power Tier Behavior](../power/power-tier-behavior.md)
- [E2E Handshake Pattern](../crypto/e2e-handshake-pattern.md)
- [NX Fallback Mitigations](../crypto/nx-fallback-mitigation.md)
