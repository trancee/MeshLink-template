package ch.trancee.meshlink.model

/**
 * Base exception for all MeshLink immediate command failures.
 *
 * All public API failures throw typed subtypes of this sealed class. Platform exceptions are
 * wrapped at the boundary and never leak to consumers. Long-running transfer failures use terminal
 * transfer outcomes, not exceptions.
 *
 * SPEC-ANCHOR: error-hierarchy
 */
public sealed class MeshLinkException(
    public open val errorCode: ErrorCode,
    override val message: String,
) : RuntimeException(message)

/** Invalid configuration parameter or state. */
public data class ConfigurationException(
    override val errorCode: ErrorCode,
    override val message: String,
) : MeshLinkException(errorCode, message)

/** Invalid lifecycle state transition attempted. */
public data class LifecycleException(
    override val errorCode: ErrorCode,
    override val message: String,
) : MeshLinkException(errorCode, message)

/** Required permission not granted. */
public data class PermissionException(
    override val errorCode: ErrorCode,
    override val message: String,
) : MeshLinkException(errorCode, message)

/** Bluetooth radio or GATT/L2CAP operation failed. */
public open class BluetoothException(
    override val errorCode: ErrorCode,
    override val message: String,
) : MeshLinkException(errorCode, message)

/** Secure storage unavailable or corrupted. */
public data class StorageException(
    override val errorCode: ErrorCode,
    override val message: String,
) : MeshLinkException(errorCode, message)

/** Cryptographic operation failed (sign, verify, encrypt, decrypt, key agreement). */
public data class CryptoException(
    override val errorCode: ErrorCode,
    override val message: String,
) : MeshLinkException(errorCode, message)

/** Trust verification failed (pin mismatch, revocation, key unknown). */
public data class TrustException(
    override val errorCode: ErrorCode,
    override val message: String,
) : MeshLinkException(errorCode, message)

/** Routing operation failed (no route, loop detected, advertisement failed). */
public data class RoutingException(
    override val errorCode: ErrorCode,
    override val message: String,
) : MeshLinkException(errorCode, message)

/** Transfer operation failed (timeout, cancelled, corrupted, session not found). */
public data class TransferException(
    override val errorCode: ErrorCode,
    override val message: String,
) : MeshLinkException(errorCode, message)

/** BLE radio lease already held by another MeshLink instance. */
public class RadioInUseException(
    override val errorCode: ErrorCode = ErrorCode.RADIO_IN_USE,
    override val message: String =
        "BLE radio lease already held by another MeshLink instance",
) : BluetoothException(errorCode, message)
