# MeshLink Technical Specification

This document captures the complete technical specification for implementing MeshLink, a library-first SDK enabling encrypted, serverless, fully offline peer-to-peer messaging between mobile devices over a short-range radio mesh network.

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

---

## 1. Vision & Product Pillars

### 1.1 Problem Statement

- Mobile devices need to communicate securely without internet, backend servers, or user accounts
- BLE mesh networking requires handling peer discovery, trust establishment, routing, and reliable transfer
- Both Android and iOS platforms must offer identical public API behavior

### 1.2 Product Pillars

1. **Zero-infrastructure trust** - Trust On First Use (TOFU) model; first mutually-authenticated handshake pins peer identity keys; subsequent mismatches require explicit reset/revocation
2. **Two-layer encryption** - Hop-by-hop link encryption (relays can forward without reading) layered under end-to-end encryption (origin/destination only)
3. **Proactive multi-hop routing** - Distance-vector-style routing control plane maintaining live route tables; host app never selects intermediate hops manually
4. **Reliable large-payload transfer** - Chunked transfer with selective acknowledgment (SACK), retransmission, and reassembly over small-frame BLE radio
5. **Power-aware operation** - Discrete power tiers governing scan duty cycle, advertisement interval, connection interval, concurrent connections, and transfer chunk size
6. **Deterministic cross-platform parity** - Identical lifecycle states, sealed error hierarchies, and diagnostic codes across Android and iOS

### 1.3 Non-Functional Requirements

| Requirement | Constraint |
|-------------|------------|
| Offline operation | Zero connectivity required once permissions granted |
| Persisted state | Only trust pin (identity material + first/verified instants); no plaintext or full identifiers cached |
| Pending state | In-memory only; does not survive process restart |
| Delivery outcomes | Explicit: success, in-progress, retrying, route-waiting, unreachable, trust-failure, timeout, unrecoverable-failure (maps from `TransferState`: COMPLETED→success, IN_PROGRESS→in-progress, RETRYING→retrying, WAITING_FOR_ROUTE→route-waiting, TIMED_OUT→timeout, FAILED→unrecoverable-failure or trust-failure; `unreachable` is a routing-layer outcome, not a transfer state) |
| Wire compatibility | Backward-compatible evolution; breaking changes require major version bump + migration |
| Performance budgets | See Section 12 |
| Runtime dependency | Maximum one Maven artifact at runtime: `kotlinx-coroutines-core`. Crypto primitives are either platform APIs (Android Security Framework, iOS Security framework) or pure-Kotlin fallbacks — this is an implementation distinction, not a runtime dependency. |
| Test coverage | 100% line/branch coverage for `:meshlink`; `commonMain` + `androidHostTest` + `iosMain`; crypto validated against Wycheproof vectors |

---

## 2. Architecture Overview

### 2.1 Module Structure

```text
meshlink/          # Shipped library (JVM + Android + iOS)
meshlink-reference/ # Reference app consuming public API only
meshlink-proof/    # Real-device validation (android/ + ios/ subdirectories)
meshlink-benchmark/ # Performance benchmarking
```

`meshlink-proof/` contains `android/` and `ios/` subdirectories for platform-specific real-device validation. Both test the same proof scenarios on their respective platforms. [Decision: docs/explanation/module-structure.md]

### 2.2 Source Set Structure

- `commonMain` - Shared business logic (security, routing, transfer, diagnostics)
- `androidMain` - Platform-specific BLE glue, fallback crypto for older Android
- `iosMain` - Platform-specific BLE glue
- `commonTest` - Pure JVM tests (protocol logic, wire codecs, crypto)
- `androidHostTest` - Host-side Android tests (crypto fallback paths)
- `androidDeviceTest` - Instrumented device tests (reserved for future use; `:meshlink` currently has no Android-specific code requiring device tests)

### 2.3 Wire Protocol Reference Standards

- RFC 7748 (X25519/X448 ECDH)
- RFC 8032 (Ed25519 signatures)
- RFC 8439 (ChaCha20-Poly1305 AEAD)
- RFC 5869 (HKDF)
- RFC 2104 (HMAC)
- RFC 6234 (SHA-2 family)
- RFC 9147 (DTLS 1.3 for replay protection patterns)
- RFC 8966 (Babel routing for feasibility conditions and seqno)
- RFC 9420 (MLS — design reference for group security)
- RFC 7435 (Opportunistic security — design reference for best-effort encryption)

---

## 3. Core Data Models

### 3.1 Peer Identity Model

```text
PeerIdentity: 16-byte stable/random identifier (generated once at install, survives key rotations)
Ed25519PublicKey: 32-byte EdDSA signing key
X25519PublicKey: 32-byte DH key for Noise handshakes
PeerFingerprint: 12-byte SHA-256(Ed25519Pub || X25519Pub) truncated, used in discovery. Ed25519 first (identity anchor), X25519 second (DH key). Both keys required. **NOTE: 12 bytes (96 bits) provides birthday bound 2^48; collision probability negligible for any practical mesh size. This is a DISCOVERY HINT ONLY — never used for authentication.** [Decision: docs/decisions/model/core-types.md]
```

**Design Note:** PeerIdentity is stable/random, NOT derived from public key. This ensures identity persists across key rotations, enabling correct TrustStore lookups during key rotation announcements. [Decision: docs/decisions/model/core-types.md]

**Sequence Number Wrapper (Mandatory):**

```kotlin
/**
 * Unsigned 32-bit sequence number with safe wrap-around comparison.
 * RFC 8966 §3.7 requires signed interpretation for seqno comparison.
 */
@JvmInline
@Serializable
value class SeqNo(private val value: UInt) {
  companion object {
    val ZERO: SeqNo = SeqNo(0u)
  }

  val raw: UInt = value

  /**
   * Returns true if this seqno is newer than [other], handling 32-bit wrap-around.
   * Uses signed comparison: (this - other) > 0 interprets as signed 32-bit.
   */
  fun isNewerThan(other: SeqNo): Boolean = (value - other.value).toInt() > 0

  /**
   * Returns true if this seqno is older than [other].
   * RFC 8966 §3.7 comparison symmetry.
   */
  fun isOlderThan(other: SeqNo): Boolean = other.isNewerThan(this)

  /**
   * Signed difference for modular arithmetic comparison.
   * (this - other) interpreted as signed 32-bit integer.
   */
  operator fun minus(other: SeqNo): Int = (value - other.value).toInt()

  /**
   * Increments this seqno by 1, wrapping at 2^32.
   * Used on cold start of mesh participation (MeshLink.start()).
   */
  fun increment(): SeqNo = SeqNo(value + 1u)
}
```

[Decision: RFC 8966 wrap-around comparison requirement]

### PowerTier Enum

