# Core Data Models

> Source: [SPEC.md §3](../../SPEC.md#3-core-data-models)

## 3.1 Peer Identity Model

```text
PeerIdentity: 16-byte stable/random identifier (generated once at install, survives key rotations)
Ed25519PublicKey: 32-byte EdDSA signing key
X25519PublicKey: 32-byte DH key for Noise handshakes
PeerFingerprint: 12-byte SHA-256(Ed25519Pub || X25519Pub) truncated, used in discovery. Ed25519 first (identity anchor), X25519 second (DH key). Both keys required. **NOTE: 12 bytes (96 bits) provides birthday bound 2^48; collision probability negligible for any practical mesh size. This is a DISCOVERY HINT ONLY — never used for authentication.** [Decision: docs/decisions/model/data-model.md]
```

**Design Note:** PeerIdentity is stable/random, NOT derived from public key. This ensures identity persists across key rotations, enabling correct TrustStore lookups during key rotation announcements. [Decision: docs/decisions/model/data-model.md]

### Sequence Number Wrapper (Mandatory)

```kotlin
/**
 * Unsigned 32-bit sequence number with safe wrap-around comparison.
 * RFC 8966 §3.7 requires signed interpretation for seqno comparison.
 */
@JvmInline
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

### PowerMode Enum

```text
enum class PowerMode { HIGH, MEDIUM, LOW }
```

- `HIGH` — Performance prioritized (20% scan, 100ms adv, 7.5ms conn, 8 concurrent, 512B chunks)
- `MEDIUM` — Balanced (default) (10% scan, 500ms adv, 15ms conn, 4 concurrent, 256B chunks)
- `LOW` — Battery conserved (5% scan, 1000ms adv, 30ms conn, 2 concurrent, 128B chunks)

[Decision: docs/decisions/power/power-mode-behavior.md]

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

## 3.6 Platform Enums (from TypeModel.kt)

All enums defined in `meshlink/src/commonMain/kotlin/ch/trancee/meshlink/model/TypeModel.kt`:

```kotlin
// Key type distinction for compile-time safety
enum class KeyType { ED25519, X25519 }

// Handshake patterns used in Noise protocol
enum class HandshakePattern { XX, IK, IX, NX }

// Scoreboard encoding strategy for selective acknowledgment
enum class ScoreboardEncoding { DYNAMIC, FIXED }

// Message priority affecting TTL and routing
enum class Priority { HIGH, NORMAL, LOW }

// Wire frame types
enum class FrameType {
    MESH_ENVELOPE, ROUTE_UPDATE, ROUTE_WITHDRAWAL, ROUTE_DIGEST,
    TRANSFER_CHUNK, TRANSFER_ACKNOWLEDGMENT, TRANSFER_CANCEL, KEY_ROTATION
}

// Transport fallback reasons (machine observable)
enum class TransportFallbackReason {
    NO_PSM_ADVERTISED, L2CAP_CONNECT_FAILED, L2CAP_DROPPED_MID_TRANSFER, LOCAL_POLICY
}

// Data plane bearer in use
enum class DataPlaneBearer { GATT, L2CAP }

// Noise session layer distinction
enum class NoiseLayer { HOP_BY_HOP, END_TO_END }

// Noise session states for state machine
enum class NoiseSessionState {
    DISCONNECTED, HANDSHAKING_XX, HANDSHAKING_IK, ESTABLISHED, REKEYING, FAILED
}

// Role in Noise handshake
enum class NoiseRole { INITIATOR, RESPONDER }

// Reason for Noise handshake failure
enum class NoiseFailureReason {
    HANDSHAKE_TIMEOUT, HANDSHAKE_MESSAGE_MALFORMED, HANDSHAKE_MESSAGE_OUT_OF_ORDER,
    REMOTE_STATIC_KEY_MISMATCH, REMOTE_STATIC_KEY_UNKNOWN, REKEY_REJECTED,
    TRANSPORT_CLOSED, MAX_RETRIES_EXCEEDED, INTERNAL_ERROR
}
```

## 3.7 Trust Record Model

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
- `REVOKED` — Explicitly revoked by user/application [Decision: docs/decisions/model/data-model.md]

## 3.3 Route Entry Model

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
  supportsL2CAP: Boolean               // Bit 8
  fastInterval: Boolean              // Bit 9
  highPowerMode: Boolean             // Bit 10
  composite: UInt                   // Serialized form: (flags shl 8) or rssiNormalized
}
```

**Metric structure:** Low byte = RSSI normalized (0-255), high bits = flags (supportsL2CAP, fastInterval, highPowerMode), enabling path selection preferring better links. [Decision: ../../decisions/routing/routing-design.md]

## 3.4 Message Header Model

`RoutingMessage` is the application-level metadata. It carries the metadata that describes a message (version, id, priority, destination). When a message is sent through the mesh, the `RoutingMessage` is serialized and placed inside a `RoutingFrame.payload` (see §3.5, §5.7). The `RoutingFrame` is the wire-level routing frame that relays use to forward the message — it carries `destination`, `payload`, and `hopLimit`. The `hopLimit` is a routing concern set by the routing layer, not by the application, so it is not a field of `RoutingMessage`.

```text
RoutingMessage {
  version: U8
  messageId: 64-bit random
  priority: Priority
  destination: PeerIdentity
  // ttl is derived from priority by the routing layer (see §8.5) and applied to the TTL field in RoutingFrame (see §3.5)
}
```

## 3.5 Transfer Session

```text
TransferSession {
  sessionId: SessionId    // 64-bit random token; identifies a transfer session uniquely
  destination: PeerIdentity
  priority: Priority  // Transfer priority for QoS-like behavior
  state: TransferState (IN_PROGRESS, WAITING_FOR_ROUTE, RETRYING, COMPLETED, FAILED, TIMED_OUT)
  failureReason: TransferFailureReason? (reason for terminal FAILED state; null otherwise)
  chunkSize: Int (selected by local power mode, bounded by peer MTU; see §10.4 for power-mode-based values)
  totalChunks: UInt (ceil(totalBytes / chunkSize))
  scoreboard: Scoreboard (dynamic bitfield; bit N = 1 if chunk N received; see §3.4)
  totalBytes: Long
  bytesReceived: Long
  startedAt: Instant
  expiresAt: Instant? (max time transfer can remain WAITING_FOR_ROUTE before failing; computed as `startedAt + retryBudget`; see §10.4 mode table for per-mode values)
  retryCount: Int
}
```

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

[Decision: docs/decisions/model/data-model.md]

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

## 3.5 Wire Frame Types

| Type | Meaning | Encryption |
|------|---------|------------|
| MESH_ENVELOPE | Routed E2E handshake or payload | Link-layer AEAD per hop |
| ROUTE_UPDATE | Route announcement with metric + seqno + destination public key | Always AEAD-encrypted |
| ROUTE_WITHDRAWAL | Route retraction | Always AEAD-encrypted |
| ROUTE_DIGEST | FNV-1a hash of route table (32-bit) | Plaintext (digest only) |
| TRANSFER_CHUNK | Payload chunk with offset + length | Link-layer AEAD per hop |
| TRANSFER_ACKNOWLEDGMENT | Dynamic bitfield SACK | Link-layer AEAD per hop |
| TRANSFER_CANCEL | Session termination | Link-layer AEAD per hop |
| KEY_ROTATION | Signed key rotation announcement | Plaintext (signature verifiable) |

ROUTE_UPDATE and ROUTE_WITHDRAWAL are always AEAD-encrypted using the Noise session key — no plaintext routing metadata is ever transmitted. [Decision: ../decisions/routing/routing-design.md, ../decisions/wire/wire-format-spec.md]

**Note:** `Hello` and `Ihu` frame types were considered and removed — BLE connection state (GATT/L2CAP connect/disconnect) provides liveness, making periodic Hello/IHU frames redundant. See [Routing Design](../decisions/routing/routing-design.md) for rationale.
