# MeshLink Data Model Specification

## Status: Proposed

## Overview

Core data types for MeshLink. All types live in `meshlink/src/commonMain/kotlin/ch/trancee/meshlink/model/`. Platform-specific implementations use `expect/actual`.

## Core Types

### PeerId

A unique identifier for a peer in the mesh.

```kotlin
/**
 * Stable 16-byte peer identifier.
 * Generated ONCE at install/first launch, stored permanently.
 * NOT derived from public key — remains stable across key rotations.
 * 
 * Rationale: 16 bytes (128 bits) provides:
 * - Birthday bound 2^64 (infeasible to collide)
 * - Unique identification for any practical mesh size
 * - Clean size (power-of-2 byte alignment)
 */
@JvmInline
value class PeerId(private val bytes: ByteArray) {
  init { require(bytes.size == 16) }
  
  val hex: String get() = bytes.joinToString("") { "%02x".format(it) }
  
  companion object {
    fun generate(): PeerId {
      val bytes = SecureRandom.nextBytes(16)
      return PeerId(bytes)
    }
  }
}
```

### PeerKey

A truncated hash used in discovery advertisements.

```kotlin
/**
 * 12-byte SHA-256 truncated public key hash.
 * Used in discovery packets and NX fallback verification.
 * 
 * Rationale: 12 bytes (96 bits) provides:
 * - Birthday bound 2^48 (infeasible collision)
 * - Fits in BLE advertisement packet
 * - Lightweight hint without full key exposure
 */
@JvmInline
value class PeerKey(private val bytes: ByteArray) {
  init { require(bytes.size == 12) }
  
  companion object {
    fun fromPublicKey(publicKey: CryptoKey): PeerKey {
      val hash = sha256(publicKey.bytes)
      return PeerKey(hash.copyOf(12)) // First 12 bytes
    }
  }
}
```

### Design Correction Note (2026-07-23)

**Why PeerId is stable/random, not derived from publicKey:**

Initial design derived PeerId from public key: `PeerId = SHA-256(publicKey).first(16)`. This created a critical flaw:

1. **Key rotation changes public key** — Therefore changes PeerId
2. **Neighbors can't look up old key** — TrustStore indexed by PeerId would be stale
3. **KeyRotationAnnouncement breaks** — Cannot verify with "old key by PeerId"

**Solution:** Generate stable PeerId ONCE at install time. This ensures:

- Peer identity persists across key rotations
- TrustStore works correctly (old key lookups succeed)
- Key rotation announcements validate properly

### CryptoKey

A full cryptographic key (Ed25519 or X25519).

```kotlin
/**
 * 32-byte cryptographic key (Ed25519 or X25519).
 * Raw key material MUST NOT be logged or exposed in diagnostics.
 */
@JvmInline
value class CryptoKey(private val bytes: ByteArray) {
  init { require(bytes.size == 32) }
  
  // Returns hex identifier for diagnostics (NOT the raw key)
  val diagnosticId: String get() = "key:${bytes.first().toHexString()}"
}
```

## Domain Models

### TrustRecord

Stored trust information for a peer.

```kotlin
/**
 * Trust record for a known peer.
 * Stored in persistent keystore; survives restarts.
 */
data class TrustRecord(
  val peerId: PeerId,
  val publicKey: CryptoKey,
  val seenAt: Instant,
  val verifiedAt: Instant,
  val status: TrustStatus
)

enum class TrustStatus {
  TRUSTED,    // TOFU-pinned identity (first successful handshake)
  REVOKED     // Explicitly revoked by user/application
}

// Trust store interface
interface TrustStore {
  suspend fun getPublicKey(peerId: PeerId): CryptoKey?
  suspend fun getPeerKey(peerId: PeerId): PeerKey?
  suspend fun save(peerId: PeerId, publicKey: CryptoKey): Boolean
  suspend fun revoke(peerId: PeerId)
}
```

### RouteEntry

Routing table entry for a destination.

```kotlin
/**
 * Route entry in the routing table.
 * Managed by RouteCoordinator; updates via RouteDigest.
 */
data class RouteEntry(
  val destination: PeerId,
  val nextHop: PeerId?,      // null = destination unreachable
  val seqNo: UInt,           // Destination-sourced sequence number
  val metric: UInt,            // Link quality metric (RSSI+flags)
  val expiresAt: Instant,    // Route expiration
  val isFeasible: Boolean     // RFC 8966 feasibility condition
)

// Link quality metric (see link-quality-metric.md)
data class LinkMetric(
  val rssiNormalized: UInt,    // 0-255 from normalizeRssi()
  val supportsCoc: Boolean,
  val fastInterval: Boolean,
  val highPowerTier: Boolean
) {
  val composite: UInt = 
    ((supportsCoc.bit(8) or fastInterval.bit(9) or highPowerTier.bit(10)) shl 8) or
    rssiNormalized
}
```

### TransferSession

Chunked transfer session state.

