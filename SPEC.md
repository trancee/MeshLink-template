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
| 4 | **Reliable large-payload transfer** | Chunked transfer with selective acknowledgment (SACK), retransmission, and reassembly over small-frame BLE radio |
| 5 | **Power-aware operation** | Discrete power modes governing scan duty cycle, advertisement interval, connection interval, concurrent connections, and transfer chunk size |
| 6 | **Deterministic cross-platform parity** | Identical lifecycle states, sealed error hierarchies, and diagnostic codes across Android and iOS |

### 1.3 Non-Functional Requirements

| Requirement | Constraint |
|-------------|------------|
| Offline operation | Zero connectivity required once permissions granted |
| Persisted state | Only trust pin (identity material + first/verified instants); no plaintext or full identifiers cached |
| Pending state | In-memory only; does not survive process restart |
| Delivery outcomes | Explicit: `success`, `in-progress`, `retrying`, `route-waiting`, `unreachable`, `trust-failure`, `timeout`, `unrecoverable-failure` |
| Wire compatibility | Backward-compatible evolution; breaking changes require major version bump + migration |
| Performance budgets | See [§12](#12-build--quality-constraints) |
| Runtime dependency | Maximum one Maven artifact: `kotlinx-coroutines-core`. Crypto uses platform APIs or pure-Kotlin fallbacks |
| Test coverage | 100% line/branch coverage for `:meshlink`; crypto validated against Wycheproof vectors |

### 1.4 Reference Standards

| Layer | Standards |
|-------|-----------|
| Crypto primitives | RFC 7748 (X25519), RFC 8032 (Ed25519), RFC 8439 (ChaCha20-Poly1305), RFC 5869 (HKDF), RFC 2104 (HMAC), RFC 6234 (SHA-2) |
| Handshake patterns | Noise Protocol Framework (XX, IK, IX, NX) |
| Routing | RFC 8966 (Babel) — feasibility condition, seqno, route digest |
| Transfer | RFC 2018 (TCP SACK), RFC 7233 (HTTP Range) |
| Replay protection | RFC 9147 (DTLS 1.3 sliding window) |
| Opportunistic security | RFC 7435 (design philosophy) |
| Wire encoding | FlatBuffers-compatible pure-Kotlin codec (not CBOR) |
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

The shipped artifact exposes **one entry point**:

```kotlin
// ch.trancee.meshlink.MeshLink
object MeshLink {
    const val VERSION: String = "0.0.0"  // Replaced at release
    // Full API surface defined in implementation milestones
}
```

All public API lives in `ch.trancee.meshlink` package. Platform differences hidden behind `expect`/`actual`.

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

**Unsigned 32-bit sequence number with safe wrap-around comparison.** Per RFC 8966 §3.7, comparisons use signed interpretation. Implements [Comparable] for sorting and standard ordering utilities.

```kotlin
@JvmInline value class SeqNo(private val value: UInt) : Comparable<SeqNo> {
    companion object {
        val ZERO: SeqNo = SeqNo(0u)
        val MAX_VALUE: SeqNo = SeqNo(UInt.MAX_VALUE)
        fun fromUInt(value: UInt): SeqNo = SeqNo(value)
        fun fromByteArray(bytes: ByteArray): SeqNo  // 4-byte big-endian deserialization
    }
    fun toUInt(): UInt = value
    fun toByteArray(): ByteArray                    // 4-byte big-endian serialization
    val isZero: Boolean
    fun isNewerThan(other: SeqNo): Boolean = (value - other.value).toInt() > 0
    fun isNewerThanOrEqualTo(other: SeqNo): Boolean = (value - other.value).toInt() >= 0
    fun isOlderThan(other: SeqNo): Boolean = other.isNewerThan(this)
    fun isOlderThanOrEqualTo(other: SeqNo): Boolean = other.isNewerThanOrEqualTo(this)
    operator fun minus(other: SeqNo): Int = (value - other.value).toInt()
    override fun compareTo(other: SeqNo): Int = minus(other)
    operator fun inc(): SeqNo = SeqNo(value + 1u)  // wraps at 2^32
    fun max(other: SeqNo): SeqNo
    fun min(other: SeqNo): SeqNo
    fun unsignedDistance(other: SeqNo): UInt         // modular forward distance, UInt wraparound
}
```

- Incremented **only on cold start** (`MeshLink.start()`)
- Self-reported by destination in `RouteUpdate` frames
- `toUInt()`/`fromUInt()` for logical wire serialization (value extraction)
- `toByteArray()`/`fromByteArray()` for 4-byte big-endian byte-level wire serialization
- `isNewerThanOrEqualTo` used by Babel feasibility condition (RFC 8966 §3.7)
- `max`/`min` for route table merges
- `compareTo` enables `sortedBy()`, `min()`, `max()` on `Iterable<SeqNo>`
- `unsignedDistance` for route staleness diagnostics and gap analysis
- **SPEC-ANCHOR**: `seqno-model`

### 3.3 Scoreboard (Immutable) & MutableScoreboard {#scoreboard-model}

**Immutable bitfield for selective acknowledgment (SACK).** Bit N = 1 means chunk N received. Length = `ceil(totalChunks / 8)` bytes.

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

### 3.4 RouteEntry {#route-entry-model}

```kotlin
data class RouteEntry(
    val source: PeerIdentity,           // Peer from whom route was learned
    val destination: PeerIdentity,      // Final destination
    val nextHop: PeerIdentity?,         // Immediate next hop (null = self-origin)
    val metric: UInt,                   // Composite: RSSI (low byte) + flags (high bits)
    val seqNo: SeqNo,                   // Destination-self-reported
    val identityKey: IdentityKey?,      // Destination's Ed25519 key
    val handshakeKey: HandshakeKey?,    // Destination's X25519 key
    val expiresAt: Instant              // Expiration time
)
```

**SPEC-ANCHOR**: `route-entry-model`

### 3.5 TransferSession {#transfer-session-model}

```kotlin
data class TransferSession(
    val sessionId: SessionId,
    val destination: PeerIdentity,
    val priority: Priority,
    val state: TransferState,
    val chunkSize: Int,                 // Bounded by peer MTU, selected by PowerMode
    val totalChunks: UInt,
    val scoreboard: Scoreboard,
    val totalBytes: Long,
    val bytesReceived: Long,
    val startedAt: Instant,
    val expiresAt: Instant?,            // Deadline for WAITING_FOR_ROUTE
    val retryCount: Int,
    val failureReason: TransferFailureReason?
)
```

**SPEC-ANCHOR**: `transfer-session-model`

### 3.6 TransferState

| State | Terminal | Description |
|-------|----------|-------------|
| `IN_PROGRESS` | No | Actively transferring |
| `WAITING_FOR_ROUTE` | No | Route lost, waiting for recovery |
| `RETRYING` | No | Retransmitting missing chunks with backoff |
| `COMPLETED` | **Yes** | All chunks received, scoreboard complete |
| `FAILED` | **Yes** | Unrecoverable or trust-related failure |
| `TIMED_OUT` | **Yes** | Retry budget/grace period exhausted |

### 3.7 TransferFailureReason (Sealed) {#transfer-failure-reason-model}

```kotlin
sealed interface TransferFailureReason {
    data class Unrecoverable(val message: String) : TransferFailureReason
    data class TrustFailure(val peerIdentity: PeerIdentity) : TransferFailureReason
}
```

### 3.8 SessionId {#session-id-model}

```kotlin
@JvmInline value class SessionId(private val value: ULong) {
    companion object { fun fromHex(hex: String): SessionId }
    override fun toString(): String  // Hex representation
}
```

**SPEC-ANCHOR**: `session-id-model`

### 3.9 LinkMetric {#link-metric-model}

**Composite UInt32 metric:**

| Bits | Field | Range | Description |
|------|-------|-------|-------------|
| 0-7 | `rssiNormalized` | 0-255 | RSSI normalized (0=unusable, 255=excellent) |
| 8 | `supportsL2CAP` | 0/1 | L2CAP CoC supported |
| 9 | `lowLatency` | 0/1 | Short connection interval |
| 10 | `highPower` | 0/1 | High power mode |
| 11-31 | Reserved | 0 | Future use |

**RSSI Normalization:**

```kotlin
rssiNormalized = when {
    rssi >= -30 -> 255
    rssi <= -100 -> 0
    else -> ((rssi + 100) * 255 / 70).toUInt()
}
```

**SPEC-ANCHOR**: `link-metric-model`

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

### 3.11 Enums (Shared) {#type-model}

| Enum | Values | Notes |
|------|--------|-------|
| `KeyType` | `ED25519`, `X25519` | Distinguishes key purposes |
| `KeyRotationReason` | `PERIODIC`, `MANUAL`, `SECURITY_EVENT` | |
| `HandshakePattern` | `XX`, `IK`, `IX`, `NX` | Noise patterns |
| `ScoreboardEncoding` | `DYNAMIC`, `FIXED` | SACK bitfield strategy |
| `Priority` | `HIGH` (10min TTL), `NORMAL` (5min), `LOW` (1min) | Affects routing TTL |
| `FrameType` | `MESH_ENVELOPE(0)`, `ROUTE_UPDATE(1)`, `ROUTE_WITHDRAWAL(2)`, `ROUTE_DIGEST(3)`, `TRANSFER_CHUNK(4)`, `TRANSFER_ACKNOWLEDGMENT(5)`, `TRANSFER_CANCEL(6)`, `KEY_ROTATION(7)` | Wire frame types |
| `DecryptFailureReason` | `AUTHENTICATION_TAG_MISMATCH`, `REPLAY_DETECTED`, `SEQUENCE_NUMBER_MISMATCH`, `KEY_UNAVAILABLE`, `MALFORMED_FRAME` | |
| `TransportFallbackReason` | `NO_PSM_ADVERTISED`, `L2CAP_CONNECT_FAILED`, `L2CAP_DROPPED_MID_TRANSFER`, `LOCAL_POLICY` | |
| `DataPlaneBearer` | `GATT`, `L2CAP` | |
| `RegulatoryRegion` | `DEFAULT`, `EU` | EU clamps adv≥300ms, scan≤70% |
| `NoiseLayer` | `HOP_BY_HOP`, `END_TO_END` | |
| `NoiseSessionState` | `DISCONNECTED`, `HANDSHAKING_XX`, `HANDSHAKING_IK`, `HANDSHAKING_IX`, `HANDSHAKING_NX`, `ESTABLISHED`, `REKEYING`, `FAILED` | |
| `NoiseRole` | `INITIATOR`, `RESPONDER` | |
| `NoiseFailureReason` | `HANDSHAKE_TIMEOUT`, `HANDSHAKE_MESSAGE_MALFORMED`, `HANDSHAKE_MESSAGE_OUT_OF_ORDER`, `REMOTE_STATIC_KEY_MISMATCH`, `REMOTE_STATIC_KEY_UNKNOWN`, `REKEY_REJECTED`, `TRANSPORT_CLOSED`, `MAX_RETRIES_EXCEEDED`, `INTERNAL_ERROR` | |
| `PowerMode` | `HIGH`, `MEDIUM`, `LOW` | See §10 for parameters |
| `VerificationLevel` | `FULL`, `TOFU_PIN`, `NX_VERIFIED`, `NONE` | Handshake verification achieved |
| `TransferDeliveryOutcome` | `SUCCESS`, `IN_PROGRESS`, `RETRYING`, `ROUTE_WAITING`, `TIMEOUT`, `UNRECOVERABLE_FAILURE`, `TRUST_FAILURE` | Mapped from TransferState |
| `PeerConnectionState` | `CONNECTED`, `DISCONNECTED` | Public API |
| `PeerLifecycleState` (internal) | `CONNECTED`, `DISCONNECTED`, `GONE` | Internal runtime tracking |
| `TrustState` | `INITIATED`, `TRUSTED`, `REVOKED` | TOFU lifecycle |
| `KeyRotationState` (internal) | `CURRENT`, `GRACE_PERIOD`, `REVOKED` | Per-peer key status |

**SPEC-ANCHOR**: `type-model`

---

## 4. Discovery & Identity

### 4.1 Advertisement Format

Single BLE advertisement packet (non-connectable, undirected):

| Field | Size | Description |
|-------|------|-------------|
| Fixed UUID | 4 bytes | `4d455348` ("MESH" in ASCII) |
| Protocol version | 3 bits | Current protocol version |
| Platform | 2 bits | `0=Android`, `1=iOS`, `2=Desktop`, `3=Reserved` |
| Power mode | 3 bits | `0=HIGH`, `1=MEDIUM`, `2=LOW`, `3-7=Reserved` |
| Mesh hash | 16 bits | Application isolation filter (FNV-1a of appId) |
| L2CAP PSM hint | 8 bits | Assigned PSM from 0x0080–0x00FF; `0` = CoC not supported |
| PeerFingerprint | 12 bytes | SHA-256(Ed25519Pub \|\| X25519Pub) truncated to 96 bits; discovery hint only |

**Total**: ~31 bits + 4 bytes + 12 bytes ≈ 31 bytes (fits in BLE ad packet)

### 4.2 Mesh Hash Derivation {#mesh-hash}

```kotlin
// FNV-1a 32-bit of appId, truncated to 16 bits
meshHash = fnv1a_32(appId.toByteArray()) & 0xFFFF
```

**Purpose**: Prevents cross-application discovery. Apps with different `appId` won't discover each other.

### 4.3 PeerFingerprint

- **Not used for authentication** — only a discovery hint
- Full public keys exchanged during Noise handshake
- Truncated SHA-256 provides 96-bit collision resistance for passive correlation

### 4.4 Privacy Trade-offs

| Aspect | Trade-off |
|--------|-----------|
| Stable PeerFingerprint | Passive observers can correlate repeated sightings more easily than rotating pseudonyms |
| Protected | Full public keys not advertised; plaintext never in ads; hop/E2E session keys established post-discovery |
| Isolation | Mesh hash derived from `appId` prevents cross-application discovery |

**ADR**: docs/decisions/discovery/mesh-hash-derivation.md

---

## 5. Trust Model (TOFU)

### 5.1 Handshake Patterns

| Layer | First Contact | Post-TOFU Reconnect | End-to-End |
|-------|---------------|---------------------|------------|
| Pattern | `Noise_XX` | `Noise_IK` | `Noise_IX` |
| Protocol | `Noise_XX_25519_ChaChaPoly_SHA256` | `Noise_IK_25519_ChaChaPoly_SHA256` | `Noise_IX_25519_ChaChaPoly_SHA256` |
| Auth | Mutual (both pins) | Mutual (both pinned) + 0-RTT | One-way (origin knows dest key) |

### 5.2 Trust Flow

```text
Discovery → GATT connection → Noise_XX handshake → INITIATED
                                                    ↓
                                            TOFU pin (first success)
                                                    ↓
                                                    TRUSTED → TrustRecord stored
```

### 5.3 Identity Distribution via Route Updates

- Each peer's public key included in `ROUTE_UPDATE` encrypted payload
- Direct neighbor (Noise XX) learns neighbor's public key, includes in route updates
- Route updates propagate hop-by-hop: each relay re-advertises destination's public key
- Enables E2E IX handshake where origin knows destination's static key before connecting
- NX fallback used only when destination key not in routing table

### 5.4 NX Fallback (Unknown Destination Key)

**Trigger**: Cold start discovery, key rotation lag, network partition

**Protocol**: `Noise_NX_25519_ChaChaPoly_SHA256` with mitigations:

| Mitigation | Parameter |
|------------|-----------|
| Rate limit | 3 attempts/minute per destination |
| Timeout | 10s (vs 30s for IX) |
| Full public key verification | 64-byte Ed25519 \|\| X25519 in payload |
| Replay protection | 32-bit nonce in payload |
| Observability | `handshake.fallback_used = true` diagnostic flag |

**Payload Verification**: `received_ed25519 == expected_ed25519 AND received_x25519 == expected_x25519` (510 bits effective entropy)

### 5.5 Key Rotation Protocol

**Triggers**: Periodic timer (default 3 days), manual API, security event

**Wire Format** (`KEY_ROTATION` frame, plaintext but signed):

```flatbuffers
KeyRotationAnnouncement {
    identityKey: IdentityKey      // NEW Ed25519 public key (32 bytes)
    handshakeKey: HandshakeKey    // NEW X25519 public key (32 bytes)
    seqNo: UInt                   // Always 1 — new crypto era
    signature: ByteArray          // 64-byte Ed25519 sig with OLD private key
    reason: KeyRotationReason     // PERIODIC | MANUAL | SECURITY_EVENT
}
```

**Neighbor Behavior**:

1. Verify signature with OLD known key
2. Accept new key into TrustStore
3. SeqNo resets to 1
4. Old key retained for grace period

**Grace Periods**:

| Rotation Type | Grace Period | Old Key |
|---------------|--------------|---------|
| PERIODIC/MANUAL | `rotationGracePeriod` (default 1h) | Accepted for in-flight sessions |
| SECURITY_EVENT | `compromiseGracePeriod` (default 0) | Rejected immediately |

**During Active Transfer**: Existing Noise sessions continue with current traffic keys. New sessions use rotated keys. Transfer layer is identity-key agnostic.

### 5.6 E2E Handshake Routing Over Mesh {#trust-record}

When destination not a direct neighbor:

```text
Phase 1: Link Setup (Noise_XX)
Origin --(GATT/L2CAP)--> Relay(s) --> Destination

Phase 2: E2E Handshake Routing
Origin wraps IX_Msg1 in RoutingFrame:
  RoutingFrame {
    destination: destination.peerIdentity,
    payload: IX_Msg1_encrypted,
    hopLimit: UByte
  }
Relay(s) decrypt hop layer → re-encrypt → forward (no E2E inspection)

Phase 3: Destination responds with IX_Msg2 wrapped for return path

Phase 4: Origin now has E2E traffic keys
```

**Security**: Relays cannot read E2E content; only link-layer encryption per hop.

### 5.7 Revocation

- Explicit API action required to reset trust
- No silent re-trust on identity mismatch
- Stored trust records persist until revoked

**ADR**: docs/decisions/crypto/crypto-design.md

---

## 6. Transport Layer

### 6.1 Bearer Selection

| Traffic Type | Preferred Bearer | Fallback |
|--------------|------------------|----------|
| Control plane | GATT (unconditionally) | None — GATT always available |
| Data plane | L2CAP CoC | GATT with same correctness guarantees |

**Control plane MUST work over GATT alone** for reliability.

### 6.2 Negotiation Sequence

1. GATT connection establishes
2. `Noise_XX_25519_ChaChaPoly_SHA256` handshake completes (control plane)
3. If both peers advertised PSM hint, attempt L2CAP CoC channel
4. On CoC success, promote data-plane traffic to CoC
5. On CoC failure, continue on GATT

### 6.3 Fallback Reasons (Machine Observable)

| Reason | Description |
|--------|-------------|
| `NO_PSM_ADVERTISED` | Peer didn't advertise PSM in discovery |
| `L2CAP_CONNECT_FAILED` | CoC connection failed |
| `L2CAP_DROPPED_MID_TRANSFER` | CoC channel dropped during transfer |
| `LOCAL_POLICY` | Local configuration disabled CoC |

**ADR**: docs/decisions/transport/mtu-negotiation.md

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

### 7.2 Fail-Closed Rules {#constant-time}

- Malformed/untrusted input **never** surfaces private keys in logs
- Invalid X25519 public keys fail before HKDF derivation
- Decrypt/sign/verify failures stop operation immediately
- No fallback to plaintext or cached secrets
- All cryptographic field operations and comparisons **MUST** implement constant-time algorithms

### 7.3 Android Crypto Constraints

- API 26-32: Runtime checks for X25519/XDH and ChaCha20-Poly1305 availability
- Pure-Kotlin fallback implementations for older devices
- Ed25519 fallback with constant-time arithmetic

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

- Window size: 64 packets (configurable)
- Per-epoch numbering (epoch increments on KeyUpdate)
- Deprotect-before-advance to avoid timing channels
- Replay detection on both hop-by-hop and E2E layers

**ADR**: docs/decisions/crypto/replay-window.md

### 7.6 Error Hierarchy (Sealed) {#delivery-outcome}

```kotlin
sealed class MeshLinkError : Exception()
sealed class SecurityError : MeshLinkError()
sealed class TrustError : SecurityError()
sealed class CryptoError : SecurityError()
sealed class TransportError : MeshLinkError()
sealed class RoutingError : MeshLinkError()
sealed class TransferError : MeshLinkError()
```

All errors in `commonMain`. Platform exceptions wrapped, never leak to consumers.

**ADR**: docs/decisions/model/error-hierarchy.md

---

## 8. Routing Layer

### 8.1 Design Principles

- **Babel-inspired** distance-vector with feasibility condition (RFC 8966)
- **Destination self-reports SeqNo** — originated only on cold start, not on reconnect
- **No Hello/IHU** — BLE connection state provides liveness (RFC 8966 §3.4)
- **RouteDigest triggers full table push** on mismatch
- **Always-encrypted metadata** — no plaintext routing frames
- **Composite link metric** — RSSI + capability flags

### 8.2 SeqNo Ownership (Critical Fix)

**Bug Fixed**: `RouteCoordinator.onPeerConnected` previously minted fresh seqno on every BLE reconnect, causing different direct neighbors to have unrelated seqno sequences.

**Solution**: Each node owns **one** local seqno counter (32-bit unsigned), incremented **only on cold start** (`MeshLink.start()`). On new direct connection, each side sends one self-origin `RouteUpdate`:

- `destination = <own peerId>`
- `nextHop = <own peerId>` (null = self-origin)
- `metric = DIRECT_ROUTE_METRIC`
- `seqNo = <own current counter>`

Receiving side adopts reported seqNo as authoritative. No new wire frame type needed.

### 8.3 Hello/IHU Removal

Removed `WireFrame.Hello`, `WireFrame.Ihu`, their type codes, and no-op dispatch branches. BLE connect/disconnect provides immediate liveness. Route metric stays flat `+1` hop count.

### 8.4 RouteDigest Resync

On receiving `RouteDigest` that doesn't match local table, receiver re-sends full current route table to that peer via `RouteAdvertisementPlanner`. Mirrors RFC 8966 wildcard route request → full table dump. No new frame type, no request/response round trip.

### 8.5 Routing Metadata Privacy

**ROUTE_UPDATE (0x01)** and **ROUTE_WITHDRAWAL (0x02)** always carry AEAD-encrypted payloads. **No plaintext mode, no negotiation, no fallback.**

**Encryption**:

- Algorithm: ChaCha20-Poly1305 (Noise session AEAD)
- Nonce: Derived from Noise session internal counter (not transmitted)
- Ciphertext: `encrypted_payload || 16-byte Poly1305 tag`
- AAD: Frame type + version

**Fail-Closed**: Decrypt/auth failures drop frame immediately. No retry with different mode.

**Diagnostics**: `route.decrypt_failures` count, `route.frame_type` (UPDATE/WITHDRAWAL)

### 8.6 Link Quality Metric

**Composite UInt32**: Low byte = RSSI normalized (0-255), High bits = flags (CoC, latency, power).

Path selection prefers: 1) Feasible routes only, 2) Lower hop count, 3) Higher metric score.

