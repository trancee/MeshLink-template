package ch.trancee.meshlink

import ch.trancee.meshlink.diagnostics.DiagnosticEvent
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
// Settings classes — immutable once built
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

public data class DiagnosticsSettings(public val eventBufferSize: Int)

// ---------------------------------------------------------------------------
// Top-level MeshLinkSettings
// ---------------------------------------------------------------------------

public data class MeshLinkSettings(
    public val appId: String,
    public val powerMode: PowerMode,
    public val regulatoryRegion: RegulatoryRegion,
    public val keyRotation: KeyRotationSettings,
    public val transfer: TransferSettings,
    public val routing: RoutingSettings,
    public val security: SecuritySettings,
    public val diagnostics: DiagnosticsSettings,
    public val emitToLog: Boolean,
    public val eventCallback: ((DiagnosticEvent) -> Unit)?,
)

// ---------------------------------------------------------------------------
// Lambda DSL — primary API per docs/decisions/model/settings-model.md
// ---------------------------------------------------------------------------

/**
 * MeshLink settings DSL builder.
 *
 * Usage:
 * ```kotlin
 * val settings = meshLinkSettings {
 *   appId = "com.example.myapp"
 *   powerMode = PowerMode.HIGH
 *   regulatoryRegion = RegulatoryRegion.EU
 *   keyRotation {
 *     interval = Duration.days(1)
 *     rotationGracePeriod = Duration.minutes(30)
 *     compromiseGracePeriod = Duration.ZERO
 *   }
 *   transfer {
 *     maxRetries = 3
 *     chunkSize = 512
 *     maxConcurrentSessionsPerPeer = 2
 *   }
 *   routing {
 *     routeUpdateMinInterval = Duration.seconds(1)
 *     routeUpdateMaxInterval = Duration.seconds(30)
 *     routeUpdateChangeThreshold = 3
 *     fullTableSyncInterval = Duration.minutes(5)
 *     routeEntryExpiry = Duration.minutes(15)
 *     feasibilityConditionEnabled = true
 *     maxRouteEntries = 256
 *   }
 *   security {
 *     fallbackMaxAttemptsPerMinute = 3
 *     fallbackTimeout = Duration.seconds(10)
 *     requireSignatureOnRouteUpdates = true
 *     defaultHandshakePattern = HandshakePattern.IX
 *   }
 *   diagnostics {
 *     eventBufferSize = 1000
 *   }
 *   emitToLog = true
 *   eventCallback = { event -> println(event) }
 * }
 * ```
 */
public fun meshLinkSettings(block: MeshLinkSettingsBuilder.() -> Unit): MeshLinkSettings =
    MeshLinkSettingsBuilder().apply(block).build()

// ---------------------------------------------------------------------------
// Builder — imperative + lambda DSL
// ---------------------------------------------------------------------------

public class MeshLinkSettingsBuilder {
    // Top-level
    public var appId: String = ""
    public var powerMode: PowerMode = PowerMode.MEDIUM
    public var regulatoryRegion: RegulatoryRegion = RegulatoryRegion.DEFAULT
    public var emitToLog: Boolean = false
    public var eventCallback: ((DiagnosticEvent) -> Unit)? = null

    // Key rotation (imperative)
    public var keyRotationInterval: Duration = 3.days
    public var keyRotationGracePeriod: Duration = 1.hours
    public var keyRotationCompromiseGracePeriod: Duration = Duration.ZERO

    // Transfer (imperative)
    public var transferMaxRetries: Int = 5
    public var transferChunkSize: Int = 256
    public var transferMaxConcurrentSessionsPerPeer: Int = 3
    public var transferScoreboardEncoding: ScoreboardEncoding = ScoreboardEncoding.DYNAMIC
    public var transferMaxChunksPerSession: UInt = 1024u

    // Routing (imperative)
    public var routingMinInterval: Duration = 1.seconds
    public var routingMaxInterval: Duration = 30.seconds
    public var routingChangeThreshold: Int = 3
    public var routingFullSyncInterval: Duration = 5.minutes
    public var routingExpiry: Duration = 15.minutes
    public var routingFeasibilityEnabled: Boolean = true
    public var routingMaxEntries: Int = 256

    // Security (imperative)
    public var securityFallbackAttempts: Int = 3
    public var securityFallbackTimeout: Duration = 10.seconds
    public var securityRequireSignature: Boolean = true
    public var securityDefaultHandshakePattern: HandshakePattern = HandshakePattern.IX

    // Diagnostics (imperative)
    public var diagnosticsBufferSize: Int = 1000

    // --- Lambda DSL nested blocks ---

    /** Nested builder for key rotation settings. */
    public fun keyRotation(block: KeyRotationSettingsBuilder.() -> Unit) {
        val builder = KeyRotationSettingsBuilder()
        builder.block()
        keyRotationInterval = builder.interval
        keyRotationGracePeriod = builder.rotationGracePeriod
        keyRotationCompromiseGracePeriod = builder.compromiseGracePeriod
    }

