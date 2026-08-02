# MeshLink Data Model — Design Decisions

**Status:** Locked — 2026-07-20

Complete type definitions in [SPEC.md §3](../../../SPEC.md#core-data-models).
Machine-readable references:

- [specs/codecs/enums.yaml](../../../specs/codecs/enums.yaml) — all enum values
- [specs/codecs/models.yaml](../../../specs/codecs/models.yaml) — all data class schemas
- [specs/protocol/state-machines.yaml](../../../specs/protocol/state-machines.yaml) — TransferState, NoiseSessionState, PeerLifecycle, TrustState
- [specs/catalogs/settings.yaml](../../../specs/catalogs/settings.yaml) — MeshLinkSettings DSL

## Peer Identity Model

### PeerIdentity is stable/random, not derived

**Initial design** derived `PeerIdentity` from public key: `SHA-256(publicKey).first(16)`. Flaw: key rotation changes public key → changes PeerIdentity → TrustStore indexed by PeerIdentity becomes stale → key-rotation continuity breaks.

**Solution:** Generate stable PeerIdentity ONCE at install time (16-byte random). Ensures identity persists across key rotations, TrustStore lookups succeed for old keys, rotation proofs validate against the stable identity.

### PeerHint is a rotating advertisement hint

`peerHint` is a 12-byte CSPRNG value carried only in the dynamic advertisement
UUID. It rotates at a randomized best-effort interval, is never persisted, and
is independent of PeerIdentity and Ed25519/X25519 keys.

It coalesces ephemeral discovery attempts but is not sent through GATT, signed,
or used as authentication. Trust, routes, transfers, and public state are keyed
only by PeerIdentity. See the
[peer-hint race decision](../discovery/peer-hint-and-identity-races.md).

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

### RouteCandidate separates cost and observation

```kotlin
internal class RouteCandidate(
  val destination: PeerIdentity,
  val nextHop: PeerIdentity,
  val sequenceNumber: SeqNo,
  val routeCost: UInt,
  val hopCount: UByte,
  val linkQuality: LinkQuality,
  val expiresAt: Instant,
)
```

`routeCost` is lower-is-better and additive. `hopCount` is independent.
`linkQuality` describes only the local authenticated next-hop link. Destination
identity, sequence, and candidate binding arrive in mandatory signed
RouteStatement; nextHop is inferred locally from the adjacent sender.

### SeqNo is an internal modular serial

RFC 8966 §3.7-style comparison interprets the UInt difference as signed within
the half-range window. `SeqNo` is not Comparable because modular serial ordering
is not a globally transitive total order.

It exposes explicit internal operations such as `isNewerThan`,
`isNewerThanOrEqualTo`, `isOlderThan`, `distanceFrom`, and `next`. The exact
`2^31` half-range difference is ambiguous and cannot drive route ordering.
Destination-owned values persist and advance independently of transport,
cryptographic keys, and peer hints.

## Transfer Model

### TransferId is a 32-bit origin-scoped counter

A logical transfer is identified by `(authenticated origin PeerIdentity,
TransferId)`. The four-byte identifier is allocated from a durably reserved,
monotonically increasing source-owned counter; zero is invalid. It is not a
secret or authorization token. This avoids repeating a globally unique 64- or
128-bit value in every chunk and acknowledgement while preserving an
unambiguous transfer namespace.

See the [Transfer Identifier ADR](../transfer/transfer-identifier.md) for
allocation, crash safety, replay retention, and wrap-around rules.

### Scoreboard: immutable SACK bitfield with O(1) completeness and mesh merge ops

```kotlin
class Scoreboard(totalChunks: UInt)               // Dynamic bitfield

// Immutable operations (return new Scoreboard, cached counts)
fun markReceived(index: Int): Scoreboard          // O(1) count update
fun markMissing(index: Int): Scoreboard           // O(1) count update
fun isComplete(): Boolean                         // O(1) — all chunks received
fun receivedCount(): Int                          // O(1)
fun missingCount(): Int                           // O(1)

// Bitwise merge operations (for mesh relay cut-through)
fun or(other: Scoreboard): Scoreboard             // Union of ACKs from multiple peers
fun and(other: Scoreboard): Scoreboard            // Intersection (all peers confirm)
fun xor(other: Scoreboard): Scoreboard            // Symmetric difference

// Lazy/zero-allocation iteration
fun missingSequence(): Sequence<Int>
inline fun forEachMissing(action: (index: Int) -> Unit)

// Wire serialization
companion fun fromBytes(totalChunks: UInt, bytes: ByteArray): Scoreboard
fun toByteArray(): ByteArray
fun get byteSize: Int                            // Bitfield byte count for framing
```

**Why immutable:** Thread-safe sharing between protocol-layer and test assertions. The immutable
`markReceived`/`markMissing` pattern enables structural sharing for free.

**Why cached counts:** `receivedCount()`/`missingCount()`/`isComplete()` track their values
incrementally — `markReceived` adds 1 when a bit flips 0→1, `markMissing` subtracts 1 when
a bit flips 1→0. Duplicate/absent marks are no-ops with zero count delta.

**Why bitwise ops (`or`/`and`/`xor`):** Mesh cut-through relay requires merging ACK bitfields
from multiple peers receiving the same transfer. `or` gives the union (all chunks any peer has
seen); `and` gives the intersection (chunks all peers confirmed); `xor` highlights divergence.

**Why bounds checking:** `IndexOutOfBoundsException` with descriptive messages (`Chunk index
5 is out of range [0, 4)`) replaces the cryptic `ArrayIndexOutOfBoundsException` from the
previous unchecked array access.

**Wire boundary:** Large-transfer acknowledgements use the fixed 256-chunk
PayloadAcknowledgment window. Whole-transfer Scoreboard serialization is not a
publicly configurable wire strategy.

**Why O(1) completeness:** The state machine transition "All chunks received +
scoreboard complete → COMPLETED" requires an O(1) check. Previously `missingCount() == 0`
was O(n) and allocated nothing but still required full iteration.

**Why dynamic:** Small transfers (10 chunks) use 1 byte; large transfers (1000 chunks) use
125 bytes. Memory scales with transfer size.

### TransferState transitions

Complete state machine in [SPEC.md §3.4.1](../../../SPEC.md#transfer-session-state-transitions) and [specs/protocol/state-machines.yaml](../../../specs/protocol/state-machines.yaml#transferstate).

Transition logic lives in `TransferCoordinator.kt`. Scoreboard completeness checked before COMPLETED.

## Configuration Model

### PowerMode maps to concrete BLE parameters

**Full table in [SPEC.md §10.4](../../../SPEC.md#mode-driven-parameters) and [specs/catalogs/settings.yaml](../../../specs/catalogs/settings.yaml#power_mode_parameter_mapping).**

Defaults in `MeshLinkSettings` match MEDIUM mode. EU region clamps adv interval floor to 300ms.

### RegulatoryRegion adds explicit clamping

- `DEFAULT`: Rely on platform BLE stack behavior
- `EU`: Clamp adv interval ≥300ms, scan duty cycle ≤70%

Clamping happens in shared policy code, not platform-specific wrappers.

## Diagnostic Events

All events defined in `DiagnosticEvent.kt` as a sealed interface hierarchy.
Machine-readable reference: [specs/catalogs/diagnostic-events.yaml](../../../specs/catalogs/diagnostic-events.yaml).

| Layer | Event Types |
|-------|-------------|
| route | `RouteDecryptFailureEvent`, `RouteDigestMismatchEvent` |
| transport | `TransportFallbackEvent` |
| transfer | `TransferDataPlaneBearerEvent`, `TransferSessionTransitionEvent`, `TransferFailureEvent` |
| power | `PowerModeEffectiveEvent` |
| handshake | `HandshakeEvent` |
| key_rotation | `KeyRotationEvent` |
| noise | `NoiseSessionTransitionEvent` |

Events are machine-observable through `MeshLink.diagnostics` and may also be mirrored to platform logging when `DiagnosticsSettings.emitToLog` is enabled.

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
- [specs/codecs/enums.yaml](../../../specs/codecs/enums.yaml)
- [specs/codecs/models.yaml](../../../specs/codecs/models.yaml)
- [specs/protocol/state-machines.yaml](../../../specs/protocol/state-machines.yaml)
- [specs/catalogs/settings.yaml](../../../specs/catalogs/settings.yaml)
- [specs/catalogs/diagnostic-events.yaml](../../../specs/catalogs/diagnostic-events.yaml)
