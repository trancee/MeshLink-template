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

    /**
     * A routed frame failed to decrypt at the link layer. Indicates either corruption, replay
     * attack, or key mismatch.
     */
    @Serializable
    data class RouteDecryptFailure(
        val peerIdentity: PeerIdentity,
        val frameType: FrameType,
        val failureReason: DecryptFailureReason,
        val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent

    /** Data plane fell back from L2CAP CoC to GATT. Emitted once per peer when fallback occurs. */
    @Serializable
    data class TransportFallback(
        val peerIdentity: PeerIdentity,
        val reason: TransportFallbackReason,
        val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent

    /**
     * Data plane bearer selected for a transfer session. Emitted at session start and on any
     * migration.
     */
    @Serializable
    data class TransferDataPlaneBearer(
        val sessionId: SessionId,
        val bearer: DataPlaneBearer,
        val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent

    /**
     * Effective power tier parameters after regulatory clamping. Emitted on tier change and at
     * startup.
     */
    @Serializable
    data class PowerTierEffective(
        val requestedTier: PowerTier,
        val effectiveTier: PowerTier,
        val regulatoryRegion: RegulatoryRegion,
        val scanDutyCyclePercent: Int,
        val advertisementIntervalMs: Int,
        val connectionIntervalMs: Int,
        val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent

    /**
     * End-to-end handshake completed or failed. Emitted for both IX (key known) and NX (fallback)
     * patterns.
     */
    @Serializable
    data class E2EHandshake(
        val sessionId: SessionId,
        val pattern: HandshakePattern,
        val fallbackUsed: Boolean,
        val fullPublicKeyVerified: Boolean,
        val rateLimitAttempts: Int,
        val nonceReplayDetected: Boolean,
        val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent

    /**
     * Key rotation announcement processed. Emitted by both initiator (sent) and receiver
     * (verified).
     */
    @Serializable
    data class KeyRotation(
        val peerIdentity: PeerIdentity,
        val oldKeyVerified: Boolean,
        val sequenceNumberReset: Boolean,
        val propagationDeadlineMet: Boolean,
        val reason: KeyRotationReason,
        val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent

    /** Noise session state transition. Emitted for every state change in [NoiseSessionState]. */
    @Serializable
    data class NoiseSessionTransition(
        val peerIdentity: PeerIdentity,
        val layer: NoiseLayer,
        val fromState: NoiseSessionState,
        val toState: NoiseSessionState,
        val role: NoiseRole,
        val handshakePattern: HandshakePattern,
        val failureReason: NoiseFailureReason?,
        val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent

    /**
     * Route table digest mismatch triggered a full resync. Emitted when receiving a peer's
     * advertisement with different digest.
     */
    @Serializable
    data class RouteDigestMismatch(
        val peerIdentity: PeerIdentity,
        val localDigest: UInt,
        val remoteDigest: UInt,
        val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent

    /** Transfer session state transition. Emitted for every state change in [TransferState]. */
    @Serializable
    data class TransferSessionTransition(
        val sessionId: SessionId,
        val peerIdentity: PeerIdentity,
        val fromState: TransferState,
        val toState: TransferState,
        val bytesTransferred: Long,
        val totalBytes: Long,
        val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent
}