```kotlin
/**
 * Transfer session for large payloads.
 * Drives chunked transfer with selective ACK.
 */
data class TransferSession(
  val sessionId: SessionId,
  val destination: PeerId,
  val status: TransferStatus,
  val chunkSize: Int,              // Based on power tier
  val totalChunks: UInt,           // ceil(totalBytes / chunkSize)
  val scoreboard: ByteArray,       // Dynamic bitfield: ceil(totalChunks / 8) bytes; bit N = 1 if chunk N missing
  val totalBytes: Long,
  val bytesReceived: Long,
  val startedAt: Instant,
  val deadline: Instant?,          // Per power tier retry budget
  val retryCount: Int
)

enum class TransferStatus {
  IN_PROGRESS,
  PAUSED,        // Waiting for route
  COMPLETE,
  FAILED,
  TIMEOUT
}

// Session ID derives from E2E handshake
@JvmInline
value class SessionId(private val bytes: ByteArray) {
  init { require(bytes.size == 4) } // Random token (32-bit)
}
```

### ConnectionState

Per-peer connection tracking.

```kotlin
/**
 * Connection state for a peer.
 * Drives peer lifecycle (CONNECTED -> DISCONNECTED -> GONE).
 */
data class ConnectionState(
  val peerId: PeerId,
  val connectionState: PeerConnectionState,
  val graceSweeps: Int,        // 0-3 for transition to GONE
  val lastRssi: Int?,          // For metric calculation
  val supportsCoc: Boolean,    // L2CAP CoC capability
  val connectionInterval: Int,   // ms
  val lastHandshake: Instant?    // For timeout calculations
)

enum class PeerConnectionState {
  CONNECTED,
  DISCONNECTED
  // GONE is internal state only, never exposed publicly
}
```

### Config

Public API configuration.

```kotlin
/**
 * MeshLink configuration DSL.
 * Single source of truth for all tunable parameters.
 */
data class MeshLinkConfig(
  val powerTier: PowerTier = PowerTier.MEDIUM,
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

// Configuration builder
fun meshLinkConfig(block: MeshLinkConfigBuilder.() -> Unit): MeshLinkConfig {
  return MeshLinkConfigBuilder().apply(block).build()
}

class MeshLinkConfigBuilder {
  var powerTier: PowerTier = PowerTier.MEDIUM
  var keyRotationInterval: Duration = Duration.days(3)
  var keyRotationGracePeriod: Duration = Duration.hours(1)
  // ... other fields
  
  fun build(): MeshLinkConfig = MeshLinkConfig(
    powerTier = powerTier,
    keyRotation = KeyRotationConfig(
      interval = keyRotationInterval,
      gracePeriod = keyRotationGracePeriod
    )
  )
}
```

## Wire Protocol Types

### Envelope

Base wire format.

```kotlin
/**
 * MeshEnvelope for routing E2E handshakes and payloads.
 * Used when destination is not a direct neighbor.
 */
data class MeshEnvelope(
  val destination: PeerId,
  val payload: ByteArray,  // Encrypted E2E content or handshake
  val hopLimit: UByte = 0   // 0 = direct only, 1+ = max hops
)
```

### HandshakePayload

E2E handshake payload.

```kotlin
/**
 * Payload for E2E handshake (IX/NX).
 * Carries PeerKey and nonce for verification.
 */
data class HandshakePayload(
  val peerKey: PeerKey,       // For NX verification
  val nonce: UInt,             // Replay protection
  val content: ByteArray      // Encrypted payload or handshake data
)
```

### KeyRotationAnnouncement

Key rotation wire format.

```kotlin
/**
 * Wire announcement for key rotation.
 * Signed with OLD key; enforces seqno reset.
 */
data class KeyRotationAnnouncement(
  val publicKey: CryptoKey,     // NEW public key
  val seqNo: UInt,               // Always 1 (new identity)
  val signature: ByteArray,       // Ed25519 signature (64 bytes)
  val reason: KeyRotationReason = KeyRotationReason.PERIODIC
)

enum class KeyRotationReason {
  PERIODIC,
  MANUAL,
  SECURITY_EVENT
}
```

## Testing Requirements

- `PeerIdTest`: verify truncation and hex encoding
- `PeerKeyTest`: verify 12-byte hash derivation
- `CryptoKeyTest`: verify diagnostic ID doesn't leak key material
- `TrustRecordTest`: verify serialization and state transitions
- `RouteEntryTest`: verify seqno and metric handling
- `TransferSessionTest`: verify dynamic bitfield scoreboard and status transitions
- `MeshLinkConfigTest`: verify DSL and defaults
- `WireFormatTest`: verify FlatBuffers serialization

## Related

- `docs/decisions/crypto/nx-fallback-mitigation.md`
- `docs/decisions/crypto/key-rotation-protocol.md`
- `docs/decisions/routing/link-quality-metric.md`
- `docs/decisions/power/power-tier-behavior.md`
