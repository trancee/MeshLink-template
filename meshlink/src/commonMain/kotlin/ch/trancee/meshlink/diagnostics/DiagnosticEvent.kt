package ch.trancee.meshlink.diagnostics

import ch.trancee.meshlink.model.DecryptFailureReason
import ch.trancee.meshlink.model.DiagnosticSeverity
import ch.trancee.meshlink.model.HandshakePattern
import ch.trancee.meshlink.model.KeyRotationReason
import ch.trancee.meshlink.model.NoiseFailureReason
import ch.trancee.meshlink.model.NoiseLayer
import ch.trancee.meshlink.model.NoiseRole
import ch.trancee.meshlink.model.NoiseSessionState
import ch.trancee.meshlink.model.PeerIdentity
import ch.trancee.meshlink.model.PowerMode
import ch.trancee.meshlink.model.RegulatoryRegion
import ch.trancee.meshlink.model.TransferFailureReason
import ch.trancee.meshlink.model.TransferId
import ch.trancee.meshlink.model.TransferState
import ch.trancee.meshlink.model.TransportFallbackReason
import ch.trancee.meshlink.model.TransportLayer
import ch.trancee.meshlink.model.VerificationLevel
import kotlin.jvm.JvmInline
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Diagnostic events for observability.
 *
 * SPEC-ANCHOR: diagnostic-event
 */
@JvmInline public value class DiagnosticCode(public val value: UShort)

@JvmInline public value class HandshakeId(public val value: UInt)

@JvmInline public value class NoiseSessionId(public val value: UInt)

public object DiagnosticCodes {
    public val ROUTE_DECRYPTION_FAILED: DiagnosticCode = DiagnosticCode(0x0501u)
    public val TRANSPORT_FALLBACK: DiagnosticCode = DiagnosticCode(0x0901u)
    public val TRANSFER_BEARER: DiagnosticCode = DiagnosticCode(0x0601u)
    public val POWER_MODE_EFFECTIVE: DiagnosticCode = DiagnosticCode(0x0101u)
    public val HANDSHAKE: DiagnosticCode = DiagnosticCode(0x0401u)
    public val KEY_ROTATION: DiagnosticCode = DiagnosticCode(0x0402u)
    public val NOISE_SESSION: DiagnosticCode = DiagnosticCode(0x0403u)
    public val ROUTE_DIGEST_MISMATCH: DiagnosticCode = DiagnosticCode(0x0502u)
    public val TRANSFER_STATE: DiagnosticCode = DiagnosticCode(0x0602u)
    public val TRANSFER_FAILURE: DiagnosticCode = DiagnosticCode(0x0603u)
}

public sealed interface DiagnosticEvent {
    public val code: DiagnosticCode
    public val severity: DiagnosticSeverity
    public val occurredAt: Instant

    public data class RouteDecryptFailureEvent(
        public val peerIdentity: PeerIdentity,
        public val frameType: UByte,
        public val failureReason: DecryptFailureReason,
        override val occurredAt: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val code: DiagnosticCode = DiagnosticCodes.ROUTE_DECRYPTION_FAILED
        override val severity: DiagnosticSeverity = DiagnosticSeverity.ERROR
    }

    public data class TransportFallbackEvent(
        public val peerIdentity: PeerIdentity,
        public val reason: TransportFallbackReason,
        override val occurredAt: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val code: DiagnosticCode = DiagnosticCodes.TRANSPORT_FALLBACK
        override val severity: DiagnosticSeverity = DiagnosticSeverity.WARN
    }

    public data class TransportLayerEvent(
        public val id: TransferId,
        public val transport: TransportLayer,
        override val occurredAt: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val code: DiagnosticCode = DiagnosticCodes.TRANSFER_BEARER
        override val severity: DiagnosticSeverity = DiagnosticSeverity.INFO
    }

