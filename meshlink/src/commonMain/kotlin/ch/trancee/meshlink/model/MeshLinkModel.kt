package ch.trancee.meshlink.model

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Common enums shared across layers.  These live in model/ so they are not
// co-located with the diagnostic event hierarchy.
// ---------------------------------------------------------------------------

/** Distinguishes Ed25519 (identity/signing) keys from X25519 (DH) keys. */
@Serializable
enum class KeyType {
    ED25519,
    X25519,
}

/** Reason a key rotation was triggered. */
@Serializable
enum class KeyRotationReason {
    PERIODIC,
    MANUAL,
    SECURITY_EVENT,
}

/** Noise handshake pattern used for link-layer and end-to-end sessions. */
@Serializable
enum class HandshakePattern {
    XX,
    IK,
    IX,
    NX,
}

/** Scoreboard bitfield encoding strategy for selective acknowledgment. */
@Serializable
enum class ScoreboardEncoding {
    DYNAMIC,
    FIXED,
}

/** Message priority that affects routing behavior and TTL. */
@Serializable
enum class Priority {
    HIGH,
    NORMAL,
    LOW,
}

// ---------------------------------------------------------------------------
// Wire-layer types
// ---------------------------------------------------------------------------

/** Frame type that appears on the wire. */
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

// ---------------------------------------------------------------------------
// Security / crypto types
// ---------------------------------------------------------------------------

/** Why a routed frame failed to decrypt at the link layer. */
@Serializable
enum class DecryptFailureReason {
    AUTHENTICATION_TAG_MISMATCH,
    REPLAY_DETECTED,
    SEQUENCE_NUMBER_MISMATCH,
    KEY_UNAVAILABLE,
    MALFORMED_FRAME,
}

// ---------------------------------------------------------------------------
// Transport types
// ---------------------------------------------------------------------------

/** Why the data plane fell back from L2CAP CoC to GATT. */
@Serializable
enum class TransportFallbackReason {
    NO_PSM_ADVERTISED,
    L2CAP_CONNECT_FAILED,
    L2CAP_DROPPED_MID_TRANSFER,
    LOCAL_POLICY,
}

/** Data plane bearer in use for a transfer session. */
@Serializable
enum class DataPlaneBearer {
    GATT,
    L2CAP,
}

// ---------------------------------------------------------------------------
// Noise session state-machine types
// ---------------------------------------------------------------------------

/** Which layer of the mesh a Noise session belongs to. */
@Serializable
enum class NoiseLayer {
    HOP_BY_HOP,
    END_TO_END,
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

/** Role in a Noise handshake. */
@Serializable
enum class NoiseRole {
    INITIATOR,
    RESPONDER,
}

/** Reason a Noise handshake failed. */
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
