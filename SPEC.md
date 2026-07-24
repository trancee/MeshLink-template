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
| Delivery outcomes | Explicit: success, in-progress, retrying, unreachable, trust-failure, timeout, unrecoverable-failure (maps from `TransferStatus`: COMPLETED→success, IN_PROGRESS→in-progress, RETRYING→retrying, SUSPENDED→in-progress (waiting for route), TIMED_OUT→timeout, FAILED→unrecoverable-failure or trust-failure; `unreachable` is a routing-layer outcome, not a transfer status) |
| Wire compatibility | Backward-compatible evolution; breaking changes require major version bump + migration |
| Performance budgets | See Section 12 |
| Runtime dependency | Maximum one: `kotlinx-coroutines-core` for shipped artifact |
| Test coverage | 100% line/branch coverage for `:meshlink` artifact; crypto validated against Wycheproof vectors |

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
PeerId: 16-byte stable/random identifier (generated once at install, survives key rotations)
Ed25519PublicKey: 32-byte EdDSA signing key
X25519PublicKey: 32-byte DH key for Noise handshakes
PeerKey: 12-byte SHA-256(Ed25519Pub || X25519Pub) truncated, used in discovery. Ed25519 first (identity anchor), X25519 second (DH key). Both keys required. [Decision: docs/decisions/model/core-types.md]
```

**Design Note:** PeerId is stable/random, NOT derived from public key. This ensures identity persists across key rotations, enabling correct TrustStore lookups during key rotation announcements. [Decision: docs/decisions/model/core-types.md]

### PowerTier Enum

```text
enum class PowerTier { HIGH, MEDIUM, LOW, OFF }
```

- `HIGH` — Performance prioritized (20% scan, 100ms adv, 7.5ms conn, 8 concurrent, 512B chunks)
- `MEDIUM` — Balanced (default) (10% scan, 500ms adv, 15ms conn, 4 concurrent, 256B chunks)
- `LOW` — Battery conserved (5% scan, 1000ms adv, 30ms conn, 2 concurrent, 128B chunks)
- `OFF` — No background activity

[Decision: docs/decisions/power/power-tier-behavior.md]

### RegulatoryRegion Enum

```text
enum class RegulatoryRegion { GLOBAL, EU }
```

- `GLOBAL` — Rely on platform's normal behavior (default)
- `EU` — Apply EU clamping (adv interval floor 300ms, scan duty cycle ceiling 70%)

[Decision: docs/decisions/regulatory-compliance.md]

### 3.2 Trust Record Model

```text
TrustRecord {
  peerId: PeerId
  publicKey: CryptoKey
  seenAt: Instant
  verifiedAt: Instant
  status: TrustStatus (TRUSTED, REVOKED)
}
```

**TrustStatus enum:**

- `TRUSTED` — TOFU-pinned identity (first successful handshake)
- `REVOKED` — Explicitly revoked by user/application [Decision: docs/decisions/model/core-types.md]

### 3.3 Route Entry Model

```text
RouteEntry {
  destination: PeerId
  nextHop: PeerId?
  metric: UInt (composite via LinkMetric; see below)
  seqNo: UInt (destination-self-reported sequence number)
  publicKey: CryptoKey? (destination's public key, learned via route updates)
  expiresAt: Instant
  isFeasible: Boolean
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

### 3.4 Message Envelope Model

`MessageEnvelope` is the application-level message model. It carries the metadata
that describes a message (version, id, ttl, priority, destination). When a message
is sent through the mesh, the `MessageEnvelope` is serialized and placed inside a
`MeshEnvelope.payload` (see §3.5, §5.7). The `MeshEnvelope` is the wire-level
routing frame that relays use to forward the message — it carries `destination`,
`payload`, and `hopLimit`. The `hopLimit` is a routing concern set by the routing
layer, not by the application, so it is not a field of `MessageEnvelope`.

```text
MessageEnvelope {
  version: U8
  messageId: 64-bit random
  priority: enum { HIGH, NORMAL, LOW }
  destination: PeerId
  // ttl is derived from priority by the routing layer (see §8.4), not set by the application
}

TransferSession {
  sessionId: SessionId (32-bit random)
  destination: PeerId
  status: TransferStatus (IN_PROGRESS, SUSPENDED, RETRYING, COMPLETED, FAILED, TIMED_OUT)
  chunkSize: Int (selected by local power tier, bounded by peer MTU)
  totalChunks: UInt (ceil(totalBytes / chunkSize))
  scoreboard: Scoreboard (dynamic bitfield; bit N = 1 if chunk N received; see §3.4)
  totalBytes: Long
  bytesReceived: Long
  startedAt: Instant
  expiresAt: Instant? (max time transfer can remain SUSPENDED before failing; computed as startedAt + retryBudget per §10.4)
  retryCount: Int
}
```

**Scoreboard:** Dynamic bitfield encoding — bitfield length = `ceil(totalChunks / 8)` bytes, derived from `totalChunks` known via TransferSession. Bit N = 1 means chunk N is received (standard SACK convention). Backed by the `Scoreboard` helper class which provides type-safe `markReceived`, `markMissing`, `isReceived`, `missingChunks` methods. [Decision: docs/decisions/model/core-types.md]

**TransferStatus to Delivery Outcome mapping:**

| TransferStatus | Delivery Outcome |
|----------------|-----------------|
| COMPLETED | success |
| IN_PROGRESS | in-progress |
| RETRYING | retrying |
| SUSPENDED | in-progress (waiting for route) |
| TIMED_OUT | timeout |
| FAILED | unrecoverable-failure (or trust-failure if trust-related) |

Note: `unreachable` is a routing-layer outcome (no route to destination), not a `TransferStatus`.

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
| PeerKey | 12 bytes | SHA-256 truncated, discovery hint only |

### 4.2 Privacy Trade-offs

- **Stable PeerKey**: Passive observers can correlate repeated sightings more easily than rotating pseudonyms
- **Protected**: Full public keys not advertised, plaintext never in ads, hop/e2e session keys established after discovery
- **Isolation**: Mesh hash derived from `appId` prevents cross-application discovery

[Decision: docs/explanation/privacy-pseudonyms.md]

---

## 5. Trust Model (TOFU)

### 5.1 Handshake Pattern

- **Hop-by-hop link layer (first contact):** `Noise_XX_25519_ChaChaPoly_SHA256` - mutual authentication for initial TOFU
- **Hop-by-hop link layer (post-TOFU reconnect):** `Noise_IK_25519_ChaChaPoly_SHA256` - proactive mutual auth + 0-RTT when both peers hold pinned keys
- **End-to-end layer**: `Noise_IX_25519_ChaChaPoly_SHA256` - origin knows destination key, destination may not know origin

[Decision: docs/decisions/crypto/e2e-handshake-pattern.md]

### 5.2 Trust Flow

```text
Discovery → GATT connection → Noise_XX_25519_ChaChaPoly_SHA256 handshake → TOFU pin → TrustRecord stored
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
- PeerKey verification in payload (validates identity claim)
- 32-bit nonce in payload (replay protection)
- Diagnostic flag: `e2e_handshake.fallback_used = true` (observability)

**Protocol:** NX_Msg1 includes PeerKey + nonce. Destination verifies: `Hash(received_static) == PeerKey`. Mismatch or replay = reject.

[Decision: docs/decisions/crypto/nx-fallback-mitigation.md]

### 5.6 Key Rotation Protocol

Key rotation triggered by:

- Periodic timer (default: **3 days**)
- Manual API: `meshLink.rotateIdentity()`
- Security event (compromise detection)

**Wire Protocol:**

```text
KeyRotationAnnouncement {
  publicKey: CryptoKey (NEW public key)
  seqNo: UInt (always 1 - new identity)
  signature: ByteArray (Ed25519 signature with OLD private key)
  reason: KeyRotationReason (PERIODIC, MANUAL, SECURITY_EVENT)
}
```

**Neighbor Behavior:**

1. Verify signature with OLD known key
2. Accept new key into TrustStore
3. Seqno resets to 1 (new crypto era)
4. Old key retained for 1-hour grace period verification

[Decision: docs/decisions/crypto/key-rotation-protocol.md]

### 5.7 E2E Handshake Routing Over Mesh

When destination is not a direct neighbor or key is unknown:

```text
Phase 1: Link Setup (standard Noise_XX_25519_ChaChaPoly_SHA256)
Origin --(GATT/L2CAP)--> Relay(s)

Phase 2: E2E Handshake Routing
Origin wraps IX_Msg1 in a MeshEnvelope (the wire-level routing frame):
  MeshEnvelope {
    destination: destination.peerId,   // set from MessageEnvelope.destination
    payload: IX_Msg1_encrypted,          // MessageEnvelope serialized + E2E content
    hopLimit: UByte                       // set by routing layer, not application
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
- **E2E fallback:** `Noise_NX_25519_ChaChaPoly_SHA256` with PeerKey verification when destination key unknown

### 7.3 Fail-Closed Rules

- Malformed/untrusted input never surfaces private keys in logs
- Invalid X25519 public keys fail before HKDF derivation
- Decrypt/sign/verify failures stop operation immediately
- No fallback to plaintext or cached secrets

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

### 8.4 TTL by Priority

| Priority | TTL |
|----------|-----|
| HIGH | 45 minutes |
| NORMAL | 15 minutes |
| LOW | 5 minutes |

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
  sessionId: UInt32 (4 bytes)
  bitfield: UInt8Vector (ceil(totalChunks / 8) bytes; bit N = 1 means chunk N received; receiver knows totalChunks from session)
}
```

The bitfield length is derived from `totalChunks` in the `TransferSession`, so no extra length field is needed in the SACK message.

[Decision: docs/decisions/wire/wire-format-spec.md]

---

## 10. Power Management

### 10.1 Power Tiers

```text
enum class PowerTier {
  HIGH,     // Performance prioritized (20% scan, 100ms adv, 7.5ms conn, 8 concurrent, 512B chunks)
  MEDIUM,   // Balanced (10% scan, 500ms adv, 15ms conn, 4 concurrent, 256B chunks) - DEFAULT
  LOW,      // Battery conserved (5% scan, 1000ms adv, 30ms conn, 2 concurrent, 128B chunks)
  OFF       // No background activity
}
```

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

### 10.3 Adaptive Grace Period

Grace period adapts based on peer stability and power tier:

- **Base period:** HIGH=15s, MEDIUM=30s, LOW=45s, OFF=0s
- **Stability factor:** Increases for stable peers, decreases for frequent disconnectors
- **Uptime factor:** Longer average sessions get longer grace periods
- **Bonded minimum:** 10 seconds guarantee (never shorter)

[Decision: docs/decisions/power/power-tier-behavior.md]

### 10.4 Tier-Driven Parameters

| Tier | Scan Duty Cycle | Adv Interval | Conn Interval | Concurrent | Chunk Size | Max Retries | Retry Budget |
|------|-----------------|--------------|---------------|------------|------------|-------------|--------------|
| HIGH | 20% | 100ms | 7.5-15ms | 8 | 512B | 10 | 60s |
| MEDIUM | 10% | 500ms | 15-30ms | 4 | 256B | 5 | 30s |
| LOW | 5% | 1000ms | 30-60ms | 2 | 128B | 3 | 15s |
| OFF | 0% | Never | N/A | 0 | N/A | 0 | 0s |

---

## 11. Diagnostics & Events

### 11.1 Peer Events

```text
sealed interface PeerEvent {
  data class Found(val peerId: PeerId, val connectionState: PeerConnectionState)
  data class StateChanged(val peerId: PeerId, val state: PeerConnectionState)
  data class Lost(val peerId: PeerId)
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

`ConnectionState` is the internal runtime tracking type that drives the peer
lifecycle (CONNECTED → DISCONNECTED → GONE). It is not exposed publicly —
only `PeerConnectionState` (the enum above) is visible to the host app.

```text
ConnectionState {
  peerId: PeerId
  connectionState: PeerConnectionState
  graceSweeps: Int          // 0-3 for transition to GONE
  lastRssi: Int?            // For metric calculation
  supportsCoc: Boolean       // L2CAP CoC capability
  connectionInterval: Int    // ms
  lastHandshake: Instant?    // For timeout calculations
}
```

`MeshStateManager` uses `ConnectionState` to track grace periods and
coordinate cleanup across routing, transfer, and presence state. The host app
only sees `PeerEvent.Found`, `PeerEvent.StateChanged`, and `PeerEvent.Lost`.

### 11.3 Diagnostic Events (Machine Observable)

| Event Category | Fields |
|----------------|--------|
| `route.*` | `route.decrypt_failure_count`, `route.frame_type` |
| `transport.*` | `transport.fallback_no_psm_advertised`, `transport.fallback_coc_connect_failed`, `transport.fallback_coc_dropped_mid_transfer`, `transport.fallback_local_policy` |
| `transfer.*` | `transfer.data_plane_bearer`, `transfer.fallback_reason` |
| `power.*` | `power.tier`, `power.regulatory_region`, `power.scan_duty_cycle_observed`, `power.advertisement_interval_ms`, `power.connection_interval_ms`, `power.grace_period_seconds`, `power.peer_stability` |
| `e2e_handshake.*` | `e2e_handshake.protocol`, `e2e_handshake.fallback_used`, `e2e_handshake.peer_key_verified`, `e2e_handshake.rate_limit_attempts`, `e2e_handshake.nonce_replay_detected` |
| `key_rotation.*` | `key_rotation.old_key_verified`, `key_rotation.seqno_reset`, `key_rotation.propagation_deadline_met` |

### 11.4 Error Model

Errors use a sealed `MeshLinkException` hierarchy in `commonMain`, with platform exceptions wrapped and never leaking to consumers:

- Trust/Security errors (PeerNotFoundError, TrustError, KeyUnknownError)
- Routing errors (NoRouteError, RouteUpdateError)
- Transfer errors (TransferTimeoutError, TransferCancelledError, TransferCorruptedError)
- Transport errors (BluetoothStateError, ConnectionTimeoutError, CocNotSupportedError)

**ErrorCode enum:** PEER_NOT_FOUND, KEY_UNKNOWN, TRUST_VIOLATION, TRANSFER_TIMEOUT, BLUETOOTH_DISABLED, CONNECTION_FAILED, INVALID_PARAMETER, INTERNAL_ERROR

---

## 12. Build & Quality Constraints

### 12.1 Performance Budgets (CI-Enforced)

| Metric | Target | Measurement |
|--------|--------|-------------|
| Throughput (1-hop L2CAP) | ≥80 KB/s Android, ≥60 KB/s iOS | Benchmark |
| Latency (1-hop, 256B, p95) | <50 ms | Benchmark |
| Memory (steady state, 8 peers) | ≤8 MB heap | Benchmark |
| Battery scan duty cycle | ≤5% | Instrumentation |
| Cold start | <500 ms to first advertisement | Benchmark |
| Routing convergence (10 nodes) | ≤3 s | Virtual harness |
| Wire codec op | <1 μs/message | JMH |

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

- `NXFallbackPeerKeyVerifyTest`: Verify PeerKey mismatch causes rejection
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

### 13.8 Acceptance Criteria Per Layer

1. **Data Model / Trust**: Wire vectors, malformed input rejection
2. **Discovery / Advertisement**: Single-packet format, PeerKey matching
3. **Security Contract**: Wycheproof vectors, fail-closed on all edge cases
4. **Routing Control**: Convergence under virtual harness, seqno correctness
5. **Chunked Transfer**: Dynamic bitfield SACK semantics, cut-through relay, retry bounds
6. **Power Policy**: Tier-to-parameter mapping, EU clamping observable
7. **Public API**: Identical Android/iOS surface, lifecycle events

---

## Implementation Order (Spec-First)

Per PROJECT.md suggested approach, sliced into vertical epics:

1. **Core Data Types (e01)** - PeerId, PeerKey, CryptoKey, TrustRecord, RouteEntry, TransferSession
2. **Wire Format (e02)** - FlatBuffers schemas, encode/decode, compatibility testing
3. **Noise Handshake XX/IK (e03)** - Hop-by-hop link encryption (XX for first contact, IK for post-TOFU reconnect) with Android/iOS platform crypto
4. **E2E Handshake IX/NX (e04)** - End-to-end encryption with mesh routing and fallback
5. **Routing Coordinator (e05)** - Babel-style seqno management, metric-based path selection
6. **Transfer Session (e06)** - Dynamic bitfield SACK protocol, cut-through relay, retry bounds
7. **Peer Lifecycle (e07)** - Adaptive grace period (CONNECTED → DISCONNECTED → GONE)
8. **Key Rotation (e08)** - Signed announcements, seqno reset, 3-day default interval
9. **iOS Proof Harness (e09)** - Real-device validation for iOS platform glue (security critical)
10. **Power Tiers (e10)** - Four-tier model with quantified parameters

Each layer validated against RFC-grounded reference algorithms before platform glue. Epics ordered by WSJF: e09 (iOS proof) boosted for security gap mitigation.

---

## 14. Configuration Model

### 14.1 Configuration DSL

```kotlin
/**
 * MeshLink configuration DSL.
 * Single source of truth for all tunable parameters.
 */
data class MeshLinkConfig(
  val powerTier: PowerTier = PowerTier.MEDIUM,
  val regulatoryRegion: RegulatoryRegion = RegulatoryRegion.GLOBAL,
  val keyRotation: KeyRotationConfig = KeyRotationConfig(),
  val transfer: TransferConfig = TransferConfig(),
  val diagnostics: DiagnosticsConfig = DiagnosticsConfig()
)

data class KeyRotationConfig(
  val interval: Duration = Duration.days(3),
  val gracePeriod: Duration = Duration.hours(1)
)

data class TransferConfig(
  val maxRetries: Int = 5,
  val chunkSize: Int = 256 // Default; overridden by power tier
)

data class DiagnosticsConfig(
  val emitToLog: Boolean = true,
  val eventCallback: (DiagnosticEvent) -> Unit = {}
)

fun meshLinkConfig(block: MeshLinkConfigBuilder.() -> Unit): MeshLinkConfig {
  return MeshLinkConfigBuilder().apply(block).build()
}

class MeshLinkConfigBuilder {
  var powerTier: PowerTier = PowerTier.MEDIUM
  var regulatoryRegion: RegulatoryRegion = RegulatoryRegion.GLOBAL

  fun keyRotation(block: KeyRotationConfigBuilder.() -> Unit) {
    keyRotationConfig = KeyRotationConfigBuilder().apply(block).build()
  }

  fun transfer(block: TransferConfigBuilder.() -> Unit) {
    transferConfig = TransferConfigBuilder().apply(block).build()
  }

  fun diagnostics(block: DiagnosticsConfigBuilder.() -> Unit) {
    diagnosticsConfig = DiagnosticsConfigBuilder().apply(block).build()
  }

  private var keyRotationConfig = KeyRotationConfig()
  private var transferConfig = TransferConfig()
  private var diagnosticsConfig = DiagnosticsConfig()

  fun build(): MeshLinkConfig = MeshLinkConfig(
    powerTier = powerTier,
    regulatoryRegion = regulatoryRegion,
    keyRotation = keyRotationConfig,
    transfer = transferConfig,
    diagnostics = diagnosticsConfig
  )
}

class KeyRotationConfigBuilder {
  var interval: Duration = Duration.days(3)
  var gracePeriod: Duration = Duration.hours(1)
  fun build() = KeyRotationConfig(interval, gracePeriod)
}

class TransferConfigBuilder {
  var maxRetries: Int = 5
  var chunkSize: Int = 256
  fun build() = TransferConfig(maxRetries, chunkSize)
}

class DiagnosticsConfigBuilder {
  var emitToLog: Boolean = true
  var eventCallback: (DiagnosticEvent) -> Unit = {}
  fun build() = DiagnosticsConfig(emitToLog, eventCallback)
}
```

[Decision: docs/decisions/model/core-types.md]

### 14.2 Usage Example

```kotlin
val config = meshLinkConfig {
  powerTier = PowerTier.HIGH
  regulatoryRegion = RegulatoryRegion.EU
  keyRotation {
    interval = Duration.days(1)       // Override 3-day default
    gracePeriod = Duration.minutes(30)
  }
  transfer {
    maxRetries = 3
    chunkSize = 512
  }
  diagnostics {
    emitToLog = false
  }
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
