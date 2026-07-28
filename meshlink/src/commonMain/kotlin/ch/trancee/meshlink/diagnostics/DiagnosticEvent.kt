package ch.trancee.meshlink.diagnostics

import ch.trancee.meshlink.model.DataPlaneBearer
import ch.trancee.meshlink.model.DecryptFailureReason
import ch.trancee.meshlink.model.DiagnosticSeverity
import ch.trancee.meshlink.model.FrameType
import ch.trancee.meshlink.model.HandshakePattern
import ch.trancee.meshlink.model.KeyRotationReason
import ch.trancee.meshlink.model.NoiseFailureReason
import ch.trancee.meshlink.model.NoiseLayer
import ch.trancee.meshlink.model.NoiseSessionState
import ch.trancee.meshlink.model.PeerIdentity
import ch.trancee.meshlink.model.PowerMode
import ch.trancee.meshlink.model.RegulatoryRegion
import ch.trancee.meshlink.model.SessionId
import ch.trancee.meshlink.model.TransferFailureReason
import ch.trancee.meshlink.model.TransferState
import ch.trancee.meshlink.model.TransportFallbackReason
import ch.trancee.meshlink.model.VerificationLevel
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Unified diagnostic event schema for MeshLink. All layers (routing, transport, transfer, power,
 * crypto) emit these events.
 *
 * Consumers receive events via the `eventCallback` in `MeshLinkSettings.diagnostics` (see SPEC.md
 * §14) and may log them when `emitToLog` is true.
 *
 * SPEC-ANCHOR: diagnostic-event
 */
public sealed interface DiagnosticEvent {

    /**
     * Category of this event (e.g. "route", "transport", "transfer", "power", "handshake",
     * "key_rotation", "noise").
     */
    public val category: String

    /** Severity level for this event. */
    public val severity: DiagnosticSeverity

    /** Structured payload for logging (key=value pairs or JSON). */
    public val payload: String

    /** When this event occurred. */
    public val timestamp: Instant

    /** A routed frame failed to decrypt at the link layer. */
    public data class RouteDecryptFailureEvent(
        public val peerIdentity: PeerIdentity,
        public val frameType: FrameType,
        public val failureReason: DecryptFailureReason,
        override val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val category: String = "route"
        override val severity: DiagnosticSeverity = DiagnosticSeverity.WARN
        override val payload: String =
            "peerIdentity=${peerIdentity} frameType=$frameType failureReason=$failureReason"
    }

    /** Data plane fell back from L2CAP CoC to GATT. */
    public data class TransportFallbackEvent(
        public val peerIdentity: PeerIdentity,
        public val reason: TransportFallbackReason,
        override val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val category: String = "transport"
        override val severity: DiagnosticSeverity = DiagnosticSeverity.WARN
        override val payload: String = "peerIdentity=${peerIdentity} reason=$reason"
    }

    /** Data plane bearer selected for a transfer session. */
    public data class TransferDataPlaneBearerEvent(
        public val sessionId: SessionId,
        public val bearer: DataPlaneBearer,
        override val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val category: String = "transfer"
        override val severity: DiagnosticSeverity = DiagnosticSeverity.INFO
        override val payload: String = "sessionId=${sessionId} bearer=$bearer"
    }

    /** Effective power mode parameters after regulatory clamping. */
    public data class PowerModeEffectiveEvent(
        public val requestedMode: PowerMode,
        public val effectiveMode: PowerMode,
        public val regulatoryRegion: RegulatoryRegion,
        public val scanDutyCyclePercent: Int,
        public val advertisementIntervalMs: Int,
        public val connectionIntervalMs: Double,
        override val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val category: String = "power"
        override val severity: DiagnosticSeverity = DiagnosticSeverity.INFO
        override val payload: String =
            "requestedMode=$requestedMode effectiveMode=$effectiveMode " +
                "region=$regulatoryRegion scanDuty=$scanDutyCyclePercent " +
                "advInterval=$advertisementIntervalMs connInterval=$connectionIntervalMs"
    }

    /** End-to-end or link-layer handshake completed or failed. */
    public data class HandshakeEvent(
        public val sessionId: SessionId,
        public val pattern: HandshakePattern,
        public val fallbackUsed: Boolean,
        public val verificationLevel: VerificationLevel,
        public val rateLimitAttempts: Int,
        public val nonceReplayDetected: Boolean,
        override val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val category: String = "handshake"
        override val severity: DiagnosticSeverity =
            if (verificationLevel == VerificationLevel.NONE) DiagnosticSeverity.ERROR
            else DiagnosticSeverity.INFO
        override val payload: String =
            "sessionId=${sessionId} pattern=$pattern fallbackUsed=$fallbackUsed " +
                "verificationLevel=$verificationLevel rateLimitAttempts=$rateLimitAttempts " +
                "nonceReplayDetected=$nonceReplayDetected"
    }

    /** Key rotation announcement processed by a peer. */
    public data class KeyRotationEvent(
        public val peerIdentity: PeerIdentity,
        public val reason: KeyRotationReason,
        public val oldKeyVerified: Boolean,
        override val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val category: String = "key_rotation"
        override val severity: DiagnosticSeverity = DiagnosticSeverity.INFO
        override val payload: String =
            "peerIdentity=${peerIdentity} reason=$reason oldKeyVerified=$oldKeyVerified"
    }

    /** Noise session established or failed. */
    public data class NoiseSessionEvent(
        public val sessionId: SessionId,
        public val layer: NoiseLayer,
        public val fromState: NoiseSessionState,
        public val toState: NoiseSessionState,
        public val reason: NoiseFailureReason?,
        override val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val category: String = "noise"
        override val severity: DiagnosticSeverity =
            if (toState == NoiseSessionState.FAILED) DiagnosticSeverity.ERROR
            else DiagnosticSeverity.INFO
        override val payload: String =
            "sessionId=${sessionId} layer=$layer fromState=$fromState " +
                "toState=$toState reason=$reason"
    }

    /** Transfer session started, progress, or completed. */
    public data class TransferSessionEvent(
        public val sessionId: SessionId,
        public val peerIdentity: PeerIdentity,
        public val state: TransferState,
        public val reason: TransferFailureReason?,
        override val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val category: String = "transfer"
        override val severity: DiagnosticSeverity =
            when (state) {
                TransferState.COMPLETED -> DiagnosticSeverity.INFO
                TransferState.FAILED,
                TransferState.TIMED_OUT -> DiagnosticSeverity.ERROR
                else -> DiagnosticSeverity.INFO
            }
        override val payload: String =
            "sessionId=${sessionId} peerIdentity=${peerIdentity} " + "state=$state reason=$reason"
    }
}