    /** Nested builder for transfer settings. */
    public fun transfer(block: TransferSettingsBuilder.() -> Unit) {
        val builder = TransferSettingsBuilder()
        builder.block()
        transferMaxRetries = builder.maxRetries
        transferChunkSize = builder.chunkSize
        transferMaxConcurrentSessionsPerPeer = builder.maxConcurrentSessionsPerPeer
        transferScoreboardEncoding = builder.scoreboardEncoding
        transferMaxChunksPerSession = builder.maxChunksPerSession
    }

    /** Nested builder for routing settings. */
    public fun routing(block: RoutingSettingsBuilder.() -> Unit) {
        val builder = RoutingSettingsBuilder()
        builder.block()
        routingMinInterval = builder.routeUpdateMinInterval
        routingMaxInterval = builder.routeUpdateMaxInterval
        routingChangeThreshold = builder.routeUpdateChangeThreshold
        routingFullSyncInterval = builder.fullTableSyncInterval
        routingExpiry = builder.routeEntryExpiry
        routingFeasibilityEnabled = builder.feasibilityConditionEnabled
        routingMaxEntries = builder.maxRouteEntries
    }

    /** Nested builder for security settings. */
    public fun security(block: SecuritySettingsBuilder.() -> Unit) {
        val builder = SecuritySettingsBuilder()
        builder.block()
        securityFallbackAttempts = builder.fallbackMaxAttemptsPerMinute
        securityFallbackTimeout = builder.fallbackTimeout
        securityRequireSignature = builder.requireSignatureOnRouteUpdates
        securityDefaultHandshakePattern = builder.defaultHandshakePattern
    }

    /** Nested builder for diagnostics settings. */
    public fun diagnostics(block: DiagnosticsSettingsBuilder.() -> Unit) {
        val builder = DiagnosticsSettingsBuilder()
        builder.block()
        diagnosticsBufferSize = builder.eventBufferSize
    }

    public fun build(): MeshLinkSettings =
        MeshLinkSettings(
            appId = appId,
            powerMode = powerMode,
            regulatoryRegion = regulatoryRegion,
            keyRotation =
                KeyRotationSettings(
                    keyRotationInterval,
                    keyRotationGracePeriod,
                    keyRotationCompromiseGracePeriod,
                ),
            transfer =
                TransferSettings(
                    transferMaxRetries,
                    transferChunkSize,
                    transferMaxConcurrentSessionsPerPeer,
                    transferScoreboardEncoding,
                    transferMaxChunksPerSession,
                ),
            routing =
                RoutingSettings(
                    routingMinInterval,
                    routingMaxInterval,
                    routingChangeThreshold,
                    routingFullSyncInterval,
                    routingExpiry,
                    routingFeasibilityEnabled,
                    routingMaxEntries,
                ),
            security =
                SecuritySettings(
                    securityFallbackAttempts,
                    securityFallbackTimeout,
                    securityRequireSignature,
                    securityDefaultHandshakePattern,
                ),
            diagnostics = DiagnosticsSettings(diagnosticsBufferSize),
            emitToLog = emitToLog,
            eventCallback = eventCallback,
        )
}

// ---------------------------------------------------------------------------
// Nested builder classes for lambda DSL
// ---------------------------------------------------------------------------

public class KeyRotationSettingsBuilder {
    public var interval: Duration = 3.days
    public var rotationGracePeriod: Duration = 1.hours
    public var compromiseGracePeriod: Duration = Duration.ZERO
}

public class TransferSettingsBuilder {
    public var maxRetries: Int = 5
    public var chunkSize: Int = 256
    public var maxConcurrentSessionsPerPeer: Int = 3
    public var scoreboardEncoding: ScoreboardEncoding = ScoreboardEncoding.DYNAMIC
    public var maxChunksPerSession: UInt = 1024u
}

public class RoutingSettingsBuilder {
    public var routeUpdateMinInterval: Duration = 1.seconds
    public var routeUpdateMaxInterval: Duration = 30.seconds
    public var routeUpdateChangeThreshold: Int = 3
    public var fullTableSyncInterval: Duration = 5.minutes
    public var routeEntryExpiry: Duration = 15.minutes
    public var feasibilityConditionEnabled: Boolean = true
    public var maxRouteEntries: Int = 256
}

public class SecuritySettingsBuilder {
    public var fallbackMaxAttemptsPerMinute: Int = 3
    public var fallbackTimeout: Duration = 10.seconds
    public var requireSignatureOnRouteUpdates: Boolean = true
    public var defaultHandshakePattern: HandshakePattern = HandshakePattern.IX
}

public class DiagnosticsSettingsBuilder {
    public var eventBufferSize: Int = 1000
}