### 8.7 Route Update Triggers {#ttl-by-priority}

| Trigger | Condition | Frame | Jitter | Immediate |
|---------|-----------|-------|--------|-----------|
| `DIRECT_LINK_UP` | New GATT/L2CAP connection | RouteUpdate (self-origin) | No | Yes |
| `METRIC_CHANGE` | `\|newRssi - oldRssi\| > threshold` (default 3 dB) | RouteUpdate | Yes (0-500ms) | No |
| `PERIODIC_FULL_SYNC` | Every `fullTableSyncInterval` (default 5 min) | RouteUpdate (all routes) | Yes | No |
| `ROUTE_EXPIRY` | Entry not refreshed before `routeEntryExpiry` (default 15 min) | RouteWithdrawal | No | Yes |
| `DIGEST_MISMATCH` | Received RouteDigest differs | RouteUpdate (full table) | No | Yes |

**Minimum interval enforcement**: No more than one update to same peer within `routeUpdateMinInterval` (default 1s).

### 8.8 Loop Detection

- `RouteEntry.source` tracks peer from whom route was learned
- Feasibility condition (RFC 8966 §3.7): route feasible only if `metric < best_known_metric` for that destination
- TTL by priority: HIGH=10min, NORMAL=5min, LOW=1min

**ADR**: docs/decisions/routing/routing-design.md

