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
| Delivery outcomes | Explicit: success, in-progress, retrying, unreachable, trust-failure, timeout, unrecoverable-failure |
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
meshlink-proof/    # Real-device validation (Android + iOS)
meshlink-benchmark/ # Performance benchmarking
```

**Critical Addition:** `meshlink-proof/ios` validates iOS platform crypto/behavior on real devices (simulators cannot substitute for BLE validation per CONSTITUTION.md §II).

### 2.2 Source Set Structure

- `commonMain` - Shared business logic (security, routing, transfer, diagnostics)
- `androidMain` - Platform-specific BLE glue, fallback crypto for older Android
- `iosMain` - Platform-specific BLE glue
- `commonTest` - Pure JVM tests (protocol logic, wire codecs, crypto)
- `androidHostTest` - Host-side Android tests (crypto fallback paths)
- `jvmTest` - JVM integration tests

### 2.3 Wire Protocol Reference Standards

- RFC 7748 (X25519/X448 ECDH)
- RFC 8032 (Ed25519 signatures)
- RFC 8439 (ChaCha20-Poly1305 AEAD)
- RFC 5869 (HKDF)
- RFC 2104 (HMAC)
- RFC 6234 (SHA-2 family)
- RFC 9147 (DTLS 1.3 for replay protection patterns)
- RFC 8966 (Babel routing for feasibility conditions and seqno)

---

## 3. Core Data Models

### 3.1 Peer Identity Model

```text
PeerId: 16-byte stable/random identifier (generated once at install, survives key rotations)
Ed25519PublicKey: 32-bit EdDSA signing key
X25519PublicKey: 32-bit DH key for Noise handshakes
PeerKey: 12-byte SHA-256 public key hash (truncated), used in discovery and NX fallback verification
```

**Design Note:** PeerId is stable/random, NOT derived from public key. This ensures identity persists across key rotations, enabling correct TrustStore lookups during key rotation announcements.

### 3.2 Trust Record Model

```text
TrustRecord {
  peerId: PeerId
  publicKey: CryptoKey
  seenAt: Instant
  verifiedAt: Instant
  status: TrustStatus (PINNED, VERIFIED, REVOKED)
}
```

**TrustStatus enum:**

- `PINNED` — TOFU-pinned identity (first successful handshake)
- `VERIFIED` — Confirmed in current session
- `REVOKED` — Explicitly revoked by user/application

### 3.3 Route Entry Model

```text
RouteEntry {
  destination: PeerId
  nextHop: PeerId?
  metric: UInt (composite: RSSI normalized 0-255 + flags for CoC/interval/power)
  seqNo: UInt (destination-self-reported sequence number)
  expiry: Instant
  isFeasible: Boolean
}
```

**Metric structure:** Low byte = RSSI normalized (0-255), high bits = flags (supportsCoC, fastInterval, highPowerTier), enabling path selection preferring better links.

### 3.4 Message Envelope Model

```text
MessageEnvelope {
  version: U8
  messageId: 128-bit random
  ttl: Duration (priority-based)
  priority: enum { HIGH, NORMAL, LOW }
  destination: PeerId
  hopLimit: UByte (0 = direct only, 1+ = max hops)
}

TransferSession {
  sessionId: SessionId (128-bit random)
  destination: PeerId
  status: TransferStatus (IN_PROGRESS, PAUSED, COMPLETE, FAILED, TIMEOUT)
  chunkSize: U16 (MTU-dependent, power tier overridden)
  scoreboard: Bitfield<UInt> (each bit = 1 if missing, 32 bytes per 256 chunks)
  totalChunks: UInt32
  startedAt: Instant
  deadline: Instant? (per power tier retry budget)
  retryCount: U32
}

ChunkRef {
  chunkIndex: U32
  chunkSize: U16
  totalChunks: U32
}

