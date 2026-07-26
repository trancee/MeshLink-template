package ch.trancee.meshlink.diagnostics

import ch.trancee.meshlink.model.DataPlaneBearer
import ch.trancee.meshlink.model.DecryptFailureReason
import ch.trancee.meshlink.model.FrameType
import ch.trancee.meshlink.model.HandshakePattern
import ch.trancee.meshlink.model.KeyRotationReason
import ch.trancee.meshlink.model.NoiseFailureReason
import ch.trancee.meshlink.model.NoiseLayer
import ch.trancee.meshlink.model.NoiseRole
import ch.trancee.meshlink.model.NoiseSessionState
import ch.trancee.meshlink.model.PeerIdentity
import ch.trancee.meshlink.model.PowerTier
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

    /** A routed frame failed to decrypt at the link layer. */
    public data class RouteDecryptFailureEvent(
        public val peerIdentity: PeerIdentity,
        public val frameType: FrameType,
        public val failureReason: DecryptFailureReason,
        public val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent

    /** Data plane fell back from L2CAP CoC to GATT. */
    public data class TransportFallbackEvent(
        public val peerIdentity: PeerIdentity,
        public val reason: TransportFallbackReason,
        public val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent

    /** Data plane bearer selected for a transfer session. */
    public data class TransferDataPlaneBearerEvent(
        public val sessionId: SessionId,
        public val bearer: DataPlaneBearer,
        public val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent

    /** Effective power tier parameters after regulatory clamping. */
    public data class PowerTierEffectiveEvent(
        public val requestedTier: PowerTier,
        public val effectiveTier: PowerTier,
        public val regulatoryRegion: RegulatoryRegion,
        public val scanDutyCyclePercent: Int,
        public val advertisementIntervalMs: Int,
        public val connectionIntervalMs: Double,
        public val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent

    /** End-to-end or link-layer handshake completed or failed. */
    public data class HandshakeEvent(
        public val sessionId: SessionId,
        public val pattern: HandshakePattern,
        public val fallbackUsed: Boolean,
        public val verificationLevel: VerificationLevel,
        public val rateLimitAttempts: Int,
        public val nonceReplayDetected: Boolean,
        public val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent

    /** Key rotation announcement processed by a peer. */
    public data class KeyRotationEvent(
        public val peerIdentity: PeerIdentity,
        public val oldKeyVerified: Boolean,
        public val sequenceNumberReset: Boolean,
        public val propagationDeadlineMet: Boolean,
        public val reason: KeyRotationReason,
        public val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent

    /** Noise session state transition. */
    public data class NoiseSessionTransitionEvent(
        public val peerIdentity: PeerIdentity,
        public val layer: NoiseLayer,
        public val fromState: NoiseSessionState,
        public val toState: NoiseSessionState,
        public val role: NoiseRole,
        public val handshakePattern: HandshakePattern,
        public val failureReason: NoiseFailureReason?,
        public val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent

    /** Route table digest mismatch triggered a full resync. */
    public data class RouteDigestMismatchEvent(
        public val peerIdentity: PeerIdentity,
        public val localDigest: UInt,
        public val remoteDigest: UInt,
        public val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent

    /** Transfer session state transition. */
    public data class TransferSessionTransitionEvent(
        public val sessionId: SessionId,
        public val peerIdentity: PeerIdentity,
        public val fromState: TransferState,
        public val toState: TransferState,
        public val bytesTransferred: Long,
        public val totalBytes: Long,
        public val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent

    /** Transfer session reached a terminal failure state. */
    public data class TransferFailureEvent(
        public val sessionId: SessionId,
        public val peerIdentity: PeerIdentity,
        public val reason: TransferFailureReason,
        public val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent
}