```text
enum class PowerTier { HIGH, MEDIUM, LOW }
```

- `HIGH` — Performance prioritized (20% scan, 100ms adv, 7.5ms conn, 8 concurrent, 512B chunks)
- `MEDIUM` — Balanced (default) (10% scan, 500ms adv, 15ms conn, 4 concurrent, 256B chunks)
- `LOW` — Battery conserved (5% scan, 1000ms adv, 30ms conn, 2 concurrent, 128B chunks)

[Decision: docs/decisions/power/power-tier-behavior.md]

```text
enum class Priority { HIGH, NORMAL, LOW }

```

### RegulatoryRegion Enum

```text
enum class RegulatoryRegion { DEFAULT, EU }
```

- `DEFAULT` — Rely on platform's normal behavior (default)
- `EU` — Apply EU clamping (adv interval floor 300ms, scan duty cycle ceiling 70%)

[Decision: docs/decisions/regulatory-compliance.md]

### 3.2 Trust Record Model

```text
TrustRecord {
  peerIdentity: PeerIdentity
  publicKey: CryptoKey
  seenAt: Instant
  verifiedAt: Instant
  state: TrustState (INITIATED, TRUSTED, REVOKED)
}
```

**TrustState enum:**

- `INITIATED` — Handshake in progress, not yet verified
- `TRUSTED` — TOFU-pinned identity (first successful handshake)
- `REVOKED` — Explicitly revoked by user/application [Decision: docs/decisions/model/core-types.md]

### 3.3 Route Entry Model

```text
RouteEntry {
  destination: PeerIdentity
  nextHop: PeerIdentity?
  source: PeerIdentity (peer from whom this route was learned; used for loop detection per RFC 8966)
  metric: UInt (composite via LinkMetric; see below)
  seqNo: SeqNo (destination-self-reported sequence number, wrapped for safe comparison)
  publicKey: CryptoKey? (destination's public key, learned via route updates)
  expiresAt: Instant
  // isFeasible is computed dynamically via the Babel feasibility condition (RFC 8966 §3.5.1),
  // not stored. The route is feasible if its metric is strictly better than the
  // feasible distance of any existing route for the same destination.
}
```

**LinkMetric** encapsulates the metric bit layout:

```text
LinkMetric {
  rssiNormalized: UInt (0-255)      // Low byte
  supportsCoc: Boolean               // Bit 8
  fastInterval: Boolean              // Bit 9
  highPowerTier: Boolean             // Bit 10
  composite: UInt                   // Serialized form: (flags shl 8) or rssiNormalized
}
```

**Metric structure:** Low byte = RSSI normalized (0-255), high bits = flags (supportsCoC, fastInterval, highPowerTier), enabling path selection preferring better links. [Decision: docs/decisions/routing/link-quality-metric.md]

### 3.4 Message Header Model

`RoutingMessage` is the application-level metadata. It carries the metadata
that describes a message (version, id, priority, destination). When a message
is sent through the mesh, the `RoutingMessage` is serialized and placed inside a
`RoutingFrame.payload` (see §3.5, §5.7). The `RoutingFrame` is the wire-level
routing frame that relays use to forward the message — it carries `destination`,
`payload`, and `hopLimit`. The `hopLimit` is a routing concern set by the routing
layer, not by the application, so it is not a field of `RoutingMessage`.

