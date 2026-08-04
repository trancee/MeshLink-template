package ch.trancee.meshlink

import ch.trancee.meshlink.model.PowerMode
import ch.trancee.meshlink.model.RegulatoryRegion
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

public data class KeyRotationSettings(
    public val interval: Duration = 3.days,
    public val rotationGracePeriod: Duration = 1.hours,
    public val compromiseGracePeriod: Duration = Duration.ZERO,
)

public data class TransferSettings(
    public val maxRetries: Int = 5,
    public val chunkSize: Int = 256,
    public val maxTransfersPerPeer: Int = 3,
)

public data class RoutingSettings(
    public val routeAdvertisementChangeThreshold: Int = 3,
    public val routeDigestInterval: Duration = 5.minutes,
    public val routeExpiry: Duration = 15.minutes,
    public val maxRoutes: Int = 256,
)

public data class DiagnosticsSettings(
    public val eventBufferSize: Int = 1000,
    public val emitLog: Boolean = false,
)

public data class MeshLinkSettings(
    public val appId: String,
    public val powerMode: PowerMode = PowerMode.MEDIUM,
    public val regulatoryRegion: RegulatoryRegion = RegulatoryRegion.DEFAULT,
    public val enableBackground: Boolean = false,
    public val keyRotation: KeyRotationSettings = KeyRotationSettings(),
    public val transfer: TransferSettings = TransferSettings(),
    public val routing: RoutingSettings = RoutingSettings(),
    public val diagnostics: DiagnosticsSettings = DiagnosticsSettings(),
)

public fun meshLinkSettings(block: MeshLinkSettingsBuilder.() -> Unit): MeshLinkSettings =
    MeshLinkSettingsBuilder().apply(block).build()

public class MeshLinkSettingsBuilder {
    public var appId: String = ""
    public var powerMode: PowerMode = PowerMode.MEDIUM
    public var regulatoryRegion: RegulatoryRegion = RegulatoryRegion.DEFAULT
    public var enableBackground: Boolean = false

    public var keyRotationInterval: Duration = 3.days
    public var keyRotationGracePeriod: Duration = 1.hours
    public var keyRotationCompromiseGracePeriod: Duration = Duration.ZERO

    public var transferMaxRetries: Int = 5
    public var transferChunkSize: Int = 256
    public var transferMaxTransfersPerPeer: Int = 3

    public var routeAdvertisementChangeThreshold: Int = 3
    public var routeDigestInterval: Duration = 5.minutes
    public var routeExpiry: Duration = 15.minutes
    public var maxRoutes: Int = 256

    public var diagnosticsEventBufferSize: Int = 1000
    public var diagnosticsEmitLog: Boolean = false

    public fun keyRotation(block: KeyRotationSettingsBuilder.() -> Unit) {
        KeyRotationSettingsBuilder().apply(block).also { builder ->
            keyRotationInterval = builder.interval
            keyRotationGracePeriod = builder.rotationGracePeriod
            keyRotationCompromiseGracePeriod = builder.compromiseGracePeriod
        }
    }

    public fun transfer(block: TransferSettingsBuilder.() -> Unit) {
        TransferSettingsBuilder().apply(block).also { builder ->
            transferMaxRetries = builder.maxRetries
            transferChunkSize = builder.chunkSize
            transferMaxTransfersPerPeer = builder.maxTransfersPerPeer
        }
    }

    public fun routing(block: RoutingSettingsBuilder.() -> Unit) {
        RoutingSettingsBuilder().apply(block).also { builder ->
            routeAdvertisementChangeThreshold = builder.routeAdvertisementChangeThreshold
            routeDigestInterval = builder.routeDigestInterval
            routeExpiry = builder.routeExpiry
            maxRoutes = builder.maxRoutes
        }
    }

