package ch.trancee.meshlink.model

/**
 * Stable explicit error codes for all [MeshLinkException] subtypes.
 *
 * Categories use UShort ranges (never enum ordinals):
 * - 0x01xx: Configuration
 * - 0x02xx: Permission
 * - 0x03xx: Bluetooth
 * - 0x04xx: Crypto
 * - 0x05xx: Routing
 * - 0x06xx: Transfer
 * - 0x07xx: Storage
 * - 0x08xx: Lifecycle
 * - 0x09xx: Transport
 * - 0x0Axx: Trust
 * - 0x0Fxx: Internal
 *
 * SPEC-ANCHOR: error-code
 */
public enum class ErrorCode(private val code: UShort) {

    // Configuration (0x01xx)
    INVALID_PARAMETER(0x0101u),
    INVALID_STATE(0x0102u),

    // Permission (0x02xx)
    PERMISSION_DENIED(0x0201u),

    // Bluetooth (0x03xx)
    BLUETOOTH_DISABLED(0x0301u),
    COC_NOT_SUPPORTED(0x0302u),
    CONNECTION_FAILED(0x0303u),
    GATT_OPERATION_FAILED(0x0304u),
    L2CAP_CHANNEL_FAILED(0x0305u),
    RADIO_IN_USE(0x0306u),

    // Crypto (0x04xx)
    CRYPTO_OPERATION_FAILED(0x0401u),
    SIGNATURE_VERIFICATION_FAILED(0x0402u),
    REPLAY_DETECTED(0x0403u),

    // Routing (0x05xx)
    NO_ROUTE(0x0501u),
    ROUTE_ADVERTISEMENT_FAILED(0x0502u),
    ROUTE_LOOP_DETECTED(0x0503u),

    // Transfer (0x06xx)
    TRANSFER_TIMEOUT(0x0601u),
    TRANSFER_CANCELLED(0x0602u),
    TRANSFER_CORRUPTED(0x0603u),
    SESSION_NOT_FOUND(0x0604u),
    CHUNK_OUT_OF_BOUNDS(0x0605u),

    // Storage (0x07xx)
    STORAGE_UNAVAILABLE(0x0701u),
    STORAGE_CORRUPTED(0x0702u),

    // Lifecycle (0x08xx)
    // Reserved for future LifecycleException codes

    // Transport (0x09xx)
    // Reserved for future transport-specific codes

    // Trust (0x0Axx)
    PEER_NOT_FOUND(0x0A01u),
    KEY_UNKNOWN(0x0A02u),
    TRUST_VIOLATION(0x0A03u),

    // Internal (0x0Fxx)
    INTERNAL_ERROR(0x0F01u),
    SERIALIZATION_FAILED(0x0F02u);

    /** Returns the error code as [UShort]. */
    public fun code(): UShort = code

    public companion object {
        /** Finds an [ErrorCode] by its raw [UShort] value. */
        public fun fromValue(value: UShort): ErrorCode? = entries.firstOrNull { it.code() == value }
    }
}