```text
RoutingMessage {
  version: U8
  messageId: 64-bit random
  priority: Priority
  destination: PeerIdentity
  // ttl is derived from priority by the routing layer (see §8.5) and applied to the TTL field in RoutingFrame (see §3.5)

TransferSession {
  sessionId: SessionId    // 64-bit random token; identifies a transfer session uniquely
  destination: PeerIdentity
  priority: Priority  // Transfer priority for QoS-like behavior
  state: TransferState (IN_PROGRESS, WAITING_FOR_ROUTE, RETRYING, COMPLETED, FAILED, TIMED_OUT)
  failureReason: TransferFailureReason? (reason for terminal FAILED state; null otherwise)
  chunkSize: Int (selected by local power tier, bounded by peer MTU; see §10.4 for power-tier-based values)
  totalChunks: UInt (ceil(totalBytes / chunkSize))
  scoreboard: Scoreboard (dynamic bitfield; bit N = 1 if chunk N received; see §3.4)
  totalBytes: Long
  bytesReceived: Long
  startedAt: Instant
  expiresAt: Instant? (max time transfer can remain WAITING_FOR_ROUTE before failing; computed as `startedAt + retryBudget`; see §10.4 tier table for per-tier values)
  retryCount: Int
}

### TransferFailureReason

```text
sealed interface TransferFailureReason {
  data class Unrecoverable(val message: String) : TransferFailureReason
  data class TrustFailure(val peerIdentity: PeerIdentity) : TransferFailureReason
}
```

**Scoreboard:** Dynamic bitfield encoding — bitfield length = `ceil(totalChunks / 8)` bytes, derived from `totalChunks` known via TransferSession. Bit N = 1 means chunk N is received (standard SACK convention). Backed by the `Scoreboard` helper class which provides type-safe methods:

- `markReceived(chunkIndex)` / `markMissing(chunkIndex)` — return new immutable `Scoreboard` instances
- `isReceived(chunkIndex)` / `isMissing(chunkIndex)` — bit inspection
- `missingChunks()` — list of unreceived chunk indices
- `receivedCount()` / `missingCount()` — counts for progress tracking
- `toByteArray()` — raw bitfield for wire serialization

[Decision: docs/decisions/model/core-types.md]

**Transfer Priority:** Transfers inherit priority from the RoutingMessage that created them (see §3.4), enabling QoS-like behavior where higher priority transfers can preempt lower priority ones when resources are constrained.

**TransferState to Delivery Outcome mapping:**

| TransferState | Delivery Outcome |
|---------------|-----------------|
| COMPLETED | success |
| IN_PROGRESS | in-progress |
| RETRYING | retrying |
| WAITING_FOR_ROUTE | route-waiting |
| TIMED_OUT | timeout |
| FAILED | unrecoverable-failure or trust-failure (see §11.4's `TransferFailureReason` type; trust-failure when `failureReason` is `TrustFailure`) [note 1] |

### 3.4.1 Transfer Session State Transitions

| Current State | Event | Next State |
|---|---|---|
| — | Session created | IN_PROGRESS |
| IN_PROGRESS | All chunks received + scoreboard complete | COMPLETED |
| IN_PROGRESS | Error, cancel, or trust failure | FAILED |
| IN_PROGRESS | Route lost, waiting for route recovery | WAITING_FOR_ROUTE |
| WAITING_FOR_ROUTE | Route found, resume transfer | IN_PROGRESS |
| WAITING_FOR_ROUTE | Retry budget or grace period exhausted | TIMED_OUT |
| IN_PROGRESS | Chunk missing, schedule retransmit | RETRYING |
| RETRYING | Retransmission complete, back in progress | IN_PROGRESS |
| RETRYING | Retry budget exhausted | FAILED |
| Any terminal | Session cleaned up | — |

[note 1] FAILED transitions carry a `TransferFailureReason` in the `failureReason` field:

- **`Unrecoverable`**: Generic error, cancel, retry budget exhausted, or non-trust transfer failure.
- **`TrustFailure`**: Trust-related failure (e.g. identity mismatch, revoked peer). The delivery outcome maps to `trust-failure` only in this case.

Note: `unreachable` is a routing-layer outcome (no route to destination), not a `TransferState`.

### 3.5 Wire Frame Types

| Type | Meaning | Encryption |
|------|---------|------------|
| MESH_ENVELOPE | Routed E2E handshake or payload | Link-layer AEAD per hop |
| ROUTE_UPDATE | Route announcement with metric + seqno + destination public key | Always AEAD-encrypted |
| ROUTE_WITHDRAWAL | Route retraction | Always AEAD-encrypted |
| ROUTE_DIGEST | FNV-1a hash of route table (32-bit) | Plaintext (digest only) |
| TRANSFER_CHUNK | Payload chunk with offset + length | Link-layer AEAD per hop |
| TRANSFER_ACK | Dynamic bitfield SACK | Link-layer AEAD per hop |
| TRANSFER_CANCEL | Session termination | Link-layer AEAD per hop |
| KEY_ROTATION_ANNOUNCEMENT | Signed key rotation announcement | Plaintext (signature verifiable) |

ROUTE_UPDATE and ROUTE_WITHDRAWAL are always AEAD-encrypted using the Noise session key — no plaintext routing metadata is ever transmitted. [Decision: docs/decisions/routing/routing-metadata-privacy.md, docs/decisions/wire/wire-format-spec.md]

**Note:** `Hello` and `Ihu` frame types were considered and removed — BLE connection state (GATT/L2CAP connect/disconnect) provides liveness, making periodic Hello/IHU frames redundant. See [Destination-sourced route freshness, IHU cost signal removal, and digest-triggered resync](docs/decisions/routing/destination-sourced-seqno-ihu-removal-digest-resync-design.md) for rationale.

---

<a id="4-discovery--identity"></a>

## 4. Discovery & Identity

### 4.1 Advertisement Format

Single BLE advertisement packet containing:

| Field | Size | Description |
|-------|------|-------------|
| Fixed UUID | 4 bytes | `4d455348` ("MESH") |
| Protocol version | 3 bits | |
| Platform | 2 bits | |
| Power tier | 3 bits | Current power mode |
| Mesh hash | 16 bits | Application isolation filter |
| L2CAP PSM hint | 8 bits | Non-zero if CoC supported |
| PeerFingerprint | 12 bytes | SHA-256 truncated, discovery hint only |

### 4.2 Privacy Trade-offs

- **Stable PeerFingerprint**: Passive observers can correlate repeated sightings more easily than rotating pseudonyms
- **Protected**: Full public keys not advertised, plaintext never in ads, hop/e2e session keys established after discovery
- **Isolation**: Mesh hash derived from `appId` prevents cross-application discovery

[Decision: docs/explanation/privacy-pseudonyms.md]

---

<a id="5-trust-model-tofu"></a>

## 5. Trust Model (TOFU)

### 5.1 Handshake Pattern

- **Hop-by-hop link layer (first contact):** `Noise_XX_25519_ChaChaPoly_SHA256` - mutual authentication for initial TOFU
- **Hop-by-hop link layer (post-TOFU reconnect):** `Noise_IK_25519_ChaChaPoly_SHA256` - proactive mutual auth + 0-RTT when both peers hold pinned keys
- **End-to-end layer**: `Noise_IX_25519_ChaChaPoly_SHA256` - origin knows destination key, destination may not know origin

[Decision: docs/decisions/crypto/e2e-handshake-pattern.md]

### 5.2 Trust Flow

```text
Discovery → GATT connection → Noise_XX_25519_ChaChaPoly_SHA256 handshake → INITIATED → TOFU pin → TRUSTED → TrustRecord stored
```

### 5.3 Identity Distribution via Route Updates

- Each peer's public key is included in `ROUTE_UPDATE` frames as part of the encrypted payload
- When a peer connects directly (Noise XX), it learns the neighbor's public key and includes it in route updates about that neighbor
- Route updates propagate hop-by-hop: each relay re-advertises the destination's public key in its own route updates
- This enables E2E IX handshake where the origin knows the destination's static key before connecting
- NX fallback (`Noise_NX`) is used only when the destination's public key is not yet in the routing table (cold start, partition recovery)
- `KEY_ROTATION_ANNOUNCEMENT` updates the public key when a peer rotates its keys

### 5.4 Revocation

- Explicit API action required to reset trust
- No silent re-trust on identity mismatch
- Stored trust records persist until revoked

### 5.5 NX Fallback for Unknown Keys

When destination's public key is unknown, `Noise_NX_25519_ChaChaPoly_SHA256` provides a degraded but functional fallback:

**Security Mitigations:**

- Rate limiting: max 3 NX attempts/minute per destination (prevents DoS)
- Timeout: 10s vs 30s for IX (limits resource window)
- Full public key verification in payload (validates identity claim)
- 32-bit nonce in payload (replay protection)
- Diagnostic flag: `handshake.fallback_used = true` (observability)

**Protocol:** NX_Msg1 includes the full 64-byte concatenated public key (Ed25519 || X25519) + nonce. Destination verifies: `received_ed25519 == expected_ed25519 AND received_x25519 == expected_x25519`. Mismatch or replay = reject.

[Decision: docs/decisions/crypto/nx-fallback-mitigation.md]

### 5.6 Key Rotation Protocol

Key rotation triggered by:

- Periodic timer (default: **3 days**)
- Manual API: `meshLink.rotateIdentity()`
- Security event (compromise detection)

**Wire Protocol:**

```flatbuffers
KeyRotationAnnouncement {
  identityKey: CryptoKey (NEW public key)
  seqNo: UInt (always 1 - new identity)
  signature: ByteArray (Ed25519 signature with OLD private key)
  reason: KeyRotationReason (PERIODIC, MANUAL, SECURITY_EVENT)
}
```

**Neighbor Behavior:**

1. Verify signature with OLD known key
2. Accept new key into TrustStore
3. Seqno resets to 1 (new crypto era)
4. Old key retained for grace period verification

**Grace Period:**

- `PERIODIC` or `MANUAL` rotation: `rotationGracePeriod` (default 1 hour) — both old and new keys accepted for in-flight sessions
- `SECURITY_EVENT` rotation: `compromiseGracePeriod` (default `ZERO`) — old key rejected immediately

**Key Rotation During Active Transfer:**

- Existing Noise sessions (link-layer and E2E) continue using current traffic keys — rotation does not terminate active sessions
- New sessions (new connections, new E2E handshakes) use the rotated keys
- Old identity key retained for the grace period to decrypt any late-arriving handshake messages
- Transfer layer is identity-key agnostic; it only depends on Noise session keys which remain valid

[Decision: docs/decisions/crypto/key-rotation-protocol.md]

### 5.7 E2E Handshake Routing Over Mesh

When destination is not a direct neighbor or key is unknown:

```text
Phase 1: Link Setup (standard Noise_XX_25519_ChaChaPoly_SHA256)
Origin --(GATT/L2CAP)--> Relay(s)

