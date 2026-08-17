package ch.trancee.meshlink.diagnostics

import ch.trancee.meshlink.model.Bearer
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
import ch.trancee.meshlink.model.TransferId
import ch.trancee.meshlink.model.TransferResult
import ch.trancee.meshlink.model.TransferState
import ch.trancee.meshlink.model.TransportFallbackReason
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
@JvmInline public value class DiagnosticCode(public val value: UShort) {
    public companion object {
        public val ROUTE_DECRYPTION_FAILED: DiagnosticCode = DiagnosticCode(0x8501u)
        public val TRANSPORT_FALLBACK: DiagnosticCode = DiagnosticCode(0x8901u)
        public val TRANSFER_BEARER: DiagnosticCode = DiagnosticCode(0x8601u)
        public val POWER_MODE_SETTINGS: DiagnosticCode = DiagnosticCode(0x8101u)
        public val HANDSHAKE: DiagnosticCode = DiagnosticCode(0x8401u)
        public val KEY_ROTATION: DiagnosticCode = DiagnosticCode(0x8402u)
        public val NOISE_SESSION: DiagnosticCode = DiagnosticCode(0x8403u)
        public val ROUTE_DIGEST_MISMATCH: DiagnosticCode = DiagnosticCode(0x8502u)
        public val TRANSFER_STATE: DiagnosticCode = DiagnosticCode(0x8602u)
        public val TRANSFER_FAILURE: DiagnosticCode = DiagnosticCode(0x8603u)
    }
}

@JvmInline public value class HandshakeId(public val value: UInt)

@JvmInline public value class NoiseSessionId(public val value: UInt)


public sealed interface DiagnosticEvent {
    public val code: DiagnosticCode
    public val severity: DiagnosticSeverity
    public val occurredAt: Instant

    public data class RouteDecryptFailureEvent(
        public val identity: PeerIdentity,
        public val frameType: UByte,
        public val failureReason: DecryptFailureReason,
        override val occurredAt: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val code: DiagnosticCode = DiagnosticCode.ROUTE_DECRYPTION_FAILED
        override val severity: DiagnosticSeverity = DiagnosticSeverity.ERROR
    }

    public data class TransportFallbackEvent(
        public val identity: PeerIdentity,
        public val reason: TransportFallbackReason,
        override val occurredAt: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val code: DiagnosticCode = DiagnosticCode.TRANSPORT_FALLBACK
        override val severity: DiagnosticSeverity = DiagnosticSeverity.WARN
    }

    public data class TransferBearerEvent(
        public val id: TransferId,
        public val bearer: Bearer,
        override val occurredAt: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val code: DiagnosticCode = DiagnosticCode.TRANSFER_BEARER
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
        override val code: DiagnosticCode = DiagnosticCode.POWER_MODE_SETTINGS
        override val severity: DiagnosticSeverity = DiagnosticSeverity.INFO
    }

    public data class HandshakeEvent(
        public val id: HandshakeId,
        public val pattern: HandshakePattern,
        public val verificationLevel: VerificationLevel,
        public val nonceReplayDetected: Boolean,
        override val occurredAt: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val code: DiagnosticCode = DiagnosticCode.HANDSHAKE
        override val severity: DiagnosticSeverity =
            if (verificationLevel == VerificationLevel.NONE) {
                DiagnosticSeverity.ERROR
            } else {
                DiagnosticSeverity.INFO
            }
    }

    public data class KeyRotationEvent(
        public val identity: PeerIdentity,
        public val oldGeneration: UInt,
        public val newGeneration: UInt,
        public val reason: KeyRotationReason,
        public val continuityVerified: Boolean,
        public val conflictDetected: Boolean,
        public val propagationDeadlineMet: Boolean,
        override val occurredAt: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val code: DiagnosticCode = DiagnosticCode.KEY_ROTATION
        override val severity: DiagnosticSeverity =
            if (continuityVerified && !conflictDetected) {
                DiagnosticSeverity.INFO
            } else {
                DiagnosticSeverity.ERROR
            }
    }

    public data class NoiseSessionEvent(
        public val id: NoiseSessionId,
        public val identity: PeerIdentity,
        public val layer: NoiseLayer,
        public val role: NoiseRole,
        public val pattern: HandshakePattern,
        public val fromState: NoiseSessionState,
        public val toState: NoiseSessionState,
        public val failureReason: NoiseFailureReason?,
        override val occurredAt: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val code: DiagnosticCode = DiagnosticCode.NOISE_SESSION
        override val severity: DiagnosticSeverity =
            if (toState == NoiseSessionState.FAILED) {
                DiagnosticSeverity.ERROR
            } else {
                DiagnosticSeverity.INFO
            }
    }

    public data class RouteDigestMismatchEvent(
        public val identity: PeerIdentity,
        public val localDigest: ULong,
        public val remoteDigest: ULong,
        override val occurredAt: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val code: DiagnosticCode = DiagnosticCode.ROUTE_DIGEST_MISMATCH
        override val severity: DiagnosticSeverity = DiagnosticSeverity.WARN
    }

    public data class TransferSessionTransitionEvent(
        public val id: TransferId,
        public val identity: PeerIdentity,
        public val state: TransferState,
        public val offset: Long,
        public val total: Long,
        public val result: TransferResult?,
        override val occurredAt: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val code: DiagnosticCode = DiagnosticCode.TRANSFER_STATE
        override val severity: DiagnosticSeverity =
            when (result) {
                null -> DiagnosticSeverity.INFO
                is TransferResult.UnrecoverableFailure -> DiagnosticSeverity.ERROR
                is TransferResult.TrustFailure -> DiagnosticSeverity.ERROR
                TransferResult.Completed,
                TransferResult.Cancelled,
                TransferResult.Expired -> DiagnosticSeverity.INFO
            }
    }

    public data class TransferFailureEvent(
        public val id: TransferId,
        public val identity: PeerIdentity,
        public val result: TransferResult,
        override val occurredAt: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val code: DiagnosticCode = DiagnosticCode.TRANSFER_FAILURE
        override val severity: DiagnosticSeverity = DiagnosticSeverity.ERROR
    }
}
