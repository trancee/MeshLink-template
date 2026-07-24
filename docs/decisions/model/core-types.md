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
    /**
     * Derives PeerKey from both public keys.
     * Order: Ed25519Pub || X25519Pub — Ed25519 first because it is the
     * identity/signing key (primary identity anchor); X25519 second because
     * it is the DH key (may be rotated independently).
     * If either key is missing, derivation fails — both keys are required.
     */
    fun fromPublicKeys(ed25519Pub: CryptoKey, x25519Pub: CryptoKey): PeerKey {
      val hash = sha256(ed25519Pub.bytes + x25519Pub.bytes)
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
  val publicKey: CryptoKey?   // Destination's public key, learned via route updates
  val expiresAt: Instant,    // Route expiration
  val isFeasible: Boolean,    // RFC 8966 feasibility condition
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
  val scoreboard: Scoreboard,       // Dynamic bitfield; bit N = 1 if chunk N received
  val totalBytes: Long,
  val bytesReceived: Long,
  val startedAt: Instant,
  val expiresAt: Instant?,        // Max time SUSPENDED before failing (startedAt + retryBudget)
  val retryCount: Int
)

/**
 * Type-safe wrapper around a dynamic bitfield for selective acknowledgment.
 * Bit N = 1 means chunk N is received (standard SACK convention).
 * Internally backed by ByteArray for wire-format compatibility.
 */
class Scoreboard(private val totalChunks: UInt) {
  private val bytes: ByteArray = ByteArray((totalChunks.toInt() + 7) / 8)

  fun markReceived(chunkIndex: Int) { bytes[chunkIndex / 8] = bytes[chunkIndex / 8].setBit(chunkIndex % 8) }
  fun markMissing(chunkIndex: Int) { bytes[chunkIndex / 8] = bytes[chunkIndex / 8].clearBit(chunkIndex % 8) }
  fun isReceived(chunkIndex: Int): Boolean = bytes[chunkIndex / 8].isBitSet(chunkIndex % 8)
  fun isMissing(chunkIndex: Int): Boolean = !isReceived(chunkIndex)
  fun missingChunks(): List<Int> = (0 until totalChunks.toInt()).filter { isMissing(it) }
  fun receivedCount(): Int = (0 until totalChunks.toInt()).count { isReceived(it) }
  fun toByteArray(): ByteArray = bytes
}

private fun Byte.setBit(bit: Int) = this.toInt() or (1 shl bit)
private fun Byte.clearBit(bit: Int) = this.toInt() and (1 shl bit).inv()
private fun Byte.isBitSet(bit: Int) = (this.toInt() shr bit) and 1 == 1

enum class TransferStatus {
  IN_PROGRESS,
  SUSPENDED,     // Waiting for route (transfer was in progress, route lost)
  RETRYING,      // Actively retrying (retransmitting chunks, backoff)
  COMPLETED,
  FAILED,
  TIMED_OUT
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

### MeshEnvelope (wire-level routing frame)

The wire-level routing frame that relays use to forward messages. It carries
the destination, the serialized `MessageEnvelope` + encrypted E2E content, and
a hop limit set by the routing layer.

```kotlin
/**
 * Wire-level routing frame for forwarding through the mesh.
 * Relays decrypt the hop layer, check destination, and forward.
 * Does NOT expose E2E payload content to relays.
 */
data class MeshEnvelope(
  val destination: PeerId,     // Final destination (set from MessageEnvelope)
  val payload: ByteArray,      // Serialized MessageEnvelope + encrypted E2E content
  val hopLimit: UByte = 0      // Set by routing layer; 0 = direct only
)
```

### MessageEnvelope (application-level message model)

The application-level message model. Carries metadata describing a message.
Serialized and placed inside `MeshEnvelope.payload` for transmission.

```kotlin
/**
 * Application-level message model.
 * Serialized into MeshEnvelope.payload for routing through the mesh.
 * hopLimit is NOT a field — it is set by the routing layer in MeshEnvelope.
 */
data class MessageEnvelope(
  val version: UByte,           // Protocol version
  val messageId: Long,         // 64-bit random for deduplication
  val ttl: Duration,           // Priority-based time-to-live
  val priority: MessagePriority, // HIGH, NORMAL, LOW
  val destination: PeerId      // Final destination
)

enum class MessagePriority { HIGH, NORMAL, LOW }
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

### TransferChunk

Wire frame carrying a chunk of a large payload.

```kotlin
/**
 * Single chunk of a chunked transfer.
 * Carried over GATT or L2CAP CoC.
 */
data class TransferChunk(
  val sessionId: SessionId,     // 4-byte session identifier
  val offset: UInt,             // Byte offset in overall payload
  val length: UShort,           // Length of this chunk's data
  val data: ByteArray,          // Chunk payload bytes
  val isLast: Boolean           // Is this the final chunk?
)
```

### TransferAck

Wire frame carrying selective acknowledgment of received chunks.

```kotlin
/**
 * Selective acknowledgment for a transfer session.
 * Bit N = 1 means chunk N is received (standard SACK convention).
 */
data class TransferAck(
  val sessionId: SessionId,     // 4-byte session identifier
  val bitfield: ByteArray       // Dynamic bitfield: ceil(totalChunks / 8) bytes
)
```

### TransferCancel

Wire frame for canceling an active transfer session.

```kotlin
/**
 * Cancel an active transfer session.
 */
data class TransferCancel(
  val sessionId: SessionId,     // 4-byte session identifier
  val reason: TransferCancelReason,
  val error: String? = null     // Optional error message
)

enum class TransferCancelReason {
  TIMEOUT,
  UNREACHABLE,
  TRUST_FAILURE,
  USER_CANCELLED,
  INTERNAL_ERROR
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