Phase 2: E2E Handshake Routing
Origin wraps IX_Msg1 in a RoutingFrame (the wire-level routing frame):
  RoutingFrame {
    destination: destination.peerIdentity,   // set from RoutingMessage.destination
    payload: IX_Msg1_encrypted,               // RoutingMessage serialized + E2E content
    hopLimit: UByte                         // set by routing layer, not application
  }

Relay(s) decrypt hop layer → re-encrypt → forward without inspecting E2E payload

Phase 3: Destination responds with IX_Msg2 wrapped for return path

Phase 4: Origin now has E2E traffic keys
```

**Security:** Relays cannot read E2E content; only link-layer encryption at each hop.

[Decision: docs/decisions/crypto/e2e-routing-over-mesh.md]

---

## 6. Transport Layer

### 6.1 Bearer Selection

| Traffic Type | Preferred Bearer | Fallback |
|--------------|------------------|----------|
| Control plane | GATT (unconditionally) | None - GATT is always available |
| Data plane | L2CAP CoC | GATT with same correctness guarantees |

**Important:** Control plane (handshake, routing, transfer control) MUST work over GATT alone for reliability.

[Decision: docs/decisions/transport/gatt-l2cap-transport-selection.md]

### 6.2 Negotiation Sequence

1. GATT connection establishes
2. `Noise_XX_25519_ChaChaPoly_SHA256` handshake completes (control plane must work over GATT alone)
3. If both peers advertised PSM hint, attempt L2CAP CoC channel
4. On CoC success, promote data-plane traffic to CoC
5. On CoC failure, continue on GATT

### 6.3 Fallback Reasons (Machine Observable)

- `transport.fallback_no_psm_advertised`
- `transport.fallback_coc_connect_failed`
- `transport.fallback_coc_dropped_mid_transfer`
- `transport.fallback_local_policy`

---

## 7. Security Layer

### 7.1 Crypto Primitives (Required)

All validated against Wycheproof test vectors:

| Primitive | Standard | Test Vector Source | Coverage |
|-----------|----------|-------------------|----------|
| X25519 | RFC 7748 | Wycheproof | 518 vectors (264 valid + 254 acceptable) |
| Ed25519 | RFC 8032 | Wycheproof | 150 vectors (88 valid + 62 invalid) |
| ChaCha20-Poly1305 | RFC 8439 | Wycheproof | 325 vectors (256 valid + 69 invalid) |
| HKDF-SHA256 | RFC 5869 | Wycheproof | 86 vectors (83 valid + 3 invalid) |
| HMAC-SHA256 | RFC 2104 | Wycheproof | 174 vectors (66 valid + 108 invalid) |
| SHA-256 | RFC 6234 | RFC-style regression corpus | Covered via other primitives' Wycheproof vectors |

[Decision: docs/decisions/crypto/vector-policy.md]

### 7.2 Handshake Patterns

- **Link layer (first contact):** `Noise_XX_25519_ChaChaPoly_SHA256` - mutual authentication for initial TOFU
- **Link layer (post-TOFU reconnect):** `Noise_IK_25519_ChaChaPoly_SHA256` - proactive mutual auth + 0-RTT when both peers hold pinned keys (1 round trip vs XX's 1.5)
- **E2E layer:** `Noise_IX_25519_ChaChaPoly_SHA256` - origin knows destination key
- **E2E fallback:** `Noise_NX_25519_ChaChaPoly_SHA256` with full public key verification when destination key unknown

### 7.3 Fail-Closed Rules

- Malformed/untrusted input never surfaces private keys in logs
- Invalid X25519 public keys fail before HKDF derivation
- Decrypt/sign/verify failures stop operation immediately
- No fallback to plaintext or cached secrets
- All cryptographic field operations and comparisons MUST implement constant-time algorithms to prevent timing side-channel attacks

### 7.4 Android Crypto Constraints

- API 26-32 runtime checks for X25519/XDH and ChaCha20-Poly1305
- Pure-Kotlin fallback implementations for older devices
- Ed25519 fallback with constant-time arithmetic (optimized for performance)

---

## 8. Routing Layer

### 8.1 Protocol Basis

Babel-style distance-vector (RFC 8966) adapted for BLE mesh:

- **Feasibility condition**: Loop avoidance by requiring candidate routes to look strictly better
- **SeqNo freshness**: Destination self-reports sequence number, prevents stale route propagation
- **Differential updates**: Only route changes advertised, not full table dumps

[Decision: docs/decisions/routing/destination-sourced-seqno-ihu-removal-digest-resync-design.md]

### 8.2 Sequence Number Semantics

- **Destination-owned**: Each node owns one seqno counter, incremented only on cold start
- **Self-origin announcements**: After connection, each node sends RouteUpdate about itself
- **No Hello/IHU frames**: BLE transport already provides liveness signals

[Decision: docs/decisions/routing/destination-sourced-seqno-ihu-removal-digest-resync-design.md]

### 8.3 Route Digest & Resync

- 32-bit FNV-1a hash of route table included in advertisements
- On mismatch, receiver pushes full table (no request/response round-trip)
- Simple correct behavior, bandwidth optimization deferred

### 8.4 Route Table Capacity

- Route tables are bounded by `maxRouteEntries` (default: 256)
- When the table exceeds this limit, least-recently-updated entries are evicted
- This prevents unbounded memory growth and ensures predictable convergence behavior

**Rationale:** 256 entries balance mesh size expectations (~10-20 peers common in typical deployment) with memory bounds. Evicting least-recently-updated entries ensures stale routes are removed first while active routes persist. The eviction happens atomically during route table updates.

### 8.4.1 Loop Detection

MeshLink uses two complementary mechanisms to prevent routing loops, following RFC 8966:

#### 1. Source-peer tracing (primary defense)

Each `RouteEntry` records the `source` peer — the immediate neighbor from whom the route was received. When evaluating a route update for a destination, the receiving peer checks whether the `source` is the same as itself. If it is, the update is silently discarded — it is a loop back to the origin.

This is the Babel-style "split horizon with poisoned reverse" principle: a node never advertises a route back to the peer from which it learned that route.

#### 2. Feasibility condition (loop avoidance)

The Babel feasibility condition (`route.metric < feasibleDistance(destination)`) is the second defense. Even if a route update passes the source check, it is only accepted if its metric is strictly better than any feasible route already in the table for the same destination. This prevents a two-node ping-pong where each node keeps accepting the other's slightly-better metric.

#### 3. SeqNo freshness (stale-route prevention)

Each `RouteEntry` carries a destination-self-reported `seqNo`. A route update whose `seqNo` is not newer than the currently accepted route for the same destination is rejected. This is handled by `SeqNo.isNewerThan()` using signed 32-bit comparison per RFC 8966 §3.7.

Together, these three mechanisms — source tracing, feasibility filtering, and seqno freshness — provide robust loop prevention for the Babel-style distance-vector routing plane.

### 8.5 TTL by Priority

| Priority | TTL |
|----------|-----|
| HIGH | 10 minutes |
| NORMAL | 5 minutes |
| LOW | 1 minute |

### 8.6 Route Update Trigger Conditions

Route updates are triggered by the following events. All non-immediate updates include random jitter (0–500 ms) to avoid synchronization storms.

| Trigger | Condition | Frame Type | Jitter |
|---------|-----------|------------|--------|
| **Direct link up** | New GATT/L2CAP connection established | `RouteUpdate` (self-origin) | None (immediate) |
| **Metric change** | `abs(newRssi - oldRssi) > routeUpdateChangeThreshold` (default 3 dB) | `RouteUpdate` | 0–500 ms |
| **Periodic full sync** | Every `fullTableSyncInterval` (default 5 min) | `RouteUpdate` (all routes) | 0–500 ms |
| **Route expiry** | Route entry not refreshed before `routeEntryExpiry` (default 15 min) | `RouteWithdrawal` | None (immediate) |
| **Digest mismatch** | Received `RouteDigest` differs from local table hash | `RouteUpdate` (all routes) | None (immediate) |

**Minimum interval enforcement**: No more than one update to the same peer within `routeUpdateMinInterval` (default 1 s), regardless of triggers.

**Maximum interval enforcement**: If no triggers fire, a keep-alive `RouteUpdate` is sent at `routeUpdateMaxInterval` (default 30 s) to refresh route freshness.

[Decision: docs/decisions/routing/destination-sourced-seqno-ihu-removal-digest-resync-design.md]

---

## 9. Transfer Layer

### 9.1 Transfer Session

The `TransferSession` model is defined in §3.4. Key fields:

- `chunkSize`: Selected by local power tier, bounded by peer MTU
- `scoreboard`: Dynamic bitfield (`ByteArray`) of length `ceil(totalChunks / 8)` bytes; bit N = 1 means chunk N received
- `totalBytes`/`bytesReceived`: Progress tracking in bytes (not chunks)

[Decision: docs/decisions/model/core-types.md]

### 9.2 Selective Acknowledgment

- **Dynamic bitfield encoding**: Bitfield length = `ceil(totalChunks / 8)` bytes, derived from `totalChunks` known via TransferSession. Bit N = 1 means chunk N is received (standard SACK convention).
- **Variable overhead**: Small transfers (10 chunks) use 1 byte; large transfers (1000 chunks) use 125 bytes
- Partial ACK never forces re-send of already-received chunks
- Scoreboard clears on session completion or explicit failure

### 9.3 Cut-Through Relay

- Pipeline forwarding without full reassembly
- Relays decrypt (hop layer) → re-encrypt (next hop) → forward
- Relay buffers maintained for local retransmission handling

### 9.4 TransferAck Wire Format

```text
TransferAck {
  sessionId: UInt64 (8 bytes)
  bitfield: UInt8Vector (ceil(totalChunks / 8) bytes; bit N = 1 means chunk N received; receiver knows totalChunks from session)
}
```

The bitfield length is derived from `totalChunks` in the `TransferSession`, so no extra length field is needed in the SACK message.

If the `TransferSession` is not found (expired or already completed), the receiver MUST reject the `TransferAck` with `TransferError.SessionNotFound`.

[Decision: docs/decisions/wire/wire-format-spec.md]

---

## 10. Power Management

### 10.1 Power Tiers

enum class PowerTier {
  HIGH,     // Performance prioritized (20% scan, 100ms adv, 7.5ms conn, 8 concurrent, 512B chunks)
  MEDIUM,   // Balanced (10% scan, 500ms adv, 15ms conn, 4 concurrent, 256B chunks) - DEFAULT
  LOW       // Battery conserved (5% scan, 1000ms adv, 30ms conn, 2 concurrent, 128B chunks)
}

### 10.2 Regulatory Region

```text
enum class RegulatoryRegion {
  DEFAULT,  // Rely on platform's normal behavior
  EU        // Apply EU clamping (adv interval floor 300ms, scan duty cycle ceiling 70%)
}
```

When region = EU:

- Advertisement interval floor: 300ms (below spec values clamped)
- Scan duty cycle ceiling: 70%

[Decision: docs/decisions/power/power-tier-behavior.md, docs/explanation/regulatory-compliance.md]

### 10.3 Grace Period

Fixed grace period per power tier:

| Tier | Grace Period |
|------|-------------|
| HIGH | 15 seconds |
| MEDIUM (default) | 30 seconds |
| LOW | 45 seconds |

After the grace period expires without reconnection, the peer transitions to GONE and ephemeral state (presence, routes, pending transfers) is cleaned up. Pinned trust state persists.

**Future work:** An adaptive grace period that adjusts based on peer stability (disconnect history) and session uptime is tracked in a separate design note and can be introduced as a future enhancement.

[Decision: docs/decisions/power/power-tier-behavior.md]

### 10.4 Tier-Driven Parameters

| Tier | Scan Duty Cycle | Adv Interval | Conn Interval | Concurrent | Chunk Size | Max Retries | Retry Budget |
|------|-----------------|--------------|---------------|------------|------------|-------------|--------------|
| HIGH | 20% | 100ms | 7.5-15ms | 8 | 512B | 10 | 60s |
| MEDIUM | 10% | 500ms | 15-30ms | 4 | 256B | 5 | 30s |
| LOW | 5% | 1000ms | 30-60ms | 2 | 128B | 3 | 15s |

*Parameter rationale:*

- **Scan duty cycle**: Based on BLE power consumption studies showing linear relationship with current draw
- **Advertisement interval**: Shorter intervals improve discovery latency but increase power consumption
- **Connection interval**: BLE connection intervals are quantized in 1.25ms units; 7.5ms (=6 units) is the minimum valid interval and the Android BLE stack floor. 15ms (=12 units) is the iOS sweet spot for throughput/power balance. The code stores these as `Double` milliseconds to preserve the exact BLE-valid values without rounding artifacts.
- **Concurrent connections**: Limited by controller resources and connection management overhead
- **Chunk sizes**: Sized to fit within BLE MTU (23-251 bytes) after accounting for L2CAP/GATT headers (4 bytes), security overhead (nonce+tag=16 bytes for ChaCha20-Poly1305), and protocol framing, while minimizing packetization overhead
- **Max retries & retry budget**: Tuned to balance reliability against resource exhaustion and battery drain

*Note: Connection intervals are shown as min-max ranges supported by the controller stack. Values that are multiples of 1.25ms are guaranteed to be valid across BLE controllers; non-multiples (e.g. 7ms) may be rejected or silently rounded by the stack.*

---

## 11. Diagnostics & Events

### 11.1 Peer Events

```text
sealed interface PeerEvent {
  data class Found(val peerIdentity: PeerIdentity, val connectionState: PeerConnectionState)
  data class StateChanged(val peerIdentity: PeerIdentity, val state: PeerConnectionState)
  data class Lost(val peerIdentity: PeerIdentity)
}
```

### 11.2 Connection States

```text
enum class PeerConnectionState {
  CONNECTED,
  DISCONNECTED
  // GONE is internal only, triggers PeerEvent.Lost
}
```

### 11.2.1 Internal Connection State Tracking

`PeerLifecycleState` is the internal runtime tracking type that drives the peer
lifecycle (CONNECTED → DISCONNECTED → GONE). It is not exposed publicly —
only `PeerConnectionState` (the enum above) is visible to the host app.

```text
PeerLifecycleState {
  peerIdentity: PeerIdentity
  connectionState: PeerConnectionState
  expiresAt: Instant?        // Non-null while grace window is active; null when GONE
  rssi: Int?                 // For metric calculation
  supportsCoc: Boolean       // L2CAP CoC capability
  connectionInterval: Int    // ms
  handshakeAt: Instant?      // For timeout calculations
}
```

`PeerStateCoordinator` uses `PeerLifecycleState` to track grace periods and
coordinate cleanup across routing, transfer, and presence state. The host app
only sees `PeerEvent.Found`, `PeerEvent.StateChanged`, and `PeerEvent.Lost`.

### 11.3 Diagnostic Events (Machine Observable)

All diagnostic events are defined in `meshlink/src/commonMain/kotlin/ch/trancee/meshlink/diagnostics/DiagnosticEvent.kt` as a sealed interface hierarchy. The table below summarizes the event types with their fields.

| Event Category | Fields | Code Type |
|----------------|--------|-----------|
| `route.*` | `peerIdentity`, `frameType`, `failureReason` | `RouteDecryptFailureEvent` |
| `transport.*` | `peerIdentity`, `reason` | `TransportFallbackEvent` |
| `transfer.*` | `sessionId`, `bearer` | `TransferDataPlaneBearerEvent` |
| `power.*` | `requestedTier`, `effectiveTier`, `regulatoryRegion`, `scanDutyCyclePercent`, `advertisementIntervalMs`, `connectionIntervalMs` | `PowerTierEffectiveEvent` |
| `handshake.*` | `sessionId`, `pattern`, `fallbackUsed`, `fullPublicKeyVerified`, `rateLimitAttempts`, `nonceReplayDetected` | `HandshakeEvent` |
| `key_rotation.*` | `peerIdentity`, `oldKeyVerified`, `sequenceNumberReset`, `propagationDeadlineMet`, `reason` | `KeyRotationEvent` |
| `route.*` | `peerIdentity`, `localDigest`, `remoteDigest` | `RouteDigestMismatchEvent` |
| `transfer.*` | `sessionId`, `peerIdentity`, `fromState`, `toState`, `bytesTransferred`, `totalBytes` | `TransferSessionTransitionEvent` |
| `transfer.*` | `sessionId`, `peerIdentity`, `reason` | `TransferFailureEvent` |
| `noise.*` | `peerIdentity`, `layer`, `fromState`, `toState`, `role`, `handshakePattern`, `failureReason` | `NoiseSessionTransitionEvent` |

**Diagnostic Field Descriptions:**

- `transfer.priority`: Reflects the Priority (HIGH/NORMAL/LOW) of the transfer, inherited from the originating RoutingMessage. Enables QoS monitoring and resource allocation decisions. This field is surfaced on `TransferSessionTransitionEvent` via the transfer session's `priority` field.
- `handshake.fallbackUsed`: `true` when the NX fallback handshake pattern is used instead of IX; set when the destination's public key is unknown.
- `handshake.fullPublicKeyVerified`: `true` when the NX fallback verified the full 64-byte concatenated public key (Ed25519 || X25519) byte-for-byte in Msg1.
- `key_rotation.sequenceNumberReset`: `true` when the neighbor accepted the new key and reset its seqno to 1.
- `key_rotation.propagationDeadlineMet`: `true` when the key rotation announcement reached all direct neighbors within the deadline.

### 11.4 Error Model

Errors use a sealed `MeshLinkException` hierarchy in `commonMain`, with platform exceptions wrapped and never leaking to consumers:

- Trust/Security errors (PeerNotFoundError, TrustError, KeyUnknownError)
- Routing errors (NoRouteError, RouteUpdateError)
- Transfer errors (TransferTimeoutError, TransferCancelledError, TransferCorruptedError)
- Transport errors (BluetoothStateError, ConnectionTimeoutError, CocNotSupportedError)

**ErrorCode enum:** `PEER_NOT_FOUND`, `KEY_UNKNOWN`, `TRUST_VIOLATION`, `TRANSFER_TIMEOUT`, `BLUETOOTH_DISABLED`, `CONNECTION_FAILED`, `INVALID_PARAMETER`, `INTERNAL_ERROR`

**TransferFailureReason:**

```text
sealed interface TransferFailureReason {
  data class Unrecoverable(val message: String) : TransferFailureReason
  data class TrustFailure(val peerIdentity: PeerIdentity) : TransferFailureReason
}
```

This type is carried by `TransferSession.failureReason` and distinguishes the two terminal failure modes that map to the `FAILED` delivery outcome:

- `Unrecoverable` → delivery outcome `unrecoverable-failure`
- `TrustFailure` → delivery outcome `trust-failure`

---

## 12. Build & Quality Constraints

### 12.1 Performance Budgets (CI-Enforced)

| Metric | Target | Measurement | Rationale |
|--------|--------|-------------|-----------|
| Throughput (1-hop L2CAP) | ≥80 KB/s Android, ≥60 KB/s iOS | Benchmark | Matches practical file transfer requirements while respecting BLE limitations |
| Latency (1-hop, 256B, p95) | <50 ms | Benchmark | Ensures responsive interactive applications (messaging, gaming) |
| Memory (steady state, 8 peers) | ≤8 MB heap | Benchmark | Targets <0.5% of typical 2GB RAM device, minimizing impact on host apps |
| Battery scan duty cycle | ≤5% | Instrumentation | Targets <5% additional drain beyond baseline for all-day operation |
| Cold start | <500 ms to first advertisement | Benchmark | Ensures responsive user experience when enabling mesh |
| Routing convergence (10 nodes) | ≤3 s | Virtual harness | Balances rapid topology adaptation with control plane overhead |
| Wire codec op | <1 μs/message | JMH | Ensures minimal CPU impact for high-throughput scenarios |

### 12.2 Code Quality Rules

- Detekt: Zero suppressions
- ktfmt: Auto-format before every commit
- BCV: Track public API, explicit versioning for breaking changes
- ExplicitApi(): All public declarations need explicit visibility/return types
- No TODO comments in merged code

### 12.3 Platform Minimums

- Android: API 26 (runtime crypto capability checks for 26-32)
- iOS: 14.0
- iOS: Native targets only on macOS host (cross-compilation limitation)
- CHANGELOG.md is auto-generated from Conventional Commits at release time, not hand-maintained

---

## 13. Testing & Verification

### 13.1 Test Suite Structure

| Layer | Location | Coverage |
|-------|----------|----------|
| Unit/JVM | `commonTest` | Full coverage |
| Host/Android | `androidHostTest` | Crypto fallback validation |
| Device/Android | `meshlink-proof/android/` | Real BLE behavior |
| Device/iOS | `meshlink-proof/ios/` | Real BLE behavior, platform crypto |
| Reference app | `meshlink-reference` | Public API consumption only |

### 13.2 iOS Proof Testing (Security-Critical)

iOS proof harness is planned under `meshlink-proof/ios/` for real-device validation (simulator cannot validate BLE). Requires physical device testing for:

- `IosCryptoProviderTest`: Verify Security framework + Secure Enclave key usage (iOS 14+)
- `CoreBluetoothThroughputTest`: Verify 15-20ms floor per BLE references
- `IosBackgroundTransferTest`: Verify background mode handling during transfers

### 13.3 Link-Layer Handshake Testing (XX + IK)

- `NoiseXXHandshakeTest`: Verify XX establishes bidirectional link keys with mutual TOFU pinning
- `NoiseIKReconnectTest`: Verify IK reconnect succeeds when both peers hold pinned keys
- `NoiseIKFallbackTest`: Verify IK is used after TOFU, XX is used for first contact
- `NoiseIK0RTTTest`: Verify 0-RTT data can be sent after IK message 1
- `NoiseIKFailClosedTest`: Verify IK fails closed on key mismatch or malformed input

### 13.4 NX Fallback Testing

- `NXFallbackPublicKeyVerifyTest`: Verify full public key mismatch causes rejection
- `NXFallbackRateLimitTest`: Verify 3rd attempt succeeds, 4th fails
- `NXFallbackTimeoutTest`: Verify 10s timeout expires correctly
- `NXFallbackReplayTest`: Verify nonce replay is rejected

### 13.5 Key Rotation Testing

- `KeyRotationAnnounceTest`: Verify signature verification and key adoption
- `KeyRotationSeqnoResetTest`: Verify seqno resets to 1, not preserved
- `KeyRotationPropagationTest`: Verify gossip reaches mesh within deadlines
- `KeyRotationRollbackTest`: Verify old key still accepted for active sessions
- `WireCompatTest`: Verify KeyRotationAnnouncement round-trips correctly

### 13.6 Virtual Mesh Harness

Multi-node scenarios exercised without physical hardware:

- Reconnect churn scenarios
- Digest-mismatch resolution
- Routing convergence tests
- Cross-platform compatibility verification

### 13.7 Wire Compatibility Testing

- Hex test vectors in `commonTest/resources/wire-compat/`
- Forward-compatibility checks
- Malformed-input validation
- **Cross-platform CI job** (`.github/workflows/wire-compat.yml`):
  - Builds `:meshlink` for `androidArm64`, `iosArm64` (no simulator)
  - Runs shared test suite `WireCompatibilityTestSuite` on each target
  - Encodes all frame types using each platform's implementation
  - Decodes all vectors using each platform's implementation
  - **Asserts byte-for-byte equality** of encoded output across all targets
  - Fails if any platform produces different bytes for the same logical frame
  - Runs on macOS runner (required for iOS targets)
  - Scheduled on every PR and nightly

### 13.8 Acceptance Criteria Per Layer

1. **Data Model / Trust**: Wire vectors, malformed input rejection
2. **Discovery / Advertisement**: Single-packet format, PeerFingerprint matching
3. **Security Contract**: Wycheproof vectors, fail-closed on all edge cases
4. **Routing Control**: Convergence under virtual harness, seqno correctness
5. **Chunked Transfer**: Dynamic bitfield SACK semantics, cut-through relay, retry bounds
6. **Power Policy**: Tier-to-parameter mapping, EU clamping observable
7. **Public API**: Identical Android/iOS surface, lifecycle events

See PROJECT.md for implementation order and epic breakdown.

---

## 14. Configuration Model

### 14.1 Configuration DSL

```kotlin
/**
 * MeshLink configuration DSL.
 * Single source of truth for all tunable parameters.
 */