---

## 9. Transfer Layer

### 9.1 Chunked Transfer with SACK

- **Chunk size**: Selected by local `PowerMode` at session start, bounded by peer's advertised MTU
- **SACK bitfield**: Dynamic length = `ceil(totalChunks / 8)` bytes. Bit N = 1 means chunk N received
- **Cut-through relay**: Relays forward chunks before full reassembly (reduces latency)

### 9.2 Transfer Session Lifecycle

```text
IN_PROGRESS
    ├── all chunks received → COMPLETED
    ├── route lost → WAITING_FOR_ROUTE
    ├── chunk missing → RETRYING
    ├── error/cancel/trust failure → FAILED
    └── retry budget exhausted → TIMED_OUT

WAITING_FOR_ROUTE
    ├── route found → IN_PROGRESS
    └── grace period exhausted → TIMED_OUT

RETRYING
    ├── retransmission complete → IN_PROGRESS
    └── retry budget exhausted → FAILED
```

### 9.3 Retry Policy

| Parameter | Default | Per PowerMode |
|-----------|---------|---------------|
| Max retries | 5 | HIGH=10, MEDIUM=5, LOW=3 |
| Retry budget | 30s | HIGH=60s, MEDIUM=30s, LOW=15s |
| Backoff | Exponential + jitter | 1s, 2s, 4s... |

