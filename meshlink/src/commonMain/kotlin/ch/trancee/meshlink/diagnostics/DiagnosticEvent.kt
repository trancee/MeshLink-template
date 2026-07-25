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
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Unified diagnostic event schema for MeshLink. All layers (routing, transport, transfer, power,
 * crypto) emit these events. Consumed by [DiagnosticsConfig.eventCallback] and logged when
 * [DiagnosticsConfig.emitToLog] is true.
 */
@Serializable
sealed interface DiagnosticEvent {

    /** A routed frame failed to decrypt at the link layer. */
    @Serializable
    data class RouteDecryptFailureEvent(
        val peerIdentity: PeerIdentity,
        val frameType: FrameType,
        val failureReason: DecryptFailureReason,
        val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent

    /** Data plane fell back from L2CAP CoC to GATT. */
    @Serializable
    data class TransportFallbackEvent(
        val peerIdentity: PeerIdentity,
        val reason: TransportFallbackReason,
        val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent

    /** Data plane bearer selected for a transfer session. */
    @Serializable
    data class TransferDataPlaneBearerEvent(
        val sessionId: SessionId,
        val bearer: DataPlaneBearer,
        val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent

    /** Effective power tier parameters after regulatory clamping. */
    @Serializable
    data class PowerTierEffectiveEvent(
        val requestedTier: PowerTier,
        val effectiveTier: PowerTier,
        val regulatoryRegion: RegulatoryRegion,
        val scanDutyCyclePercent: Int,
        val advertisementIntervalMs: Int,
        val connectionIntervalMs: Double,
        val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent

    /** End-to-end or link-layer handshake completed or failed. */
    @Serializable
    data class HandshakeEvent(
        val sessionId: SessionId,
        val pattern: HandshakePattern,
        val fallbackUsed: Boolean,
        val fullPublicKeyVerified: Boolean,
        val rateLimitAttempts: Int,
        val nonceReplayDetected: Boolean,
        val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent

    /** Key rotation announcement processed by a peer. */
    @Serializable
    data class KeyRotationEvent(
        val peerIdentity: PeerIdentity,
        val oldKeyVerified: Boolean,
        val sequenceNumberReset: Boolean,
        val propagationDeadlineMet: Boolean,
        val reason: KeyRotationReason,
        val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent

    /** Noise session state transition. */
    @Serializable
    data class NoiseSessionTransitionEvent(
        val peerIdentity: PeerIdentity,
        val layer: NoiseLayer,
        val fromState: NoiseSessionState,
        val toState: NoiseSessionState,
        val role: NoiseRole,
        val handshakePattern: HandshakePattern,
        val failureReason: NoiseFailureReason?,
        val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent

    /** Route table digest mismatch triggered a full resync. */
    @Serializable
    data class RouteDigestMismatchEvent(
        val peerIdentity: PeerIdentity,
        val localDigest: UInt,
        val remoteDigest: UInt,
        val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent

    /** Transfer session state transition. */
    @Serializable
    data class TransferSessionTransitionEvent(
        val sessionId: SessionId,
        val peerIdentity: PeerIdentity,
        val fromState: TransferState,
        val toState: TransferState,
        val bytesTransferred: Long,
        val totalBytes: Long,
        val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent

    /** Transfer session reached a terminal failure state. */
    @Serializable
    data class TransferFailureEvent(
        val sessionId: SessionId,
        val peerIdentity: PeerIdentity,
        val reason: TransferFailureReason,
        val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent
}