data class MeshLinkSettings(
  val powerTier: PowerTier = PowerTier.MEDIUM,
  val regulatoryRegion: RegulatoryRegion = RegulatoryRegion.DEFAULT,
  val keyRotation: KeyRotationSettings = KeyRotationSettings(),
  val transfer: TransferSettings = TransferSettings(),
  val routing: RoutingSettings = RoutingSettings(),
  val security: SecuritySettings = SecuritySettings(),
  val diagnostics: DiagnosticsSettings = DiagnosticsSettings()
)

data class KeyRotationSettings(
  /**
   * How often to automatically rotate identity keys.
   * Default: 3 days.
   */
  val interval: Duration = Duration.days(3),
  /**
   * Grace period for the OLD key after a PLANNED rotation (periodic or manual).
   * During this window, both old and new keys are accepted for in-flight sessions.
   * Default: 1 hour.
   */
  val rotationGracePeriod: Duration = Duration.hours(1),
  /**
   * Grace period for the OLD key after a SECURITY-EVENT rotation (suspected compromise).
   * Set to ZERO for immediate revocation — old key is rejected instantly.
   * Non-zero values allow a brief overlap for safety but weaken the security response.
   * Default: ZERO.
   */
  val compromiseGracePeriod: Duration = Duration.ZERO
)