### 9.4 TransferDeliveryOutcome Mapping

| TransferState | FailureReason | Outcome |
|---------------|---------------|---------|
| COMPLETED | — | `success` |
| IN_PROGRESS | — | `in-progress` |
| RETRYING | — | `retrying` |
| WAITING_FOR_ROUTE | — | `route-waiting` |
| TIMED_OUT | — | `timeout` |
| FAILED | Unrecoverable | `unrecoverable-failure` |
| FAILED | TrustFailure | `trust-failure` |

### 9.5 Wire Frames

| Frame | Type | Encryption | Key Fields |
|-------|------|------------|------------|
| `TRANSFER_CHUNK` | 4 | Link-layer AEAD | sessionId, chunkIndex, offset, length, payload, isLast |
| `TRANSFER_ACKNOWLEDGMENT` | 5 | Link-layer AEAD | sessionId, bitfield (dynamic) |
| `TRANSFER_CANCEL` | 6 | Link-layer AEAD | sessionId, reason |

**SPEC-ANCHOR**: `transfer-session-model`, `scoreboard-model`

---

## 10. Power Management

### 10.1 Power Modes {#power-mode-settings}

| Parameter | HIGH | MEDIUM | LOW |
|-----------|------|--------|-----|
| Scan duty cycle | 20% | 10% | 5% |
| Advertisement interval | 100 ms | 500 ms | 1000 ms |
| Connection interval | 7.5 ms | 15 ms | 30 ms |
| Max concurrent connections | 8 | 4 | 2 |
| Chunk size | 512 B | 256 B | 128 B |
| Max retries | 10 | 5 | 3 |
| Retry budget | 60 s | 30 s | 15 s |
| Grace period (disconnect→GONE) | 15 s | 30 s | 45 s |

