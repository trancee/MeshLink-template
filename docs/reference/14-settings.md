# Settings Model

> Source: [SPEC.md §14](../../SPEC.md#14-settings-model)

## 14.1 Settings DSL

```kotlin
/**
 * MeshLink settings DSL.
 * Single source of truth for all tunable parameters.
 */
data class MeshLinkSettings(
  val powerMode: PowerMode = PowerMode.MEDIUM,
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
  val chunkSize: Int = 256, // Default; overridden by power mode
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
 * MeshLink settings DSL.
 * Usage:
 * ```kotlin
 * val settings = meshLinkSettings {
 *   powerMode = PowerMode.HIGH
 *   keyRotation { interval = Duration.days(1) }
 * }
 * ```
 */
fun meshLinkSettings(block: MeshLinkSettings.() -> Unit): MeshLinkSettings {
  return MeshLinkSettings().apply(block)
}
```

[Decision: docs/decisions/model/data-model.md]

## 14.2 Usage Example

```kotlin
val settings = meshLinkSettings {
  powerMode = PowerMode.HIGH
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
    routeUpdateMaxInterval = Duration.seconds(30),
    routeUpdateChangeThreshold = 3,
    fullTableSyncInterval = Duration.minutes(5),
    routeEntryExpiry = Duration.minutes(15),
    feasibilityConditionEnabled = true,
    maxRouteEntries = 256,
  )
  security = SecuritySettings(
    fallbackMaxAttemptsPerMinute = 3,
    fallbackTimeout = Duration.seconds(10),
    requireSignatureOnRouteUpdates = true,
    defaultHandshakePattern = HandshakePattern.IX,
  )
  diagnostics = DiagnosticsSettings(
    emitToLog = true,
    eventBufferSize = 1000,
  )
}
```
