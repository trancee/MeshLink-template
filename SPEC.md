# MeshLink Technical Specification

**Version**: 0.1.0-draft | **Status**: Active Development

---

## Table of Contents

1. [Vision & Product Pillars](#1-vision--product-pillars)
2. [Architecture Overview](#2-architecture-overview)
3. [Core Data Models](#3-core-data-models)
4. [Discovery & Identity](#4-discovery--identity)
5. [Trust Model (TOFU)](#5-trust-model-tofu)
6. [Transport Layer](#6-transport-layer)
7. [Security Layer](#7-security-layer)
8. [Routing Layer](#8-routing-layer)
9. [Transfer Layer](#9-transfer-layer)
10. [Power Management](#10-power-management)
11. [Diagnostics & Events](#11-diagnostics--events)
12. [Build & Quality Constraints](#12-build--quality-constraints)
13. [Testing & Verification](#13-testing--verification)
14. [Settings Model](#14-settings-model)
15. [Future Work](#15-future-work)

---

## 1. Vision & Product Pillars

### 1.1 Problem Statement

Mobile devices need to communicate securely without internet, backend servers, or user accounts. BLE mesh networking requires handling peer discovery, trust establishment, routing, and reliable transfer. Both Android and iOS platforms must offer identical public API behavior.

### 1.2 Product Pillars

| # | Pillar | Description |
|---|--------|-------------|
| 1 | **Zero-infrastructure trust** | Trust On First Use (TOFU): first mutually-authenticated handshake pins peer identity keys; subsequent mismatches require explicit reset/revocation |
| 2 | **Two-layer encryption** | Hop-by-hop link encryption (relays forward without reading) layered under end-to-end encryption (origin/destination only) |
| 3 | **Proactive multi-hop routing** | Distance-vector-style routing control plane maintaining live route tables; host app never selects intermediate hops manually |
| 4 | **Reliable large-payload transfer** | Chunked transfer with selective acknowledgement (SACK), retransmission, and reassembly over small-frame BLE radio |
| 5 | **Power-aware operation** | Discrete power modes governing scan duty cycle, advertisement interval, connection interval, concurrent connections, and transfer chunk size |
| 6 | **Deterministic cross-platform parity** | Identical lifecycle states, sealed error hierarchies, and diagnostic codes across Android and iOS |

### 1.3 Non-Functional Requirements

| Requirement | Constraint |
|-------------|------------|
| Offline operation | Zero connectivity required once permissions granted |
| Persisted state | Only trust pin (identity material + first/verified instants); no plaintext or full identifiers cached |
| Pending state | In-memory only; does not survive process restart |
| Delivery outcomes | Explicit terminal: `COMPLETED`, `CANCELLED`, `EXPIRED`, `UNRECOVERABLE_FAILURE`, `TRUST_FAILURE`; non-terminal progress is `null` (see §3.6 TransferState, §11.4) |
| Wire compatibility | Backward-compatible evolution; breaking changes require major version bump + migration |
| Performance budgets | See [§12](#12-build--quality-constraints) |
| Runtime dependency | Maximum one Maven artifact: `kotlinx-coroutines-core`. Crypto uses platform APIs or pure-Kotlin fallbacks |
| Test coverage | 100% line/branch coverage for `:meshlink`; crypto validated against Wycheproof vectors |

### 1.4 Reference Standards

| Layer | Standards |
|-------|-----------|
| Crypto primitives | RFC 7748 (X25519), RFC 8032 (Ed25519), RFC 8439 (ChaCha20-Poly1305), RFC 5869 (HKDF), RFC 2104 (HMAC), RFC 6234 (SHA-2) |
| Handshake patterns | Noise Protocol Framework (XX for unpinned contact, IK for pinned reconnect) |
| Routing | RFC 8966 (Babel) — feasibility condition, seqno, route digest |
| Transfer | RFC 2018 (TCP SACK), RFC 7233 (HTTP Range) |
| Replay protection | RFC 9147 (DTLS 1.3 sliding window) |
| Opportunistic security | RFC 7435 (design philosophy) |
| Wire encoding | Custom pure-Kotlin MeshLink Wire Codec, inspired by selected FlatBuffers techniques but not byte-compatible by contract |
| Compression (optional) | RFC 1950/1951/1952 (zlib), RFC 7932 (Brotli), RFC 8878 (Zstandard) |

---

## 2. Architecture Overview

### 2.1 Module Structure

```text
meshlink/              # Shipped library (JVM + Android + iOS)
meshlink-reference/    # Reference app consuming public API only (Compose Multiplatform)
meshlink-proof/        # Real-device validation (android/ + ios/ subdirectories)
meshlink-benchmark/    # Performance benchmarking
```

**Why separate modules?**

- `meshlink-reference`: Validates DX/integration regressions; only calls public API
- `meshlink-proof`: Validates real BLE behavior (crypto provider selection, hardware differences); needs internal access
- `meshlink-benchmark`: Measures throughput, latency, memory against budgets

Dokka, SKIE, and 100% coverage gate apply **only to `meshlink`**.

### 2.2 Source Set Structure

| Source Set | Purpose |
|------------|---------|
| `commonMain` | Shared business logic (security, routing, transfer, diagnostics) |
| `androidMain` | Platform-specific BLE glue, fallback crypto for Android API 26-32 |
| `iosMain` | Platform-specific BLE glue |
| `commonTest` | Pure JVM tests (protocol logic, wire codecs, crypto) |
| `androidHostTest` | Host-side Android tests (crypto fallback paths) |
| `androidDeviceTest` | Reserved for future use |

### 2.3 Public API Surface

`MeshLink` is a final instance-based class constructed from immutable settings
and an opaque platform environment:

```kotlin
class MeshLink(
    settings: MeshLinkSettings,
    environment: MeshLinkEnvironment,
) {
    val state: StateFlow<MeshLinkState>
    val peers: StateFlow<List<KnownPeer>>
    val transfers: StateFlow<List<Transfer>>
    val messages: Flow<Message>
    val diagnostics: Flow<DiagnosticEvent>
    val powerMode: StateFlow<PowerMode>
    val powerModeSettings: StateFlow<PowerModeSettings>

    suspend fun start()
    suspend fun pause()
    suspend fun resume()
    suspend fun stop()
    suspend fun setPowerMode(powerMode: PowerMode)

    suspend fun sendMessage(
        destination: PeerIdentity,
        payload: ByteArray,
        options: TransferOptions = TransferOptions.DEFAULT,
    ): MessageHandle

    suspend fun sendPayload(
        destination: PeerIdentity,
        source: TransferSource,
        options: TransferOptions = TransferOptions.DEFAULT,
    ): TransferHandle

    suspend fun revokeTrust(peer: PeerIdentity)
    suspend fun resetTrust(peer: PeerIdentity)
}

```plaintext
Note: This API is the target design; implementation is in progress via TDD.
The current meshlink/src/commonMain/kotlin/ch/trancee/meshlink/MeshLink.kt is a placeholder for BCV baseline.
```

Platform factory functions create `MeshLinkEnvironment`; Android context and
iOS framework types never enter shared protocol code. Multiple instances may
coexist, but one physical environment grants its BLE radio lease to only one
running instance. Virtual environments may run concurrently.

Applications address each remote installation by one stable PeerIdentity.
peerHint, TransportHandle, keys, key generations, proof chains, Noise epochs,
and route next hops remain internal; valid rotations and reconnects never make
the application replace identity or key material.

Lifecycle states are `UNINITIALIZED`, `CONFIGURED`, `RUNNING`, `PAUSED`, and `STOPPED`. The
constructor transitions `UNINITIALIZED` → `CONFIGURED`; `start()` transitions `CONFIGURED` → `RUNNING`.
Commands are serialized, idempotent at their target state, and restartable after
stop. Immediate failures use `MeshLinkException`; transfer failures use terminal
outcomes. The public API lives in `ch.trancee.meshlink`, with platform
differences hidden behind the environment factories and internal
`expect`/`actual` implementations.

**ADR**: docs/decisions/api/public-api-and-lifecycle.md

---

## 3. Core Data Models

### 3.1 PeerIdentity {#peer-identity-model}

**Stable 16-byte peer identifier.** Generated ONCE at install/first launch, stored permanently. NOT derived from public key — remains stable across key rotations.

```kotlin
@JvmInline value class PeerIdentity(private val parts: Pair<ULong, ULong>) {
    companion object {
        val ZERO: PeerIdentity = PeerIdentity(0UL to 0UL)
        fun generate(): PeerIdentity
        fun fromBytes(bytes: ByteArray): PeerIdentity  // requires 16 bytes
    }
    fun toByteArray(): ByteArray
    override fun toString(): String  // 32-char hex
}
```

| Property | Description |
|----------|-------------|
| `parts.first` | Lower 8 bytes (big-endian) |
| `parts.second` | Upper 8 bytes (big-endian) |

**SPEC-ANCHOR**: `peer-identity-model`

### 3.2 SeqNo {#seqno-model}

**Unsigned 32-bit sequence number with safe wrap-around comparison.** Per RFC 8966 §3.7, comparisons use signed interpretation. SeqNo is internal and deliberately NOT `Comparable` — modular serial ordering is not a globally transitive total order. Explicit operations (`isNewerThan`, `isOlderThan`, `distanceFrom`, `inc`) handle wrap-around safely.

```kotlin
@JvmInline value class SeqNo(private val value: UInt) {
    companion object {
        val ZERO: SeqNo = SeqNo(0u)
        val MAX_VALUE: SeqNo = SeqNo(UInt.MAX_VALUE)
        fun fromUInt(value: UInt): SeqNo = SeqNo(value)
        fun fromByteArray(bytes: ByteArray): SeqNo  // 4-byte big-endian deserialization
    }
    fun rawValue(): UInt = value
    fun toByteArray(): ByteArray                    // 4-byte big-endian serialization
    val isZero: Boolean
    fun isNewerThan(other: SeqNo): Boolean = (value - other.value).toInt() > 0
    fun isNewerThanOrEqualTo(other: SeqNo): Boolean = (value - other.value).toInt() >= 0
    fun isOlderThan(other: SeqNo): Boolean = other.isNewerThan(this)
    fun isOlderThanOrEqualTo(other: SeqNo): Boolean = other.isNewerThanOrEqualTo(this)
    operator fun inc(): SeqNo = SeqNo(value + 1u)  // wraps at 2^32
    fun distanceFrom(other: SeqNo): UInt         // modular unsigned forward distance
}
```

- Incremented **only on cold start** (`MeshLink.start()`)
- Self-reported by destination in `RouteAdvertisement` frames
- `rawValue()`/`fromUInt()` for logical wire serialization (value extraction)
- `toByteArray()`/`fromByteArray()` for 4-byte big-endian byte-level wire serialization
- `isNewerThanOrEqualTo` used by Babel feasibility condition (RFC 8966 §3.7)
- `distanceFrom` for route staleness diagnostics and gap analysis
- **SPEC-ANCHOR**: `seqno-model`

### 3.3 Scoreboard (Immutable) & MutableScoreboard {#scoreboard-model}

**Immutable bitfield for selective acknowledgement (SACK).** Bit N = 1 means chunk N received. Length = `ceil(totalChunks / 8)` bytes.

```kotlin
class Scoreboard(totalChunks: UInt) {
    fun markReceived(index: Int): Scoreboard
    fun markMissing(index: Int): Scoreboard
    fun isReceived(index: Int): Boolean
    fun isMissing(index: Int): Boolean
    fun missingChunks(): List<Int>
    fun receivedCount(): Int
    fun missingCount(): Int
    fun toByteArray(): ByteArray
}
```

**MutableScoreboard**: High-performance mutable accumulator for hot paths. Call `toImmutable()` for thread-safe snapshot.

**SPEC-ANCHOR**: `scoreboard-model`

### 3.4 RouteCandidate {#route-candidate-model}

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

Destination-owned identity, sequence, and key candidates arrive in a mandatory
signed RouteStatement. `nextHop` is inferred locally from the authenticated
adjacent sender. Routing models remain internal to the SDK.

**SPEC-ANCHOR**: `route-candidate-model`

### 3.5 TransferSession {#transfer-session-model}

```kotlin
data class TransferSession(
    val id: TransferId,
    val destination: PeerIdentity,
    val priority: Priority,
    val state: TransferState,
    val result: TransferResult?,            // Terminal outcome; null while non-terminal
    val chunkSize: Int,                 // Bounded by peer MTU, selected by PowerMode
    val totalChunks: UInt,
    val scoreboard: Scoreboard,
    val total: Long,
    val offset: Long,
    val startedAt: Instant,
    val expiresAt: Instant?,            // Deadline for ROUTE_UNAVAILABLE
    val retryCount: Int,
)
```

**SPEC-ANCHOR**: `transfer-session-model`

### 3.6 TransferState

| State | Terminal | Description |
|-------|----------|-------------|
| `AWAITING_DECISION` | No | Manifest validated; awaiting automatic capacity or host sink decision |
| `TRANSFERRING` | No | Actively transferring |
| `ROUTE_UNAVAILABLE` | No | Route lost, waiting for recovery |
| `RETRANSMITTING` | No | Selectively retransmitting missing chunks under adaptive RTO |

### 3.7 TransferResult {#transfer-result}

```kotlin
sealed interface TransferResult {
    data object Completed : TransferResult
    data object Cancelled : TransferResult
    data object Expired : TransferResult
    data class UnrecoverableFailure(val message: String) : TransferResult
    data class TrustFailure(val identity: PeerIdentity) : TransferResult
}
```

| Outcome | Carries | Notes |
|---------|--------|-------|
| `Completed` | — | All chunks acknowledged and delivered |
| `Cancelled` | — | Sender cancelled before completion |
| `Expired` | — | Time-to-live exceeded without completion |
| `UnrecoverableFailure` | `message: String` | Protocol, sink, or non-trust failure |
| `TrustFailure` | `identity: PeerIdentity` | Identity mismatch, revoked peer, or security policy violation |

TransferResult is a sealed interface (not an enum) because failure outcomes carry structured
data. Non-terminal progress is represented by TransferState (see §3.6); transfer status
returns null until a terminal condition occurs.

**SPEC-ANCHOR**: `transfer-result`

### 3.8 TransferId and MessageId {#transfer-id-model} {#message-id-model}

|A payload is identified by `(authenticated origin PeerIdentity, kind: TransferKind, id)`,
where `id` is a `TransferId` for `PAYLOAD` payloads or `MessageId` for `MESSAGE`
payloads. Both share the same 32-bit `UInt` wire slot; `kind` determines
interpretation and zero is reserved as invalid for either.

A transfer is identified by `(authenticated origin PeerIdentity, TransferId)`.
The origin allocates values from a durably reserved, monotonically increasing
32-bit counter. Zero is reserved as invalid.

```kotlin
@JvmInline value class TransferId private constructor(private val value: UInt) {
    override fun toString(): String  // Eight-character hex representation
}
```

The identifier is correlation data, not an authorization token. Active state
and completed-transfer tombstones use the complete origin-scoped tuple. Counter
ranges are persisted before use so a crash can skip values but cannot reuse an
allocated value under the same `PeerIdentity`.

**SPEC-ANCHOR**: `transfer-id-model`

**ADR**: docs/decisions/transfer/transfer-identifier.md

### 3.9 LinkQuality {#link-quality-model}

```kotlin
internal class LinkQuality(
    val smoothedRssi: Int,
    val normalizedRssi: UByte,
)
```

`LinkQuality` is a higher-is-better local RSSI observation used after routeCost
and hopCount during selection. L2CAP capability, health, and connection latency
belong to transport state and never enter this routing model.
RSSI normalization and the quadratic link-cost conversion are specified in §8.

**SPEC-ANCHOR**: `link-quality-model`

### 3.10 IdentityKey / HandshakeKey {#identity-key-model} {#handshake-key-model}

```kotlin
@JvmInline value class IdentityKey(private val bytes: ByteArray) {  // 32-byte Ed25519
    companion object {
        fun fromHex(hex: String): IdentityKey   // 64 hex chars
        fun fromBytes(bytes: ByteArray): IdentityKey
    }
    fun toByteArray(): ByteArray
    override fun toString(): String  // Hex
}

@JvmInline value class HandshakeKey(private val bytes: ByteArray) {  // 32-byte X25519
    // Same pattern as IdentityKey
}
```

**SPEC-ANCHOR**: `identity-key-model`, `handshake-key-model`

### 3.10.1 MeshLinkVersion {#meshlink-version}

**Semantic version for MeshLink releases.** Structured as `major.minor.patch` with `Comparable` support for ordering.

```kotlin
public data class MeshLinkVersion(
    public val major: Int,
    public val minor: Int,
    public val patch: Int,
) : Comparable<MeshLinkVersion> {
    public companion object {
        public fun parse(version: String): MeshLinkVersion
    }
    override fun toString(): String  // "major.minor.patch"
    override fun compareTo(other: MeshLinkVersion): Int
}
```

- `MeshLink.VERSION` exposes the current library version as a `MeshLinkVersion`
- `parse("1.2.3")` constructs from a semver string; throws `IllegalArgumentException` on malformed input
- Comparison is major → minor → patch, matching standard semver ordering
- **SPEC-ANCHOR**: `meshlink-version`

### 3.11 Enums (Shared) {#enums}

| Enum | Values | Notes |
|------|--------|-------|
| `KeyType` | `ED25519`, `X25519` | Distinguishes key purposes |
| `KeyRotationReason` | `PERIODIC`, `MANUAL`, `SECURITY_EVENT` | |
| `HandshakePattern` | `XX`, `IK` | XX for unpinned contact; IK for trusted pinned reconnect |
| `Priority` | `HIGH` (10min default timeToLive), `NORMAL` (5min), `LOW` (1min) | Affects delivery scheduling/time, never hop limit |
| `FrameType` | `MESH_ENVELOPE(0x00)`; routing `0x01`–`0x06`; transfer `0x20`–`0x24`; key/epoch `0x40`–`0x42` | Explicit UByte codes; never enum ordinals |
| `DecryptFailureReason` | `AUTHENTICATION_TAG_MISMATCH`, `REPLAY_DETECTED`, `SEQUENCE_NUMBER_MISMATCH`, `KEY_UNAVAILABLE`, `MALFORMED_FRAME` | |
| `TransportFallbackReason` | `L2CAP_UNAVAILABLE`, `L2CAP_CONNECT_FAILED`, `L2CAP_OPEN_TIMEOUT`, `L2CAP_STREAM_ERROR`, `L2CAP_STALLED`, `L2CAP_DROPPED_MID_TRANSFER`, `LOCAL_POLICY` | |
| `Bearer` | `GATT`, `L2CAP` | |
| `RegulatoryRegion` | `DEFAULT`, `EU` | EU clamps adv≥300ms, scan≤70% |
| `NoiseLayer` | `HOP_BY_HOP`, `END_TO_END` | |
| `NoiseSessionState` | `DISCONNECTED`, `HANDSHAKING_XX`, `HANDSHAKING_IK`, `ESTABLISHED`, `RENEWING`, `FAILED` | |
| `NoiseRole` | `INITIATOR`, `RESPONDER` | |
| `NoiseFailureReason` | `HANDSHAKE_TIMEOUT`, `HANDSHAKE_MESSAGE_MALFORMED`, `HANDSHAKE_MESSAGE_OUT_OF_ORDER`, `REMOTE_STATIC_KEY_MISMATCH`, `REMOTE_STATIC_KEY_UNKNOWN`, `REKEY_REJECTED`, `TRANSPORT_CLOSED`, `MAX_RETRIES_EXCEEDED`, `INTERNAL_ERROR` | |
| `PowerMode` | `HIGH`, `MEDIUM`, `LOW` | See §10 for parameters |
| `VerificationLevel` | `FULL`, `TOFU_PIN`, `NONE` | Handshake verification achieved |
| `TransferKind` | `MESSAGE (0x00)`, `PAYLOAD (0x01)` | Explicit UByte codes for wire format discrimination |
| `PayloadDecision` (internal) | `ACCEPTED (0x00)`, `REJECTED (0x01)` | Internal transfer sink decision |
| `L2capState` (internal) | `UNSUPPORTED`, `AVAILABLE`, `CONNECTING`, `ACTIVE`, `BACKING_OFF`, `DISABLED` | L2CAP channel health state |
| `PeerState` | `CONNECTED`, `DISCONNECTED` | Public API |
| `PeerLifecycle` (internal) | `CONNECTED`, `DISCONNECTED`, `GONE` | Internal runtime tracking |
| `PeerTrust` | `UNVERIFIED`, `VERIFYING`, `TRUSTED`, `MISMATCHED`, `REVOKED` | Trust classification per known peer |
| `KeyRotationState` (internal) | `CURRENT`, `GRACE_PERIOD`, `REVOKED` | Per-peer key status |
| `MeshLinkState` | `UNINITIALIZED`, `CONFIGURED`, `RUNNING`, `PAUSED`, `STOPPED` | Instance lifecycle (see §2.3) |
| `DiagnosticSeverity` | `DEBUG`, `INFO`, `WARN`, `ERROR` | Severity for diagnostic events (see §11) |

**SPEC-ANCHOR**: `enums`

---

## 4. Discovery & Identity

### 4.1 Advertisement Format

MeshLink emits a **connectable, undirected legacy BLE advertisement** while it
is available for peer connections. Connectability permits the subsequent GATT
and L2CAP setup; it does not establish application trust.

The packet advertises two service UUIDs:

| Service UUID | Size | Description |
|--------------|------|-------------|
| Protocol marker | 32 bits | Private, unassigned `0x4D455348` (`"MESH"` in ASCII); known scan-filter value, not Bluetooth SIG-assigned |
| Discovery metadata | 128 bits | Dynamic UUID whose 16 bytes contain the packed metadata below |

The dynamic 128-bit UUID uses this network byte layout:

| Field | Size | Description |
|-------|------|-------------|
| Protocol version | 3 bits | Current protocol version |
| Platform | 3 bits | `0=Android`, `1=iOS`, `2=Desktop`, `3-7=Reserved` |
| Power mode | 2 bits | `0=HIGH`, `1=MEDIUM`, `2=LOW`, `3=Reserved` |
| Mesh hash | 16 bits | Application isolation filter (FNV-1a of appId) |
| Capability flags | 8 bits | Bit 0 = L2CAP available; bits 1-7 reserved and zero |
| Peer hint | 12 bytes | Random rotating `peerHint`; unauthenticated candidate-deduplication value with limited privacy guarantees |

The two UUID AD structures plus the normal flags consume approximately 27 of
the 31 legacy-advertisement bytes. MeshLink does not add a local name or other
optional advertisement fields.

The dynamic UUID is a fast path. When a platform does not surface it — in
particular under iOS background advertising restrictions — the fixed MeshLink
GATT service exposes full PeerIdentity, version, key generation, 16-bit PSM, and a fresh nonce.
peerHint remains advertisement-only. Advertisement and GATT metadata are
untrusted until the security handshake authenticates identity and keys.

### 4.2 Mesh Hash Derivation {#mesh-hash}

```kotlin
// FNV-1a 32-bit of appId, truncated to 16 bits
meshHash = fnv1a_32(appId.toByteArray()) & 0xFFFF
```

**Purpose**: Reduces cross-application discovery. `meshHash` is only a
16-bit radio filter; collisions are resolved by the 128-bit `appHash` security
boundary.

```text
appHash = first128Bits(SHA-256("MeshLink app-id v1" || UTF8(appId)))
```

`appHash` is the 128-bit application-isolation value bound into security
handshakes. `handshakeHash` is reserved for the Noise transcript hash `h`; the
two names never refer to the same value.

### 4.3 Peer Hint

`peerHint` is a random 12-byte value generated by the platform CSPRNG. It is
carried only in the dynamic advertisement UUID and is not persisted, sent
through GATT, included in identity bindings, or used for authentication.

A new hint is generated whenever advertising starts and at a uniformly random
best-effort interval from 10 through 20 minutes. Suspended applications are not
awakened solely for rotation; an overdue hint rotates before advertising resumes
when the platform permits.

The hint coalesces short-lived scan candidates and may tentatively map a changed
`TransportHandle` to a known identity before mandatory IK. It never keys trust,
routes, transfers, sessions, or public peer state. A copied hint can cause only
bounded connection work.

Android controller RPA rotation is independent and cannot be atomically coupled
through portable app APIs. Consequently `peerHint` reduces installation-lifetime
static identification but does not guarantee unlinkability against continuous
passive observation.

### 4.4 Privacy Trade-offs

| Aspect | Trade-off |
|--------|-----------|
| Rotating peer hint | Reduces stable application identifiers but independently rotated RPA/advertisement fields can still permit continuous correlation |
| Protected | Full PeerIdentity and public keys are not advertised; trust is established only after GATT and Noise |
| Isolation | `meshHash` filters discovery; 128-bit `appHash` enforces the security boundary |

**ADRs**:

- docs/decisions/discovery/connectable-advertisement.md
- docs/decisions/discovery/mesh-hash-derivation.md
- docs/decisions/discovery/peer-hint-and-identity-races.md

---

## 5. Trust Model (TOFU)

### 5.1 Handshake Patterns

| Layer | No Trusted Pin | Trusted Current Pin |
|-------|----------------|---------------------|
| Direct hop | `Noise_XX_25519_ChaChaPoly_SHA256` | `Noise_IK_25519_ChaChaPoly_SHA256` |
| Routed E2E | Routed `Noise_XX_25519_ChaChaPoly_SHA256` | Routed `Noise_IK_25519_ChaChaPoly_SHA256` |

No application early data is sent in IK. A pinned mismatch fails closed and
never starts XX until explicit trust reset.

### 5.2 First-Contact Identity Binding and Automatic TOFU

Direct first contact uses `Noise_XX_25519_ChaChaPoly_SHA256`. Each peer carries
a canonical, Ed25519-signed identity binding in its encrypted handshake payload:

```text
IdentityBinding {
    version
    appHash
    identity
    ed25519PublicKey
    x25519PublicKey
    keyGeneration
}
```

Acceptance requires a valid signature, equality between the bound X25519 key
and Noise static key, compatible protocol version and appHash, and a valid key
generation. `peerHint` is deliberately outside the trust binding. Trust is not mutated until the complete XX
transcript yields `handshakeHash`.

```text
Discovery
    → GATT connection
    → Noise XX completes
    → identity binding fully validates
    → automatic TOFU pin
    → TRUSTED record with immutable seenAt and latest verifiedAt
```

Automatic TOFU proves continuity after first contact; it does not claim
out-of-band verification of the first real-world peer.

**ADR**: docs/decisions/crypto/identity-binding-and-fail-closed.md

### 5.3 Identity Distribution via Route Updates

- Route updates may carry signed candidate identity bindings for discovery and planning.
- Route-learned keys are candidates until the destination has a trusted pin.
- An unpinned E2E first contact always uses routed XX.
- Only an existing trusted, current destination pin permits routed IK.
- A route candidate never changes trust state by itself.

### 5.4 Key Rotation Protocol

**Triggers**: Periodic timer (default 3 days), manual API, security event

Each rotation creates a signed continuity proof carried inside hop-encrypted
control traffic:

```flatbuffers
KeyRotationProof {
    version: UByte
    appHash: [ubyte]                    // 16 bytes
    identity: [ubyte]               // unchanged 16 bytes
    oldGeneration: UInt
    newGeneration: UInt
    newIdentityKey: [ubyte]             // Ed25519, 32 bytes
    newHandshakeKey: [ubyte]            // X25519, 32 bytes
    reason: KeyRotationReason
    continuitySignature: [ubyte]        // old Ed25519 key, 64 bytes
    possessionSignature: [ubyte]        // new Ed25519 key, 64 bytes
}
```

Validation requires unchanged PeerIdentity/appHash, contiguous generation,
valid old-key continuity and new-key possession signatures, and a different
new key pair. Routing SeqNo remains independent and never resets.

Proofs are retained for the installation lifetime. A peer that missed rotations
performs rotation-recovery XX, receives the encrypted proof chain from its
pinned generation to current, and updates trust only after every link validates.
This XX mode is continuity recovery, never TOFU fallback. A missing, rolled-back,
or forked chain fails closed.

Local rotation persists the new binding, proof, generation, and grace material
atomically before exposing the new generation. Planned rotation may let existing
sessions drain for `rotationGracePeriod`; new sessions use the current binding.
Security-event rotation rejects old keys and closes old-key sessions immediately.
Transfers remain addressed by PeerIdentity and resume only after required session
authentication succeeds.

Applications continue using the same PeerIdentity and never manage keys,
generations, or proof chains.

**ADR**: docs/decisions/crypto/key-rotation-propagation.md

### 5.5 E2E Handshake Routing Over Mesh {#trust-record}

When the destination is not a direct neighbor:

```text
Phase 1: Establish authenticated hop sessions along the route
Origin --(GATT/L2CAP)--> Relay(s) --> Destination

Phase 2: Route E2E handshake messages
Origin wraps the next XX or IK message in RoutingFrame:
  RoutingFrame {
    destination: destination.identity,
    payload: e2eHandshakeMessage,
    hopLimit: UByte
  }
Relay(s) decrypt hop layer → re-encrypt → forward (no E2E inspection)

Phase 3: Destination returns the next handshake message over the mesh

Phase 4: Completed handshake yields fresh E2E traffic keys
```

**Security**: Relays cannot read E2E handshake or application content; they see
only routing information required for forwarding.

### 5.6 Trust Reset and Revocation

`resetTrust(identity)` cancels active work, deletes the peer's current
binding/rotation position, and permits a future first-contact XX/automatic TOFU
flow. It never changes local identity or keys.

`revokeTrust(identity)` cancels active work, persists `REVOKED`, rejects
future XX/IK/rotation recovery after identity resolution, and does not delete the
blocking record. An explicit reset is required to permit trust again.

Neither command accepts or exposes key material.

### 5.7 Revocation

- Explicit reset/revoke API action is required
- No silent re-trust on identity mismatch
- Revoked records persist until explicit reset

### 5.7 Noise Session Renewal

Every hop and E2E Noise session has a hard 24-hour lifetime. A fresh IK renewal
is scheduled with monotonic per-session jitter:

```text
renewalAt = establishedAt + uniform random duration in [21h, 23h]
expiresAt = establishedAt + 24h
```

The lower lexicographic `PeerIdentity` is the preferred initiator. The other
peer takes over only at an independently jittered instant from 23h30m through
23h50m when renewal has not completed. Equal full identities across two
installations fail closed.

Initiator retries use exponential backoff with full jitter and cannot cross hard
expiry. Idle suspended applications are not awakened solely for renewal. On
activity after expiry, old keys are discarded and fresh IK must complete before
protected traffic resumes.

Per-direction authenticated record counters also enforce:

```text
soft renewal threshold = 2^31 records
hard record limit      = 2^32 records
```

Fresh IK creates a pending epoch. The preferred initiator then sends an
authenticated `EpochCommit(newEpoch, finalOldOutboundCounter, handshakeHash)`
under the pending keys. The responder validates it and returns
`EpochAcknowledgement` with its own final old counter. The responder activates
new outbound traffic after sending the acknowledgement; the initiator activates
after receiving it.

Control retries are idempotent but use fresh record counters and never reuse an
AEAD nonce. Each side starts a 30-second old-epoch receive drain only when it
locally activates the new epoch. Old records above the authenticated final
counter are rejected. Pending new-epoch application records remain bounded and
undelivered until activation. Missing transfer data is retransmitted under the
new epoch through SACK. Traffic keys are never persisted.

**ADRs**:

- docs/decisions/crypto/crypto-design.md
- docs/decisions/crypto/noise-session-renewal.md

---

## 6. Transport Layer

### 6.1 Bearer Selection

| Traffic Type | Preferred Bearer | Fallback |
|--------------|------------------|----------|
| Control plane | GATT (unconditionally) | None — GATT always available |
| Data plane | L2CAP CoC | GATT with same correctness guarantees |

**Control plane MUST work over GATT alone** for reliability.

### 6.2 GATT Service and Negotiation

The private unassigned `0x4D455348` service exposes:

| Characteristic | UUID | Properties |
|----------------|------|------------|
| Metadata | `4D455348-0001-1000-8000-00805F9B34FB` | read |
| Channel | `4D455348-0002-1000-8000-00805F9B34FB` | write, write-without-response, notify, indicate |

The private component namespace reserves `0000` for the service, `0001` for
metadata, `0002` for channel, and `0003`–`00FF` for future characteristics.
Assigned component values are never reused.

Control uses write-with-response/indication. GATT fallback data uses
write-without-response/notification under MeshLink SACK. Noise cannot start
until the channel subscription is confirmed.

Negotiation sequence:

1. GATT connection establishes
2. `Noise_XX_25519_ChaChaPoly_SHA256` handshake completes (control plane)
3. After Noise validates GATT metadata, attempt L2CAP when its 16-bit PSM is valid and non-zero; the advertisement capability bit is only a hint
4. On CoC success, promote data-plane traffic to CoC
5. On CoC failure, continue on GATT

### 6.3 Bearer Framing

GATT fragments are connection-local:

```text
GattFragment {
    index: UShort
    if index == 0: totalLength: UShort
    payload: remaining bytes
}
```

Index zero starts; completion occurs at exact totalLength. Continuations are
strictly sequential. There are no flags or record IDs. Maximum frame size is
65,535 bytes; unauthenticated connections are limited to 4 KiB. State is scoped
by `(TransportHandle, ConnectionContext.generation)` and direction, so concurrent
peers cannot collide.

L2CAP uses `UShort frameLength` little-endian followed by exact frame bytes.
Both bearers yield identical MeshLink Wire Codec frames.

**ADR**: docs/decisions/transport/gatt-channel-and-framing.md

### 6.4 Fallback Reasons (Machine Observable)

| Reason | Description |
|--------|-------------|
| `L2CAP_UNAVAILABLE` | Advertisement/GATT capability indicates no usable L2CAP channel |
| `L2CAP_CONNECT_FAILED` | CoC connection failed |
| `L2CAP_OPEN_TIMEOUT` | Channel did not open before deadline |
| `L2CAP_STREAM_ERROR` | Stream read/write failed |
| `L2CAP_STALLED` | Channel made no bounded forward progress |
| `L2CAP_DROPPED_MID_TRANSFER` | CoC channel dropped during transfer |
| `LOCAL_POLICY` | Local configuration disabled CoC |

### 6.5 L2CAP Health and Circuit Breaking

L2CAP capability is transport state, not routing LinkQuality. Open
failure/timeout, EOF, stream error, stall, partial-frame timeout, or channel drop
immediately moves new data assignment to GATT while preserving route, trust,
E2E session, and transfer state. SACK retransmits missing chunks.

Per-peer process-local backoff is 15–30 seconds, 1–2 minutes, then 5–10 minutes.
A fourth failure disables L2CAP for the process lifetime. `failureCount` resets
only after ten healthy minutes or one error-free transfer of at least 1 MiB.
Health is not persisted.

### 6.6 Background Operation

`MeshLinkSettings.enableBackground` is immutable and defaults to false. When
true, start validates platform-authorized background integration.

Android host apps own the connected-device foreground service, notification,
permission UX, and restart policy; MeshLink owns BLE state and PendingIntent
helpers. iOS host apps own Bluetooth usage/background declarations and launch
forwarding; MeshLink owns Core Bluetooth restoration identifiers and manager
reconstruction.

Background execution is best effort. Suspension may delay/coalesce callbacks;
OS restoration rebuilds platform and persisted identity/trust only. Noise
traffic keys, routes, active transfers, peerHint, and TransportHandle never
survive process death. Force-stop/force-quit has no relaunch guarantee.

**ADRs**:

- docs/decisions/transport/mtu-negotiation.md
- docs/decisions/transport/background-operation.md

---

## 7. Security Layer

### 7.1 Crypto Primitives (All Validated Against Wycheproof)

| Primitive | Standard | Wycheproof Vectors |
|-----------|----------|-------------------|
| X25519 | RFC 7748 | 518 (264 valid + 254 acceptable) |
| Ed25519 | RFC 8032 | 150 (88 valid + 62 invalid) |
| ChaCha20-Poly1305 | RFC 8439 | 325 (256 valid + 69 invalid) |
| HKDF-SHA256 | RFC 5869 | 86 (83 valid + 3 invalid) |
| HMAC-SHA256 | RFC 2104 | 174 (66 valid + 108 invalid) |
| SHA-256 | RFC 6234 | Covered via other primitives |

### 7.2 Fail-Closed Rules {#fail-closed}

Fail closed is the project-wide default. Uncertainty cannot create authority,
plaintext, or partially validated state.

- Malformed or incompatible input is rejected before durable or protocol-state mutation.
- Invalid X25519 public keys fail before HKDF derivation.
- Signature, identity-binding, Noise, or AEAD failures terminate the affected operation.
- Pinned identity or key mismatch never falls back to first-contact TOFU without explicit reset.
- Replay rejection occurs without response amplification or state mutation.
- No failure path retries as plaintext, reuses stale keys, silently downgrades security, or regenerates corrupted identity state.
- Runtime configuration failure preserves the previous effective values.
- A specified fallback is allowed only when it preserves required security properties, is bounded and observable, and has dedicated tests.
- Failure is contained to the smallest safe frame, transfer, peer, or instance scope rather than crashing unrelated work.
- Typed diagnostics and errors contain no private keys, shared secrets, session keys, KDF output, or payload plaintext.

**ADR**: docs/decisions/crypto/identity-binding-and-fail-closed.md

### 7.3 Provider and Private-Key Boundaries

Primitive selection is platform-first per primitive after once-per-process
known-answer, negative, secure-random, and private-key persistence round-trip
tests before advertising. Unsupported/failing primitives use the validated pure-Kotlin fallback;
if neither validates, startup fails. Secure random is platform-mandatory and has
no deterministic production fallback.

Private keys remain opaque provider-owned handles. Android fallback/exportable
keys are AES-256-GCM wrapped by an Android Keystore key in backup-excluded
app-private atomic storage. iOS uses non-synchronizable
`AfterFirstUnlockThisDeviceOnly` Keychain items. MeshLink remains inactive after
reboot until first unlock.

Self-test keys are ephemeral and never enter installation storage. Provider
selection reruns after process/OS/provider changes and must fit the 500 ms cold-
start budget.

Private bytes never enter public APIs, strings, logs, exceptions, diagnostics,
wire frames, crash reports, or test reports. Storage corruption fails closed and
never silently regenerates keys under the same PeerIdentity.

**ADR**: docs/decisions/crypto/private-key-handling.md

### 7.4 Constant-Time Policy {#constant-time}

All operations on secret data (private keys, shared secrets, session keys, KDF output) **MUST** use constant-time implementations:

- Scalar multiplication (X25519): Montgomery ladder
- Scalar arithmetic (Ed25519): No data-dependent branches
- AEAD decrypt/verify: Constant-time tag comparison
- HKDF/HMAC: Constant-time HMAC implementation
- Array comparisons: `MessageDigest.isEqual` or equivalent

**ADR**: docs/decisions/crypto/constant-time-policy.md

### 7.5 Replay Window {#replay-window}

**Sliding bitmap window** per RFC 9147:

- **Window size**: 64 packets (fixed; see `ReplayWindow.WINDOW_SIZE`)
- **Bitmap shift direction**: right-shift (`ULong.shr`) so that existing bit positions correctly map to nonces under the new baseNonce after an advance; bits shifted off the low end are evicted
- **Per-epoch numbering** (epoch increments on KeyUpdate); `advanceEpoch()` resets the bitmap and epoch counter
- **Deprotect-before-advance** (RFC 9147 §4.2): the replay check is performed before any bitmap mutation, preventing replay and fresh nonces within the window from taking different code paths
- **Replay detection** on both hop-by-hop and E2E layers

**ADR**: docs/decisions/crypto/replay-window.md

### 7.6 Error Hierarchy (Sealed) {#error-hierarchy}

```text
MeshLinkException
├── ConfigurationException
├── LifecycleException
├── PermissionException
├── BluetoothException
├── StorageException
├── CryptoException
├── TrustException
├── RoutingException
└── TransferException
```

All public immediate command failures use typed `MeshLinkException` subtypes
with explicit stable UShort `ErrorCode` values grouped by category: 0x01xx
configuration, 0x02xx permission, 0x03xx bluetooth, 0x04xx crypto, 0x05xx routing,
0x06xx transfer, 0x07xx storage, 0x08xx lifecycle, 0x09xx transport, 0x0Axx trust,
and 0x0Fxx internal. Codes never use enum ordinals or carry sensitive context.
Platform exceptions are wrapped and never leak. Untrusted parsing uses sealed
internal results; long-running payload failures use terminal status/outcome.
`CancellationException` remains normal coroutine cancellation.

**ADR**: docs/decisions/model/error-hierarchy.md

---

## 8. Routing Layer

### 8.1 Model and Selection

MeshLink uses a Babel-inspired distance-vector protocol over authenticated BLE
neighbors. Route state separates lower-is-better additive `routeCost`,
independent `hopCount`, higher-is-better local `linkQuality`, and local
`nextHop`.

Selection uses feasible candidates, lowest routeCost, lowest hopCount, highest
local linkQuality, then lowest lexicographic nextHop.

### 8.2 Link and Path Cost

RSSI normalizes from 0 at -100 dBm through 255 at -30 dBm. Shared code computes:

```text
qualityLoss    = 255 - normalizedRssi
qualityPenalty = ceil(qualityLoss² / 255)
linkCost       = 64 + qualityPenalty
routeCost      = saturating sum of linkCost values
```

Link cost ranges from 64 through 319; `UInt.MAX_VALUE` means infinity. Strong
multi-hop paths may beat weak direct paths.

### 8.3 Smoothing and Hysteresis

RSSI uses `smoothed = (3 × previous + sample) / 4`. Cost updates advertise only
after at least 3 dB smoothed change. A candidate must remain best for two
observations spanning one second and improve by
`max(16, currentRouteCost / 10)`. Loss, withdrawal, expiry, infeasibility,
hop-limit violation, or trust failure switches immediately.

### 8.4 Feasibility and Sequence Ownership

Feasible distance is `(sequenceNumber, routeCost)`. A candidate is feasible when
its sequence is newer, or equal with lower cost.

Each destination owns and persists its 32-bit sequence. It increments before
advertising on cold start, valid sequence advancement, or internal route reset. It
does not change for reconnect, peerHint/TransportHandle/RPA change, RSSI update,
refresh, Noise renewal, key rotation, or bearer migration.

`SeqNo` is internal and not Comparable. It exposes explicit modular operations,
including `distanceFrom`; an exact half-range difference is ambiguous.

### 8.5 Feasibility-Starvation Recovery

```text
RouteSequenceAdvancement {
    requester
    destination
    sequenceNumber
    requestId
    hopLimit
}
```

Send immediately through the best known authenticated next hop. After 500 ms
without a sufficiently new route, fan out to other authenticated neighbors.
Relays deduplicate `(requester, requestId)`, exclude the incoming neighbor, and
enforce hopLimit. One request per destination is active; attempts expire after
three seconds, dedup state after 30 seconds, and each peer may trigger at most
three destination advancements per minute. Failed attempts retry with
exponential backoff and full jitter.

### 8.6 Signed Statements and Advertisements

```text
RouteStatement {
    version
    appHash
    destination
    sequenceNumber
    identityBinding
    signature
}

RouteAdvertisement {
    statement
    routeCost
    hopCount
}
```

Destination signatures are mandatory. Relays may update routeCost and hopCount
under hop authentication. `nextHop` remains local and is inferred from the
authenticated adjacent sender. An authenticated malicious relay can still lie
about mutable path fields; Byzantine path proofs are outside v0.1.

### 8.7 Encrypted Routing Control

Advertisements, withdrawals, digests, sequence advancements, synchronization, and
snapshots are all hop-encrypted/authenticated after adjacent Noise succeeds.
Authentication failure drops a frame before routing parsing. No plaintext retry
or downgrade exists.

### 8.8 Per-Neighbor Split Horizon and Synchronization

Each RouteExport excludes candidates whose nextHop is that export neighbor. A
self-origin route is always exported; an alternate route through another
neighbor may be exported. If a previously exported route becomes reachable only
through the recipient, send immediate RouteWithdrawal. Explicit withdrawal
replaces poison reverse.

```text
RouteExport[neighbor] // canonical routes last advertised to neighbor
RouteImport[neighbor] // canonical wire routes last accepted from neighbor

RouteDigest           // summary of RouteExport
RouteSynchronization  // receiver requests synchronization
RouteSnapshot         // sender returns complete RouteExport
```

A digest compares with `RouteImport[sender]`, never the receiver's full table.
On mismatch, the receiver sends RouteSynchronization; the sender returns RouteSnapshot; the
receiver validates and atomically replaces only that sender's import. Digest
input sorts by destination and includes RouteStatement, routeCost, and hopCount,
excluding nextHop, local linkQuality, timers, and feasibility state.

RouteDigest is the first 64 bits of SHA-256 over canonical field encoding, never
raw implementation-buffer layout. RouteDigest, RouteSynchronization, and RouteSnapshot carry a
per-adjacency UInt revision that starts at zero for a fresh hop Noise session and
increments whenever RouteExport changes. Stale revisions are rejected.

### 8.9 Route Update Triggers {#ttl-by-priority}

| Trigger | Behavior |
|---------|----------|
| Authenticated direct link up | Immediate self-origin statement/advertisement |
| Smoothed RSSI threshold | Jittered differential advertisement |
| Periodic refresh | Bounded jitter |
| Expiry/withdrawal/trust failure | Immediate |
| Digest mismatch | Immediate RouteSynchronization |
| Successful sequence advancement | Immediate destination advertisement |

Ordinary updates coalesce under the internal one-second cooldown. Withdrawal,
trust invalidation, sequence recovery, and requested synchronization do not wait.

### 8.10 Time-to-Live and Hop Limit

`TransferOptions.timeToLive` is elapsed delivery lifetime; priority defaults are
10, 5, and 1 minutes. `maximumHopCount = 16` is an independent fixed protocol
bound for every priority. Envelopes start at 16, relays decrement before
forwarding, and zero is dropped. RouteAdvertisement rejects hopCount at or above
16; RouteSequenceAdvancement uses the same bound. Neither value is publicly
configurable as the other.

**ADR**: docs/decisions/routing/routing-design.md

---

## 9. Transfer Layer

### 9.1 Manifest and Acceptance

Every finite payload starts with an E2E-encrypted PayloadManifest carrying kind
(MESSAGE/PAYLOAD), source-owned ID, origin/destination, priority,
timeToLive, totalLength, chunkSize, and chunkCount.

Messages up to 64 KiB auto-accept under a 2 MiB global incomplete-message budget.
Large transfers require a host TransferSink. At most three offers per peer and
eight globally wait up to 30 seconds in AWAITING_DECISION. No chunks transmit
before PayloadDecision ACCEPTED.

### 9.2 Lifecycle and Lifetime

```text
AWAITING_DECISION
    ├── accepted → TRANSFERRING
    ├── rejected/timeout → FAILED
    └── cancelled → CANCELLED

TRANSFERRING
    ├── all chunks acknowledged → COMPLETED
    ├── route lost → ROUTE_UNAVAILABLE
    ├── missing chunks → RETRANSMITTING
    ├── error/trust failure → FAILED
    └── cancelled → CANCELLED

ROUTE_UNAVAILABLE / RETRANSMITTING
    ├── recovered → TRANSFERRING
    ├── timeToLive exhausted → EXPIRED
    └── cancelled → CANCELLED
```

Origin owns a monotonic lifetime; manifest duration is UInt milliseconds and no
wall-clock timestamp is trusted. Relays forward cut-through with bounded queues
and own no persistent payload/retry state.

### 9.3 Chunks and Selective Acknowledgement

PayloadChunk contains only kind, id, index, and payload. Offset, length, and
finality derive from the manifest.

PayloadAcknowledgement contains kind, id, `start`, and a fixed 32-byte bitmap.
All indices below start are cumulative; bit n acknowledges start+n. Sender keeps
at most 256 chunks in flight and rereads missing data through TransferSource.

ACK emits after 32 chunks; after 100/250/500 ms in HIGH/MEDIUM/LOW; or
immediately for gap, full window, final chunk, or retransmission probe.

### 9.4 Retransmission Timeout

RTO is retransmission timeout: the wait without adequate acknowledgement before
selective retransmission. Initial RTO is
`clamp(1s + hopCount×250ms + powerAckDelay, 1s, 10s)`. Valid non-retransmitted
samples update smoothed RTT and variation; RTO becomes
`smoothedRtt + max(4×rttVariation, 250ms)`, clamped 1–30 seconds. Karn's rule
excludes retransmitted samples. Unsuccessful timeout doubles RTO up to 30
seconds. Route/bearer change resets the relevant estimator. RTO never extends
remaining timeToLive.

### 9.5 Wire Frames

| Frame | Code | Purpose |
|-------|------|---------|
| `PAYLOAD_MANIFEST` | `0x20` | Offer and immutable chunk/lifetime contract |
| `PAYLOAD_DECISION` | `0x21` | Accepted or typed rejection |
| `PAYLOAD_CHUNK` | `0x22` | Minimal indexed payload bytes |
| `PAYLOAD_ACKNOWLEDGEMENT` | `0x23` | 256-chunk sliding SACK window |
| `PAYLOAD_CANCELLATION` | `0x24` | Idempotent message/transfer cancellation |

Every frame repeats kind; identity is `(origin, kind, id)`. All are E2E Noise
records inside hop-encrypted MESH_ENVELOPE.

Receiver delivery is at most once per identity. Sender SUCCESS requires full
acknowledgement; TIMEOUT means confirmation is unknown, not proof of
non-delivery. Large totalLength is allowed through checked Long/UInt/UShort
representation and host sink policy; SDK memory remains window-bounded.

**ADR**: docs/decisions/transfer/payload-transfer-protocol.md

**SPEC-ANCHOR**: `transfer-session-model`, `scoreboard-model`

---

## 10. Power Management

### 10.1 Power Modes {#power-mode-settings}

| Parameter | HIGH | MEDIUM | LOW |
|-----------|------|--------|-----|
| Scan duty cycle | 20% | 10% | 5% |
| Advertisement interval | 100 ms | 500 ms | 1000 ms |
| Active connection interval | 7.5–15 ms | 15–30 ms | 30–60 ms |
| Idle connection interval | 15.0 ms | 30.0 ms | 60.0 ms |
| Max concurrent connections | 8 | 4 | 2 |
| Chunk size | 512 B | 256 B | 128 B |
| Max retries | 10 | 5 | 3 |
| Retry budget | 60 s | 30 s | 15 s |
| Grace period (disconnect→GONE) | 15 s | 30 s | 45 s |

### 10.2 Active and Idle Connections

Handshakes, urgent control, acknowledgements, and transfer data request the
mode's active interval. After five seconds with no queued work, every mode
requests a 500–1000 ms idle interval. New work immediately requests active
latency again. Platform clamping/negotiation may delay or alter requests;
effective values are observable.

The constitutional 5% scan-duty battery target applies to LOW/background idle
benchmarks. HIGH and MEDIUM intentionally trade battery for discovery and
throughput.

### 10.3 EU Regulatory Clamping

When `regulatoryRegion = RegulatoryRegion.EU`:

- Advertisement interval **clamped to ≥ 300 ms**
- Scan duty cycle **clamped to ≤ 70%**

Applied at runtime, observable in diagnostics.

### 10.4 Grace Periods

Per-mode grace period controls `PeerLifecycle` transition `DISCONNECTED → GONE`. During grace period:

- Routes can degrade before full retraction
- Transfers can pause instead of being abandoned
- Host app observes disconnected presence in the `peers` snapshot

**ADR**: docs/decisions/power/power-mode-behavior.md

**SPEC-ANCHOR**: `power-mode-settings`

---

## 11. Diagnostics & Events

### 11.1 Public Observation {#diagnostic-event}

```kotlin
val peers: StateFlow<List<KnownPeer>>
val transfers: StateFlow<List<Transfer>>
val messages: Flow<Message>
val diagnostics: Flow<DiagnosticEvent>
```

`peers` includes canonical identities across unverified, verifying,
trusted, mismatched, and revoked trust states. Advertisement-only
candidates are not canonical peers. `seenAt` is the immutable instant the full
identity was first learned; `verifiedAt` is the nullable instant of the latest
successful verification.

Messages are complete, authenticated incoming `Message` values. Transfers are
current pending or active message/finite-transfer operations. Diagnostics are
bounded events rather than retained history.

### 11.2 Peer Lifecycle (Internal)

```text
CONNECTED (active BLE link)
    └── BLE link lost → DISCONNECTED (grace period active)
            ├── BLE reconnects → CONNECTED
            └── Grace period expires → GONE (ephemeral cleanup, trust retained)
```

Grace period: HIGH=15s, MEDIUM=30s, LOW=45s.

### 11.3 Diagnostic Event Metadata

Every diagnostic event carries explicit stable metadata:

```kotlin
val code: DiagnosticCode
val severity: DiagnosticSeverity
val occurredAt: Instant
```

`occurredAt` names the event instant. Diagnostic codes use explicit stable ranges
aligned with the Exception `ErrorCode` ranges (see §7.6): 0x01xx configuration,
0x02xx permission, 0x03xx bluetooth, 0x04xx crypto, 0x05xx routing, 0x06xx transfer,
0x07xx storage, 0x08xx lifecycle, 0x09xx transport, 0x0Axx trust, and 0x0Fxx
internal. Events may include redacted PeerIdentity, MessageId/TransferId, frame
code, provider label, and error code, but never raw handles, addresses, keys,
ciphertext, payloads, or platform exception text.

### 11.4 Diagnostic Event Hierarchy

```kotlin
sealed interface DiagnosticEvent {
    data class PowerModeEffectiveEvent(...) : DiagnosticEvent    // 0x01xx configuration
    data class HandshakeEvent(...) : DiagnosticEvent              // 0x04xx crypto
    data class KeyRotationEvent(...) : DiagnosticEvent            // 0x04xx crypto
    data class NoiseSessionEvent(...) : DiagnosticEvent           // 0x04xx crypto
    data class RouteDecryptFailureEvent(...) : DiagnosticEvent   // 0x05xx routing
    data class RouteDigestMismatchEvent(...) : DiagnosticEvent   // 0x05xx routing
    data class TransferBearerEvent(...) : DiagnosticEvent  // 0x06xx transfer
    data class TransferSessionTransitionEvent(...) : DiagnosticEvent // 0x06xx transfer
    data class TransferFailureEvent(...) : DiagnosticEvent      // 0x06xx transfer
    data class TransportFallbackEvent(...) : DiagnosticEvent    // 0x09xx transport
    // ... extensible per diagnostic-events.yaml catalog
}
```

### 11.5 Severity Levels

`DEBUG`, `INFO`, `WARN`, `ERROR` are fixed by event consequence and mapped to
platform logging (Logcat, unified logging) without allowing configuration to
downgrade security/error events. Routine events may be coalesced; security
failures retain counters. ERROR does not imply process termination.

### 11.6 Diagnostic Flow Delivery

An internal bounded channel serializes diagnostic producers. Applications
collect the public flow in their own coroutine context; MeshLink never invokes
application callbacks from BLE or protocol threads. Buffer saturation preserves
security/error events ahead of lower-severity events and emits a summarized
overflow event.

**ADR**: docs/decisions/diagnostics/flow-delivery.md

**SPEC-ANCHOR**: `diagnostic-event`

---

## 12. Build & Quality Constraints

### 12.1 Performance Budgets (CI-Enforced)

| Target | Budget | Measurement |
|--------|--------|-------------|
| Throughput (1-hop L2CAP) | ≥80 KB/s (Android Pixel 6+), ≥60 KB/s (iOS iPhone 12+) | `meshlink-benchmark` |
| Latency (1-hop, 256B, p95) | <50 ms after connection established | `meshlink-benchmark` |
| Memory (steady state, 8 peers) | ≤8 MB heap | `meshlink-benchmark` |
| Battery (LOW/background idle) | Target ≤5% scan duty; request 500–1000 ms idle interval after 5 s without queued work | Effective power settings |
| Cold start | <500 ms from `mesh.start()` to first advertisement | `meshlink-benchmark` |
| Routing convergence | ≤3 s for 10-node topology change (virtual transport) | `meshlink-benchmark` |
| Wire codec encode/decode | <1 μs/message (JVM benchmark) | `kotlinx-benchmark` |

**Regression gate**: >10% vs last committed benchmark blocks merge.

### 12.2 Code Quality (Per CONSTITUTION.md)

- Detekt zero suppressions (test suppressions require justification)
- ktfmt formatting before every commit
- Full descriptive identifiers (no `cfg`, `mgr`, `idx`, `tmp`, `msg`)
- BCV tracks public API; `.api` diff requires version-bump rationale
- `explicitApi()` enabled
- No `TODO` comments in merged code
- Dependencies pinned to exact versions, upgraded promptly

### 12.3 Platform Minimums

- Android API 26 (Android 8.0)
- iOS 14
- Higher APIs guarded at runtime

### 12.4 Runtime Dependencies

Only `kotlinx-coroutines-core` in shipped `:meshlink` artifact. `kotlinx-datetime` for `Duration` in settings DSL is acknowledged exception.

---

## 13. Testing & Verification

### 13.1 Test Suite Structure

| Layer | Location | Coverage |
|-------|----------|----------|
| Unit/JVM | `commonTest` | Full (crypto, routing, transfer, wire codec) |
| Host/Android | `androidHostTest` | Crypto fallback paths |
| Device/Android | `meshlink-proof/android/` | Real BLE behavior |
| Device/iOS | `meshlink-proof/ios/` | Real BLE, Secure Enclave, background modes |
| Reference app | `meshlink-reference` | Public API consumption only |

### 13.2 Key Test Requirements

- **Crypto**: Validated against Wycheproof vectors (all primitives)
- **Multi-node**: Virtual mesh harness (no physical hardware in CI)
- **Wire compatibility**: Hex test vectors in `commonTest/resources/wire-compat/`
- **Cross-platform**: Byte-for-byte equality across targets
- **No emulator/simulator BLE tests** — they don't implement real radios

### 13.3 Acceptance Criteria Per Layer

| Layer | Criteria |
|-------|----------|
| Data Model / Trust | Wire vectors, malformed input rejection |
| Discovery / Advertisement | Two-service-UUID format, peerHint rotation/deduplication, RPA races, GATT identity resolution |
| Security Contract | Wycheproof vectors, signed identity binding, appHash isolation, fail-closed on all edge cases |
| Routing Control | Convergence under virtual harness, seqno correctness |
| Chunked Transfer | Fixed 256-chunk sliding SACK, cut-through relay, adaptive RTO, and retry bounds |
| Power Policy | Mode-to-parameter mapping, EU clamping observable |
| Public API | Identical Android/iOS surface, lifecycle events |

---

## 14. Settings Model

### 14.1 Settings DSL

```kotlin
// Entry point: ch.trancee.meshlink.meshLinkSettings
val settings = meshLinkSettings {
    appId = "com.example.myapp" // stable normalized UTF-8 application/profile ID; max 255 bytes
    powerMode = PowerMode.HIGH
    regulatoryRegion = RegulatoryRegion.EU
    enableBackground = true
    
    keyRotation {
        interval = Duration.days(1)
        rotationGracePeriod = Duration.minutes(30)
        compromiseGracePeriod = Duration.ZERO
    }
    
    transfer {
        maxRetries = 3
        chunkSize = 512
        maxTransfersPerPeer = 2
    }
    
    routing {
        routeAdvertisementChangeThreshold = 3
        routeDigestInterval = Duration.minutes(5)
        routeExpiry = Duration.minutes(15)
        maxRoutes = 256
    }
    
    diagnostics {
        eventBufferSize = 1000
        emitLog = true
    }
}
```

### 14.2 Settings Classes (Immutable)

```kotlin
data class MeshLinkSettings(
    // Required, non-empty normalized UTF-8; max 255 bytes and stable across updates.
    val appId: String,
    val powerMode: PowerMode,
    val regulatoryRegion: RegulatoryRegion,
    val enableBackground: Boolean,
    val keyRotation: KeyRotationSettings,
    val transfer: TransferSettings,
    val routing: RoutingSettings,
    val diagnostics: DiagnosticsSettings
)

data class KeyRotationSettings(
    val interval: Duration = Duration.days(3),
    val rotationGracePeriod: Duration = Duration.hours(1),
    val compromiseGracePeriod: Duration = Duration.ZERO
)

data class TransferSettings(
    val maxRetries: Int = 5,
    val chunkSize: Int = 256,
    val maxTransfersPerPeer: Int = 3
)

data class RoutingSettings(
    val routeAdvertisementChangeThreshold: Int = 3,
    val routeDigestInterval: Duration = Duration.minutes(5),
    val routeExpiry: Duration = Duration.minutes(15),
    val maxRoutes: Int = 256
)

data class DiagnosticsSettings(
    val eventBufferSize: Int = 1000,
    val emitLog: Boolean = false
)
```

### 14.3 Implementation

- **Lambda DSL** (`meshLinkSettings { }`) is primary API — Kotlin idiom, readable, type-safe
- **Imperative builder** retained for programmatic construction (e.g., from settings file)
- Both paths produce identical `MeshLinkSettings` instances
- Settings remain immutable for an instance except for runtime power-mode changes through `MeshLink.setPowerMode`
- `PowerMode.settings` contains nominal values; `MeshLink.powerModeSettings` exposes values after regulatory and platform clamping
- Diagnostics are collected from `MeshLink.diagnostics`; no callback is configured in settings
- `MeshLinkSettings` is the **source of truth** for defaults — `specs/catalogs/settings.yaml` is checked against it. Static settings validation occurs at construction; runtime prerequisites are checked by `start()`.

**SPEC-ANCHOR**: `setting-model`

**ADR**: docs/decisions/model/settings-model.md (rationale only)

---

## 15. Future Work

### 15.1 PQ-Hybrid Key Establishment

**Candidate**: Conservative hybrid (C2) — classical + ML-KEM-768 staged extension

**ADR**: docs/decisions/crypto/pq-hybrid-candidate-matrix.md

### 15.2 Throughput-Based Link Metrics

Replace RSSI proxy with measured throughput for routing decisions.

### 15.3 Payload Compression

Optional zlib/Brotli/Zstd for large payloads.

### 15.4 Group Messaging

MLS (RFC 9420) integration for multi-recipient E2E encryption.

---

## Machine-Readable Specification Layout

| Path | Ownership | Purpose |
|------|-----------|---------|
| `specs/codecs/frames.yaml` | Authored normative | Exact MeshLink frame codes, fields, widths, bounds, protection, and evolution |
| `specs/codecs/enums.yaml` | Authored normative | Explicit enum storage/codes/reserved ranges/unknown behavior; never ordinals |
| `specs/codecs/models.yaml` | Authored normative | Reusable encoded value layouts |
| `specs/protocol/state-machines.yaml` | Authored normative | Protocol states, guards, timing, retry, and invariants |
| `specs/catalogs/diagnostic-events.yaml` | Source-derived catalog | Diagnostic event names, fields, and severity |
| `specs/catalogs/settings.yaml` | Source-derived catalog | Public settings and defaults |
| `specs/traceability/specification-map.yaml` | Generated traceability | SPEC ↔ ADR ↔ codec ↔ code ↔ test mapping and implementation status |
| `specs/product/`, `specs/epics/`, `specs/tests/` | Authored planning | Scope, story plans, and test architecture |

Codec/protocol files are reviewed source, not generated runtime code. The custom
pure-Kotlin MeshLink Wire Codec must conform to them. Catalog/traceability update
tooling must make CI fail when committed projections are stale.

---

## Traceability Index

| Spec Section | ADR(s) | Code Location |
|--------------|--------|---------------|
| §1 Vision | — | — |
| §2 Architecture | docs/explanation/module-structure.md | meshlink/build.gradle.kts |
| §3 Data Models | docs/decisions/model/data-model.md | meshlink/src/commonMain/kotlin/ch/trancee/meshlink/model/ |
| §4 Discovery | docs/decisions/discovery/connectable-advertisement.md, docs/decisions/discovery/mesh-hash-derivation.md | specs/codecs/frames.yaml |
| §5 Trust/TOFU | docs/decisions/crypto/crypto-design.md | specs/codecs/enums.yaml, specs/protocol/state-machines.yaml |
| §6 Transport | docs/decisions/transport/mtu-negotiation.md, docs/decisions/transport/gatt-channel-and-framing.md, docs/decisions/transport/background-operation.md | meshlink/src/androidMain/, meshlink/src/iosMain/ (BLE glue) |
| §7 Security | docs/decisions/crypto/crypto-design.md, identity-binding-and-fail-closed.md, constant-time-policy.md, replay-window.md, key-rotation-propagation.md, error-hierarchy.md | specs/codecs/enums.yaml, specs/protocol/state-machines.yaml |
| §8 Routing | docs/decisions/routing/routing-design.md | RouteCandidate, LinkQuality, RouteStatement; routing coordinator (planned) |

| §9 Transfer | docs/decisions/model/data-model.md, docs/decisions/transfer/transfer-identifier.md | TransferSession.kt, Scoreboard.kt, TransferResult.kt; TransferCoordinator (planned) |
| §11 Diagnostics | docs/decisions/diagnostics/flow-delivery.md, docs/decisions/api/public-api-and-lifecycle.md | DiagnosticEvent.kt |
| §12 Build Quality | — | meshlink/build.gradle.kts |
| §13 Testing | — | — |
| §14 Settings | docs/decisions/model/settings-model.md | MeshLinkSettings.kt |
| §15 Future | docs/decisions/crypto/pq-hybrid-candidate-matrix.md | — |

---