### 10.2 EU Regulatory Clamping

When `regulatoryRegion = RegulatoryRegion.EU`:

- Advertisement interval **clamped to ≥ 300 ms**
- Scan duty cycle **clamped to ≤ 70%**

Applied at runtime, observable in diagnostics.

### 10.3 Grace Periods

Per-mode grace period controls `PeerLifecycleState` transition `DISCONNECTED → GONE`. During grace period:

- Routes can degrade before full retraction
- Transfers can pause instead of being abandoned
- Host app sees `PeerEvent.StateChanged(..., DISCONNECTED)`

**ADR**: docs/decisions/power/power-mode-behavior.md

**SPEC-ANCHOR**: `power-mode-settings`

---

## 11. Diagnostics & Events

### 11.1 Peer Events (Public) {#diagnostic-event}

```kotlin
sealed interface PeerEvent {
    data class Found(val peerId: PeerIdentity, val state: PeerConnectionState) : PeerEvent
    data class StateChanged(val peerId: PeerIdentity, val state: PeerConnectionState) : PeerEvent
    data class Lost(val peerId: PeerIdentity) : PeerEvent
}
```

### 11.2 Peer Lifecycle (Internal)

```text
CONNECTED (active BLE link)
    └── BLE link lost → DISCONNECTED (grace period active)
            ├── BLE reconnects → CONNECTED
            └── Grace period expires → GONE (ephemeral cleanup, trust retained)
```