data class TransferSettings(
  val maxRetries: Int = 5,
  val chunkSize: Int = 256, // Default; overridden by power tier
  val maxConcurrentSessionsPerPeer: Int = 3,
  val scoreboardEncoding: ScoreboardEncoding = ScoreboardEncoding.DYNAMIC,
  // ScoreboardEncoding.FIXED requires maxChunksPerSession to pre-allocate bitfield
  val maxChunksPerSession: UInt = 1024u // Used when scoreboardEncoding = FIXED
)

/**
 * Scoreboard encoding strategy for selective acknowledgment.
 * DYNAMIC adjusts bitfield size to transfer size; FIXED pre-allocates for predictability.
 */
enum class ScoreboardEncoding {
  DYNAMIC,  // Dynamic bitfield size based on totalChunks - saves memory for small transfers
  FIXED     // Fixed pre-allocated bitfield - predictable memory, maxChunksPerSession required
}

data class RoutingSettings(
  /**
   * Minimum interval between route updates to the same peer.
   * Prevents update storms during link flapping.
   * Default: 1 second.
   */
  val routeUpdateMinInterval: Duration = Duration.seconds(1),
  /**
   * Maximum interval between route updates to the same peer when no changes occur.
   * Acts as a keep-alive for route freshness.
   * Default: 30 seconds.
   */
  val routeUpdateMaxInterval: Duration = Duration.seconds(30),
  /**
   * Minimum RSSI change (in decibels) required to trigger a route update.
   * Smaller values = more responsive routing but more control traffic.
   * Larger values = quieter control plane but slower reaction to link quality changes.
   * Default: 3 dB (roughly "noticeable but not noise").
   */
  val routeUpdateChangeThreshold: Int = 3,
  /**
   * Interval for sending full route table to all peers (periodic full sync).
   * Ensures eventual consistency even if differential updates are lost.
   * Default: 5 minutes.
   */
  val fullTableSyncInterval: Duration = Duration.minutes(5),
  /**
   * Time after which a route entry expires if not refreshed.
   * Must be > fullTableSyncInterval to avoid premature expiry.
   * Default: 15 minutes.
   */
  val routeEntryExpiry: Duration = Duration.minutes(15),
  /**
   * Whether to enforce the Babel feasibility condition (loop avoidance).
   * Should always be true in production; false only for testing.
   * Default: true.
   */
  val feasibilityConditionEnabled: Boolean = true,
  /**
   * Maximum number of route entries to maintain in the routing table.
   * When exceeded, least-recently-updated entries are evicted.
   * Default: 256 (suitable for typical personal mesh networks of 10-20 peers).
   */
  val maxRouteEntries: Int = 256
)

