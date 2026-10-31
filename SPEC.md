# MeshLink Technical Specification

**Version**: 0.1.0-draft | **Status**: Active Development
**Authoritative source of truth** for the `:meshlink` artifact implementation. Machine-readable contracts live in `specs/`; design rationale lives in `docs/decisions/`; thin navigation facades live in `docs/reference/`.

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
14. [Configuration Model](#14-configuration-model)
15. [Future Work](#15-future-work)
16. [Wire Codec Reference](#16-wire-codec-reference)
17. [Implementation Status Summary](#17-implementation-status-summary)
18. [Machine-Readable Specification Layout](#18-machine-readable-specification-layout)
19. [Traceability Index](#19-traceability-index)

---

## 1. Vision & Product Pillars

**ADR**: — | **Specs**: — | **Code**: — | **Status**: ✅ Vision is complete

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
| Performance budgets | See [§12.1](#121-performance-budgets-ci-enforced) |
| Runtime dependency | `kotlinx-coroutines-core` + `ch.trancee.meshlink:meshlink-crypto` v0.1.1 (see §12.4) — Crypto uses the dedicated `meshlink-crypto` artifact from Maven Central; no additional runtime artifacts |
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

> **NOT FlatBuffers**: The MeshLink Wire Codec is a custom pure-Kotlin format inspired by (but not compatible with) Google FlatBuffers. No FlatBuffers runtime, `.fbs` schemas, or `flatc` dependency is used. See `docs/explanation/why-meshlink-wire-codec.md`.

---

## 2. Architecture Overview

<a id="meshlink-arch"></a>

**ADRs**: `docs/explanation/module-structure.md` | **Specs**: — | **Code**: `meshlink/build.gradle.kts`, `meshlink/src/commonMain/kotlin/ch/trancee/meshlink/MeshLink.kt`, `MeshLinkEnvironment.kt` | **Status**: ⚠️ Partial (API scaffolded, lifecycle not implemented)

### 2.1 Module Structure

```text
meshlink/              # Shipped library (JVM + Android + iOS arm64)
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
| `androidMain` | Platform-specific BLE glue, fallback crypto for Android API 21-32 |
| `iosMain` | Platform-specific BLE glue |
| `commonTest` | Pure JVM tests (protocol logic, wire codecs, crypto) |
| `androidHostTest` | Host-side Android tests (crypto fallback paths) |
| `androidDeviceTest` | Reserved for future use |

<a id="meshlink-public-api"></a>

### 2.3 Public API Surface {#meshlink-public-api}

`MeshLink` is a final instance-based class constructed from immutable settings
and an opaque `MeshLinkEnvironment`:

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
```

The current `MeshLink.kt` contains public API scaffolds with `TODO("Not implemented — scaffold for BCV baseline")` for `start()`, `pause()`, `resume()`, `stop()`, `setPowerMode()`, `sendMessage()`, `sendPayload()`, `revokeTrust()`, and `resetTrust()`. The `create()` factory and observable `StateFlow`/`Flow` properties are live. See `meshlink/src/jvmTest/kotlin/ch/trancee/meshlink/MeshLinkTest.kt` for the scaffold test pattern.

<a id="meshlink-environment"></a>

### 2.4 Platform Environment {#meshlink-environment}

Platform factory functions create `MeshLinkEnvironment`; Android context and
iOS framework types never enter shared protocol code. Multiple instances may
coexist, but one physical environment grants its BLE radio lease to only one
running instance. Virtual environments may run concurrently.

Applications address each remote installation by one stable PeerIdentity.
peerHint, TransportHandle, keys, key generations, proof chains, Noise epochs,
and route next hops remain internal; valid rotations and reconnects never make
the application replace identity or key material.

**ADR**: `docs/decisions/api/public-api-and-lifecycle.md`

### 2.5 Lifecycle States {#meshlink-state}

Lifecycle states are `UNINITIALIZED`, `CONFIGURED`, `RUNNING`, `PAUSED`, and `STOPPED`. The
constructor transitions `UNINITIALIZED` → `CONFIGURED`; `start()` transitions `CONFIGURED` → `RUNNING`.
Commands are serialized, idempotent at their target state, and restartable after
stop. Immediate failures use `MeshLinkException`; transfer failures use terminal
outcomes. The public API lives in `ch.trancee.meshlink`, with platform
differences hidden behind the environment factories and internal
`expect`/`actual` implementations.

**ADR**: `docs/decisions/api/public-api-and-lifecycle.md` | **Code**: `MeshLinkState.kt` | **Spec**: `specs/protocol/state-machines.yaml`

---

## 3. Core Data Models

**ADR**: `docs/decisions/model/data-model.md` | **Specs**: `specs/codecs/models.yaml`, `specs/codecs/enums.yaml` | **Code**: `meshlink/src/commonMain/kotlin/ch/trancee/meshlink/model/` | **Status**: ✅ Implemented (data classes live; integration with protocol layer not yet implemented)

<a id="peer-identity-model"></a>

### 3.1 PeerIdentity {#peer-identity-model}

**Stable 16-byte peer identifier.** Generated ONCE at install/first launch, stored permanently. NOT derived from public key — remains stable across key rotations.

```kotlin
@JvmInline value class PeerIdentity(private val parts: Pair<ULong, ULong>) {
    companion object {
        val ZERO: PeerIdentity = PeerIdentity(0UL to 0UL)
        fun generate(): PeerIdentity
        fun fromBytes(bytes: ByteArray): PeerIdentity  // requires 16 bytes
        fun fromHex(hex: String): PeerIdentity          // 32-char hex
    }
    fun toBytes(): ByteArray          // 16-byte big-endian
    override fun toString(): String   // 32-char hex
}
```

| Property | Description |
|----------|-------------|
| `parts.first` | Upper 8 bytes (big-endian) |
| `parts.second` | Lower 8 bytes (big-endian) |

- `ZERO` is the all-zero identity, used for initialization and comparison only; never a real peer.
- `generate()` uses platform CSPRNG.
- `fromBytes(bytes)` requires exactly 16 bytes or throws `IllegalArgumentException`.

**SPEC-ANCHOR**: `peer-identity-model` | **Code**: `PeerIdentity.kt`

<a id="seqno-model"></a>

### 3.2 SeqNo {#seqno-model}

**Unsigned 32-bit sequence number with safe wrap-around comparison.** Per RFC 8966 §3.7, comparisons use signed interpretation. SeqNo is internal and deliberately NOT `Comparable` — modular serial ordering is not a globally transitive total order. Explicit operations (`isNewerThan`, `isOlderThan`, `distanceFrom`, `inc`) handle wrap-around safely.

```kotlin
@JvmInline value class SeqNo(private val value: UInt) {
    companion object {
        val ZERO: SeqNo = SeqNo(0u)
        val MAX_VALUE: SeqNo = SeqNo(UInt.MAX_VALUE)  // 2^32 - 1 = 4294967295
        fun fromUInt(value: UInt): SeqNo = SeqNo(value)
        fun fromBytes(bytes: ByteArray): SeqNo  // 4-byte big-endian; throws if != 4 bytes
    }
    fun rawValue(): UInt = value
    fun toBytes(): ByteArray                    // 4-byte big-endian serialization
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
- `toBytes()`/`fromBytes()` for 4-byte big-endian byte-level wire serialization
- `isNewerThanOrEqualTo` used by Babel feasibility condition (RFC 8966 §3.7)
- `distanceFrom` for route staleness diagnostics and gap analysis
- An exact half-range difference (`2^31`) is ambiguous and treated as not-newer

**SPEC-ANCHOR**: `seqno-model` | **Code**: `SeqNo.kt`

<a id="scoreboard-model"></a>

### 3.3 Scoreboard (Immutable) & MutableScoreboard {#scoreboard-model}

**Immutable bitfield for selective acknowledgement (SACK).** Bit N = 1 means chunk N received. Length = `ceil(totalChunks / 8)` bytes.

```kotlin
class Scoreboard(totalChunks: UInt) {
    val totalChunks: UInt
    val byteSize: Int  // ceil(totalChunks / 8)
    fun markReceived(index: Int): Scoreboard
    fun markMissing(index: Int): Scoreboard
    fun isReceived(index: Int): Boolean
    fun isMissing(index: Int): Boolean
    fun missingChunks(): List<Int>
    fun missingSequence(): Sequence<Int>
    fun forEachMissing(action: (index: Int) -> Unit)
    fun receivedCount(): Int   // O(1), cached
    fun missingCount(): Int    // O(1), derived
    fun isComplete(): Boolean  // O(1)
    fun or(other: Scoreboard): Scoreboard   // union of received chunks
    fun and(other: Scoreboard): Scoreboard  // intersection
    fun xor(other: Scoreboard): Scoreboard  // symmetric difference
    fun toBytes(): ByteArray   // defensive copy for wire transmission
}
```

**MutableScoreboard**: High-performance mutable accumulator for hot paths. Call `toImmutable()` for thread-safe snapshot.

**Wire protocol note**: The `Scoreboard` model is a dynamic-bitfield internal structure for bounded messages and testing. The large-transfer wire protocol uses the **fixed 256-chunk sliding SACK window** described in §9.3 and §16.4 — this model is not a configurable wire encoding.

**SPEC-ANCHOR**: `scoreboard-model` | **Code**: `Scoreboard.kt`

<a id="transfer-session-model"></a>

### 3.4 TransferSession {#transfer-session-model}

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

| Field | Description |
|-------|-------------|
| `id` | Source-owned 32-bit TransferId unique within the authenticated origin PeerIdentity namespace |
| `destination` | Final destination peer for this transfer |
| `priority` | QoS priority inherited from the originating message; affects delivery scheduling/time, never hop limit |
| `state` | Current lifecycle state (see §3.6) |
| `result` | Terminal outcome; null while in a non-terminal state |
| `chunkSize` | Selected chunk size in bytes (bounded by peer MTU and PowerMode) |
| `totalChunks` | Total number of chunks in this transfer |
| `scoreboard` | Tracks which chunks have been received via SACK bitfield |
| `offset` | Highest contiguous payload boundary accepted by the sink or acknowledged by the remote peer |
| `total` | Total payload size in bytes |
| `startedAt` | When this transfer session was started |
| `expiresAt` | Deadline for ROUTE_UNAVAILABLE state; null when transferring or retransmitting |
| `retryCount` | Number of retransmit attempts made so far |

**SPEC-ANCHOR**: `transfer-session-model` | **Code**: `TransferSession.kt`

### 3.5 TransferState

| State | Terminal | Description |
|-------|----------|-------------|
| `AWAITING_DECISION` | No | Manifest validated; awaiting automatic message capacity or host sink decision |
| `TRANSFERRING` | No | Actively transferring |
| `ROUTE_UNAVAILABLE` | No | Route lost, waiting for recovery |
| `RETRANSMITTING` | No | Selectively retransmitting missing chunks under adaptive RTO |

The full TransferState machine (with transitions and protocol limits) is defined in `specs/protocol/state-machines.yaml` §`TransferState`.

<a id="transfer-result"></a>

### 3.6 TransferResult {#transfer-result}

```kotlin
sealed interface TransferResult {
    data object Completed : TransferResult
    data object Cancelled : TransferResult
    data object Expired : TransferResult
    data class UnrecoverableFailure(val message: String) : TransferResult
    data class TrustFailure(val identity: PeerIdentity) : TransferResult
}
```

| Outcome | Carries | Wire code | Notes |
|---------|--------|-----------|-------|
| `Completed` | — | `0x00` | All chunks acknowledged and delivered |
| `Cancelled` | — | `0x01` | Sender cancelled before completion |
| `Expired` | — | `0x02` | Time-to-live exceeded without completion |
| `UnrecoverableFailure` | `message: String` | `0x03` | Protocol, sink, or non-trust failure — followed by a UShort length-prefixed UTF-8 message string |
| `TrustFailure` | `identity: PeerIdentity` | `0x04` | Identity mismatch, revoked peer, or security policy violation — followed by a 16-byte PeerIdentity |

TransferResult is a sealed interface (not an enum) because failure outcomes carry structured data. Non-terminal progress is represented by TransferState (see §3.5); transfer status returns null until a terminal condition occurs.

**Wire discriminant**: `TransferResultCode` uses explicit `UByte` codes (never ordinals). See `specs/codecs/enums.yaml`.

**SPEC-ANCHOR**: `transfer-result` | **Code**: `TransferResult.kt`

<a id="transfer-id-model"></a>
<a id="message-id-model"></a>

### 3.7 TransferId and MessageId {#transfer-id-model} {#message-id-model}

A payload is identified by `(authenticated origin PeerIdentity, kind: TransferKind, id)`,
where `id` is a `TransferId` for `PAYLOAD` payloads or `MessageId` for `MESSAGE`
payloads. Both share the same 32-bit `UInt` wire slot; `kind` determines
interpretation and zero is reserved as invalid for either.

A transfer is identified by `(authenticated origin PeerIdentity, TransferId)`.
The origin allocates values from a durably reserved, monotonically increasing
32-bit counter. Zero is reserved as invalid.

```kotlin
@JvmInline
public value class TransferId(private val value: UInt) {
    public fun rawValue(): UInt
    public fun toBytes(): ByteArray  // 4-byte big-endian
    public operator fun inc(): TransferId  // wrapping increment
    override fun toString(): String  // eight-character hex representation

    public companion object {
        public val ZERO: TransferId = TransferId(0u)
        public fun fromBytes(bytes: ByteArray): TransferId  // 4-byte big-endian deserialization
    }
}
```

| Field | Type | Wire size | Valid values |
|-------|------|-----------|-------------|
| `value` | `UInt` | 4 bytes (big-endian) | `1..UInt.MAX_VALUE` (0 reserved as invalid) |

- The identifier is correlation data, not an authorization token.
- Active state and completed-transfer tombstones use the complete origin-scoped tuple.
- Counter ranges are persisted before use so a crash can skip values but cannot reuse an allocated value under the same `PeerIdentity`.
- After `UInt.MAX_VALUE`, skip zero and reject candidates present in active state or retained tombstones.

**`MessageId`** follows the identical 32-bit `UInt` wire slot pattern, scoped to `MESSAGE` kind. See `docs/decisions/transfer/transfer-identifier.md`.

**SPEC-ANCHOR**: `transfer-id-model`, `message-id-model` | **Code**: `TransferId.kt`, `MessageId.kt`, `Message.kt` | **ADR**: `docs/decisions/transfer/transfer-identifier.md`

<a id="link-quality-model"></a>

### 3.8 LinkQuality {#link-quality-model}

```kotlin
internal class LinkQuality(
    val smoothedRssi: Int,      // EWMA-smoothed RSSI in dBm
    val normalizedRssi: UByte,  // Smoothed RSSI normalized to 0-255
)
```

`LinkQuality` is a higher-is-better local RSSI observation used after routeCost
and hopCount during selection. L2CAP capability, health, and connection latency
belong to transport state and never enter this routing model. RSSI
normalization and the quadratic link-cost conversion are specified in §8.3.

**SPEC-ANCHOR**: `link-quality-model` | **Code**: `TransferSource.kt` (note: `L2capState.kt` also referenced by transport)

<a id="identity-key-model"></a>
<a id="handshake-key-model"></a>

### 3.9 IdentityKey / HandshakeKey {#identity-key-model} {#handshake-key-model}

```kotlin
@JvmInline value class IdentityKey(private val bytes: ByteArray) {  // 32-byte Ed25519
    companion object {
        fun fromHex(hex: String): IdentityKey   // 64 hex chars
        fun fromBytes(bytes: ByteArray): IdentityKey  // requires 32 bytes
    }
    fun toBytes(): ByteArray  // 32 raw bytes
    override fun toString(): String  // Hex
}

@JvmInline value class HandshakeKey(private val bytes: ByteArray) {  // 32-byte X25519
    companion object {
        fun fromHex(hex: String): HandshakeKey
        fun fromBytes(bytes: ByteArray): HandshakeKey
    }
    fun toBytes(): ByteArray  // 32 raw bytes
}
```

| Key type | Standard | Size | Purpose |
|----------|----------|------|---------|
| `IdentityKey` | Ed25519 (RFC 8032) | 32 bytes | Mesh identity verification and signing |
| `HandshakeKey` | X25519 (RFC 7748) | 32 bytes | Noise handshake DH key exchange |

- `IdentityKey` is the long-term Ed25519 signing key for identity binding and route statement signatures.
- `HandshakeKey` is the long-term X25519 DH key used as the Noise static key in XX/IK handshakes.
- Both are stored as opaque provider-owned handles (see §7.4).

**SPEC-ANCHOR**: `identity-key-model`, `handshake-key-model` | **Code**: `IdentityKey.kt`, `HandshakeKey.kt`, `CryptoKeyConstants.kt`

<a id="meshlink-version"></a>

### 3.10 MeshLinkVersion {#meshlink-version}

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

**SPEC-ANCHOR**: `meshlink-version` | **Code**: `MeshLinkVersion.kt`

<a id="app-hash-model"></a>

### 3.11 AppHash {#app-hash-model}

**128-bit application isolation hash.** Derived from `appId`; two MeshLink instances with different `appId` values never interoperate.

```text
appHash = first128Bits(SHA-256("MeshLink app-id v1" || UTF8(appId)))
```

| Property | Description |
|----------|-------------|
| Storage | `Pair<ULong, ULong>` (upper/lower 64 bits, big-endian) — same representation pattern as PeerIdentity to avoid ByteArray allocation in hot paths |
| `derive(appId)` | Computes appHash from appId string |
| Mesh hash | 16-bit FNV-1a of appId, used as a radio filter (`meshHash = fnv1a_32(appId) & 0xFFFF`). Collisions resolved by 128-bit appHash boundary |

**ADRs**: `docs/decisions/discovery/mesh-hash-derivation.md` | **Code**: `AppHash.kt`, `MeshHash.kt`

<a id="enums"></a>

### 3.12 Enums (Shared) {#enums}

All enums use explicit storage codes (never enum ordinals) on the wire. `FrameType`, `TransferKind`, `TransferResultCode`, and `PayloadDecision` carry explicit `UByte` codes with unknown-reject semantics. All other enums use ordinals for in-memory only.

**ADR**: `docs/decisions/model/data-model.md` | **Spec**: `specs/codecs/enums.yaml` | **Code**: `Enums.kt`

**SPEC-ANCHOR**: `enums`

#### 3.12.1 Wire-Coded Enums

| Enum | Storage | Values | Notes |
|------|---------|--------|-------|
| `FrameType` | `UByte` | 15 values (0x00–0x42) | Explicit codes; unknown rejected. See §16.2 |
| `TransferKind` | `UByte` | `MESSAGE=0x00`, `PAYLOAD=0x01` | Wire discrimination |
| `TransferResultCode` | `UByte` | `COMPLETED=0x00`, `CANCELLED=0x01`, `EXPIRED=0x02`, `UNRECOVERABLE_FAILURE=0x03`, `TRUST_FAILURE=0x04` | Wire discriminant; failure subtypes carry trailing payload |
| `PayloadDecision` | `UByte` (internal) | `ACCEPTED=0x00`, `REJECTED=0x01` | Internal transfer sink decision |

#### 3.12.2 Ordinal Enums

| Enum | Values | Notes |
|------|--------|-------|
| `KeyType` | `ED25519`, `X25519` | Distinguishes key purposes |
| `KeyRotationReason` | `PERIODIC`, `MANUAL`, `SECURITY_EVENT` | |
| `HandshakePattern` | `XX`, `IK` | XX for unpinned contact; IK for pinned reconnect |
| `Priority` | `HIGH` (10 min TTL), `NORMAL` (5 min), `LOW` (1 min) | Affects delivery scheduling/time, never hop limit |
| `DecryptFailureReason` | `AUTHENTICATION_TAG_MISMATCH`, `REPLAY_DETECTED`, `SEQUENCE_NUMBER_MISMATCH`, `KEY_UNAVAILABLE`, `MALFORMED_FRAME` | |
| `TransportFallbackReason` | `L2CAP_UNAVAILABLE`, `L2CAP_CONNECT_FAILED`, `L2CAP_OPEN_TIMEOUT`, `L2CAP_STREAM_ERROR`, `L2CAP_STALLED`, `L2CAP_DROPPED_MID_TRANSFER`, `LOCAL_POLICY` | |
| `Bearer` | `GATT`, `L2CAP` | Data plane in use |
| `RegulatoryRegion` | `DEFAULT`, `EU` | EU clamps adv ≥ 300 ms, scan ≤ 70% |
| `NoiseLayer` | `HOP_BY_HOP`, `END_TO_END` | Which protocol layer a Noise session belongs to |
| `NoiseSessionState` | `DISCONNECTED`, `HANDSHAKING_XX`, `HANDSHAKING_IK`, `ESTABLISHED`, `RENEWING`, `FAILED` | See §7.5 state machine |
| `NoiseRole` | `INITIATOR`, `RESPONDER` | |
| `NoiseFailureReason` | `HANDSHAKE_TIMEOUT`, `HANDSHAKE_MESSAGE_MALFORMED`, `HANDSHAKE_MESSAGE_OUT_OF_ORDER`, `REMOTE_STATIC_KEY_MISMATCH`, `REMOTE_STATIC_KEY_UNKNOWN`, `REKEY_REJECTED`, `TRANSPORT_CLOSED`, `MAX_RETRIES_EXCEEDED`, `INTERNAL_ERROR` | |
| `PowerMode` | `HIGH`, `MEDIUM`, `LOW` | See §10 |
| `VerificationLevel` | `FULL`, `TOFU_PIN`, `NONE` | Handshake verification achieved |
| `TransferState` | `AWAITING_DECISION`, `TRANSFERRING`, `ROUTE_UNAVAILABLE`, `RETRANSMITTING` | Non-terminal lifecycle (see §3.5) |
| `L2capState` (internal) | `UNSUPPORTED`, `AVAILABLE`, `CONNECTING`, `ACTIVE`, `BACKING_OFF`, `DISABLED` | L2CAP channel health |
| `PeerState` | `CONNECTED`, `DISCONNECTED` | Public API |
| `PeerLifecycle` (internal) | `CONNECTED`, `DISCONNECTED`, `GONE` | Internal runtime tracking |
| `PeerTrust` | `UNVERIFIED`, `VERIFYING`, `TRUSTED`, `MISMATCHED`, `REVOKED` | Trust classification |
| `KeyRotationState` (internal) | `CURRENT`, `GRACE_PERIOD`, `REVOKED` | Per-peer key status |
| `MeshLinkState` | `UNINITIALIZED`, `CONFIGURED`, `RUNNING`, `PAUSED`, `STOPPED` | Instance lifecycle (see §2.5) |
| `DiagnosticSeverity` | `DEBUG`, `INFO`, `WARN`, `ERROR` | See §11.5 |

#### 3.12.3 Error and Diagnostic Code Ranges

**ErrorCode** (Exception UShort, high bit `0x0000`):

| Category | Range |
|----------|-------|
| Configuration | `0x01xx` |
| Permission | `0x02xx` |
| Bluetooth | `0x03xx` |
| Crypto | `0x04xx` |
| Routing | `0x05xx` |
| Transfer | `0x06xx` |
| Storage | `0x07xx` |
| Lifecycle | `0x08xx` |
| Transport | `0x09xx` |
| Trust | `0x0Axx` |
| Internal | `0x0Fxx` |

**DiagnosticCode** (UShort, high bit `0x8000`): Disjoint from ErrorCode by the high bit so a diagnostic event can never be mistaken for an exception.

| Category | Range |
|----------|-------|
| Configuration | `0x81xx` |
| Permission | `0x82xx` |
| Bluetooth | `0x83xx` |
| Crypto | `0x84xx` |
| Routing | `0x85xx` |
| Transfer | `0x86xx` |
| Storage | `0x87xx` |
| Lifecycle | `0x88xx` |
| Transport | `0x89xx` |
| Trust | `0x8Axx` |
| Internal | `0x8Fxx` |

Codes never use enum ordinals and carry no sensitive context. See §7.6 for the full ErrorCode category members and §11.4 for the full DiagnosticCode event catalog.

<a id="known-peer-model"></a>

### 3.13 KnownPeer {#known-peer-model}

```kotlin
public data class KnownPeer(
    public val identity: PeerIdentity,
    public val state: PeerState,
    public val trust: PeerTrust,
    public val routeCost: UInt?,
    public val hopCount: UByte?,
    public val sessionCount: Int,
    public val diagnosticCode: DiagnosticCode?,
    public val diagnosticSeverity: DiagnosticSeverity? = null,
    public val seenAt: Instant,
    public val verifiedAt: Instant?,
)
```

`KnownPeer` snapshots are exposed via `MeshLink.peers` StateFlow (see §2.3). Advertisement-only
candidates are not canonical peers. `seenAt` is the immutable instant the full
identity was first learned; `verifiedAt` is the nullable instant of the latest
successful authentication. `routeCost`, `hopCount`, and `sessionCount` are nullable
or zero until routing and connection are established.

**SPEC-ANCHOR**: `known-peer-model` | **Code**: `KnownPeer.kt`, `MeshLinkState.kt`, `Enums.kt` | **ADR**: `docs/decisions/model/data-model.md`

<a id="wire-codec-core"></a>

### 3.14 MeshLink Wire Codec {#wire-codec-core}

The custom pure-Kotlin Wire Codec encodes all protocol frames. **Model serialization uses big-endian**; the **wire envelope uses little-endian** for its length field — an intentional separation (see `docs/explanation/why-meshlink-wire-codec.md`).

**Envelope format** (applied by bearer framing in §6.4):

```text
code    : UByte    (1 byte)   — explicit FrameType code, never ordinal
version : UByte    (1 byte)   — protocol version, bound into signature/AEAD context
length  : UShort   (2 bytes, little-endian, LE)  — payload length, 0..65535
payload : ByteArray(length)   — wire-encoded frame body
```

- Maximum payload: 65535 bytes (UShort max).
- The envelope is produced by `FrameWriter` and consumed by `FrameReader`.
- Big-endian byte order is used for all model values within payloads (e.g., `SeqNo.toBytes()`, `TransferId.toBytes()`, `PeerIdentity.toBytes()`).

**SPEC-ANCHOR**: `wire-codec-core` | **Code**: `wire/codec/FrameCodec.kt`, `FrameReader.kt`, `FrameWriter.kt`, `WireFrame.kt`, `Field.kt`, `FieldType.kt`, `ByteOrder.kt`

The complete frame-level specification (all 15 FrameTypes, field layouts, bearing encryption, GATT/L2CAP framing, and discovery advertisement format) is in §16.

---

## 4. Discovery & Identity

**ADRs**: `connectable-advertisement.md`, `mesh-hash-derivation.md`, `peer-hint-and-identity-races.md` | **Specs**: `specs/codecs/frames.yaml` (`discovery_advertisement`) | **Code**: `MeshHash.kt` | **Status**: ❌ Not Implemented

<a id="mesh-hash"></a>
<a id="app-hash"></a>

### 4.1 Advertisement Format {#discovery-advertisement}

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
| Packed header | 1 byte | Protocol version (3 bits, 0-2), Platform (3 bits: 0=Android, 1=iOS, 2=Desktop, 3-7=Reserved), Power mode (2 bits: 0=HIGH, 1=MEDIUM, 2=LOW, 3=Reserved) |
| Mesh hash | 16 bits | Application isolation filter (FNV-1a of appId) |
| Capability flags | 1 byte | Bit 0 = L2CAP available; bits 1-7 reserved and zero |
| Peer hint | 12 bytes | Random rotating `peerHint` |

The two UUID AD structures plus the normal flags consume approximately 27 of
the 31 legacy-advertisement bytes. MeshLink does not add a local name or other
optional advertisement fields.

The dynamic UUID is a fast path. When a platform does not surface it — in
particular under iOS background advertising restrictions — the fixed MeshLink
GATT service exposes full PeerIdentity, version, key generation, 16-bit PSM, and a fresh nonce.
peerHint remains advertisement-only. Advertisement and GATT metadata are
untrusted until the security handshake authenticates identity and keys.

**SPEC-ANCHOR**: `discovery-advertisement` | **Spec**: `specs/codecs/frames.yaml#discovery_advertisement` | **ADR**: `docs/decisions/discovery/connectable-advertisement.md`

### 4.2 Mesh Hash Derivation {#mesh-hash}

```text
meshHash = fnv1a_32(appId.toByteArray()) & 0xFFFF       // 16-bit radio filter
appHash  = first128Bits(SHA-256("MeshLink app-id v1" || UTF8(appId)))  // 128-bit security boundary
```

**Purpose**: `meshHash` reduces cross-application discovery. It is only a
16-bit radio filter; collisions are resolved by the 128-bit `appHash` security
boundary.

`appHash` is the 128-bit application-isolation value bound into security
handshakes. `handshakeHash` is reserved for the Noise transcript hash `h`; the
two names never refer to the same value.

**ADRs**: `docs/decisions/discovery/mesh-hash-derivation.md` | **Code**: `MeshHash.kt`, `AppHash.kt`

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

**ADR**: `docs/decisions/discovery/peer-hint-and-identity-races.md`

### 4.4 Privacy Trade-offs

| Aspect | Trade-off |
|--------|-----------|
| Rotating peer hint | Reduces stable application identifiers but independently rotated RPA/advertisement fields can still permit continuous correlation |
| Protected | Full PeerIdentity and public keys are not advertised; trust is established only after GATT and Noise |
| Isolation | `meshHash` filters discovery; 128-bit `appHash` enforces the security boundary |

**ADRs**:

- `docs/decisions/discovery/connectable-advertisement.md`
- `docs/decisions/discovery/mesh-hash-derivation.md`
- `docs/decisions/discovery/peer-hint-and-identity-races.md`

### 4.5 Discovery Attempt State Machine {#discovery-attempt-state}

Internal verb-only states for one bounded pre-identity BLE discovery attempt:

```text
DISCOVERED → CONNECTING → RESOLVING → AUTHENTICATING → SUCCEEDED
                    ↘          ↘           ↘            ↗
                    BACKING_OFF ← (transient failure at any step)
                    ↓
                  FAILED (capacity, malformed metadata, permanent policy)
```

**Deduplication rules**:

| Condition | Action |
|-----------|--------|
| Same TransportHandle, same peerHint | Update observation only |
| Same TransportHandle, new peerHint | Attach observation to active attempt |
| New TransportHandle, mapped peerHint | Tentatively select mapped pin but require fresh IK |
| Same claimed identity | Serialize through one per-peer coordinator |
| Canonical merge | Only after SUCCEEDED |

**Spec**: `specs/protocol/state-machines.yaml#DiscoveryAttemptState`

---

## 5. Trust Model (TOFU)

**ADRs**: `crypto-design.md`, `identity-binding-and-fail-closed.md`, `key-rotation-propagation.md`, `noise-session-renewal.md`, `private-key-handling.md`, `replay-window.md`, `constant-time-policy.md` | **Specs**: `specs/codecs/enums.yaml`, `specs/protocol/state-machines.yaml` | **Code**: `ConstantTime.kt`, `ReplayWindow.kt` | **Status**: ❌ Not Implemented

### 5.1 Handshake Patterns

| Layer | No Trusted Pin | Trusted Current Pin |
|-------|----------------|---------------------|
| Direct hop | `Noise_XX_25519_ChaChaPoly_SHA256` | `Noise_IK_25519_ChaChaPoly_SHA256` |
| Routed E2E | Routed `Noise_XX_25519_ChaChaPoly_SHA256` | Routed `Noise_IK_25519_ChaChaPoly_SHA256` |

No application early data is sent in IK. A pinned mismatch fails closed and
never starts XX until explicit trust reset.

**ADRs**: `docs/decisions/crypto/crypto-design.md`

### 5.2 First-Contact Identity Binding and Automatic TOFU

Direct first contact uses `Noise_XX_25519_ChaChaPoly_SHA256`. Each peer carries
a canonical, Ed25519-signed identity binding in its encrypted handshake payload:

```text
IdentityBinding {
    version       : UByte
    appHash       : ByteArray (16 bytes)
    identity      : PeerIdentity (16 bytes)
    ed25519PublicKey: IdentityKey (32 bytes)
    x25519PublicKey: HandshakeKey (32 bytes)
    keyGeneration : UInt
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

**ADR**: `docs/decisions/crypto/identity-binding-and-fail-closed.md` | **Spec**: `specs/protocol/state-machines.yaml#PeerTrust`

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

```text
KeyRotationProof {
    version: UByte
    appHash: ByteArray                    // 16 bytes
    identity: PeerIdentity                // 16 bytes, unchanged by rotation
    oldGeneration: UInt                   // 4 bytes
    newGeneration: UInt                   // 4 bytes; must equal oldGeneration + 1
    newIdentityKey: IdentityKey           // Ed25519, 32 bytes
    newHandshakeKey: HandshakeKey          // X25519, 32 bytes
    reason: KeyRotationReason             // UByte
    continuitySignature: ByteArray        // old Ed25519 key, 64 bytes
    possessionSignature: ByteArray        // new Ed25519 key, 64 bytes
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
sessions drain for `rotationGracePeriod` (default 1 hour); new sessions use the
current binding. Security-event rotation rejects old keys and closes old-key
sessions immediately (compromiseGracePeriod = ZERO → immediate old-key rejection).
Transfers remain addressed by PeerIdentity and resume only after required session
authentication succeeds.

Applications continue using the same PeerIdentity and never manage keys,
generations, or proof chains.

**ADR**: `docs/decisions/crypto/key-rotation-propagation.md` | **Spec**: `specs/codecs/frames.yaml#KEY_ROTATION`

**Propagation deadlines**: 1-hop < 1 second; 2-hop < 3 seconds (best-effort, observable via `KeyRotationEvent.propagationDeadlineMet`).

### 5.5 E2E Handshake Routing Over Mesh {#e2e-routing}

When the destination is not a direct neighbor:

```text
Phase 1: Establish authenticated hop sessions along the route
Origin --(GATT/L2CAP)--> Relay(s) --> Destination

Phase 2: Route E2E handshake messages
Origin wraps the next XX or IK message in RoutingFrame:
  RoutingFrame {
    destination: destination.identity   // 16 bytes
    payload: e2eHandshakeMessage
    hopLimit: UByte                      // initialized to 16, decremented before forwarding
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

**ADR**: `docs/decisions/storage/persistence-strategy.md`

### 5.7 Revocation

- Explicit reset/revoke API action is required
- No silent re-trust on identity mismatch
- Revoked records persist until explicit reset

### 5.8 Noise Session Renewal

Every hop and E2E Noise session has a hard 24-hour lifetime. A fresh IK renewal
is scheduled with monotonic per-session jitter:

```text
renewalAt  = establishedAt + uniform random duration in [21h, 23h]
expiresAt  = establishedAt + 24h          // hard lifetime
takeoverAt = establishedAt + uniform random in [23h30m, 23h50m]
```

- The lower lexicographic `PeerIdentity` is the preferred initiator. The other peer takes over only at an independently jittered instant in [23h30m, 23h50m] when renewal has not completed. Equal full identities across two installations fail closed.
- Initiator retries use exponential backoff with full jitter and cannot cross hard expiry.
- Idle suspended applications are not awakened solely for renewal.
- On activity after expiry, old keys are discarded and fresh IK must complete before protected traffic resumes.

Per-direction authenticated record counters also enforce:

```text
soft renewal threshold = 2^31 records  (2,147,483,648)
hard record limit      = 2^32 records  (4,294,967,296)
```

**NoiseSessionState machine**: `DISCONNECTED` → `HANDSHAKING_XX` or `HANDSHAKING_IK` → `ESTABLISHED` → `RENEWING` → `ESTABLISHED` (success) or `FAILED` (hard expiry / record limit / transport closed). See §7.5.

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

- `docs/decisions/crypto/crypto-design.md`
- `docs/decisions/crypto/noise-session-renewal.md`

**Spec**: `specs/protocol/state-machines.yaml#NoiseSessionState`, `specs/codecs/frames.yaml#noise_epoch_control`

---

## 6. Transport Layer

**ADRs**: `gatt-channel-and-framing.md`, `mtu-negotiation.md`, `background-operation.md` | **Specs**: — | **Code**: `L2capState.kt`, `SecureRandom.kt` | **Status**: ❌ Not Implemented

### 6.1 Bearer Selection

| Traffic Type | Preferred Bearer | Fallback |
|--------------|------------------|----------|
| Control plane | GATT (unconditionally) | None — GATT always available |
| Data plane | L2CAP CoC | GATT with same correctness guarantees |

**Control plane MUST work over GATT alone** for reliability.

### 6.2 GATT Service

| Property | Value |
|----------|-------|
| Service UUID (32-bit) | `0x4D455348` (`"MESH"` in ASCII) |
| Service UUID (128-bit) | `4D455348-0000-1000-8000-00805F9B34FB` |
| Assigned by Bluetooth SIG | No |
| Private component allocation | `0x0000` service, `0x0001` metadata, `0x0002` channel, `0x0003`–`0x00FF` reserved |

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

**ADR**: `docs/decisions/transport/gatt-channel-and-framing.md` | **Spec**: `specs/codecs/frames.yaml#gatt_service`

### 6.3 GATT Metadata Record

Untrusted fixed bootstrap record read before Noise and transcript-bound before any field is authoritative:

| Field | Type | Size | Description |
|-------|------|------|-------------|
| `version` | `UByte` | 1 | Protocol version |
| `appHash` | `ByteArray` | 16 | `first128Bits(SHA-256("MeshLink app-id v1" + UTF8(appId)))` — app-id v1 concatenated with UTF8(appId) |
| `identity` | `PeerIdentity` | 16 | Claim only until IK/XX succeeds |
| `keyGeneration` | `UInt` | 4 | Hint for current pin or rotation-recovery selection; never updates trust by itself |
| `psm` | `UShort` | 2 | Authoritative after Noise transcript validation; v1 valid values `0x0000` or `0x0080`–`0x00FF` |
| `nonce` | `ByteArray` | 16 | Fresh per physical GATT connection; mixed with canonical metadata into the Noise prologue |

**Excluded** from GATT metadata: peerHint, privateKeys, noiseState, routeState, transferState, TransportHandle.

**ADR**: `docs/decisions/transport/gatt-channel-and-framing.md` | **Spec**: `specs/codecs/frames.yaml#gatt_metadata`

### 6.4 Bearer Framing {#bearer-framing}

GATT fragments are connection-local and bearer-specific; they wrap MeshLink Wire Codec frames without modifying them.

#### 6.4.1 GATT Fragmentation

```text
GattFragment {
    index       : UShort   (2 bytes, little-endian)    — zero starts a record
    totalLength : UShort   (2 bytes, little-endian)    — present only when index == 0
    payload     : ByteArray                  — remaining bytes
}
```

- `index` zero starts a record; completion occurs at exact `totalLength`.
- Continuations are strictly sequential (must equal previous index + 1).
- There are no flags or record IDs.
- Maximum frame size: **65,535 bytes** under authenticated GATT; **4,096 bytes** for pre-auth (unauthenticated) connections.
- **Invalidation**: Gap, wrap, overflow, timeout, conflicting duplicate, disconnect, or malformed start discards the active record; ignore continuations until the next index zero.
- **Concurrency**: One active record per direction per `(TransportHandle, ConnectionContext.generation)`.
- Estimated total bytes with flags: ~27 of 31 legacy-advertisement bytes.

#### 6.4.2 L2CAP Framing

```text
L2capFrame {
    length    : UShort   (2 bytes, little-endian)  — 1..65535
    frameBytes: ByteArray(length)  — exact MeshLink Wire Codec envelope
}
```

Both bearers yield identical MeshLink Wire Codec frames after bearer deframbling.

**ADR**: `docs/decisions/transport/gatt-channel-and-framing.md` | **Spec**: `specs/codecs/frames.yaml#gatt_fragment, l2cap_frame`

### 6.5 Negotiation Sequence

1. GATT connection establishes.
2. `Noise_XX_25519_ChaChaPoly_SHA256` handshake completes (control plane).
3. After Noise validates GATT metadata, attempt L2CAP when its 16-bit PSM is valid and non-zero; the advertisement capability bit is only a hint.
4. On CoC success, promote data-plane traffic to CoC.
5. On CoC failure, continue on GATT.

### 6.6 Fallback Reasons (Machine Observable)

| Reason | Description |
|--------|-------------|
| `L2CAP_UNAVAILABLE` | Advertisement/GATT capability indicates no usable L2CAP channel |
| `L2CAP_CONNECT_FAILED` | CoC connection failed |
| `L2CAP_OPEN_TIMEOUT` | Channel did not open before deadline |
| `L2CAP_STREAM_ERROR` | Stream read/write failed |
| `L2CAP_STALLED` | Channel made no bounded forward progress |
| `L2CAP_DROPPED_MID_TRANSFER` | CoC channel dropped during transfer |
| `LOCAL_POLICY` | Local configuration disabled CoC |

### 6.7 L2CAP Health and Circuit Breaking

L2CAP capability is transport state, not routing LinkQuality. Open
failure/timeout, EOF, stream error, stall, partial-frame timeout, or channel drop
immediately moves new data assignment to GATT while preserving route, trust,
E2E session, and transfer state. SACK retransmits missing chunks.

Per-peer process-local backoff schedule (L2capHealth state machine):

| Failure | Backoff |
|---------|---------|
| 1st failure | 15–30 seconds |
| 2nd failure | 1–2 minutes |
| 3rd failure | 5–10 minutes |
| 4th failure | **Disabled for process lifetime** |

- `failureCount` resets only after **ten healthy minutes** or **one error-free transfer of at least 1 MiB**.
- After four failures, L2CAP is disabled (`DISABLED` terminal state) for the process lifetime for that neighbor.
- Health is not persisted.

**ADR**: `docs/decisions/transport/gatt-channel-and-framing.md` | **Spec**: `specs/protocol/state-machines.yaml#L2capState`

### 6.8 Background Operation

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

- `docs/decisions/transport/mtu-negotiation.md`
- `docs/decisions/transport/background-operation.md`

---

## 7. Security Layer

**ADRs**: `crypto-design.md`, `identity-binding-and-fail-closed.md`, `constant-time-policy.md`, `private-key-handling.md`, `replay-window.md`, `noise-session-renewal.md`, `key-rotation-propagation.md`, `model/error-hierarchy.md` | **Specs**: `specs/codecs/enums.yaml`, `specs/protocol/state-machines.yaml` | **Code**: `ConstantTime.kt`, `ReplayWindow.kt`, `PayloadDecision.kt` | **Status**: ❌ Not Implemented (ConstantTime.kt, ReplayWindow.kt implemented as primitives; full Noise/crypto stack not wired)

### 7.1 Crypto Primitives (All Validated Against Wycheproof)

The `:meshlink` artifact depends on exactly two runtime dependencies:
`kotlinx-coroutines-core` and `ch.trancee.meshlink:meshlink-crypto` v0.1.1. No
additional crypto runtime is required.

| Primitive | Standard | Wycheproof Vectors | meshlink-crypto v0.1.1 API |
|-----------|----------|-------------------|---------------------------|
| SHA-256 | RFC 6234 | Covered via other primitives | `Crypto.sha256(ByteArray): ByteArray` |
| SHA-512 | RFC 6234 | — | `Crypto.sha512(ByteArray): ByteArray` |
| HMAC-SHA256 | RFC 2104 | 174 (66 valid + 108 invalid) | `Crypto.hmacSha256(key, data): ByteArray`; `Crypto.verifyHmacSha256(key, data, mac): Boolean` (constant-time) |
| HKDF-SHA256 | RFC 5869 | 86 (83 valid + 3 invalid) | `Crypto.hkdfSha256(ikm, salt, info, outLen): ByteArray`; `Crypto.hkdfSha256Extract(salt, ikm): ByteArray`; `Crypto.hkdfSha256Expand(prk, info, outLen): ByteArray`. **Parameter order**: ikm FIRST, salt SECOND. |
| X25519 | RFC 7748 | 518 (264 valid + 254 acceptable) | `Crypto.x25519(privateKey, publicKey): ByteArray`; `Crypto.deriveX25519PublicKey(privateKey): ByteArray` |
| Ed25519 | RFC 8032 | 150 (88 valid + 62 invalid) | `Crypto.ed25519Sign(privateKey, data): ByteArray`; `Crypto.ed25519Verify(publicKey, data, signature): Boolean`; `Crypto.ed25519PublicKeyFromPrivate(privateKey): ByteArray` |
| ChaCha20-Poly1305 | RFC 8439 | 325 (256 valid + 69 invalid) | `Crypto.chacha20Poly1305Encrypt(key, nonce, plainText, aad): ByteArray`; `Crypto.chacha20Poly1305Decrypt(key, nonce, cipherText, aad): ByteArray?` |
| Secure random | Platform CSPRNG | — | `Crypto.randomBytes(size: Int): ByteArray` |

**Parameter conventions**:

- HKDF parameter order is **ikm first, salt second** — this is the only deviation from the RFC's `HMAC-Hash(salt, IKM)` Extract stage convention, chosen for API ergonomics. The implementation must internally swap to the RFC-correct order.
- `chacha20Poly1305Decrypt` returns `null` on authentication failure (constant-time tag comparison) rather than throwing.
- All byte-array inputs are defensive copies; private key bytes never enter public APIs.

**ADR**: `docs/decisions/crypto/meshlink-crypto-dependency.md` | **Reference**: `docs/reference/meshlink-crypto-api.md`

<a id="fail-closed"></a>

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

**ADR**: `docs/decisions/crypto/identity-binding-and-fail-closed.md`

<a id="provider-boundaries"></a>

### 7.3 Provider and Private-Key Boundaries {#provider-boundaries}

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

**ADR**: `docs/decisions/crypto/private-key-handling.md`

<a id="constant-time"></a>

### 7.4 Constant-Time Policy {#constant-time}

All operations on secret data (private keys, shared secrets, session keys, KDF output) **MUST** use constant-time implementations:

- Scalar multiplication (X25519): Montgomery ladder
- Scalar arithmetic (Ed25519): No data-dependent branches
- AEAD decrypt/verify: Constant-time tag comparison (`Crypto.chacha20Poly1305Decrypt` returns `null` on failure)
- HKDF/HMAC: Constant-time HMAC implementation
- Array comparisons: `ConstantTime` primitives or `Crypto.verifyHmacSha256`

**ADR**: `docs/decisions/crypto/constant-time-policy.md` | **Code**: `ConstantTime.kt` | **Spec**: `specs/codecs/enums.yaml`

**ConstantTime primitives** (4 functions in `ConstantTime.kt`):

- `equals(a: ByteArray, b: ByteArray): Boolean` — constant-time array equality
- `verify(bytes: ByteArray, expected: ByteArray): Boolean` — constant-time verification
- `select(ifTrue: ByteArray, ifFalse: ByteArray, pickTrue: Boolean): ByteArray` — constant-time conditional selection
- `isZero(bytes: ByteArray): Boolean` — constant-time zero check

<a id="replay-window"></a>

### 7.5 Replay Window {#replay-window}

**Sliding bitmap window** per RFC 9147 §4.2:

- **Window size**: 64 bits (fixed)
- **Bitmap shift direction**: right-shift (`ULong.shr`) so that existing bit positions correctly map to nonces under the new baseNonce after an advance; bits shifted off the low end are evicted
- **Per-epoch numbering** (epoch increments on KeyUpdate); `advanceEpoch()` resets the bitmap and epoch counter
- **Deprotect-before-advance** (RFC 9147 §4.2): the replay check is performed before any bitmap mutation, preventing replay and fresh nonces within the window from taking different code paths
- **Replay detection** on both hop-by-hop and E2E layers
- **Silent drop** on replay (no response amplification or state mutation)

**ADR**: `docs/decisions/crypto/replay-window.md` | **Code**: `ReplayWindow.kt` | **Spec**: `specs/protocol/state-machines.yaml`

### 7.6 Error Hierarchy (Sealed) {#error-hierarchy} {#error-code}

```text
MeshLinkException
├── ConfigurationException     0x01xx — Invalid parameter, invalid state
├── LifecycleException         0x08xx — Invalid lifecycle command
├── PermissionException          0x02xx — Permission denied
├── BluetoothException           0x03xx — Bluetooth disabled, COC not supported, connection failed, GATT operation failed, L2CAP channel failed, radio in use
├── StorageException             0x07xx — Storage unavailable, storage corrupted
├── CryptoException              0x04xx — Crypto operation failed, signature verification failed, replay detected
├── TrustException               0x0Axx — Peer not found, key unknown, trust violation
├── RoutingException             0x05xx — No route, route advertisement failed, route loop detected
└── TransferException            0x06xx — Transfer timeout, transfer cancelled, transfer corrupted, session not found, chunk out of bounds
```

All public immediate command failures use typed `MeshLinkException` subtypes with an `ErrorCode`. Platform exceptions are wrapped at their boundary. Long-running transfer failures are terminal transfer outcomes, not exceptions emitted later by a send call. Coroutine cancellation remains coroutine cancellation.

**ADR**: `docs/decisions/model/error-hierarchy.md` | **Code**: `ErrorCode.kt`, `Exceptions.kt` | **Spec**: `specs/catalogs/diagnostic-events.yaml#exception_error_codes`

### 7.7 Noise Session State Machine {#noise-state-machine}

```text
DISCONNECTED
    → HANDSHAKING_XX  (no trusted destination pin; XX + signed identity binding)
    → HANDSHAKING_IK  (trusted current pin; IK, no app early data)
HANDSHAKING_XX
    → ESTABLISHED     (XX + identity binding succeed)
    → FAILED          (timeout, malformed binding, mismatch, retry exhaustion)
HANDSHAKING_IK
    → ESTABLISHED     (IK + pinned identity binding succeeds)
    → FAILED          (handshake failure or pinned mismatch)
ESTABLISHED
    → RENEWING        (renewal time or 2^31 soft record threshold)
    → FAILED          (transport closed or 24h hard / 2^32 hard record limit without renewal)
RENEWING
    → ESTABLISHED     (fresh IK succeeds, atomic epoch install)
    → ESTABLISHED     (attempt fails before hard expiry; current epoch remains valid)
    → FAILED          (hard lifetime or record limit reached before renewal succeeds)
```

**Renewal protocol parameters** (from `specs/protocol/state-machines.yaml`):

| Parameter | Value |
|-----------|-------|
| Hard lifetime | 24 hours |
| Renewal target (jittered) | [21h, 23h] |
| Takeover window | [23h30m, 23h50m] |
| Preferred initiator | Lower lexicographic PeerIdentity |
| Soft record limit per direction | 2^31 (2,147,483,648) |
| Hard record limit per direction | 2^32 (4,294,967,296) |
| Old-epoch drain timeout | 30 seconds |
| Pending application record delivery | Blocked until local activation |

**Epoch commit/acknowledge** (see §5.8 for full description):

- `EpochCommit`: sent by preferred initiator under pending keys
- `EpochAcknowledgement`: sent by responder under pending keys
- Responder activates new outbound epoch after sending ack; initiator activates after validating ack
- No record keys are ever reused; each retry uses a fresh counter

---

## 8. Routing Layer

**ADRs**: `docs/decisions/routing/routing-design.md` | **Specs**: `specs/protocol/state-machines.yaml`, `specs/codecs/enums.yaml` | **Code**: `RoutingPolicy.kt` | **Status**: ❌ Not Implemented (RoutingPolicy.kt is configuration only; route computation/coordination not implemented)

### 8.1 Model and Selection

MeshLink uses a Babel-inspired distance-vector protocol over authenticated BLE
neighbors. Route state separates lower-is-better additive `routeCost`,
independent `hopCount`, higher-is-better local `linkQuality`, and local
`nextHop`.

Selection uses feasible candidates, lowest routeCost, lowest hopCount, highest
local linkQuality, then lowest lexicographic nextHop. A candidate is **feasible**
when its sequence is newer, or equal with lower cost (RFC 8966 §3.7 feasibility condition).

| Internal type | Visibility | Description |
|--------------|------------|-------------|
| `RouteCandidate` | internal | One candidate route from an authenticated neighbor |
| `LinkQuality` | internal | Higher-is-better local RSSI observation |
| `RouteExport[neighbor]` | internal | Canonical routes last advertised to that neighbor |
| `RouteImport[neighbor]` | internal | Canonical wire routes last accepted from that neighbor |

### 8.2 Link and Path Cost

RSSI normalizes from 0 at -100 dBm through 255 at -30 dBm. Shared code computes:

```text
qualityLoss    = 255 - normalizedRssi
qualityPenalty = ceil(qualityLoss² / 255)
linkCost       = 64 + qualityPenalty     // ranges 64..319
routeCost      = saturating sum of linkCost values   // UInt.MAX_VALUE = infinity
```

- Link cost ranges from 64 through 319; `UInt.MAX_VALUE` means infinity.
- Strong multi-hop paths may beat weak direct paths.
- L2CAP health is NOT part of routing LinkQuality — it is transport state.

**ADRs**: `docs/decisions/routing/routing-design.md`

### 8.3 Smoothing and Hysteresis

RSSI uses EWMA smoothing with a 3:1 ratio:

```text
smoothed = (3 × previous + sample) / 4
```

Cost updates advertise only after at least **3 dB** smoothed RSSI change. A candidate must remain best for **two observations spanning one second** and improve by `max(16, currentRouteCost / 10)` before switching. Loss, withdrawal, expiry, infeasibility, hop-limit violation, or trust failure switches immediately (no hysteresis).

**RoutingUpdateTrigger** (from `specs/protocol/state-machines.yaml`):

| Trigger | Condition | Frame | Jitter | Immediate |
|---------|-----------|-------|--------|-----------|
| Authenticated link up | Adjacent Noise session established | RouteAdvertisement | No | Yes |
| RSSI threshold | EWMA smoothed RSSI changes by ≥ 3 dB | RouteAdvertisement | Yes | No |
| Periodic refresh | routeDigestInterval reached | RouteDigest | Yes | No |
| Route expiry | Candidate not renewed before routeExpiry | RouteWithdrawal | No | Yes |
| Digest mismatch | RouteDigest differs from RouteImport | RouteSynchronization | No | Yes |
| Sequence advancement | Destination persisted newer sequence | RouteAdvertisement | No | Yes |

Ordinary non-immediate updates coalesce under the internal **one-second** route advertisement cooldown. Withdrawal, disconnect, trust invalidation, sequence advancement, and synchronization are immediate exceptions.

### 8.4 Feasibility and Sequence Ownership

Feasible distance is `(sequenceNumber, routeCost)`. A candidate is feasible when
its sequence is newer, or equal with lower cost.

Each destination owns and persists its 32-bit sequence. It increments before
advertising on cold start, valid sequence advancement, or internal route reset. It
does not change for reconnect, peerHint/TransportHandle/RPA change, RSSI update,
refresh, Noise renewal, key rotation, or bearer migration.

`SeqNo` is internal and not Comparable. It exposes explicit modular operations,
including `distanceFrom`; an exact half-range difference (`2^31`) is ambiguous and treated as not-newer.

### 8.5 Feasibility-Starvation Recovery

```text
RouteSequenceAdvancement {
    requester       : PeerIdentity   (16 bytes)
    destination     : PeerIdentity   (16 bytes)
    sequenceNumber  : UInt           (4 bytes)
    requestId       : UInt           (4 bytes)
    hopLimit        : UByte          (1 byte; max 16)
}
```

- Sent immediately through the best known authenticated next hop.
- After **500 ms** without a sufficiently new route, fan out to other authenticated neighbors.
- Relays deduplicate `(requester, requestId)`, exclude the incoming neighbor, and enforce hopLimit.
- One request per destination is active.
- Attempts expire after **3 seconds**, dedup state after **30 seconds**.
- Each peer may trigger at most **three** destination advancements per minute.
- Failed attempts retry with exponential backoff and full jitter.

### 8.6 Signed Statements and Advertisements

```text
RouteStatement {
    version        : UByte
    appHash        : ByteArray (16 bytes)
    destination    : PeerIdentity (16 bytes)
    sequenceNumber : UInt (4 bytes)
    identityBinding: IdentityBinding
    signature      : ByteArray (64 bytes)   // Ed25519 over canonical statement body
}

RouteAdvertisement {
    statement  : RouteStatement
    routeCost  : UInt (4 bytes)
    hopCount   : UByte (1 byte)
}
```

Destination signatures are mandatory (Ed25519 over the canonical statement body). Relays may update `routeCost` and `hopCount` under hop authentication. `nextHop` remains local and is inferred from the authenticated adjacent sender. An authenticated malicious relay can still lie about mutable path fields; Byzantine path proofs are outside v0.1.

### 8.7 Encrypted Routing Control

Advertisements, withdrawals, digests, sequence advancements, synchronization, and
snapshots are all hop-encrypted/authenticated after adjacent Noise succeeds.
Authentication failure drops a frame before routing parsing. No plaintext retry
or downgrade exists.

### 8.8 Per-Neighbor Split Horizon and Synchronization

Each `RouteExport` excludes candidates whose nextHop is that export neighbor. A
self-origin route is always exported; an alternate route through another
neighbor may be exported. If a previously exported route becomes reachable only
through the recipient, send immediate `RouteWithdrawal`. Explicit withdrawal
replaces poison reverse.

```text
RouteExport[neighbor]  // canonical routes last advertised to neighbor
RouteImport[neighbor]  // canonical wire routes last accepted from neighbor before local cost/policy

RouteDigest            // summary of RouteExport
RouteSynchronization   // receiver requests synchronization
RouteSnapshot          // sender returns complete RouteExport
```

- A digest compares with `RouteImport[sender]`, never the receiver's full table.
- On mismatch, the receiver sends `RouteSynchronization`; the sender returns `RouteSnapshot`; the receiver validates and atomically replaces only that sender's import.
- Digest input sorts by destination and includes RouteStatement, routeCost, and hopCount, excluding nextHop, local linkQuality, timers, and feasibility state.
- `RouteDigest` = first 64 bits of SHA-256 over canonical field encoding, never raw implementation-buffer layout.
- `RouteDigest`, `RouteSynchronization`, and `RouteSnapshot` carry a per-adjacency `UInt` revision that starts at zero for a fresh hop Noise session and increments whenever RouteExport changes. Stale revisions are rejected.
- A matching `RouteDigest` renews every unchanged entry in that sender's `RouteImport`; no periodic unchanged advertisement is required.

### 8.9 Time-to-Live and Hop Limit

`TransferOptions.timeToLive` is elapsed delivery lifetime; priority defaults are
10, 5, and 1 minutes (see §3.12 Priority). `maximumHopCount = 16` is an independent fixed protocol
bound for every priority. Envelopes start at 16, relays decrement before
forwarding, and zero is dropped. RouteAdvertisement rejects hopCount at or above
16; RouteSequenceAdvancement uses the same bound. Neither value is publicly
configurable.

**ADRs**: `docs/decisions/routing/routing-design.md`, `docs/decisions/model/mesh-size-limits.md` | **Spec**: `specs/protocol/state-machines.yaml#RoutingUpdateTrigger`

---

## 9. Transfer Layer

**ADRs**: `data-model.md`, `error-hierarchy.md`, `transfer-identifier.md`, `payload-transfer-protocol.md`, `payload-identity-and-naming.md`, `transfer-source-sink-contract.md` | **Specs**: `specs/codecs/models.yaml`, `specs/protocol/state-machines.yaml`, `specs/codecs/frames.yaml` | **Code**: `TransferSession.kt`, `Scoreboard.kt`, `TransferResult.kt`, `TransferId.kt`, `TransferKind.kt`, `TransferOptions.kt`, `TransferStatus.kt`, `Transfer.kt`, `TransferHandle.kt`, `TransferSink.kt`, `TransferSource.kt`, `MessageId.kt`, `Message.kt`, `MessageHandle.kt` | **Status**: ⚠️ Partial (data models implemented; TransferCoordinator not implemented)

### 9.1 Manifest and Acceptance

Every finite payload starts with an E2E-encrypted PayloadManifest carrying kind
(MESSAGE/PAYLOAD), source-owned ID, origin/destination, priority,
timeToLive, totalLength, chunkSize, and chunkCount.

| Parameter | Value |
|-----------|-------|
| Messages (auto-accept) | Up to **64 KiB** |
| Incomplete-message global budget | **2 MiB** |
| Large transfers | Require a host `TransferSink` |
| Offers per peer | At most **3** wait in `AWAITING_DECISION` |
| Global incoming offers cap | **8** |
| AWAITING_DECISION timeout | **30 seconds** |
| Pre-auth max frame | **4,096 bytes** (unauthenticated GATT) |

No chunks transmit before `PayloadDecision` ACCEPTED.

**ADR**: `docs/decisions/transfer/payload-identity-and-naming.md` | **Spec**: `specs/codecs/frames.yaml#PAYLOAD_MANIFEST`

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

**ADR**: `docs/decisions/transfer/transfer-source-sink-contract.md` | **Spec**: `specs/protocol/state-machines.yaml#TransferState`

### 9.3 Chunks and Selective Acknowledgement

`PayloadChunk` contains only kind, id, index, and payload. Offset, length, and
finality derive from the manifest.

`PayloadAcknowledgement` contains kind, id, `start`, and a fixed 32-byte (256-bit) bitmap:

- All indices below `start` are cumulative.
- Bit n acknowledges `start + n`.
- Sender keeps at most **256 chunks** in flight and rereads missing data through `TransferSource`.

ACK emits after:

- **32 chunks** received, OR
- **100 ms** (HIGH), **250 ms** (MEDIUM), or **500 ms** (LOW), OR
- immediately for gap, full window, final chunk, or retransmission probe.

### 9.4 Retransmission Timeout

RTO is retransmission timeout: the wait without adequate acknowledgement before
selective retransmission. Initial RTO is:

```text
initialRTO = clamp(1s + hopCount × 250ms + powerAckDelay, 1s, 10s)
```

Valid (non-retransmitted) samples update smoothed RTT and variation; RTO becomes:

```text
RTO = smoothedRtt + max(4 × rttVariation, 250ms)    // clamped 1s..30s
```

- **Karn's rule**: retransmitted samples are excluded from RTT estimation.
- **Backoff**: unsuccessful timeout doubles RTO up to 30 seconds.
- Route/bearer change resets the relevant estimator.
- RTO never extends remaining timeToLive.

### 9.5 Transfer Priorities and TTL

| Priority | Default TTL |
|----------|-------------|
| HIGH | 10 minutes |
| NORMAL | 5 minutes |
| LOW | 1 minute |

Priority affects delivery scheduling and default TTL, never maximumHopCount (fixed at 16).

### 9.6 Transfer API

```kotlin
// MESSAGE: in-memory payload up to 64 KiB
suspend fun sendMessage(
    destination: PeerIdentity,
    payload: ByteArray,
    options: TransferOptions = TransferOptions.DEFAULT,
): MessageHandle

// PAYLOAD: large transfer with random-access source
suspend fun sendPayload(
    destination: PeerIdentity,
    source: TransferSource,
    options: TransferOptions = TransferOptions.DEFAULT,
): TransferHandle
```

Both send methods accept optional `TransferOptions.DEFAULT` with initial options
priority and an optional positive time-to-live override. Chunk sizing, bearer
selection, retry algorithms, compression, and cryptographic modes are not
per-transfer options.

**TransferSource** (random-access read interface):

```kotlin
interface TransferSource {
    val total: Long
    suspend fun read(offset: Long, length: Int): ByteArray
}
```

**TransferSink** (host-provided write interface for incoming large transfers):

```kotlin
interface TransferSink {
    suspend fun accept()           // called on manifest acceptance
    suspend fun write(offset: Long, data: ByteArray)  // random-access write
    suspend fun close()           // finalize and acknowledge
    suspend fun cancel()          // reject or abort
}
```

Both `accept(sink)` and `reject()` are idempotent. Handles expose one atomic `StateFlow<TransferStatus>`, an awaitable terminal outcome, and explicit idempotent cancellation.

### 9.7 Wire Frames

| Frame | Code | Purpose |
|-------|------|---------|
| `PAYLOAD_MANIFEST` | `0x20` | Offer and immutable chunk/lifetime contract |
| `PAYLOAD_DECISION` | `0x21` | Accepted or typed rejection |
| `PAYLOAD_CHUNK` | `0x22` | Minimal indexed payload bytes |
| `PAYLOAD_ACKNOWLEDGEMENT` | `0x23` | 256-chunk sliding SACK window (32-byte bitmap) |
| `PAYLOAD_CANCELLATION` | `0x24` | Idempotent message/transfer cancellation |

Every frame repeats kind; identity is `(origin, kind, id)`. All are E2E Noise
records inside hop-encrypted MESH_ENVELOPE.

Receiver delivery is at most once per identity. Sender SUCCESS requires full
acknowledgement; TIMEOUT means confirmation is unknown, not proof of
non-delivery. Large totalLength is allowed through checked Long/UInt/UShort
representation and host sink policy; SDK memory remains window-bounded.

**ADRs**: `docs/decisions/transfer/payload-transfer-protocol.md`, `docs/decisions/transfer/transfer-identifier.md` | **Specs**: `specs/codecs/frames.yaml#PAYLOAD_*`, `specs/codecs/models.yaml`

**SPEC-ANCHOR**: `transfer-session-model`, `scoreboard-model`

---

## 10. Power Management

**ADRs**: `docs/decisions/power/power-mode-behavior.md` | **Specs**: `specs/catalogs/settings.yaml`, `specs/codecs/enums.yaml` | **Code**: `PowerMode.kt` | **Status**: ⚠️ Partial (PowerMode enum/model implemented; behavior/clamping not implemented)

<a id="power-mode-settings"></a>

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
| Idle transition delay | 60 s | 120 s | 300 s |

### 10.2 Active and Idle Connections

Handshakes, urgent control, acknowledgements, and transfer data request the
mode's active interval. After **5 seconds** with no queued work, every mode
requests a **500–1000 ms** idle interval. New work immediately requests active
latency again. Platform clamping/negotiation may delay or alter requests;
effective values are observable via `powerModeSettings` and `PowerModeEffectiveEvent`.

The constitutional 5% scan-duty battery target applies to LOW/background idle
benchmarks. HIGH and MEDIUM intentionally trade battery for discovery and
throughput.

### 10.3 EU Regulatory Clamping

When `regulatoryRegion = RegulatoryRegion.EU`:

- Advertisement interval **clamped to ≥ 300 ms**
- Scan duty cycle **clamped to ≤ 70%**

Applied at runtime, observable in diagnostics (`PowerModeEffectiveEvent`).

| Parameter | Floor/Ceiling |
|-----------|---------------|
| Advertisement interval floor | 300 ms |
| Scan duty cycle ceiling | 70% |

**ADRs**: `docs/decisions/power/power-mode-behavior.md` | **Spec**: `specs/catalogs/settings.yaml#eu_clamping, power_mode_parameter_mapping`

**SPEC-ANCHOR**: `power-mode-settings`

---

## 11. Diagnostics & Events

**ADRs**: `diagnostics/flow-delivery.md`, `api/public-api-and-lifecycle.md` | **Specs**: `specs/catalogs/diagnostic-events.yaml`, `specs/codecs/enums.yaml` | **Code**: `DiagnosticEvent.kt` | **Status**: ⚠️ Partial (DiagnosticEvent/DiagnosticCode models implemented; flow delivery not implemented)

### 11.1 Public Observation

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

**Spec**: `specs/protocol/state-machines.yaml#PeerLifecycle`

### 11.3 Diagnostic Event Metadata

Every diagnostic event carries explicit stable metadata:

```kotlin
val code: DiagnosticCode
val severity: DiagnosticSeverity
val occurredAt: Instant
```

`occurredAt` names the event instant. Diagnostic codes use explicit stable ranges
in the `0x81xx`–`0x8Fxx` space, aligned with (but never overlapping) the Exception
`ErrorCode` ranges (see §7.6) by the high bit `0x8000`: `0x81xx` configuration,
`0x82xx` permission, `0x83xx` bluetooth, `0x84xx` crypto, `0x85xx` routing, `0x86xx` transfer,
`0x87xx` storage, `0x88xx` lifecycle, `0x89xx` transport, `0x8Axx` trust, and `0x8Fxx`
internal. The high bit distinguishes diagnostic codes from error codes on the wire
so a diagnostic event can never be mistaken for an exception. Events may include redacted
PeerIdentity, MessageId/TransferId, frame code, provider label, and error code, but never
raw handles, addresses, keys, ciphertext, payloads, or platform exception text.

**SPEC-ANCHOR**: `diagnostic-event` | **Spec**: `specs/catalogs/diagnostic-events.yaml`

### 11.4 Diagnostic Event Catalog {#diagnostic-event-catalog}

All 10 diagnostic event types, their codes, severity, and fields:

| # | Event | Code | Severity | Category | Fields |
|---|-------|------|----------|----------|--------|
| 1 | `RouteDecryptFailureEvent` | `0x8501` | ERROR | Route | identity: PeerIdentity, frameType: UByte, failureReason: DecryptFailureReason |
| 2 | `TransportFallbackEvent` | `0x8901` | WARN | Transport | identity: PeerIdentity, reason: TransportFallbackReason |
| 3 | `TransferBearerEvent` | `0x8601` | INFO | Transfer | id: TransferId, bearer: Bearer |
| 4 | `PowerModeEffectiveEvent` | `0x8101` | INFO | Configuration | requestedMode, effectiveMode, regulatoryRegion, scanDutyCycle, advertisementInterval, activeConnectionInterval, idleConnectionInterval, idleTransitionDelay, concurrentConnectionLimit, chunkSize, retryLimit, retryBudget, disconnectGracePeriod |
| 5 | `HandshakeEvent` | `0x8401` | INFO/WARN | Crypto | id: HandshakeId, pattern: HandshakePattern, verificationLevel: VerificationLevel, nonceReplayDetected: Boolean |
| 6 | `KeyRotationEvent` | `0x8402` | INFO/WARN | Crypto | identity: PeerIdentity, oldGeneration: UInt, newGeneration: UInt, reason: KeyRotationReason, continuityVerified: Boolean, conflictDetected: Boolean, propagationDeadlineMet: Boolean |
| 7 | `NoiseSessionEvent` | `0x8403` | INFO/WARN | Crypto | id: NoiseSessionId, identity: PeerIdentity, layer: NoiseLayer, role: NoiseRole, pattern: HandshakePattern, fromState: NoiseSessionState, toState: NoiseSessionState, failureReason: NoiseFailureReason? |
| 8 | `RouteDigestMismatchEvent` | `0x8502` | WARN | Route | identity: PeerIdentity, localDigest: ULong, remoteDigest: ULong |
| 9 | `TransferSessionTransitionEvent` | `0x8602` | INFO | Transfer | id: TransferId, identity: PeerIdentity, state: TransferState, offset: Long, total: Long, result: TransferResult? |
| 10 | `TransferFailureEvent` | `0x8603` | ERROR | Transfer | id: TransferId, identity: PeerIdentity, result: TransferResult |

**Code ranges**:

- Configuration: `0x81xx`
- Permission: `0x82xx`
- Bluetooth: `0x83xx`
- Crypto: `0x84xx`
- Routing: `0x85xx`
- Transfer: `0x86xx`
- Storage: `0x87xx`
- Lifecycle: `0x88xx`
- Transport: `0x89xx`
- Trust: `0x8Axx`
- Internal: `0x8Fxx`

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

**ADR**: `docs/decisions/diagnostics/flow-delivery.md`

**SPEC-ANCHOR**: `diagnostic-event`

---

## 12. Build & Quality Constraints

**Status**: ✅ Implemented (build configuration)

### 12.1 Performance Budgets (CI-Enforced)

| Target | Budget | Measurement |
|--------|--------|-------------|
| Throughput (1-hop L2CAP) | ≥80 KB/s (Android Pixel 6+), ≥60 KB/s (iOS iPhone 12+) | `meshlink-benchmark` |
| Latency (1-hop, 256B, p95) | <50 ms after connection established | `meshlink-benchmark` |
| Memory (steady state, 8 peers) | ≤8 MB heap | `meshlink-benchmark` |
| Battery (LOW/background idle) | Target ≤5% scan duty; request 500–1000 ms idle interval after 5 s without queued work | Effective power settings |
| Cold start | <500 ms from `mesh.start()` to first advertisement | `meshlink-benchmark` |
| Routing convergence | ≤3 s for 10-node topology change (virtual transport) | `meshlink-benchmark` |
| Wire codec encode/decode | <1 µs/message (JVM benchmark) | `kotlinx-benchmark` |

**Regression gate**: >10% vs last committed benchmark blocks merge.

### 12.2 Code Quality

- **100% Kover coverage**: line and branch coverage for `:meshlink` — `./gradlew :meshlink:koverVerify`
- Detekt zero suppressions (test suppressions require justification)
- Spotless formatting before every commit — `./gradlew :meshlink:spotlessApply`
- Full descriptive identifiers (no `cfg`, `mgr`, `idx`, `tmp`, `msg`)
- BCV tracks public API; `.api` diff requires version-bump rationale — `./gradlew :meshlink:jvmApiDump`
- `explicitApi()` enabled
- No `TODO` comments in merged code
- Dependencies pinned to exact versions, upgraded promptly
- All Gradle invocations must pass `--rerun-tasks --no-build-cache`

### 12.3 Platform Minimums

| Platform | Minimum |
|----------|---------|
| Android | API 21 (Android 5.0) |
| iOS | iOS 14 |
| JDK | 21 (jvmToolchain(21)) |
| Kotlin | 2.4.10 |
| AGP | 9.3.1 |

Higher APIs guarded at runtime. See `gradle/libs.versions.toml` for exact version pins.

### 12.4 Runtime Dependencies

The shipped `:meshlink` artifact depends on exactly two runtime dependencies:
`kotlinx-coroutines-core` (always) and `ch.trancee.meshlink:meshlink-crypto`
v0.1.1 from Maven Central. `kotlin.time.Duration`, `kotlin.time.Instant`,
and `kotlin.time.Clock` are part of the Kotlin standard library as of Kotlin
2.1+; no separate `kotlinx-datetime` dependency is required. See
CONSTITUTION.md Technical Constraints.

**ADR**: `docs/decisions/crypto/meshlink-crypto-dependency.md` | **Code**: `meshlink/build.gradle.kts`

### 12.5 Quality Gate Tiers

| Tier | When | Runs |
|------|------|------|
| Fast | Every commit, touched modules only | `gitleaks protect --staged`, `./gradlew :meshlink:spotlessApply`, `./gradlew :meshlink:detekt` |
| Full | Every push | `gitleaks detect`, `./gradlew :meshlink:build` |
| CI (authoritative) | Every PR | Full tier + `koverVerify` (100%), `apiCheck`, `spotlessCheck`, iOS device compilation |

---

## 13. Testing & Verification

**Status**: ✅ Implemented (test infrastructure)

### 13.1 Test Suite Structure

| Layer | Location | Coverage |
|-------|----------|----------|
| Unit/JVM | `commonTest` | Full (crypto, routing, transfer, wire codec) |
| Host/Android | `androidHostTest` | Crypto fallback paths |
| Device/Android | `meshlink-proof/android/` | Real BLE behavior |
| Device/iOS | `meshlink-proof/ios/` | Real BLE, Secure Enclave, background modes |
| Reference app | `meshlink-reference` | Public API consumption only |

### 13.2 Key Test Requirements

- **Crypto**: Validated against Wycheproof vectors (all primitives) — `commonTest/resources/wycheproof/`
- **Multi-node**: Virtual mesh harness (no physical hardware in CI)
- **Wire compatibility**: Hex test vectors in `commonTest/resources/wire-compat/`
- **Cross-platform**: Byte-for-byte equality across targets
- **No emulator/simulator BLE tests** — they don't implement real radios

### 13.3 Acceptance Criteria Per Layer

| Layer | Criteria |
|-------|----------|
| Data Model / Trust | Wire vectors, malformed input rejection |
| Discovery / Advertisement | Two-service-UUID format, peerHint rotation/deduplication, RPA races, GATT identity resolution |
| Security Contract | Wycheproof vectors, identity binding, appHash isolation, fail-closed on all edge cases |
| Routing Control | Convergence under virtual harness, seqno correctness |
| Chunked Transfer | Fixed 256-chunk sliding SACK window, cut-through relay, adaptive RTO, retry bounds |
| Power Policy | Mode-to-parameter mapping, EU clamping observable |
| Public API | Identical Android/iOS surface, lifecycle events |

### 13.4 Test File Organization

- **1:1 Mapping**: Each source file gets exactly one test file (`./src/commonTest/.../<Name>Test.kt`)
- **Package Matching**: Test files live in the same package as the source under `commonTest/`
- **Test Patterns**: Constructor-direct usage (`TransferId(42u)`), raw-value comparisons (`.toUInt()`, `.toByteArray()`), backtick test names, AAA structure

### 13.5 Bootstrapping

New clones must run `./scripts/bootstrap.sh` once before building. See `docs/how-to/bootstrap-project-tooling.md`.

---

## 14. Configuration Model

**ADRs**: `docs/decisions/model/settings-model.md`, `docs/decisions/api/public-api-and-lifecycle.md` | **Specs**: `specs/catalogs/settings.yaml` | **Code**: `MeshLinkSettings.kt` | **Status**: ⚠️ Partial (MeshLinkSettings DSL implemented; integration in MeshLink.kt not yet wired)

### 14.1 Settings DSL

```kotlin
// Entry point: ch.trancee.meshlink.meshLinkSettings
val settings = meshLinkSettings {
    appId = "com.example.myapp"           // required; non-empty; ≤ 255 UTF-8 bytes; stable across updates
    powerMode = PowerMode.MEDIUM          // default
    regulatoryRegion = RegulatoryRegion.EU // default DEFAULT
    enableBackground = false              // default

    keyRotation {
        interval = Duration.days(3)       // default
        rotationGracePeriod = Duration.hours(1)  // default; old key still accepted during planned rotation
        compromiseGracePeriod = Duration.ZERO    // default; immediate old-key rejection on security events
    }

    transfer {
        maxRetries = 5                    // default
        chunkSize = 256                   // default (overridden by PowerMode at session start)
        maxTransfersPerPeer = 3           // default; max 3
    }

    routing {
        routeAdvertisementChangeThreshold = 3   // default; dB of smoothed RSSI change
        routeDigestInterval = Duration.minutes(5)  // default
        routeExpiry = Duration.minutes(15)       // default; must be > routeDigestInterval
        maxRoutes = 256                          // default and max
    }

    diagnostics {
        eventBufferSize = 1000  // default
        emitLog = false         // default
    }
}
```

### 14.2 Settings Classes (Immutable)

```kotlin
data class MeshLinkSettings(
    val appId: String,                           // required; ≤ 255 UTF-8 bytes
    val powerMode: PowerMode = PowerMode.MEDIUM,
    val regulatoryRegion: RegulatoryRegion = RegulatoryRegion.DEFAULT,
    val enableBackground: Boolean = false,
    val keyRotation: KeyRotationSettings = KeyRotationSettings(),
    val transfer: TransferSettings = TransferSettings(),
    val routing: RoutingSettings = RoutingSettings(),
    val diagnostics: DiagnosticsSettings = DiagnosticsSettings(),
)

data class KeyRotationSettings(
    val interval: Duration = Duration.days(3),
    val rotationGracePeriod: Duration = Duration.hours(1),
    val compromiseGracePeriod: Duration = Duration.ZERO,
)

data class TransferSettings(
    val maxRetries: Int = 5,
    val chunkSize: Int = 256,       // overridden by PowerMode at session start
    val maxTransfersPerPeer: Int = 3,  // max 3
)

data class RoutingSettings(
    val routeAdvertisementChangeThreshold: Int = 3,  // dB
    val routeDigestInterval: Duration = Duration.minutes(5),
    val routeExpiry: Duration = Duration.minutes(15),  // must be > routeDigestInterval
    val maxRoutes: Int = 256,       // max and default
)

data class DiagnosticsSettings(
    val eventBufferSize: Int = 1000,
    val emitLog: Boolean = false,
)
```

### 14.3 Construction Validation

Static settings are validated during construction and invalid values throw a typed
`ConfigurationException` before an instance is returned. Validation covers:

| Rule | Constraint |
|------|-----------|
| `appId` | Non-empty, normalized UTF-8, ≤ 255 bytes |
| Durations | Positive where required |
| `routeExpiry` vs `routeDigestInterval` | `routeExpiry > routeDigestInterval` |
| `maxRoutes` | Positive, ≤ 256 |
| `maxTransfersPerPeer` | Positive, ≤ 3 |
| Chunk/grace-period bounds | Valid ranges |
| Security invariants | Not disableable |

Runtime prerequisites are validated by `start()`: permissions, Bluetooth
availability, protected key storage, provider self-tests, background integration, and the physical-radio lease.

### 14.4 Runtime Mutability

| Setting | Mutable at runtime? |
|--------|-------------------|
| `powerMode` | ✅ Yes — `setPowerMode()` |
| `regulatoryRegion` | ❌ No — requires new instance |
| `enableBackground` | ❌ No — requires new instance |
| `keyRotation.*` | ❌ No — requires new instance |
| `transfer.*` | ❌ No — requires new instance |
| `routing.*` | ❌ No — requires new instance |
| `diagnostics.*` | ❌ No — requires new instance |

`PowerMode.settings` contains nominal values. `powerMode` exposes the successfully
selected mode, while `powerModeSettings` exposes values after regulatory and
platform clamping. A failed update leaves both at their previous successful values.
Existing transfers retain their established chunk framing; new transfers and connections
use the updated effective values.

`MeshLinkSettings` is the source of truth for defaults — `specs/catalogs/settings.yaml` is
checked against it. Connection interval adaptation: idle after 5s without work, 500–1000 ms idle interval.

**SPEC-ANCHOR**: `settings-model` | **ADR**: `docs/decisions/model/settings-model.md` | **Spec**: `specs/catalogs/settings.yaml`

---

## 15. Future Work

**ADRs**: `docs/decisions/crypto/pq-hybrid-candidate-matrix.md` | **Status**: 🚧 Planned

### 15.1 PQ-Hybrid Key Establishment

**Candidate**: Conservative hybrid (C2) — classical X25519 + ML-KEM-768 with staged extension frames.

**Why not aggressive**: Requires provider maturity and state-machine work beyond first spike.

**ADR**: `docs/decisions/crypto/pq-hybrid-candidate-matrix.md`

### 15.2 Throughput-Based Link Metrics

Replace RSSI proxy with measured throughput for routing decisions (post-connection refinement).

### 15.3 Payload Compression

Optional zlib/Brotli/Zstd for large payloads (RFC 1950/1951/1952, RFC 7932, RFC 8878).

### 15.4 Group Messaging

MLS (RFC 9420) integration for multi-recipient E2E encryption.

---

## 16. Wire Codec Reference

**ADR**: `docs/explanation/why-meshlink-wire-codec.md` | **Specs**: `specs/codecs/frames.yaml` (authoritative) | **Code**: `wire/codec/FrameCodec.kt`, `FrameReader.kt`, `FrameWriter.kt`, `WireFrame.kt`, `Field.kt`, `FieldType.kt`, `ByteOrder.kt` | **Status**: ✅ Implemented (codec primitives; frame content not yet generated/parsed)

> **NOT FlatBuffers**: The MeshLink Wire Codec is a custom pure-Kotlin format inspired by selected FlatBuffers techniques but **not** byte-compatible. No FlatBuffers runtime, `.fbs` schemas, or `flatc` dependency is used. The codec uses explicit UByte codes for FrameType and never encodes enum ordinals.

<a id="wire-envelope"></a>

### 16.1 Envelope Format {#wire-envelope}

Every MeshLink frame on the wire is wrapped in the canonical envelope:

| Field | Type | Size | Byte order | Description |
|-------|------|------|------------|-------------|
| `code` | `UByte` | 1 | — | Explicit `FrameType` code (never ordinal) |
| `version` | `UByte` | 1 | — | Protocol version, bound into signature/AEAD context |
| `length` | `UShort` | 2 | Little-endian | Payload length in bytes: `0..65535` |
| `payload` | `ByteArray` | `length` | — | Wire-encoded frame body |

- Maximum payload: 65,535 bytes (UShort.MAX_VALUE).
- All model values within payloads use **big-endian** encoding (e.g., `SeqNo.toBytes()`, `TransferId.toBytes()`, `PeerIdentity.toBytes()`).
- The envelope's `length` field uses **little-endian** — this is the only little-endian field in the codec, chosen for consistency with BLE/L2CAP length-prefix conventions.
- Unknown `code` values are dropped before payload parsing (fail-closed).

**SPEC-ANCHOR**: `wire-envelope` | **Code**: `FrameCodec.kt`, `Field.kt`, `FieldType.kt`, `ByteOrder.kt` | **Spec**: `specs/codecs/frames.yaml#frame`

### 16.2 FrameType Codes {#frame-type-codes}

All 15 FrameType wire codes are explicit `UByte` values (never ordinals). Unknown codes are rejected.

| Code | Name | Area | Encryption | Purpose |
|------|------|------|------------|---------|
| `0x00` | `MESH_ENVELOPE` | Routing | Hop AEAD per relay path | Routed E2E handshake or payload |
| `0x01` | `ROUTE_ADVERTISEMENT` | Routing | Hop AEAD + destination-signed RouteStatement | Advertise a route with cost |
| `0x02` | `ROUTE_WITHDRAWAL` | Routing | Hop AEAD | Adjacent sender retracts one route |
| `0x03` | `ROUTE_DIGEST` | Routing | Hop AEAD | Summary of sender's RouteExport |
| `0x04` | `ROUTE_SEQUENCE_ADVANCEMENT` | Routing | Hop AEAD | Feasibility-starvation recovery request |
| `0x05` | `ROUTE_SYNCHRONIZATION` | Routing | Hop AEAD | Receiver requests full sync after digest mismatch |
| `0x06` | `ROUTE_SNAPSHOT` | Routing | Hop AEAD | Complete RouteExport response |
| `0x20` | `PAYLOAD_MANIFEST` | Transfer | E2E Noise inside hop AEAD | Offer and immutable chunk/lifetime contract |
| `0x21` | `PAYLOAD_DECISION` | Transfer | E2E Noise inside hop AEAD | Accept or typed rejection |
| `0x22` | `PAYLOAD_CHUNK` | Transfer | E2E Noise inside hop AEAD | Minimal indexed payload bytes |
| `0x23` | `PAYLOAD_ACKNOWLEDGEMENT` | Transfer | E2E Noise inside hop AEAD | 256-chunk sliding SACK window |
| `0x24` | `PAYLOAD_CANCELLATION` | Transfer | E2E Noise inside hop AEAD | Idempotent cancellation |
| `0x40` | `KEY_ROTATION` | Key/Epoch | Hop AEAD + dual signatures | Signed key continuity proof |
| `0x41` | `EPOCH_COMMIT` | Key/Epoch | Pending new-epoch AEAD | Initiator commits new epoch |
| `0x42` | `EPOCH_ACKNOWLEDGEMENT` | Key/Epoch | Pending new-epoch AEAD | Responder acknowledges new epoch |

**Code ranges**: Control/routing `0x01`–`0x06`; Transfer `0x20`–`0x24`; Key/epoch `0x40`–`0x42`. Codes never overlap ranges across protocol areas.

**Spec**: `specs/codecs/frames.yaml#frame_type_enum`, `specs/codecs/enums.yaml#FrameType`

### 16.3 GATT Service and Metadata

**Service UUID** (private, unassigned, not Bluetooth SIG):

| Property | Value |
|----------|-------|
| 32-bit | `0x4D455348` (ASCII `"MESH"`) |
| 128-bit | `4D455348-0000-1000-8000-00805F9B34FB` |
| Assigned by Bluetooth SIG | No |

Private component namespace:

| Component | UUID suffix | Properties |
|-----------|-------------|------------|
| Service | `0000` | — |
| Metadata | `0001` | read |
| Channel | `0002` | write, write-without-response, notify, indicate |
| Reserved | `0003`–`00FF` | Future use; assigned values never reused |

**GATT Metadata Record** (untrusted bootstrap, transcript-bound after Noise):

| Field | Type | Size | Description |
|-------|------|------|-------------|
| `version` | `UByte` | 1 | Protocol version |
| `appHash` | `ByteArray` | 16 | `first128Bits(SHA-256("MeshLink app-id v1" + UTF8(appId)))` — app-id v1 concatenated with UTF8(appId) |
| `identity` | `PeerIdentity` | 16 | Claim only until IK/XX succeeds |
| `keyGeneration` | `UInt` | 4 | Hint for current pin or rotation-recovery selection |
| `psm` | `UShort` | 2 | 16-bit L2CAP PSM; v1 valid values `0x0000` or `0x0080`–`0x00FF` |
| `nonce` | `ByteArray` | 16 | Fresh per GATT connection; mixed into Noise prologue |

**Subscription gate**: Noise cannot start until the Channel characteristic notification/indication subscription is confirmed.

**Spec**: `specs/codecs/frames.yaml#gatt_service, gatt_metadata`

### 16.4 Bearer Framing {#bearer-framing-spec}

#### 16.4.1 GATT Fragmentation

```text
GattFragment {
    index       : UShort   (2 bytes, little-endian)    — zero starts a record
    totalLength : UShort   (2 bytes, little-endian)    — present only when index == 0
    payload     : ByteArray                  — remaining characteristic value bytes
}
```

- Index zero starts a record; completion occurs at exact `totalLength`.
- Continuations must equal previous index + 1 (strictly sequential).
- No flags or record IDs.
- **Maximum frame size**: 65,535 bytes (authenticated); 4,096 bytes (pre-auth/unauthenticated GATT).
- **Invalidation**: Gap, wrap, overflow, timeout, conflicting duplicate, disconnect, or malformed start discards active record; ignore continuations until next index zero.
- **Concurrency**: One active record per direction per `(TransportHandle, ConnectionContext.generation)`.

#### 16.4.2 L2CAP Framing

```text
L2capFrame {
    length    : UShort   (2 bytes, little-endian)  — valid 1..65535
    frameBytes: ByteArray(length)  — exact Wire Codec envelope
}
```

Both bearers yield identical MeshLink Wire Codec frames after bearer deframbling.

**Spec**: `specs/codecs/frames.yaml#gatt_fragment, l2cap_frame`

### 16.5 Frame Field Types

| FieldType | Kotlin type | Size | Byte order | Description |
|-----------|-------------|------|------------|-------------|
| `UByte` | `UByte` | 1 | — | Single unsigned byte |
| `UShort` | `UShort` | 2 | Configurable | 16-bit unsigned integer |
| `UInt` | `UInt` | 4 | Configurable | 32-bit unsigned integer |
| `ULong` | `ULong` | 8 | Configurable | 64-bit unsigned integer |
| `ByteArray` | `ByteArray` | Variable (length-prefixed in envelope) | — | Raw bytes |

- **Envelope fields** (`code`, `version`): unsigned byte, no endianness.
- **Envelope `length`**: little-endian.
- **Inner model fields** (SeqNo, TransferId, PeerIdentity, etc.): big-endian.

**Spec**: `specs/codecs/frames.yaml#frame`, `specs/codecs/enums.yaml` | **Code**: `Field.kt`, `FieldType.kt`, `ByteOrder.kt`

### 16.6 Field Encoding Rules

- All `UByte` codes are explicit, never enum ordinals.
- `UInt` and `ULong` fields use big-endian for model serialization.
- `UShort` length fields in bearer framing use little-endian.
- The `version` byte is bound into the Noise transcript hash and all signature contexts.
- Unknown frame codes are dropped before payload parsing (fail-closed, see §7.2).

---

## 17. Implementation Status Summary

The following table reconciles the implementation status from `specs/traceability/specification-map.yaml` with the current source state. "Implemented" means the data model or primitive exists with full test coverage; "Partial" means the model exists but the protocol-layer wiring is not yet implemented; "Not Implemented" means the feature is specified but no implementation exists beyond scaffolding.

| § Section | Status | Key implemented files | Key not-yet-implemented components |
|-----------|--------|----------------------|-----------------------------------|
| §1 Vision | ✅ Complete | — | — |
| §2 Architecture | ⚠️ Partial | `MeshLink.kt` (scaffold), `MeshLinkEnvironment.kt` | Lifecycle (start/pause/resume/stop), BLE glue (`androidMain`, `iosMain`) |
| §3 Core Data Models | ✅ Implemented | All model value classes, `Enums.kt`, `Scoreboard.kt`, `SeqNo.kt` | `RouteCandidate`, `LinkQuality` (planned) |
| §4 Discovery & Identity | ❌ Not Implemented | `MeshHash.kt` | Advertisement, peerHint rotation, GATT metadata resolution |
| §5 Trust Model (TOFU) | ❌ Not Implemented | `ConstantTime.kt`, `ReplayWindow.kt` | Noise XX/IK, identity binding, key rotation |
| §6 Transport Layer | ❌ Not Implemented | `L2capState.kt` | Bearer selection, GATT/L2CAP framing, negotiation sequence |
| §7 Security Layer | ⚠️ Partial | `ConstantTime.kt`, `ReplayWindow.kt`, `PayloadDecision.kt` | Noise state machine, CryptoProvider wiring, key rotation protocol |
| §8 Routing Layer | ❌ Not Implemented | `RoutingPolicy.kt` (config only) | RouteCandidate, RouteStatement, RouteCoordinator, RouteDigestTracker, RouteAdvertisementPlanner |
| §9 Transfer Layer | ⚠️ Partial | `TransferSession.kt`, `Scoreboard.kt`, `TransferResult.kt`, `TransferId.kt`, etc. | `TransferCoordinator`, message/transfer integration |
| §10 Power Management | ⚠️ Partial | `PowerMode.kt` | Mode-driven behavior (scan/adv/conn intervals, EU clamping) |
| §11 Diagnostics & Events | ⚠️ Partial | `DiagnosticEvent.kt`, `DiagnosticCode` | Diagnostic flow delivery |
| §12 Build & Quality | ✅ Implemented | `build.gradle.kts` | — |
| §13 Testing & Verification | ✅ Implemented | Test infrastructure | Multi-node virtual harness, device proof tests |
| §14 Configuration Model | ⚠️ Partial | `MeshLinkSettings.kt` | DSL integration in `MeshLink.kt` (create/observe wiring) |
| §15 Future Work | 🚧 Planned | — | PQ-hybrid, throughput metrics, compression, group messaging |
| §16 Wire Codec | ✅ Implemented | `FrameCodec.kt`, `FrameReader.kt`, `FrameWriter.kt` | Frame content generation/parsing (not yet wired to protocol) |

**Legend**: ✅ Complete | ⚠️ Partial | ❌ Not Implemented | 🚧 Planned

---

## 18. Machine-Readable Specification Layout

| Path | Ownership | Purpose |
|------|-----------|---------|
| `specs/codecs/frames.yaml` | Authored normative | Exact MeshLink frame codes, fields, widths, bounds, protection, and evolution |
| `specs/codecs/enums.yaml` | Authored normative | Explicit enum storage/codes/reserved ranges/unknown behavior; never ordinals |
| `specs/codecs/models.yaml` | Authored normative | Reusable encoded value layouts |
| `specs/protocol/state-machines.yaml` | Authored normative | Protocol states, guards, timing, retry, and invariants |
| `specs/catalogs/diagnostic-events.yaml` | Source-derived | Diagnostic event names, fields, and severity |
| `specs/catalogs/settings.yaml` | Source-derived | Configuration/default catalog |
| `specs/traceability/specification-map.yaml` | Generated traceability | SPEC ↔ ADR ↔ codec ↔ code ↔ test map and implementation status |
| `specs/product/` | Authored planning | Product scope, vision, and success criteria |
| `specs/epics/` | Authored planning | Epic-level story plans |
| `specs/tests/` | Authored planning | Test architecture and strategy |

Codec/protocol files are reviewed source, not generated runtime code. The custom
pure-Kotlin MeshLink Wire Codec must conform to them. Catalog/traceability update
tooling must make CI fail when committed projections are stale.

---

## 19. Traceability Index

| Spec Section | ADR(s) | Code Location | Status |
|--------------|--------|---------------|--------|
| §1 Vision | — | — | ✅ |
| §2 Architecture | `docs/decisions/api/public-api-and-lifecycle.md`, `docs/explanation/module-structure.md` | `meshlink/build.gradle.kts`, `MeshLink.kt`, `MeshLinkEnvironment.kt` | ⚠️ |
| §3 Data Models | `docs/decisions/model/data-model.md` | `meshlink/src/commonMain/kotlin/ch/trancee/meshlink/model/` (PeerIdentity, SeqNo, Scoreboard, TransferSession, Enums, IdentityKey, HandshakeKey, AppHash, MeshLinkVersion, KnownPeer, TransferResult, etc.) | ✅ |
| §4 Discovery | `connectable-advertisement.md`, `mesh-hash-derivation.md`, `peer-hint-and-identity-races.md` | `MeshHash.kt` | ❌ |
| §5 Trust/TOFU | `crypto-design.md`, `identity-binding-and-fail-closed.md`, `private-key-handling.md`, `key-rotation-propagation.md`, `noise-session-renewal.md`, `replay-window.md`, `constant-time-policy.md`, `persistence-strategy.md` | `ConstantTime.kt`, `ReplayWindow.kt`, `PayloadDecision.kt` | ⚠️ |
| §6 Transport | `gatt-channel-and-framing.md`, `mtu-negotiation.md`, `background-operation.md` | `L2capState.kt`, `SecureRandom.kt` | ❌ |
| §7 Security | `crypto-design.md`, `identity-binding-and-fail-closed.md`, `constant-time-policy.md`, `private-key-handling.md`, `replay-window.md`, `noise-session-renewal.md`, `key-rotation-propagation.md`, `error-hierarchy.md`, `meshlink-crypto-dependency.md` | `ConstantTime.kt`, `ReplayWindow.kt`, `PayloadDecision.kt` | ⚠️ |
| §8 Routing | `routing-design.md`, `mesh-size-limits.md` | `RoutingPolicy.kt` | ❌ |
| §9 Transfer | `data-model.md`, `error-hierarchy.md`, `transfer-identifier.md`, `payload-transfer-protocol.md`, `payload-identity-and-naming.md`, `transfer-source-sink-contract.md` | `TransferSession.kt`, `Scoreboard.kt`, `TransferResult.kt`, `TransferId.kt`, `TransferKind.kt`, `TransferOptions.kt`, `TransferStatus.kt`, `Transfer.kt`, `TransferHandle.kt`, `TransferSink.kt`, `TransferSource.kt`, `MessageId.kt`, `Message.kt`, `MessageHandle.kt` | ⚠️ |
| §10 Power | `power-mode-behavior.md` | `PowerMode.kt` | ⚠️ |
| §11 Diagnostics | `flow-delivery.md`, `public-api-and-lifecycle.md` | `DiagnosticEvent.kt` | ⚠️ |
| §12 Build Quality | `meshlink-crypto-dependency.md` | `meshlink/build.gradle.kts` | ✅ |
| §13 Testing | — | `commonTest/`, `androidHostTest/`, `meshlink-proof/`, `meshlink-benchmark/` | ✅ |
| §14 Configuration | `settings-model.md`, `public-api-and-lifecycle.md` | `MeshLinkSettings.kt` | ⚠️ |
| §15 Future | `pq-hybrid-candidate-matrix.md` | — | 🚧 |
| §16 Wire Codec | `docs/explanation/why-meshlink-wire-codec.md` | `wire/codec/FrameCodec.kt`, `FrameReader.kt`, `FrameWriter.kt`, `WireFrame.kt`, `Field.kt`, `FieldType.kt`, `ByteOrder.kt` | ✅ |

---

## SPEC-ANCHOR Reference

The following `SPEC-ANCHOR` tags are used throughout this specification and in source files. Each anchor must resolve to a section in this document.

| Anchor | § Section | Source File |
|--------|-----------|-------------|
| `specification-map` | §19 (this index) | `specs/traceability/specification-map.yaml` |
| `peer-identity-model` | §3.1 | `PeerIdentity.kt` |
| `seqno-model` | §3.2 | `SeqNo.kt` |
| `scoreboard-model` | §3.3 | `Scoreboard.kt` |
| `transfer-session-model` | §3.4 | `TransferSession.kt` |
| `transfer-result` | §3.6 | `TransferResult.kt` |
| `transfer-id-model` | §3.7 | `TransferId.kt` |
| `message-id-model` | §3.7 | `MessageId.kt` |
| `link-quality-model` | §3.8 | (planned: LinkQuality in routing) |
| `identity-key-model` | §3.9 | `IdentityKey.kt` |
| `handshake-key-model` | §3.9 | `HandshakeKey.kt` |
| `meshlink-version` | §3.10 | `MeshLinkVersion.kt` |
| `app-hash-model` | §3.11 | `AppHash.kt` |
| `enums` | §3.12 | `Enums.kt` |
| `known-peer-model` | §3.13 | `KnownPeer.kt` |
| `wire-codec-core` | §3.14, §16 | `wire/codec/FrameCodec.kt` |
| `discovery-advertisement` | §4.1 | (planned) |
| `mesh-hash` | §4.2 | `MeshHash.kt` |
| `power-mode-settings` | §10.1 | `PowerMode.kt` |
| `fail-closed` | §7.2 | (policy) |
| `provider-boundaries` | §7.3 | (policy) |
| `constant-time` | §7.4 | `ConstantTime.kt` |
| `replay-window` | §7.5 | `ReplayWindow.kt` |
| `error-hierarchy` | §7.6 | `ErrorCode.kt`, `Exceptions.kt` |
| `error-code` | §7.6 | `ErrorCode.kt` |
| `diagnostic-event` | §11.3 | `DiagnosticEvent.kt` |
| `settings-model` | §14 | `MeshLinkSettings.kt` |
| `wire-envelope` | §16.1 | `FrameCodec.kt`, `Field.kt` |
| `frame-type-codes` | §16.2 | `Enums.kt` (FrameType) |
| `bearer-framing-spec` | §16.4 | `FrameReader.kt`, `FrameWriter.kt` |
| `state-machine` | §3.5, §7.5, §11.2 | `state-machines.yaml` |

---

<!--
  SPEC-ANCHOR: specification-map
  This section is the machine-readable traceability index cross-referenced
  against specs/traceability/specification-map.yaml.
-->