Grace period: HIGH=15s, MEDIUM=30s, LOW=45s.

### 11.3 Diagnostic Event Hierarchy

```kotlin
sealed interface DiagnosticEvent {
    data class NoiseSessionTransition(...) : DiagnosticEvent
    data class RouteUpdateReceived(...) : DiagnosticEvent
    data class RouteDigestMismatch(...) : DiagnosticEvent
    data class TransferProgress(...) : DiagnosticEvent
    data class TransferCompleted(...) : DiagnosticEvent
    data class TransferFailed(...) : DiagnosticEvent
    data class KeyRotationAnnounced(...) : DiagnosticEvent
    data class CryptoOperation(...) : DiagnosticEvent
    data class PeerDiscovered(...) : DiagnosticEvent
    data class PeerConnected(...) : DiagnosticEvent
    data class PeerDisconnected(...) : DiagnosticEvent
    data class ConfigChanged(...) : DiagnosticEvent
    // ... extensible
}
```

### 11.4 Severity Levels

`DEBUG`, `INFO`, `WARN`, `ERROR` — mapped to platform logging (Logcat, OSLog).

### 11.5 Callback Threading

All diagnostic callbacks dispatched on coroutine dispatcher (configurable via `DiagnosticsSettings`). Not on BLE callback threads.

