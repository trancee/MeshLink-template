package ch.trancee.meshlink

import ch.trancee.meshlink.model.HandshakePattern
import ch.trancee.meshlink.model.PowerMode
import ch.trancee.meshlink.model.RegulatoryRegion
import ch.trancee.meshlink.model.ScoreboardEncoding
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// ---------------------------------------------------------------------------
// Settings classes — immutable once built, mutable on builder
// ---------------------------------------------------------------------------

public data class KeyRotationSettings(
    public val interval: Duration,
    public val rotationGracePeriod: Duration,
    public val compromiseGracePeriod: Duration,
)

public data class TransferSettings(
    public val maxRetries: Int,
    public val chunkSize: Int,
    public val maxConcurrentSessionsPerPeer: Int,
    public val scoreboardEncoding: ScoreboardEncoding,
    public val maxChunksPerSession: UInt,
)

public data class RoutingSettings(
    public val routeUpdateMinInterval: Duration,
    public val routeUpdateMaxInterval: Duration,
    public val routeUpdateChangeThreshold: Int,
    public val fullTableSyncInterval: Duration,
    public val routeEntryExpiry: Duration,
    public val feasibilityConditionEnabled: Boolean,
    public val maxRouteEntries: Int,
)

public data class SecuritySettings(
    public val fallbackMaxAttemptsPerMinute: Int,
    public val fallbackTimeout: Duration,
    public val requireSignatureOnRouteUpdates: Boolean,
    public val defaultHandshakePattern: HandshakePattern,
)

public data class DiagnosticsSettings(
    public val emitToLog: Boolean,
    public val eventBufferSize: Int,
)

// ---------------------------------------------------------------------------
// Top-level MeshLinkSettings
// ---------------------------------------------------------------------------

public data class MeshLinkSettings(
    public val powerMode: PowerMode,
    public val regulatoryRegion: RegulatoryRegion,
    public val keyRotation: KeyRotationSettings,
    public val transfer: TransferSettings,
    public val routing: RoutingSettings,
    public val security: SecuritySettings,
    public val diagnostics: DiagnosticsSettings,
)

// ---------------------------------------------------------------------------
// Builder — imperative, no lambda blocks — Kover traceable
// ---------------------------------------------------------------------------

public class MeshLinkSettingsBuilder {
    public var powerMode: PowerMode = PowerMode.MEDIUM
    public var regulatoryRegion: RegulatoryRegion = RegulatoryRegion.DEFAULT
    public var keyRotationInterval: Duration = 3.days
    public var keyRotationGracePeriod: Duration = 1.hours
    public var keyRotationCompromiseGracePeriod: Duration = Duration.ZERO
    public var transferMaxRetries: Int = 5
    public var transferChunkSize: Int = 256
    public var transferMaxConcurrentSessionsPerPeer: Int = 3
    public var transferScoreboardEncoding: ScoreboardEncoding = ScoreboardEncoding.DYNAMIC
    public var transferMaxChunksPerSession: UInt = 1024u
    public var routingMinInterval: Duration = 1.seconds
    public var routingMaxInterval: Duration = 30.seconds
    public var routingChangeThreshold: Int = 3
    public var routingFullSyncInterval: Duration = 5.minutes
    public var routingExpiry: Duration = 15.minutes
    public var routingFeasibilityEnabled: Boolean = true
    public var routingMaxEntries: Int = 256
    public var securityFallbackAttempts: Int = 3
    public var securityFallbackTimeout: Duration = 10.seconds
    public var securityRequireSignature: Boolean = true
    public var securityDefaultHandshakePattern: HandshakePattern = HandshakePattern.IX
    public var diagnosticsEmitToLog: Boolean = true
    public var diagnosticsBufferSize: Int = 1000

    public fun build(): MeshLinkSettings =
        MeshLinkSettings(
            powerMode,
            regulatoryRegion,
            KeyRotationSettings(
                keyRotationInterval,
                keyRotationGracePeriod,
                keyRotationCompromiseGracePeriod,
            ),
            TransferSettings(
                transferMaxRetries,
                transferChunkSize,
                transferMaxConcurrentSessionsPerPeer,
                transferScoreboardEncoding,
                transferMaxChunksPerSession,
            ),
            RoutingSettings(
                routingMinInterval,
                routingMaxInterval,
                routingChangeThreshold,
                routingFullSyncInterval,
                routingExpiry,
                routingFeasibilityEnabled,
                routingMaxEntries,
            ),
            SecuritySettings(
                securityFallbackAttempts,
                securityFallbackTimeout,
                securityRequireSignature,
                securityDefaultHandshakePattern,
            ),
            DiagnosticsSettings(diagnosticsEmitToLog, diagnosticsBufferSize),
        )
}
