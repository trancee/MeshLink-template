package ch.trancee.meshlink.model

import kotlin.ExperimentalUnsignedTypes
import kotlin.OptIn

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
@Suppress("MagicNumber")
@OptIn(ExperimentalUnsignedTypes::class)
public enum class ErrorCode(private val value: Int) {

    // Configuration (0x01xx)
    INVALID_PARAMETER(0x0101),
    INVALID_STATE(0x0102),

    // Permission (0x02xx)
    PERMISSION_DENIED(0x0201),

    // Bluetooth (0x03xx)
    BLUETOOTH_DISABLED(0x0301),
    COC_NOT_SUPPORTED(0x0302),
    CONNECTION_FAILED(0x0303),
    GATT_OPERATION_FAILED(0x0304),
    L2CAP_CHANNEL_FAILED(0x0305),

    // Crypto (0x04xx)
    CRYPTO_OPERATION_FAILED(0x0401),
    SIGNATURE_VERIFICATION_FAILED(0x0402),
    REPLAY_DETECTED(0x0403),

    // Routing (0x05xx)
    NO_ROUTE(0x0501),
    ROUTE_ADVERTISEMENT_FAILED(0x0502),
    ROUTE_LOOP_DETECTED(0x0503),

    // Transfer (0x06xx)
    TRANSFER_TIMEOUT(0x0601),
    TRANSFER_CANCELLED(0x0602),
    TRANSFER_CORRUPTED(0x0603),
    SESSION_NOT_FOUND(0x0604),
    CHUNK_OUT_OF_BOUNDS(0x0605),

    // Storage (0x07xx)
    STORAGE_UNAVAILABLE(0x0701),
    STORAGE_CORRUPTED(0x0702),

    // Lifecycle (0x08xx)
    // Reserved for future LifecycleException codes

    // Transport (0x09xx)
    // Reserved for future transport-specific codes

    // Trust (0x0Axx)
    PEER_NOT_FOUND(0x0A01),
    KEY_UNKNOWN(0x0A02),
    TRUST_VIOLATION(0x0A03),

    // Internal (0x0Fxx)
    INTERNAL_ERROR(0x0F01),
    SERIALIZATION_FAILED(0x0F02);

    /** Returns the error code as UShort for wire serialization. */
    public fun toUShort(): UShort = value.toUShort()

    public companion object {
        /** Finds an [ErrorCode] by its raw UShort value. */
        public fun fromValue(value: UShort): ErrorCode? =
            values().firstOrNull { it.toUShort() == value }
    }
}