**ADR**: docs/decisions/diagnostics/callback-threading.md

**SPEC-ANCHOR**: `diagnostic-event`

---

## 12. Build & Quality Constraints

### 12.1 Performance Budgets (CI-Enforced)

| Target | Budget | Measurement |
|--------|--------|-------------|
| Throughput (1-hop L2CAP) | ≥80 KB/s (Android Pixel 6+), ≥60 KB/s (iOS iPhone 12+) | `meshlink-benchmark` |
| Latency (1-hop, 256B, p95) | <50 ms after connection established | `meshlink-benchmark` |
| Memory (steady state, 8 peers) | ≤8 MB heap | `meshlink-benchmark` |
| Battery | ≤5% scan duty cycle, ≥500 ms connection interval | Derived from PowerMode |
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
| Discovery / Advertisement | Single-packet format, PeerFingerprint matching |
| Security Contract | Wycheproof vectors, fail-closed on all edge cases |
| Routing Control | Convergence under virtual harness, seqno correctness |
| Chunked Transfer | Dynamic bitfield SACK semantics, cut-through relay, retry bounds |
| Power Policy | Mode-to-parameter mapping, EU clamping observable |
| Public API | Identical Android/iOS surface, lifecycle events |

---

## 14. Settings Model

### 14.1 Settings DSL

```kotlin
// Entry point: ch.trancee.meshlink.meshLinkSettings
val settings = meshLinkSettings {
    appId = "com.example.myapp"
    powerMode = PowerMode.HIGH
    regulatoryRegion = RegulatoryRegion.EU
    
    keyRotation {
        interval = Duration.days(1)
        rotationGracePeriod = Duration.minutes(30)
        compromiseGracePeriod = Duration.ZERO
    }
    
    transfer {
        maxRetries = 3
        chunkSize = 512
        maxConcurrentSessionsPerPeer = 2
    }
    
    routing {
        routeUpdateMinInterval = Duration.seconds(1)
        routeUpdateMaxInterval = Duration.seconds(30)
        routeUpdateChangeThreshold = 3
        fullTableSyncInterval = Duration.minutes(5)
        routeEntryExpiry = Duration.minutes(15)
        feasibilityConditionEnabled = true
        maxRouteEntries = 256
    }
    
    security {
        fallbackMaxAttemptsPerMinute = 3
        fallbackTimeout = Duration.seconds(10)
        requireSignatureOnRouteUpdates = true
        defaultHandshakePattern = HandshakePattern.IX
    }
    
    diagnostics {
        eventBufferSize = 1000
    }
    
    emitToLog = true
    eventCallback = { event -> println(event) }
}
```

### 14.2 Settings Classes (Immutable)