Chunk {
  messageId: 128-bit
  index: U32
  total: U32
  payload: ByteArray
  padding: ByteArray (to fill BLE MTU)
}
```

**Note:** Scoreboard uses bitfield encoding (not ChunkRange pairs) for efficient SACK.

### 3.5 Wire Frame Types (Current Design)

| Type | Meaning |
|------|---------|
| MESH_ENVELOPE | Routed E2E handshake or payload |
| ROUTE_UPDATE | Route announcement with metric + seqno |
| ROUTE_DIGEST | FNV-1a hash of route table (32-bit) |
| TRANSFER_CHUNK | Payload chunk with offset + length |
| TRANSFER_ACK | Bitfield selective ACK (32 bytes per 256 chunks) |
| TRANSFER_CANCEL | Session termination |
| KEY_ROTATION_ANNOUNCEMENT | Signed key rotation announcement |

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
| Key hash | 12 bytes | SHA-256 truncated, discovery hint only |

### 4.2 Privacy Trade-offs

- **Stable keyHash**: Passive observers can correlate repeated sightings more easily than rotating pseudonyms
- **Protected**: Full public keys not advertised, plaintext never in ads, hop/e2e session keys established after discovery
- **Isolation**: Mesh hash derived from `appId` prevents cross-application discovery

---

## 5. Trust Model (TOFU)

### 5.1 Handshake Pattern

- **Hop-by-hop link layer**: Noise XX (`Noise_XX_25619_ChaChaPoly_SHA256`) - mutual authentication for first contact
- **End-to-end layer**: Noise IX (`Noise_IX_25519_ChaChaPoly_SHA256`) - origin knows destination key, destination may not know origin

### 5.2 Trust Flow

```text
Discovery → GATT connection → Noise XX handshake → TOFU pin → TrustRecord stored
```

### 5.3 Identity Gossip

- Signed identity records distributed through mesh
- Enables E2E handshake where origin knows destination static key before connection
- Wire format: signed Ed25519/X25519 public key announcements

### 5.4 Revocation

- Explicit API action required to reset trust
- No silent re-trust on identity mismatch
- Stored trust records persist until revoked

### 5.5 NX Fallback for Unknown Keys

When destination's public key is unknown, Noise NX provides a degraded but functional fallback:

**Security Mitigations:**

- Rate limiting: max 3 NX attempts/minute per destination (prevents DoS)
- Timeout: 10s vs 30s for IX (limits resource window)
- PeerKey verification in payload (validates identity claim)
- 32-bit nonce in payload (replay protection)
- Diagnostic flag: `e2e_handshake_used_fallback = true` (observability)

**Protocol:** NX_Msg1 includes PeerKey + nonce. Destination verifies: `Hash(received_static) == PeerKey`. Mismatch or replay = reject.

### 5.6 Key Rotation Protocol

Key rotation triggered by:

- Periodic timer (default: **3 days** - reduced from 90 for security)
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

### 5.7 E2E Handshake Routing Over Mesh

When destination is not a direct neighbor or key is unknown:

```text
Phase 1: Link Setup (standard Noise XX)
Origin --(GATT/L2CAP)--> Relay(s)

Phase 2: E2E Handshake Routing
Origin wraps IX_Msg1 in MeshEnvelope:
  MeshEnvelope {
    destination: destination.peerId,
    payload: IX_Msg1_encrypted,
    hopLimit: UByte
  }

Relay(s) decrypt hop layer → re-encrypt → forward without inspecting E2E payload

Phase 3: Destination responds with IX_Msg2 wrapped for return path

