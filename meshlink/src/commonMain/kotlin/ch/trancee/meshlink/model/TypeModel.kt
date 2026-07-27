package ch.trancee.meshlink.model

// ---------------------------------------------------------------------------
// Common enums shared across layers.  These live in model/ so they are not
// co-located with the diagnostic event hierarchy.
// ---------------------------------------------------------------------------
// SPEC-ANCHOR: type-model

/** Verification level achieved during handshake. */
public enum class VerificationLevel {
    /** Full 64-byte public key verified against routing table. */
    FULL,
    /** TOFU-first-contact: key pinned on first successful handshake. */
    TOFU_PIN,
    /** NX fallback: key verified via payload but not pre-known. */
    NX_VERIFIED,
    /** No verification — handshake failed or was rejected. */
    NONE,
}

/** Distinguishes Ed25519 (identity/signing) keys from X25519 (DH) keys. */
public enum class KeyType {
    ED25519,
    X25519,
}

/** Reason a key rotation was triggered. */
public enum class KeyRotationReason {
    PERIODIC,
    MANUAL,
    SECURITY_EVENT,
}

/** Noise handshake pattern used for link-layer and end-to-end sessions. */
public enum class HandshakePattern {
    XX,
    IK,
    IX,
    NX,
}

/** Scoreboard bitfield encoding strategy for selective acknowledgment. */
public enum class ScoreboardEncoding {
    DYNAMIC,
    FIXED,
}

/** Message priority that affects routing behavior and TTL. */
public enum class Priority {
    HIGH,
    NORMAL,
    LOW,
}

// ---------------------------------------------------------------------------
// Wire-layer types
// ---------------------------------------------------------------------------

/** Frame type that appears on the wire. */
public enum class FrameType {
    MESH_ENVELOPE,
    ROUTE_UPDATE,
    ROUTE_WITHDRAWAL,
    ROUTE_DIGEST,
    TRANSFER_CHUNK,
    TRANSFER_ACKNOWLEDGMENT,
    TRANSFER_CANCEL,
    KEY_ROTATION,
}

// ---------------------------------------------------------------------------
// Security / crypto types
// ---------------------------------------------------------------------------

/** Why a routed frame failed to decrypt at the link layer. */
public enum class DecryptFailureReason {
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
public enum class TransportFallbackReason {
    NO_PSM_ADVERTISED,
    L2CAP_CONNECT_FAILED,
    L2CAP_DROPPED_MID_TRANSFER,
    LOCAL_POLICY,
}

/** Data plane bearer in use for a transfer session. */
public enum class DataPlaneBearer {
    GATT,
    L2CAP,
}

/** Regulatory region controlling BLE radio policy clamping. */
public enum class RegulatoryRegion {
    DEFAULT,
    EU,
}

// ---------------------------------------------------------------------------
// Peer lifecycle types
// ---------------------------------------------------------------------------

/** Public peer connection states exposed to host apps. */
public enum class PeerConnectionState {
    CONNECTED,
    DISCONNECTED,
}

// ---------------------------------------------------------------------------
// Noise session state-machine types
// ---------------------------------------------------------------------------

/** Which layer of the mesh a Noise session belongs to. */
public enum class NoiseLayer {
    HOP_BY_HOP,
    END_TO_END,
}

/** Noise session states. Applies to both hop-by-hop (XX/IK) and end-to-end (IX/NX) layers. */
public enum class NoiseSessionState {
    DISCONNECTED,
    HANDSHAKING_XX,
    HANDSHAKING_IK,
    HANDSHAKING_IX,
    HANDSHAKING_NX,
    ESTABLISHED,
    REKEYING,
    FAILED,
}

/** Role in a Noise handshake. */
public enum class NoiseRole {
    INITIATOR,
    RESPONDER,
}

/** Reason a Noise handshake failed. */
public enum class NoiseFailureReason {
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

// ---------------------------------------------------------------------------
// Trust & identity types
// ---------------------------------------------------------------------------

/** Trust record state in the TrustStore. Tracks TOFU pinning lifecycle. */
public enum class TrustState {
    /** Handshake in progress, not yet verified. */
    INITIATED,
    /** TOFU-pinned identity (first successful handshake). */
    TRUSTED,
    /** Explicitly revoked by user/application. */
    REVOKED,
}

/** Internal per-peer key rotation status. */
internal enum class KeyRotationState {
    /** Key is active and current. */
    CURRENT,
    /** Old key retained for grace period after rotation. */
    GRACE_PERIOD,
    /** Key fully revoked, no longer accepted. */
    REVOKED,
}

/** Internal peer lifecycle tracking type. Not exposed publicly. */
internal enum class PeerLifecycleState {
    /** Active BLE link. */
    CONNECTED,
    /** BLE link lost, grace period active. */
    DISCONNECTED,
    /** Grace period expired, ephemeral state cleaned up. */
    GONE,
}

// ---------------------------------------------------------------------------
// Diagnostic types
// ---------------------------------------------------------------------------

/** Severity level for diagnostic events. */
public enum class DiagnosticSeverity {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

// ---------------------------------------------------------------------------
// Delivery outcome types
// ---------------------------------------------------------------------------

/** Explicit delivery outcomes surfaced to host apps. Maps from TransferState. */
public enum class TransferDeliveryOutcome {
    SUCCESS,
    IN_PROGRESS,
    RETRYING,
    ROUTE_WAITING,
    TIMEOUT,
    UNRECOVERABLE_FAILURE,
    TRUST_FAILURE,
}
