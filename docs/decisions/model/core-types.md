# MeshLink Data Model Specification

## Status: Proposed

## Overview

Core data types for MeshLink. All types live in `meshlink/src/commonMain/kotlin/ch/trancee/meshlink/model/`. Platform-specific implementations use `expect/actual`.

## Core Types

### PeerIdentity

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
value class PeerIdentity(private val bytes: ByteArray) {
  init { require(bytes.size == 16) }
  
  val hex: String get() = bytes.joinToString("") { "%02x".format(it) }
  
  companion object {
    fun generate(): PeerIdentity {
      val bytes = SecureRandom.nextBytes(16)
      return PeerIdentity(bytes)
    }
  }
}
```

### PeerFingerprint

A truncated hash used in discovery advertisements.

```kotlin
/**
 * 12-byte SHA-256 truncated public key hash.
 * Used in discovery packets only (not in NX handshake — full keys used there).
 * 
 * Rationale: 12 bytes (96 bits) provides:
 * - Birthday bound 2^48 (infeasible collision)
 * - Fits in BLE advertisement packet
 * - Lightweight hint without full key exposure
 */
@JvmInline
value class PeerFingerprint(private val bytes: ByteArray) {
  init { require(bytes.size == 12) }

  companion object {
    /**
     * Derives PeerFingerprint from both public keys.
     * Order: Ed25519Pub || X25519Pub — Ed25519 first because it is the
     * identity/signing key (primary identity anchor); X25519 second because
     * it is the DH key (may be rotated independently).
     * If either key is missing, derivation fails — both keys are required.
     */
    fun fromKeys(identityKey: Ed25519Key, handshakeKey: X25519Key): PeerFingerprint {
      val hash = sha256(identityKey.bytes + handshakeKey.bytes)
      return PeerFingerprint(hash.copyOf(12)) // First 12 bytes
    }
  }
}
```

### Design Correction Note (2026-07-23)

**Why PeerIdentity is stable/random, not derived from publicKey:**

Initial design derived PeerIdentity from public key: `PeerIdentity = SHA-256(publicKey).first(16)`. This created a critical flaw:

1. **Key rotation changes public key** — Therefore changes PeerIdentity
2. **Neighbors can't look up old key** — TrustStore indexed by PeerIdentity would be stale
3. **KeyRotationAnnouncement breaks** — Cannot verify with "old key by PeerIdentity"

**Solution:** Generate stable PeerIdentity ONCE at install time. This ensures:

- Peer identity persists across key rotations
- TrustStore works correctly (old key lookups succeed)
- Key rotation announcements validate properly

### CryptoKey

A sealed interface that distinguishes Ed25519 (identity/signing) keys from X25519 (DH) keys, so the compiler prevents mixing them up.

```kotlin
enum class KeyType { ED25519, X25519 }

sealed interface CryptoKey {
  val bytes: ByteArray
  val keyType: KeyType

  /** Non-key hex identifier for diagnostics. Raw key material MUST NOT be logged. */
  val diagnosticId: String get() = "key:${bytes.first().toHexString()}"
}

@JvmInline
value class Ed25519Key(private val bytes: ByteArray) : CryptoKey {
  init { require(bytes.size == 32) }
  override val keyType = KeyType.ED25519
  override val bytes get() = bytes
}

@JvmInline
value class X25519Key(private val bytes: ByteArray) : CryptoKey {
  init { require(bytes.size == 32) }
  override val keyType = KeyType.X25519
  override val bytes get() = bytes
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
  val peerIdentity: PeerIdentity,
  val publicKey: Ed25519Key,
  val seenAt: Instant,
  val verifiedAt: Instant,
  val state: TrustState
)

enum class TrustState {
  INITIATED, // Handshake in progress, not yet verified
  TRUSTED,    // TOFU-pinned identity (first successful handshake)
  REVOKED     // Explicitly revoked by user/application
}