data class SecuritySettings(
  /**
   * Maximum fallback handshake attempts allowed per minute per destination.
   * Exceeding this limit causes new attempts to be rejected until the window resets.
   * Default: 3 (matches the spec mitigation for DoS via unauthenticated handshakes).
   */
  val fallbackMaxAttemptsPerMinute: Int = 3,
  /**
   * Timeout for fallback handshake (stricter than IX).
   * Fallback has no responder authentication, so a tighter bound limits exposure.
   * Default: 10 seconds.
   */
  val fallbackTimeout: Duration = Duration.seconds(10),
  /**
   * Whether ROUTE_UPDATE frames must carry an end-to-end signature from the
   * originating peer (covering the destination's public key).
   * Prevents a malicious relay from substituting the destination's key (MITM on handshake).
   * Should always be true in production; false only for testing.
   * Default: true.
   */
  val requireSignatureOnRouteUpdates: Boolean = true,
  /**
   * Default handshake pattern when destination key is known.
   * IX = one-way authenticated (origin knows dest key). NX = fallback (key unknown).
   * Default: IX.
   */
  val defaultHandshakePattern: HandshakePattern = HandshakePattern.IX
)

data class DiagnosticsSettings(
  val emitToLog: Boolean = true,
  val eventBufferSize: Int = 1000
)

