package ch.trancee.meshlink.model

/**
 * Base exception for all MeshLink immediate command failures.
 *
 * All public API failures throw typed subtypes of this sealed interface. Platform exceptions are
 * wrapped at the boundary and never leak to consumers. Long-running transfer failures use terminal
 * transfer outcomes, not exceptions.
 *
 * SPEC-ANCHOR: error-hierarchy
 */
public sealed interface MeshLinkException {
    /** Stable error code for programmatic handling. */
    public val errorCode: ErrorCode

    /** Human-readable message (redacted, no sensitive data). */
    public val message: String
}

/** Invalid configuration parameter or state. */
public data class ConfigurationException(
    public override val errorCode: ErrorCode,
    public override val message: String,
) : MeshLinkException

/** Invalid lifecycle state transition attempted. */
public data class LifecycleException(
    public override val errorCode: ErrorCode,
    public override val message: String,
) : MeshLinkException

/** Required permission not granted. */
public data class PermissionException(
    public override val errorCode: ErrorCode,
    public override val message: String,
) : MeshLinkException

/** Bluetooth radio or GATT/L2CAP operation failed. */
public data class BluetoothException(
    public override val errorCode: ErrorCode,
    public override val message: String,
) : MeshLinkException

/** Secure storage unavailable or corrupted. */
public data class StorageException(
    public override val errorCode: ErrorCode,
    public override val message: String,
) : MeshLinkException

/** Cryptographic operation failed (sign, verify, encrypt, decrypt, key agreement). */
public data class CryptoException(
    public override val errorCode: ErrorCode,
    public override val message: String,
) : MeshLinkException

/** Trust verification failed (pin mismatch, revocation, key unknown). */
public data class TrustException(
    public override val errorCode: ErrorCode,
    public override val message: String,
) : MeshLinkException

/** Routing operation failed (no route, loop detected, advertisement failed). */
public data class RoutingException(
    public override val errorCode: ErrorCode,
    public override val message: String,
) : MeshLinkException

/** Transfer operation failed (timeout, cancelled, corrupted, session not found). */
public data class TransferException(
    public override val errorCode: ErrorCode,
    public override val message: String,
) : MeshLinkException

/** BLE radio lease already held by another MeshLink instance. */
public class RadioInUseException(
    public override val errorCode: ErrorCode = ErrorCode.BLUETOOTH_DISABLED,
    public override val message: String =
        "BLE radio lease already held by another MeshLink instance",
) : MeshLinkException