// Trust store interface
interface TrustStore {
  suspend fun getPublicKey(peerIdentity: PeerIdentity): Ed25519Key?
  suspend fun getPeerFingerprint(peerIdentity: PeerIdentity): PeerFingerprint?
  suspend fun save(peerIdentity: PeerIdentity, identityKey: Ed25519Key): Boolean
  suspend fun revoke(peerIdentity: PeerIdentity)
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
  val destination: PeerIdentity,
  val nextHop: PeerIdentity?,      // null = destination unreachable
  val seqNo: UInt,           // Destination-sourced sequence number
  val metric: UInt,            // Link quality metric (RSSI+flags)
  val identityKey: Ed25519Key?,   // Destination's identity key, learned via route updates
  val expiresAt: Instant,    // Route expiration
  /**
   * RFC 8966 §3.5.1 feasibility condition: true if this route's metric
   * is strictly better than the feasible distance of any existing
   * route for the same destination. Prevents loop formation in
   * distance-vector routing.
   */
  val isFeasible: Boolean,
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
  val destination: PeerIdentity,
  val state: TransferState,
  val chunkSize: Int,              // Based on power tier
  val totalChunks: UInt,           // ceil(totalBytes / chunkSize)
  val scoreboard: Scoreboard,       // Dynamic bitfield; bit N = 1 if chunk N received
  val totalBytes: Long,
  val bytesReceived: Long,
  val startedAt: Instant,
  val expiresAt: Instant?,        // Max time WAITING_FOR_ROUTE before failing (startedAt + retryBudget)
  val retryCount: Int
)

/**
 * Type-safe wrapper around a dynamic bitfield for selective acknowledgment.
 * Bit N = 1 means chunk N is received (standard SACK convention).
 * Internally backed by ByteArray for wire-format compatibility.
 */
/**
 * Type-safe wrapper around a dynamic bitfield for selective acknowledgment.
 * Bit N = 1 means chunk N is received (standard SACK convention).
 * Internally backed by ByteArray for wire-format compatibility.
 *
 * This class is immutable — [markReceived] and [markMissing] return a new
 * [Scoreboard] instance rather than mutating the original.
 */
class Scoreboard private constructor(
  private val totalChunks: UInt,
  private val bytes: ByteArray,
) {
  constructor(totalChunks: UInt) : this(totalChunks, ByteArray((totalChunks.toInt() + 7) / 8))

  fun markReceived(chunkIndex: Int): Scoreboard {
    val new = bytes.copyOf()
    new[chunkIndex / 8] = new[chunkIndex / 8].setBit(chunkIndex % 8)
    return Scoreboard(totalChunks, new)
  }

  fun markMissing(chunkIndex: Int): Scoreboard {
    val new = bytes.copyOf()
    new[chunkIndex / 8] = new[chunkIndex / 8].clearBit(chunkIndex % 8)
    return Scoreboard(totalChunks, new)
  }

  fun isReceived(chunkIndex: Int): Boolean = bytes[chunkIndex / 8].isBitSet(chunkIndex % 8)
  fun isMissing(chunkIndex: Int): Boolean = !isReceived(chunkIndex)
  fun missingChunks(): List<Int> = (0 until totalChunks.toInt()).filter { isMissing(it) }
  fun receivedCount(): Int = (0 until totalChunks.toInt()).count { isReceived(it) }
  fun toByteArray(): ByteArray = bytes
}

private fun Byte.setBit(bit: Int) = this.toInt() or (1 shl bit)
private fun Byte.clearBit(bit: Int) = this.toInt() and (1 shl bit).inv()
private fun Byte.isBitSet(bit: Int) = (this.toInt() shr bit) and 1 == 1

enum class TransferState {
  IN_PROGRESS,
  WAITING_FOR_ROUTE,     // Waiting for route (transfer was in progress, route lost)
  RETRYING,       // Actively retrying (retransmitting chunks, backoff)
  COMPLETED,
  FAILED,
  TIMED_OUT
}

/**
 * Valid state transitions for [TransferSession].
 *
 * | Current State | Event | Next State |
 * |---|---|---|
 * | — | Session created | IN_PROGRESS |
 * | IN_PROGRESS | All chunks received + scoreboard complete | COMPLETED |
 * | IN_PROGRESS | Error, cancel, or trust failure | FAILED |
 * | IN_PROGRESS | Route lost, waiting for route recovery | WAITING_FOR_ROUTE |
 * | WAITING_FOR_ROUTE | Route found, resume transfer | IN_PROGRESS |
 * | WAITING_FOR_ROUTE | Retry budget or grace period exhausted | TIMED_OUT |
 * | IN_PROGRESS | Chunk missing, schedule retransmit | RETRYING |
 * | RETRYING | Retransmission complete, back in progress | IN_PROGRESS |
 * | RETRYING | Retry budget exhausted | FAILED |
 * | Any terminal | Session cleaned up | — |
 */