    public fun diagnostics(block: DiagnosticsSettingsBuilder.() -> Unit) {
        DiagnosticsSettingsBuilder().apply(block).also { builder ->
            diagnosticsEventBufferSize = builder.eventBufferSize
            diagnosticsEmitLog = builder.emitLog
        }
    }

    public fun build(): MeshLinkSettings {
        require(appId.isNotBlank()) { "appId must not be blank" }
        require(appId.encodeToByteArray().size <= MAX_APP_ID_BYTES) {
            "appId must be at most 255 UTF-8 bytes"
        }
        require(keyRotationInterval > Duration.ZERO) { "key rotation interval must be positive" }
        require(keyRotationGracePeriod >= Duration.ZERO) {
            "key rotation grace period must not be negative"
        }
        require(keyRotationCompromiseGracePeriod >= Duration.ZERO) {
            "compromise grace period must not be negative"
        }
        require(transferMaxRetries >= 0) { "maxRetries must not be negative" }
        require(transferChunkSize > 0) { "chunkSize must be positive" }
        require(transferMaxTransfersPerPeer in MIN_TRANSFERS_PER_PEER..MAX_TRANSFERS_PER_PEER) {
            "maxTransfersPerPeer must be between 1 and 3"
        }
        require(routeAdvertisementChangeThreshold >= 0) {
            "routeAdvertisementChangeThreshold must not be negative"
        }
        require(routeDigestInterval > Duration.ZERO) { "routeDigestInterval must be positive" }
        require(routeExpiry > routeDigestInterval) {
            "routeExpiry must be greater than routeDigestInterval"
        }
        require(maxRoutes in MIN_ROUTES..MAX_ROUTES) { "maxRoutes must be between 1 and 256" }
        require(diagnosticsEventBufferSize > 0) { "eventBufferSize must be positive" }

        return MeshLinkSettings(
            appId = appId,
            powerMode = powerMode,
            regulatoryRegion = regulatoryRegion,
            enableBackground = enableBackground,
            keyRotation =
                KeyRotationSettings(
                    interval = keyRotationInterval,
                    rotationGracePeriod = keyRotationGracePeriod,
                    compromiseGracePeriod = keyRotationCompromiseGracePeriod,
                ),
            transfer =
                TransferSettings(
                    maxRetries = transferMaxRetries,
                    chunkSize = transferChunkSize,
                    maxTransfersPerPeer = transferMaxTransfersPerPeer,
                ),
            routing =
                RoutingSettings(
                    routeAdvertisementChangeThreshold = routeAdvertisementChangeThreshold,
                    routeDigestInterval = routeDigestInterval,
                    routeExpiry = routeExpiry,
                    maxRoutes = maxRoutes,
                ),
            diagnostics =
                DiagnosticsSettings(
                    eventBufferSize = diagnosticsEventBufferSize,
                    emitLog = diagnosticsEmitLog,
                ),
        )
    }
}

public class KeyRotationSettingsBuilder {
    public var interval: Duration = 3.days
    public var rotationGracePeriod: Duration = 1.hours
    public var compromiseGracePeriod: Duration = Duration.ZERO
}

public class TransferSettingsBuilder {
    public var maxRetries: Int = 5
    public var chunkSize: Int = 256
    public var maxTransfersPerPeer: Int = 3
}

public class RoutingSettingsBuilder {
    public var routeAdvertisementChangeThreshold: Int = 3
    public var routeDigestInterval: Duration = 5.minutes
    public var routeExpiry: Duration = 15.minutes
    public var maxRoutes: Int = 256
}

public class DiagnosticsSettingsBuilder {
    public var eventBufferSize: Int = 1000
    public var emitLog: Boolean = false
}

private const val MAX_APP_ID_BYTES: Int = 255
private const val MIN_TRANSFERS_PER_PEER: Int = 1
private const val MAX_TRANSFERS_PER_PEER: Int = 3
private const val MIN_ROUTES: Int = 1
private const val MAX_ROUTES: Int = 256
