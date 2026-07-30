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

RFC 8966 §3.7 requires signed interpretation. `(this - other).toInt() > 0` handles wrap at 2^32.

Implements `Comparable<SeqNo>` so seqnos can be sorted and compared in standard Kotlin ordering utilities (`sorted()`, `min()` on collections).

- `toUInt()` / `fromUInt(value)` provide logical wire serialization (raw value extraction)
- `toByteArray()` / `fromByteArray(bytes)` provide 4-byte big-endian byte-level wire serialization
- `isNewerThanOrEqualTo` / `isOlderThanOrEqualTo` support the Babel feasibility condition (`>=` comparison)
- `max(other)` / `min(other)` select the newer/older seqno for route table merges
- `compareTo` delegates to `minus`: same modular signed comparison as all comparison methods
- `operator inc()` advances the seqno by 1 with modular wrap at 2^32
- `unsignedDistance(other)` returns the modular unsigned forward distance, useful for route staleness diagnostics
- `MAX_VALUE` documents the 2^32 - 1 boundary; `isZero` is a convenience check
- Modular comparison window is 2^31: at exactly ±2^31 the comparison is ambiguous and `isNewerThan` returns false (conservative)

Implemented in `SeqNo.kt`.

## Transfer Model

### Scoreboard: immutable SACK bitfield with O(1) completeness and mesh merge ops

```kotlin
class Scoreboard(totalChunks: UInt)               // Dynamic bitfield
class Scoreboard(totalChunks: UInt, maxChunksPerSession: UInt)  // FIXED pre-allocation

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

**Why FIXED encoding:** When `totalChunks` is unknown upfront (e.g. streaming transfer) but
`maxChunksPerSession` provides a safe upper bound, the FIXED constructor pre-allocates the
bitfield to avoid resize logic. The `scoreboardEncoding` setting in `TransferSettings` controls
which constructor is used.

**Why bounds checking:** `IndexOutOfBoundsException` with descriptive messages (`Chunk index
5 is out of range [0, 4)`) replaces the cryptic `ArrayIndexOutOfBoundsException` from the
previous unchecked array access.

**Why `fromBytes` companion:** Wire deserialization of `TRANSFER_ACKNOWLEDGMENT` frames
requires constructing a Scoreboard from raw bytes. Previously `fromBytes` was `internal`,
blocking this use case.

**Why O(1) completeness:** The state machine transition "All chunks received +
scoreboard complete → COMPLETED" requires an O(1) check. Previously `missingCount() == 0`
was O(n) and allocated nothing but still required full iteration.

**Why dynamic:** Small transfers (10 chunks) use 1 byte; large transfers (1000 chunks) use
125 bytes. Memory scales with transfer size.

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