Phase 4: Origin now has E2E traffic keys
```

**Security:** Relays cannot read E2E content; only link-layer encryption at each hop.

---

## 6. Transport Layer

### 6.1 Bearer Selection

| Traffic Type | Preferred Bearer | Fallback |
|--------------|------------------|----------|
| Control plane | GATT (unconditionally) | None - GATT is always available |
| Data plane | L2CAP CoC | GATT with same correctness guarantees |

**Important:** Control plane (handshake, routing, transfer control) MUST work over GATT alone for reliability.

### 6.2 Negotiation Sequence

1. GATT connection establishes
2. Noise XX handshake completes (control plane must work over GATT alone)
3. If both peers advertised PSM hint, attempt L2CAP CoC channel
4. On CoC success, promote data-plane traffic to CoC
5. On CoC failure, continue on GATT

### 6.3 Fallback Reasons (Machine Observable)

- `fallback_no_psm_advertised`
- `fallback_coc_connect_failed`
- `fallback_coc_dropped_mid_transfer`
- `fallback_local_policy`

---

## 7. Security Layer

### 7.1 Crypto Primitives (Required)

All validated against Wycheproof test vectors:

| Primitive | Standard | Minimum Coverage |
|-----------|----------|------------------|
| X25519 | RFC 7748 | 518 vectors |
| Ed25519 | RFC 8032 | 150 vectors |
| ChaCha20-Poly1305 | RFC 8439 | Comprehensive |
| HKDF-SHA256 | RFC 5869 | Comprehensive |
| HMAC-SHA256 | RFC 2104 | Comprehensive |
| SHA-256 | RFC 6234 | Comprehensive |

### 7.2 Handshake Patterns

- **Link layer (hop-by-hop):** Noise XX (`Noise_XX_25519_ChaChaPoly_SHA256`) - mutual authentication
- **E2E layer:** Noise IX (`Noise_IX_25519_ChaChaPoly_SHA256`) - origin knows destination key
- **E2E fallback:** Noise NX with PeerKey verification when destination key unknown
- **Future optimization:** Noise IK for post-TOFU reconnect (deferred)

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

### 8.2 Sequence Number Semantics

- **Destination-owned**: Each node owns one seqno counter, incremented only on cold start
- **Self-origin announcements**: After connection, each node sends RouteUpdate about itself
- **No Hello/IHU frames**: BLE transport already provides liveness signals

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

### 9.1 Chunked Transfer Protocol

```text
TransferSession {
  sessionId: SessionId (16-byte random token)
  destination: PeerId
  status: TransferStatus (IN_PROGRESS, PAUSED, COMPLETE, FAILED, TIMEOUT)
  chunkSize: UInt16 (MTU-dependent, power tier overridden)
  scoreboard: Bitfield<UInt> (each bit = 1 if missing, 32 bytes per 256 chunks)
  totalChunks: UInt32
  chunksReceived: UInt32
  startedAt: Instant
  deadline: Instant? (per power tier retry budget)
  retryCount: UInt
}
```

### 9.2 Selective Acknowledgment

- **Bitfield encoding**: Each bit represents one chunk (bit N = chunk N missing)
- **Single bitfield**: 32 bytes for up to 256 chunks (practical BLE transfer limit)
- **Multi-bitfield**: For large transfers, concatenate bitfields (each 32 bytes)
- Sparse/dense patterns both efficient - constant predictable overhead per 256-chunk window
- Partial ACK never forces re-send of already-received chunks
- Scoreboard clears on session completion or explicit failure

### 9.3 Cut-Through Relay

- Pipeline forwarding without full reassembly
- Relays decrypt (hop layer) → re-encrypt (next hop) → forward
- Relay buffers maintained for local retransmission handling

### 9.4 TransferAck Wire Format

```text
TransferAck {
  sessionId: UInt128 (16 bytes)
  bitfield: UInt8Vector (32 bytes per 256-chunk window, MSB = chunk 0)
}