// Session ID is a random 64-bit token used to correlate chunks within a transfer session
@JvmInline
value class SessionId(private val bytes: ByteArray) {
  init { require(bytes.size == 8) } // Random token (64-bit)
}
```

### PeerLifecycleState

Per-peer connection tracking.

```kotlin
/**
 * Connection state for a peer.
 * Drives peer lifecycle (CONNECTED -> DISCONNECTED -> GONE).
 */
data class PeerLifecycleState(
  val peerIdentity: PeerIdentity,
  val connectionState: PeerConnectionState,
  val expiresAt: Instant?,     // Non-null while in grace window; null when GONE
  val rssi: Int?,          // For metric calculation
  val supportsCoc: Boolean,    // L2CAP CoC capability
  val connectionInterval: Int,   // ms
  val handshakeAt: Instant?    // For timeout calculations
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
data class MeshLinkSettings(
  val powerTier: PowerTier = PowerTier.MEDIUM,
  val keyRotation: KeyRotationSettings = KeyRotationSettings(),
  val transfer: TransferSettings = TransferSettings(),
  val diagnostics: DiagnosticsSettings = DiagnosticsSettings()
)

data class KeyRotationSettings(
  val interval: Duration = Duration.days(3),
  val gracePeriod: Duration = Duration.hours(1)
)

data class TransferSettings(
  val maxRetries: Int = 5,
  val chunkSize: Int = 256, // Default; overridden by power tier
)

data class DiagnosticsSettings(
  val emitToLog: Boolean = true,
  val eventBufferSize: Int = 1000
)

```

## Wire Protocol Types

### Envelope

### RoutingFrame (wire-level routing frame)

The wire-level routing frame that relays use to forward messages. It carries
the destination, the serialized `RoutingMessage` + encrypted E2E content, and
a hop limit set by the routing layer.

```kotlin
/**
 * Wire-level routing frame for forwarding through the mesh.
 * Relays decrypt the hop layer, check destination, and forward.
 * Does NOT expose E2E payload content to relays.
 */
data class RoutingFrame(
  val destination: PeerIdentity,     // Final destination (set from RoutingMessage)
  val payload: ByteArray,        // Flexible inner content (RoutingMessage + E2E payload)
  val hopLimit: UByte            // Set by routing layer; 0 = direct only
)
```

### RoutingMessage (application-level routing metadata)

The application-level metadata. Carries version, id, priority, destination for mesh routing.
Serialized into `RoutingFrame.payload` for routing through the mesh.

```kotlin
/**
 * Application-level routing metadata.
 * Serialized into RoutingFrame.payload for routing through the mesh.
 * hopLimit and TTL are set by the routing layer, not the application.
 * TTL is derived from priority by the routing layer.
 */
data class RoutingMessage(
  val version: UByte,           // Protocol version
  val messageId: Long,         // 64-bit random for deduplication
  val priority: MessagePriority, // HIGH, NORMAL, LOW
  val destination: PeerIdentity      // Final destination
)

enum class MessagePriority { HIGH, NORMAL, LOW }
```

### HandshakePayload

E2E handshake payload.

```kotlin
/**
 * Payload for E2E handshake (IX/NX).
 * NX carries full public keys (64 bytes) for verification.
 * IX does not carry keys (destination key already known via route).
 * PeerFingerprint included for logging/correlation.
 */
data class HandshakePayload(
  val peerFingerprint: PeerFingerprint,       // For logging/correlation
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
  val identityKey: Ed25519Key,     // NEW identity key
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
  val sessionId: SessionId,     // 8-byte session identifier
  val offset: UInt,             // Byte offset in overall payload
  val length: UShort,           // Length of this chunk's data
  val data: ByteArray          // Chunk payload bytes
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
  val sessionId: SessionId,     // 8-byte session identifier
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
  val sessionId: SessionId,     // 8-byte session identifier
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

- `PeerIdentityTest`: verify truncation and hex encoding
- `PeerFingerprintTest`: verify 12-byte hash derivation
- `CryptoKeyTest`: verify diagnostic ID doesn't leak key material
- `TrustRecordTest`: verify serialization and state transitions
- `RouteEntryTest`: verify seqno and metric handling
- `TransferSessionTest`: verify dynamic bitfield scoreboard and status transitions
- `MeshLinkSettingsTest`: verify DSL and defaults
- `WireFormatTest`: verify FlatBuffers serialization

## Related

- `docs/decisions/crypto/nx-fallback-mitigation.md`
- `docs/decisions/crypto/key-rotation-protocol.md`
- `docs/decisions/routing/link-quality-metric.md`
- `docs/decisions/power/power-tier-behavior.md`