/**
 * MeshLink configuration DSL.
 * Usage:
 * ```kotlin
 * val settings = meshLinkSettings {
 *   powerTier = PowerTier.HIGH
 *   keyRotation { interval = Duration.days(1) }
 * }
 * ```
 */
fun meshLinkSettings(block: MeshLinkSettings.() -> Unit): MeshLinkSettings {
  return MeshLinkSettings().apply(block)
}
```

[Decision: docs/decisions/model/core-types.md]

### 14.2 Usage Example

```kotlin
val config = meshLinkSettings {
  powerTier = PowerTier.HIGH
  regulatoryRegion = RegulatoryRegion.EU
  keyRotation = KeyRotationSettings(
    interval = Duration.days(1),
    rotationGracePeriod = Duration.minutes(30),
    compromiseGracePeriod = Duration.ZERO,
  )
  transfer = TransferSettings(
    maxRetries = 3,
    chunkSize = 512,
    maxConcurrentSessionsPerPeer = 2,
  )
  routing = RoutingSettings(
    routeUpdateMinInterval = Duration.seconds(1),
    routeUpdateChangeThreshold = 5,
    feasibilityConditionEnabled = true,
  )
  security = SecuritySettings(
    fallbackMaxAttemptsPerMinute = 5,
    requireSignatureOnRouteUpdates = true,
  )
  diagnostics = DiagnosticsSettings(
    emitToLog = false,
    eventBufferSize = 2000,
  )
}
```

---

## 15. Future Work

### 15.1 PQ-Hybrid Key Establishment

Post-quantum hybrid key establishment (X25519 + ML-KEM) is being evaluated per
`docs/decisions/crypto/pq-hybrid-candidate-matrix.md`. The recommended shortlist
candidate is C2 (conservative + staged extension frames), with measured overhead
of +46ms latency and +184 bytes payload. PQ hybrid is **not** part of the current
implementation scope — it is tracked as a future enhancement.

### 15.2 Noise IK for E2E Layer

Note: `Noise_IK_25519_ChaChaPoly_SHA256` is **already implemented** for the
**link layer** (post-TOFU reconnect between direct neighbors). This future-work
item is about extending IK to the **E2E layer** — when both origin and destination
already hold each other's static keys, IK could provide proactive authentication +
0-RTT for end-to-end sessions. This is a separate optimization from the link-layer
IK and is not part of the current implementation scope.

### 15.3 Throughput-Based Link Metrics

Current routing uses RSSI-based metrics as a proxy for link quality. A future
enhancement could add throughput-based metrics (actual bytes/sec) measured after
connection establishment, combined with RSSI for initial path selection.

### 15.4 Payload Compression

Optional payload compression (zlib/deflate/gzip, Brotli, Zstandard) is a reasonable
future add-on for large transfers, not part of the current spec.