// For large transfers: multiple bitfields concatenated
// Each 32-byte bitfield covers chunks [N*256 .. (N+1)*256)
// Example: 512 chunks = 64 bytes total (bitfield[0] + bitfield[1])
```

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

### 10.2 EU Regulatory Clamping

When region = EU:

- Advertisement interval floor: 300ms (below spec values clamped)
- Scan duty cycle ceiling: 70%

### 10.3 Adaptive Grace Period

Grace period adapts based on peer stability and power tier:

- **Base period:** HIGH=15s, MEDIUM=30s, LOW=45s, OFF=0s
- **Stability factor:** Increases for stable peers, decreases for frequent disconnectors
- **Uptime factor:** Longer average sessions get longer grace periods
- **Bonded minimum:** 10 seconds guarantee (never shorter)

### 10.4 Tier-Driven Parameters

| Tier | Scan Duty Cycle | Adv Interval | Conn Interval | Concurrent | Chunk Size |
|------|-----------------|--------------|---------------|------------|------------|
| HIGH | 20% | 100ms | 7.5-15ms | 8 | 512B |
| MEDIUM | 10% | 500ms | 15-30ms | 4 | 256B |
| LOW | 5% | 1000ms | 30-60ms | 2 | 128B |
| OFF | 0% | Never | N/A | 0 | N/A |

---

## 11. Diagnostics & Events

### 11.1 Peer Events

```text
sealed interface PeerEvent {
  data class Found(val peerId: PeerId, val connectionState: ConnectionState)
  data class StateChanged(val peerId: PeerId, val state: ConnectionState)
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

### 11.3 Diagnostic Events (Machine Observable)

| Event Category | Fields |
|----------------|--------|
| Routing privacy | `negotiated_mode`, `fallback_reason`, `downgrade_verdict`, `envelope_version`, `envelope_failure` |
| Transfer | `data_plane_bearer`, `fallback_reason` |
| Power mode | `effective_values`, `clampWarnings` |
| E2E Handshake | `protocol`, `fallback_used`, `peer_key_verified`, `rate_limit_attempts`, `nonce_replay_detected` |
| Key Rotation | `old_key_verified`, `seqno_reset`, `propagation_deadline_met` |

### 11.4 Error Model

Sealed hierarchy in `commonMain`, platform exceptions wrapped:

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

---

## 13. Testing & Verification

### 13.1 Test Suite Structure

| Layer | Location | Coverage |
|-------|----------|----------|
| Unit/JVM | `commonTest` | Full coverage |
| Host/Android | `androidHostTest` | Crypto fallback validation |
| Device/Android | `meshlink-proof` | Real BLE behavior |
| Device/iOS | `meshlink-proof/ios` | Real BLE behavior, platform crypto |
| Reference app | `meshlink-reference` | Public API consumption only |

### 13.2 iOS Proof Testing (Security-Critical)

iOS has no real-device validation in current design (simulator cannot validate BLE). Requires physical device testing for:

- `IosCryptoProviderTest`: Verify Security framework + Secure Enclave key usage (iOS 14+)
- `CoreBluetoothThroughputTest`: Verify 15-20ms floor per BLE references
- `IosBackgroundTransferTest`: Verify background mode handling during transfers

### 13.3 NX Fallback Testing

- `NXFallbackPeerKeyVerifyTest`: Verify PeerKey mismatch causes rejection
- `NXFallbackRateLimitTest`: Verify 3rd attempt succeeds, 4th fails
- `NXFallbackTimeoutTest`: Verify 10s timeout expires correctly
- `NXFallbackReplayTest`: Verify nonce replay is rejected

### 13.4 Key Rotation Testing

- `KeyRotationAnnounceTest`: Verify signature verification and key adoption
- `KeyRotationSeqnoResetTest`: Verify seqno resets to 1, not preserved
- `KeyRotationPropagationTest`: Verify gossip reaches mesh within deadlines
- `KeyRotationRollbackTest`: Verify old key still accepted for active sessions
- `WireCompatTest`: Verify KeyRotationAnnouncement round-trips correctly

### 13.5 Virtual Mesh Harness

Multi-node scenarios exercised without physical hardware:

- Reconnect churn scenarios
- Digest-mismatch resolution
- Routing convergence tests
- Cross-platform compatibility verification

### 13.6 Wire Compatibility Testing

- Hex test vectors in `commonTest/resources/wire-compat/`
- Forward-compatibility checks
- Malformed-input validation

### 13.7 Acceptance Criteria Per Layer

1. **Data Model / Trust**: Wire vectors, malformed input rejection
2. **Discovery / Advertisement**: Single-packet format, key hash matching
3. **Security Contract**: Wycheproof vectors, fail-closed on all edge cases
4. **Routing Control**: Convergence under virtual harness, seqno correctness
5. **Chunked Transfer**: Bitfield SACK semantics, cut-through relay, retry bounds
6. **Power Policy**: Tier-to-parameter mapping, EU clamping observable
7. **Public API**: Identical Android/iOS surface, lifecycle events

---

## Implementation Order (Spec-First)

Per PROJECT.md suggested approach, sliced into vertical epics:

1. **Core Data Types (e01)** - PeerId, PeerKey, CryptoKey, TrustRecord, RouteEntry, TransferSession
2. **Wire Format (e02)** - FlatBuffers schemas, encode/decode, compatibility testing
3. **Noise Handshake XX (e03)** - Hop-by-hop link encryption with Android/iOS platform crypto
4. **E2E Handshake IX/NX (e04)** - End-to-end encryption with mesh routing and fallback
5. **Routing Coordinator (e05)** - Babel-style seqno management, metric-based path selection
6. **Transfer Session (e06)** - Bitfield SACK protocol, cut-through relay, retry bounds
7. **Peer Lifecycle (e07)** - Adaptive grace period (CONNECTED → DISCONNECTED → GONE)
8. **Key Rotation (e08)** - Signed announcements, seqno reset, 3-day default interval
9. **iOS Proof Harness (e09)** - Real-device validation for iOS platform glue (security critical)
10. **Power Tiers (e10)** - Four-tier model with quantified parameters

Each layer validated against RFC-grounded reference algorithms before platform glue. Epics ordered by WSJF: e09 (iOS proof) boosted for security gap mitigation.

---

## 14. Configuration Model

### 14.1 Configuration DSL

```kotlin
MeshLinkConfig {
  powerTier: PowerTier (default: MEDIUM)
  keyRotation: KeyRotationConfig {
    interval: Duration (default: 3 days)
    gracePeriod: Duration (default: 1 hour)
  }
  transfer: TransferConfig {
    maxRetries: Int (default: 5)
    chunkSize: Int (default: 256 bytes, overridden by power tier)
  }
  diagnostics: DiagnosticsConfig {
    emitToLog: Boolean (default: true)
    eventCallback: (DiagnosticEvent) -> Unit
  }
}
```

### 14.2 Usage Example

```kotlin
val config = meshLinkConfig {
  powerTier = PowerTier.HIGH
  keyRotationInterval = Duration.days(1)  // Override 3-day default
  keyRotationGracePeriod = Duration.minutes(30)
}
```