    public data class PowerModeEffectiveEvent(
        public val requestedMode: PowerMode,
        public val effectiveMode: PowerMode,
        public val regulatoryRegion: RegulatoryRegion,
        public val scanDutyCycle: Int,
        public val advertisementInterval: Duration,
        public val activeConnectionInterval: Duration,
        public val idleConnectionInterval: Duration,
        public val idleTransitionDelay: Duration,
        public val concurrentConnectionLimit: Int,
        public val chunkSize: Int,
        public val retryLimit: Int,
        public val retryBudget: Duration,
        public val disconnectGracePeriod: Duration,
        override val occurredAt: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val code: DiagnosticCode = DiagnosticCodes.POWER_MODE_EFFECTIVE
        override val severity: DiagnosticSeverity = DiagnosticSeverity.INFO
    }

    public data class HandshakeEvent(
        public val id: HandshakeId,
        public val pattern: HandshakePattern,
        public val verificationLevel: VerificationLevel,
        public val nonceReplayDetected: Boolean,
        override val occurredAt: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val code: DiagnosticCode = DiagnosticCodes.HANDSHAKE
        override val severity: DiagnosticSeverity =
            if (verificationLevel == VerificationLevel.NONE) {
                DiagnosticSeverity.ERROR
            } else {
                DiagnosticSeverity.INFO
            }
    }

    public data class KeyRotationEvent(
        public val peerIdentity: PeerIdentity,
        public val oldGeneration: UInt,
        public val newGeneration: UInt,
        public val reason: KeyRotationReason,
        public val continuityVerified: Boolean,
        public val conflictDetected: Boolean,
        public val propagationDeadlineMet: Boolean,
        override val occurredAt: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val code: DiagnosticCode = DiagnosticCodes.KEY_ROTATION
        override val severity: DiagnosticSeverity =
            if (continuityVerified && !conflictDetected) {
                DiagnosticSeverity.INFO
            } else {
                DiagnosticSeverity.ERROR
            }
    }

    public data class NoiseSessionEvent(
        public val id: NoiseSessionId,
        public val peerIdentity: PeerIdentity,
        public val layer: NoiseLayer,
        public val role: NoiseRole,
        public val pattern: HandshakePattern,
        public val fromState: NoiseSessionState,
        public val toState: NoiseSessionState,
        public val failureReason: NoiseFailureReason?,
        override val occurredAt: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val code: DiagnosticCode = DiagnosticCodes.NOISE_SESSION
        override val severity: DiagnosticSeverity =
            if (toState == NoiseSessionState.FAILED) {
                DiagnosticSeverity.ERROR
            } else {
                DiagnosticSeverity.INFO
            }
    }

    public data class RouteDigestMismatchEvent(
        public val peerIdentity: PeerIdentity,
        public val localDigest: ULong,
        public val remoteDigest: ULong,
        override val occurredAt: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val code: DiagnosticCode = DiagnosticCodes.ROUTE_DIGEST_MISMATCH
        override val severity: DiagnosticSeverity = DiagnosticSeverity.WARN
    }

    public data class TransferSessionTransitionEvent(
        public val id: TransferId,
        public val peerIdentity: PeerIdentity,
        public val state: TransferState,
        public val offset: Long,
        public val total: Long,
        public val reason: TransferFailureReason?,
        override val occurredAt: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val code: DiagnosticCode = DiagnosticCodes.TRANSFER_STATE
        override val severity: DiagnosticSeverity =
            when (state) {
                TransferState.FAILED,
                TransferState.EXPIRED -> DiagnosticSeverity.ERROR
                else -> DiagnosticSeverity.INFO
            }
    }

    public data class TransferFailureEvent(
        public val id: TransferId,
        public val peerIdentity: PeerIdentity,
        public val reason: TransferFailureReason,
        override val occurredAt: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val code: DiagnosticCode = DiagnosticCodes.TRANSFER_FAILURE
        override val severity: DiagnosticSeverity = DiagnosticSeverity.ERROR
    }
}
