package ch.trancee.meshlink.diagnostics

import ch.trancee.meshlink.model.DataPlaneBearer
import ch.trancee.meshlink.model.DecryptFailureReason
import ch.trancee.meshlink.model.DiagnosticSeverity
import ch.trancee.meshlink.model.FrameType
import ch.trancee.meshlink.model.HandshakePattern
import ch.trancee.meshlink.model.KeyRotationReason
import ch.trancee.meshlink.model.NoiseFailureReason
import ch.trancee.meshlink.model.NoiseLayer
import ch.trancee.meshlink.model.NoiseRole
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
            "peerIdentity=${peerIdentity.hex} frameType=$frameType failureReason=$failureReason"
    }

    /** Data plane fell back from L2CAP CoC to GATT. */
    public data class TransportFallbackEvent(
        public val peerIdentity: PeerIdentity,
        public val reason: TransportFallbackReason,
        override val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val category: String = "transport"
        override val severity: DiagnosticSeverity = DiagnosticSeverity.WARN
        override val payload: String = "peerIdentity=${peerIdentity.hex} reason=$reason"
    }

    /** Data plane bearer selected for a transfer session. */
    public data class TransferDataPlaneBearerEvent(
        public val sessionId: SessionId,
        public val bearer: DataPlaneBearer,
        override val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val category: String = "transfer"
        override val severity: DiagnosticSeverity = DiagnosticSeverity.INFO
        override val payload: String = "sessionId=${sessionId.raw} bearer=$bearer"
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
            "sessionId=${sessionId.raw} pattern=$pattern fallbackUsed=$fallbackUsed " +
                "verificationLevel=$verificationLevel rateLimitAttempts=$rateLimitAttempts " +
                "nonceReplayDetected=$nonceReplayDetected"
    }

    /** Key rotation announcement processed by a peer. */
    public data class KeyRotationEvent(
        public val peerIdentity: PeerIdentity,
        public val oldKeyVerified: Boolean,
        public val sequenceNumberReset: Boolean,
        public val propagationDeadlineMet: Boolean,
        public val reason: KeyRotationReason,
        override val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val category: String = "key_rotation"
        override val severity: DiagnosticSeverity =
            if (oldKeyVerified) DiagnosticSeverity.INFO else DiagnosticSeverity.ERROR
        override val payload: String =
            "peerIdentity=${peerIdentity.hex} oldKeyVerified=$oldKeyVerified " +
                "seqNoReset=$sequenceNumberReset propagationDeadlineMet=$propagationDeadlineMet " +
                "reason=$reason"
    }

    /** Noise session state transition. */
    public data class NoiseSessionTransitionEvent(
        public val peerIdentity: PeerIdentity,
        public val layer: NoiseLayer,
        public val fromState: NoiseSessionState,
        public val toState: NoiseSessionState,
        public val role: NoiseRole,
        public val handshakePattern: HandshakePattern,
        public val failureReason: NoiseFailureReason?,
        override val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val category: String = "noise"
        override val severity: DiagnosticSeverity =
            if (toState == NoiseSessionState.FAILED) DiagnosticSeverity.ERROR
            else DiagnosticSeverity.INFO
        override val payload: String =
            "peerIdentity=${peerIdentity.hex} layer=$layer fromState=$fromState " +
                "toState=$toState role=$role pattern=$handshakePattern failureReason=$failureReason"
    }

    /** Route table digest mismatch triggered a full resync. */
    public data class RouteDigestMismatchEvent(
        public val peerIdentity: PeerIdentity,
        public val localDigest: UInt,
        public val remoteDigest: UInt,
        override val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val category: String = "route"
        override val severity: DiagnosticSeverity = DiagnosticSeverity.WARN
        override val payload: String =
            "peerIdentity=${peerIdentity.hex} localDigest=$localDigest remoteDigest=$remoteDigest"
    }

    /** Transfer session state transition. */
    public data class TransferSessionTransitionEvent(
        public val sessionId: SessionId,
        public val peerIdentity: PeerIdentity,
        public val fromState: TransferState,
        public val toState: TransferState,
        public val bytesTransferred: Long,
        public val totalBytes: Long,
        override val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val category: String = "transfer"
        override val severity: DiagnosticSeverity =
            if (toState.name.contains("FAILED") || toState.name.contains("TIME_OUT"))
                DiagnosticSeverity.ERROR
            else DiagnosticSeverity.INFO
        override val payload: String =
            "sessionId=${sessionId.raw} peerIdentity=${peerIdentity.hex} " +
                "fromState=$fromState toState=$toState " +
                "bytesTransferred=$bytesTransferred totalBytes=$totalBytes"
    }

    /** Transfer session reached a terminal failure state. */
    public data class TransferFailureEvent(
        public val sessionId: SessionId,
        public val peerIdentity: PeerIdentity,
        public val reason: TransferFailureReason,
        override val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent {
        override val category: String = "transfer"
        override val severity: DiagnosticSeverity = DiagnosticSeverity.ERROR
        override val payload: String =
            "sessionId=${sessionId.raw} peerIdentity=${peerIdentity.hex} reason=$reason"
    }
}
