# MeshLink Data Model — Design Decisions

**Status:** Locked — 2026-07-20

Complete type definitions in [SPEC.md §3](../../../SPEC.md#core-data-models).
Machine-readable references:

- [specs/enums.yaml](../../../specs/enums.yaml) — all enum values
- [specs/data-models.yaml](../../../specs/data-models.yaml) — all data class schemas
- [specs/state-machines.yaml](../../../specs/state-machines.yaml) — TransferState, NoiseSessionState, PeerLifecycleState, TrustState
- [specs/settings.yaml](../../../specs/settings.yaml) — MeshLinkSettings DSL

## Peer Identity Model

### PeerIdentity is stable/random, not derived

**Initial design** derived `PeerIdentity` from public key: `SHA-256(publicKey).first(16)`. Flaw: key rotation changes public key → changes PeerIdentity → TrustStore indexed by PeerIdentity becomes stale → KeyRotationAnnouncement breaks.

**Solution:** Generate stable PeerIdentity ONCE at install time (16-byte random). Ensures identity persists across key rotations, TrustStore lookups succeed for old keys, KeyRotationAnnouncement validates properly.

### PeerFingerprint is truncated discovery hint

12-byte `SHA-256(Ed25519Pub || X25519Pub)`. Used in discovery advertisements only. Ed25519 first (identity anchor), X25519 second (DH key may rotate independently). Both keys required. 12 bytes (96 bits) provides birthday bound 2^48 — negligible collision probability for any practical mesh. **Discovery hint only — never used for authentication.**

### CryptoKey distinguishes signing from DH keys

```kotlin
sealed interface CryptoKey {
  val keyType: KeyType        // ED25519 or X25519
  val diagnosticId: String    // NEVER the raw key
  internal val keyBytes: ByteArray
}
```

`diagnosticId` prevents key material leaking into logs. Raw access is `internal` only.

## Routing Model

### RouteEntry structure

```kotlin
data class RouteEntry(
  val source: PeerIdentity,        // peer from whom route was learned (loop detection)
  val destination: PeerIdentity,
  val nextHop: PeerIdentity?,
  val metric: UInt,                // composite via LinkMetric; feasibility computed dynamically
  val seqNo: SeqNo,                // destination-self-reported, wrapped for safe comparison
  val identityKey: IdentityKey?,   // learned via route updates; enables E2E IX handshake
  val handshakeKey: HandshakeKey?,  
  val expiresAt: Instant,
)
```

- `metric` composite = `(flags shl 8) or rssiNormalized` per [Routing Design](../routing/routing-design.md)
- `isFeasible` computed dynamically via Babel feasibility condition (RFC 8966 §3.5.1), not stored
- `identityKey` enables E2E IX handshake — see [Crypto Design](../crypto/crypto-design.md)

### SeqNo wraps UInt with signed comparison

RFC 8966 §3.7 requires signed interpretation. `(this - other).toInt() > 0` handles wrap at 2^32. Implemented in `SeqNo.kt`.

## Transfer Model

### Scoreboard uses immutable dynamic bitfield

```kotlin
class Scoreboard(totalChunks: UInt) {
  fun markReceived(chunkIndex: Int): Scoreboard  // Returns new instance
  fun markMissing(chunkIndex: Int): Scoreboard
  fun isReceived(chunkIndex: Int): Boolean
  fun missingChunks(): List<Int>
  fun toByteArray(): ByteArray
}
```

**Why immutable:** Thread-safe sharing between protocol-layer and test assertions.
**Why dynamic:** Small transfers (10 chunks) use 1 byte; large transfers (1000 chunks) use 125 bytes. Memory scales with transfer size.

### TransferState transitions

Complete state machine in [SPEC.md §3.4.1](../../../SPEC.md#transfer-session-state-transitions) and [specs/state-machines.yaml](../../../specs/state-machines.yaml#transferstate).

Transition logic lives in `TransferCoordinator.kt`. Scoreboard completeness checked before COMPLETED.

## Configuration Model

### PowerMode maps to concrete BLE parameters

**Full table in [SPEC.md §10.4](../../../SPEC.md#mode-driven-parameters) and [specs/settings.yaml](../../../specs/settings.yaml#power_mode_parameter_mapping).**

Defaults in `MeshLinkSettings` match MEDIUM mode. EU region clamps adv interval floor to 300ms.

### RegulatoryRegion adds explicit clamping

- `DEFAULT`: Rely on platform BLE stack behavior
- `EU`: Clamp adv interval ≥300ms, scan duty cycle ≤70%

Clamping happens in shared policy code, not platform-specific wrappers.

## Diagnostic Events

All events defined in `DiagnosticEvent.kt` as a sealed interface hierarchy.
Machine-readable reference: [specs/diagnostic-events.yaml](../../../specs/diagnostic-events.yaml).

| Layer | Event Types |
|-------|-------------|
| route | `RouteDecryptFailureEvent`, `RouteDigestMismatchEvent` |
| transport | `TransportFallbackEvent` |
| transfer | `TransferDataPlaneBearerEvent`, `TransferSessionTransitionEvent`, `TransferFailureEvent` |
| power | `PowerModeEffectiveEvent` |
| handshake | `HandshakeEvent` |
| key_rotation | `KeyRotationEvent` |
| noise | `NoiseSessionTransitionEvent` |

Events are machine-observable: consumed by `eventCallback` in settings or logged when `emitToLog = true`.

## Testing Matrix

| Type | Test Class | Verifies |
|------|------------|----------|
| SeqNo | `SeqNoTest` | Wrap-around comparison |
| PeerIdentity/Fingerprint | `PeerIdentityTest` | Generation, truncation |
| Scoreboard | `ScoreboardTest` | Bitfield operations |
| RouteEntry | `RouteEntryTest` | Seqno/metric handling |
| PowerMode | `PowerModeTest` | Parameter mapping |
| RoutingPolicy | `RoutingPolicyTest` | Settings validation |
| TransferFailureReason | `TransferFailureReasonTest` | Sealed type coverage |

All types require 100% line/branch coverage in `:meshlink`.

## Related

- [SPEC.md Core Models](../../../SPEC.md#core-data-models)
- [Routing Design](../routing/routing-design.md)
- [Power Mode Behavior](../power/power-mode-behavior.md)
- [Crypto Design](../crypto/crypto-design.md)
- [specs/enums.yaml](../../../specs/enums.yaml)
- [specs/data-models.yaml](../../../specs/data-models.yaml)
- [specs/state-machines.yaml](../../../specs/state-machines.yaml)
- [specs/settings.yaml](../../../specs/settings.yaml)
- [specs/diagnostic-events.yaml](../../../specs/diagnostic-events.yaml)