```kotlin
data class MeshLinkSettings(
    val appId: String,
    val powerMode: PowerMode,
    val regulatoryRegion: RegulatoryRegion,
    val keyRotation: KeyRotationSettings,
    val transfer: TransferSettings,
    val routing: RoutingSettings,
    val security: SecuritySettings,
    val diagnostics: DiagnosticsSettings,
    val emitToLog: Boolean,
    val eventCallback: ((DiagnosticEvent) -> Unit)?
)

data class KeyRotationSettings(
    val interval: Duration = Duration.days(3),
    val rotationGracePeriod: Duration = Duration.hours(1),
    val compromiseGracePeriod: Duration = Duration.ZERO
)

data class TransferSettings(
    val maxRetries: Int = 5,
    val chunkSize: Int = 256,
    val maxConcurrentSessionsPerPeer: Int = 3,
    val scoreboardEncoding: ScoreboardEncoding = ScoreboardEncoding.DYNAMIC,
    val maxChunksPerSession: UInt = 1024u
)

data class RoutingSettings(
    val routeUpdateMinInterval: Duration = Duration.seconds(1),
    val routeUpdateMaxInterval: Duration = Duration.seconds(30),
    val routeUpdateChangeThreshold: Int = 3,
    val fullTableSyncInterval: Duration = Duration.minutes(5),
    val routeEntryExpiry: Duration = Duration.minutes(15),
    val feasibilityConditionEnabled: Boolean = true,
    val maxRouteEntries: Int = 256
)

data class SecuritySettings(
    val fallbackMaxAttemptsPerMinute: Int = 3,
    val fallbackTimeout: Duration = Duration.seconds(10),
    val requireSignatureOnRouteUpdates: Boolean = true,
    val defaultHandshakePattern: HandshakePattern = HandshakePattern.IX
)

data class DiagnosticsSettings(
    val eventBufferSize: Int = 1000
)
```

### 14.3 Implementation

- **Lambda DSL** (`meshLinkSettings { }`) is primary API — Kotlin idiom, readable, type-safe
- **Imperative builder** retained for programmatic construction (e.g., from settings file)
- Both paths produce identical `MeshLinkSettings` instances
- `MeshLinkSettings` is the **source of truth** for defaults — `specs/settings.yaml` is generated from it

**SPEC-ANCHOR**: `setting-model`

**ADR**: docs/decisions/model/settings-model.md (rationale only)

---

## 15. Future Work

### 15.1 PQ-Hybrid Key Establishment

**Candidate**: Conservative hybrid (C2) — classical + ML-KEM-768 staged extension

**ADR**: docs/decisions/crypto/pq-hybrid-candidate-matrix.md

### 15.2 Noise IK for E2E Layer

Replace IX with IK when both peers hold pinned keys for mutual 0-RTT E2E.

### 15.3 Throughput-Based Link Metrics

Replace RSSI proxy with measured throughput for routing decisions.

### 15.4 Payload Compression

Optional zlib/Brotli/Zstd for large payloads.

### 15.5 Group Messaging

MLS (RFC 9420) integration for multi-recipient E2E encryption.

---

## Machine-Readable Specs (Generated)

The following files in `specs/` are **generated from source code and this SPEC.md** — do not edit manually:

| File | Generated From | Purpose |
|------|----------------|---------|
| `enums.yaml` | `TypeModel.kt`, `PowerMode.kt` | All public enums with values/metadata |
| `data-models.yaml` | Model classes in `model/` | Data class schemas |
| `state-machines.yaml` | This SPEC.md (§5, §8, §9, §11) | State machine definitions |
| `wire-frames.yaml` | This SPEC.md (§4, §6, §8, §9) | Wire format definitions |
| `diagnostic-events.yaml` | `DiagnosticEvent.kt` | Diagnostic event catalog |
| `settings.yaml` | `MeshLinkSettings.kt` | Configuration DSL schemas |
| `cross-ref-index.yaml` | This SPEC.md + ADRs + code | SPEC ↔ ADR ↔ Code traceability |

**Generation script**: `scripts/generate-specs.sh` (run at build time)

---

## Traceability Index

| Spec Section | ADR(s) | Code Location |
|--------------|--------|---------------|
| §1 Vision | — | — |
| §2 Architecture | docs/explanation/module-structure.md | meshlink/build.gradle.kts |
| §3 Data Models | docs/decisions/model/data-model.md | meshlink/src/commonMain/kotlin/ch/trancee/meshlink/model/ |
| §4 Discovery | docs/decisions/discovery/mesh-hash-derivation.md | specs/wire-frames.yaml |
| §5 Trust/TOFU | docs/decisions/crypto/crypto-design.md | specs/enums.yaml, specs/state-machines.yaml |
| §6 Transport | docs/decisions/transport/mtu-negotiation.md | — |
| §7 Security | docs/decisions/crypto/crypto-design.md, constant-time-policy.md, replay-window.md, key-rotation-propagation.md, error-hierarchy.md | specs/enums.yaml, specs/state-machines.yaml |
| §8 Routing | docs/decisions/routing/routing-design.md | RouteEntry.kt, RoutingPolicy.kt, LinkMetric.kt |
| §9 Transfer | docs/decisions/model/data-model.md | TransferSession.kt, Scoreboard.kt, TransferFailureReason.kt |
| §10 Power | docs/decisions/power/power-mode-behavior.md | PowerMode.kt |
| §11 Diagnostics | docs/decisions/diagnostics/callback-threading.md | DiagnosticEvent.kt |
| §12 Build Quality | — | meshlink/build.gradle.kts |
| §13 Testing | — | — |
| §14 Settings | docs/decisions/model/settings-model.md | MeshLinkSettings.kt |
| §15 Future | docs/decisions/crypto/pq-hybrid-candidate-matrix.md | — |

---
