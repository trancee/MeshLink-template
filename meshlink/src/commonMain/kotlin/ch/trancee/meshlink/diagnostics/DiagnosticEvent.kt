package ch.trancee.meshlink.diagnostics

import ch.trancee.meshlink.model.KeyRotationReason
import ch.trancee.meshlink.model.PeerIdentity
import ch.trancee.meshlink.model.PowerTier
import ch.trancee.meshlink.model.RegulatoryRegion
import ch.trancee.meshlink.model.SessionId
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

    /** Transfer session state transition. Emitted for every state change in [TransferStatus]. */
    @Serializable
    data class TransferSessionTransition(
        val sessionId: SessionId,
        val peerIdentity: PeerIdentity,
        val fromStatus: TransferStatus,
        val toStatus: TransferStatus,
        val bytesTransferred: Long,
        val totalBytes: Long,
        val timestamp: Instant = Clock.System.now(),
    ) : DiagnosticEvent
}

/** Frame types that appear on the wire. */
@Serializable
enum class FrameType {
    MESH_ENVELOPE,
    ROUTE_UPDATE,
    ROUTE_WITHDRAWAL,
    ROUTE_DIGEST,
    TRANSFER_CHUNK,
    TRANSFER_ACK,
    TRANSFER_CANCEL,
    KEY_ROTATION_ANNOUNCEMENT,
}

/** Why a frame failed to decrypt. */
@Serializable
enum class DecryptFailureReason {
    AUTHENTICATION_TAG_MISMATCH,
    REPLAY_DETECTED,
    SEQUENCE_NUMBER_MISMATCH,
    KEY_UNAVAILABLE,
    MALFORMED_FRAME,
}

/** Why the data plane fell back to GATT. */
@Serializable
enum class TransportFallbackReason {
    NO_PSM_ADVERTISED,
    L2CAP_CONNECT_FAILED,
    L2CAP_DROPPED_MID_TRANSFER,
    LOCAL_POLICY,
}

/** Data plane bearer in use. */
@Serializable
enum class DataPlaneBearer {
    GATT,
    L2CAP,
}

/** Handshake pattern used. */
@Serializable
enum class HandshakePattern {
    IX,
    NX,
}

/** Key rotation trigger reason. */
@Serializable
enum class KeyRotationReason {
    PERIODIC,
    MANUAL,
    SECURITY_EVENT,
}

/** Noise session layer. */
@Serializable
enum class NoiseLayer {
    PEER,
    MESH,
}

/** Noise link-layer session states. */
@Serializable
enum class NoiseSessionState {
    DISCONNECTED,
    HANDSHAKING_XX,
    HANDSHAKING_IK,
    ESTABLISHED,
    REKEYING,
    FAILED,
}

/** Role in Noise handshake. */
@Serializable
enum class NoiseRole {
    INITIATOR,
    RESPONDER,
}

/** Noise link-layer failure reasons. */
@Serializable
enum class NoiseFailureReason {
    HANDSHAKE_TIMEOUT,
    HANDSHAKE_MESSAGE_MALFORMED,
    HANDSHAKE_MESSAGE_OUT_OF_ORDER,
    REMOTE_STATIC_KEY_MISMATCH,
    REMOTE_STATIC_KEY_UNKNOWN,
    REKEY_REJECTED,
    TRANSPORT_CLOSED,
    MAX_RETRIES_EXCEEDED,
    INTERNAL_ERROR,
}

/** Transfer session status (public API). */
@Serializable
enum class TransferStatus {
    IN_PROGRESS,
    WAITING_FOR_ROUTE,
    RETRYING,
    COMPLETED,
    FAILED,
    TIMED_OUT,
}

/** SACK bitfield encoding mode. */
@Serializable
enum class SackEncoding {
    DYNAMIC,
    FIXED,
}
