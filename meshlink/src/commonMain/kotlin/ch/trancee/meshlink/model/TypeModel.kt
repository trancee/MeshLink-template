package ch.trancee.meshlink.model

// SPEC-ANCHOR: type-model

/** Verification level achieved during handshake. */
public enum class VerificationLevel {
    FULL,
    TOFU_PIN,
    NONE,
}

/** Distinguishes Ed25519 identity/signing keys from X25519 DH keys. */
public enum class KeyType {
    ED25519,
    X25519,
}

/** Reason a long-term key rotation was triggered. */
public enum class KeyRotationReason {
    PERIODIC,
    MANUAL,
    SECURITY_EVENT,
}

/** Noise pattern selected by trust state. */
public enum class HandshakePattern {
    XX,
    IK,
}

/** Message priority affecting delivery scheduling and default timeToLive. */
public enum class Priority {
    HIGH,
    NORMAL,
    LOW,
}

/** Explicit internal MeshLink Wire Codec frame codes. */
internal enum class FrameType(public val code: UByte) {
    MESH_ENVELOPE(FrameCode.MESH_ENVELOPE),
    ROUTE_ADVERTISEMENT(FrameCode.ROUTE_ADVERTISEMENT),
    ROUTE_WITHDRAWAL(FrameCode.ROUTE_WITHDRAWAL),
    ROUTE_DIGEST(FrameCode.ROUTE_DIGEST),
    ROUTE_SEQUENCE_ADVANCEMENT(FrameCode.ROUTE_SEQUENCE_ADVANCEMENT),
    ROUTE_SYNCHRONIZATION(FrameCode.ROUTE_SYNCHRONIZATION),
    ROUTE_SNAPSHOT(FrameCode.ROUTE_SNAPSHOT),
    PAYLOAD_MANIFEST(FrameCode.PAYLOAD_MANIFEST),
    PAYLOAD_DECISION(FrameCode.PAYLOAD_DECISION),
    PAYLOAD_CHUNK(FrameCode.PAYLOAD_CHUNK),
    PAYLOAD_ACKNOWLEDGEMENT(FrameCode.PAYLOAD_ACKNOWLEDGEMENT),
    PAYLOAD_CANCELLATION(FrameCode.PAYLOAD_CANCELLATION),
    KEY_ROTATION(FrameCode.KEY_ROTATION),
    EPOCH_COMMIT(FrameCode.EPOCH_COMMIT),
    EPOCH_ACKNOWLEDGEMENT(FrameCode.EPOCH_ACKNOWLEDGEMENT),
}

private object FrameCode {
    const val MESH_ENVELOPE: UByte = 0x00u
    const val ROUTE_ADVERTISEMENT: UByte = 0x01u
    const val ROUTE_WITHDRAWAL: UByte = 0x02u
    const val ROUTE_DIGEST: UByte = 0x03u
    const val ROUTE_SEQUENCE_ADVANCEMENT: UByte = 0x04u
    const val ROUTE_SYNCHRONIZATION: UByte = 0x05u
    const val ROUTE_SNAPSHOT: UByte = 0x06u
    const val PAYLOAD_MANIFEST: UByte = 0x20u
    const val PAYLOAD_DECISION: UByte = 0x21u
    const val PAYLOAD_CHUNK: UByte = 0x22u
    const val PAYLOAD_ACKNOWLEDGEMENT: UByte = 0x23u
    const val PAYLOAD_CANCELLATION: UByte = 0x24u
    const val KEY_ROTATION: UByte = 0x40u
    const val EPOCH_COMMIT: UByte = 0x41u
    const val EPOCH_ACKNOWLEDGEMENT: UByte = 0x42u
}

/** Why a routed frame failed to decrypt at the link layer. */
public enum class DecryptFailureReason {
    AUTHENTICATION_TAG_MISMATCH,
    REPLAY_DETECTED,
    SEQUENCE_NUMBER_MISMATCH,
    KEY_UNAVAILABLE,
    MALFORMED_FRAME,
}

/** Why data fell back from L2CAP to GATT. */
public enum class TransportFallbackReason {
    L2CAP_UNAVAILABLE,
    L2CAP_CONNECT_FAILED,
    L2CAP_OPEN_TIMEOUT,
    L2CAP_STREAM_ERROR,
    L2CAP_STALLED,
    L2CAP_DROPPED_MID_TRANSFER,
    LOCAL_POLICY,
}

/** Data-plane bearer in use for a payload operation. */
public enum class DataPlaneBearer {
    GATT,
    L2CAP,
}

/** Regulatory region controlling BLE policy clamping. */
public enum class RegulatoryRegion {
    DEFAULT,
    EU,
}

/** Public peer connection state. */
public enum class PeerState {
    CONNECTED,
    DISCONNECTED,
}

/** Noise layer for a hop or E2E session. */
public enum class NoiseLayer {
    HOP_BY_HOP,
    END_TO_END,
}

/** Noise session states for hop and E2E layers. */
public enum class NoiseSessionState {
    DISCONNECTED,
    HANDSHAKING_XX,
    HANDSHAKING_IK,
    ESTABLISHED,
    RENEWING,
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

/** Trust classification of a known peer. */
public enum class PeerTrust {
    UNVERIFIED,
    VERIFYING,
    TRUSTED,
    MISMATCHED,
    REVOKED,
}

/** Internal per-peer key rotation status. */
internal enum class KeyRotationState {
    CURRENT,
    GRACE_PERIOD,
    REVOKED,
}

/** Internal peer lifecycle used for grace-period cleanup. */
internal enum class PeerLifecycle {
    CONNECTED,
    DISCONNECTED,
    GONE,
}

/** Severity level for diagnostic events. */
public enum class DiagnosticSeverity {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

/** Terminal payload delivery outcomes. */
public enum class TransferDeliveryOutcome {
    SUCCESS,
    CANCELLED,
    TIMEOUT,
    UNRECOVERABLE_FAILURE,
    TRUST_FAILURE,
}
