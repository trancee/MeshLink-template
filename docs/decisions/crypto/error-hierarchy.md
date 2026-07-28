# MeshLink Exception Hierarchy (Error Wrapping Strategy)

**Status:** Locked — 2026-07-28

## Context

Per `CONSTITUTION.md §III`: "Errors use sealed `MeshLinkException` hierarchy in `commonMain`, with platform exceptions wrapped and **MUST NOT leak to consumers**."

Per `docs/reference/diagnostics.md §11.4`:

- Trust/Security errors (PeerNotFoundError, TrustError, KeyUnknownError)
- Routing errors (NoRouteError, RouteUpdateError)
- Transfer errors (TransferTimeoutError, TransferCancelledError, TransferCorruptedError)
- Transport errors (BluetoothStateError, ConnectionTimeoutError, CocNotSupportedError)

**ErrorCode enum:** `PEER_NOT_FOUND`, `KEY_UNKNOWN`, `TRUST_VIOLATION`, `TRANSFER_TIMEOUT`, `BLUETOOTH_DISABLED`, `CONNECTION_FAILED`, `INVALID_PARAMETER`, `INTERNAL_ERROR`

This ADR defines the complete sealed exception hierarchy for cross-platform use.

## Decision

**Define a single sealed `MeshLinkException` hierarchy in `commonMain` with error codes, wrapping all platform-specific exceptions at the boundary.**

### Exception Hierarchy

```kotlin
// meshlink/src/commonMain/kotlin/ch/trancee/meshlink/MeshLinkException.kt

/**
 * Base exception for all MeshLink errors.
 * 
 * All platform-specific exceptions (Android BluetoothException, iOS NSError, etc.)
 * MUST be caught at the platform boundary and wrapped in the appropriate
 * MeshLinkException subtype before propagating to common code or consumers.
 * 
 * Consumers should catch [MeshLinkException] and handle by [code] for programmatic
 * error handling, or use [message] for logging/display.
 */
public sealed class MeshLinkException(
    public val code: ErrorCode,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** Unique error code for programmatic handling. */
    public enum class ErrorCode {
        // Trust/Security
        PEER_NOT_FOUND,
        KEY_UNKNOWN,
        TRUST_VIOLATION,
        SIGNATURE_VERIFICATION_FAILED,
        REPLAY_DETECTED,
        
        // Routing
        NO_ROUTE,
        ROUTE_UPDATE_FAILED,
        ROUTE_LOOP_DETECTED,
        
        // Transfer
        TRANSFER_TIMEOUT,
        TRANSFER_CANCELLED,
        TRANSFER_CORRUPTED,
        SESSION_NOT_FOUND,
        CHUNK_OUT_OF_BOUNDS,
        
        // Transport
        BLUETOOTH_DISABLED,
        BLUETOOTH_UNAVAILABLE,
        CONNECTION_FAILED,
        CONNECTION_TIMEOUT,
        COC_NOT_SUPPORTED,
        GATT_OPERATION_FAILED,
        L2CAP_CHANNEL_FAILED,
        
        // Configuration
        INVALID_PARAMETER,
        INVALID_STATE,
        PERMISSION_DENIED,
        
        // Internal
        INTERNAL_ERROR,
        CRYPTO_OPERATION_FAILED,
        SERIALIZATION_FAILED,
    }
    
    // =========================================================================
    // Trust / Security Errors
    // =========================================================================
    
    /** Peer identity not found in trust store or routing table. */
    public data class PeerNotFoundError(
        public val peerIdentity: PeerIdentity,
        cause: Throwable? = null,
    ) : MeshLinkException(ErrorCode.PEER_NOT_FOUND, "Peer not found: $peerIdentity", cause)
    
    /** Trust verification failed (TOFU pin mismatch, revoked peer, etc.) */
    public data class TrustError(
        public val peerIdentity: PeerIdentity,
        public val reason: TrustFailureReason,
        cause: Throwable? = null,
    ) : MeshLinkException(ErrorCode.TRUST_VIOLATION, "Trust violation for $peerIdentity: $reason", cause)
    
    /** Destination public key unknown (triggers NX fallback). */
    public data class KeyUnknownError(
        public val peerIdentity: PeerIdentity,
        cause: Throwable? = null,
    ) : MeshLinkException(ErrorCode.KEY_UNKNOWN, "Public key unknown for $peerIdentity", cause)
    
    /** Signature verification failed on key rotation or route update. */
    public data class SignatureVerificationFailedError(
        public val peerIdentity: PeerIdentity,
        public val frameType: FrameType,
        cause: Throwable? = null,
    ) : MeshLinkException(ErrorCode.SIGNATURE_VERIFICATION_FAILED, "Signature verification failed for $peerIdentity", cause)
    
    /** Replay attack detected on Noise session. */
    public data class ReplayDetectedError(
        public val sessionId: SessionId,
        public val nonce: Long,
        cause: Throwable? = null,
    ) : MeshLinkException(ErrorCode.REPLAY_DETECTED, "Replay detected: session=$sessionId nonce=$nonce", cause)
    
    // =========================================================================
    // Routing Errors
    // =========================================================================
    
    /** No route to destination. */
    public data class NoRouteError(
        public val destination: PeerIdentity,
        cause: Throwable? = null,
    ) : MeshLinkException(ErrorCode.NO_ROUTE, "No route to $destination", cause)
    
    /** Route update processing failed. */
    public data class RouteUpdateError(
        public val frameType: FrameType,
        public val reason: String,
        cause: Throwable? = null,
    ) : MeshLinkException(ErrorCode.ROUTE_UPDATE_FAILED, "Route update failed: $reason", cause)
    
    /** Routing loop detected (feasibility condition violated). */
    public data class RouteLoopDetectedError(
        public val destination: PeerIdentity,
        public val path: List<PeerIdentity>,
        cause: Throwable? = null,
    ) : MeshLinkException(ErrorCode.ROUTE_LOOP_DETECTED, "Routing loop detected to $destination via $path", cause)
    
    // =========================================================================
    // Transfer Errors
    // =========================================================================
    
    /** Transfer timed out (retry budget or grace period exhausted). */
    public data class TransferTimeoutError(
        public val sessionId: SessionId,
        public val bytesTransferred: Long,
        public val totalBytes: Long,
        cause: Throwable? = null,
    ) : MeshLinkException(ErrorCode.TRANSFER_TIMEOUT, "Transfer $sessionId timed out ($bytesTransferred/$totalBytes)", cause)
    
    /** Transfer explicitly cancelled. */
    public data class TransferCancelledError(
        public val sessionId: SessionId,
        public val reason: TransferFailureReason,
        cause: Throwable? = null,
    ) : MeshLinkException(ErrorCode.TRANSFER_CANCELLED, "Transfer $sessionId cancelled: $reason", cause)
    
    /** Transfer data corrupted (checksum/decryption failure). */
    public data class TransferCorruptedError(
        public val sessionId: SessionId,
        public val chunkIndex: Int,
        cause: Throwable? = null,
    ) : MeshLinkException(ErrorCode.TRANSFER_CORRUPTED, "Transfer $sessionId chunk $chunkIndex corrupted", cause)
    
    /** Transfer session not found (expired or completed). */
    public data class SessionNotFoundError(
        public val sessionId: SessionId,
        cause: Throwable? = null,
    ) : MeshLinkException(ErrorCode.SESSION_NOT_FOUND, "Session not found: $sessionId", cause)
    
    /** Chunk index out of bounds. */
    public data class ChunkOutOfBoundsError(
        public val sessionId: SessionId,
        public val chunkIndex: Int,
        public val totalChunks: Int,
        cause: Throwable? = null,
    ) : MeshLinkException(ErrorCode.CHUNK_OUT_OF_BOUNDS, "Chunk $chunkIndex out of bounds (0-$totalChunks)", cause)
    
    // =========================================================================
    // Transport Errors
    // =========================================================================
    
    /** Bluetooth is disabled or unavailable. */
    public data class BluetoothDisabledError(
        cause: Throwable? = null,
    ) : MeshLinkException(ErrorCode.BLUETOOTH_DISABLED, "Bluetooth is disabled", cause)
    
    /** Bluetooth hardware unavailable. */
    public data class BluetoothUnavailableError(
        cause: Throwable? = null,
    ) : MeshLinkException(ErrorCode.BLUETOOTH_UNAVAILABLE, "Bluetooth hardware unavailable", cause)
    
    /** Connection failed (GATT or L2CAP). */
    public data class ConnectionFailedError(
        public val peerIdentity: PeerIdentity,
        public val transport: DataPlaneBearer,
        public val reason: String,
        cause: Throwable? = null,
    ) : MeshLinkException(ErrorCode.CONNECTION_FAILED, "Connection failed to $peerIdentity via $transport: $reason", cause)
    
    /** Connection timed out. */
    public data class ConnectionTimeoutError(
        public val peerIdentity: PeerIdentity,
        public val transport: DataPlaneBearer,
        cause: Throwable? = null,
    ) : MeshLinkException(ErrorCode.CONNECTION_TIMEOUT, "Connection timeout to $peerIdentity via $transport", cause)
    
    /** L2CAP CoC not supported on this device/OS version. */
    public data class CocNotSupportedError(
        cause: Throwable? = null,
    ) : MeshLinkException(ErrorCode.COC_NOT_SUPPORTED, "L2CAP CoC not supported", cause)
    
    /** GATT operation failed. */
    public data class GattOperationFailedError(
        public val operation: String,
        public val status: Int,
        cause: Throwable? = null,
    ) : MeshLinkException(ErrorCode.GATT_OPERATION_FAILED, "GATT $operation failed with status $status", cause)
    
    /** L2CAP channel operation failed. */
    public data class L2capChannelFailedError(
        public val operation: String,
        public val reason: String,
        cause: Throwable? = null,
    ) : MeshLinkException(ErrorCode.L2CAP_CHANNEL_FAILED, "L2CAP $operation failed: $reason", cause)
    
    // =========================================================================
    // Configuration Errors
    // =========================================================================
    
    /** Invalid parameter passed to API. */
    public data class InvalidParameterError(
        public val parameterName: String,
        public val reason: String,
        cause: Throwable? = null,
    ) : MeshLinkException(ErrorCode.INVALID_PARAMETER, "Invalid parameter '$parameterName': $reason", cause)
    
    /** Operation invalid in current state. */
    public data class InvalidStateError(
        public val currentState: String,
        public val expectedState: String,
        cause: Throwable? = null,
    ) : MeshLinkException(ErrorCode.INVALID_STATE, "Invalid state: $currentState (expected $expectedState)", cause)
    
    /** Required permission not granted. */
    public data class PermissionDeniedError(
        public val permission: String,
        cause: Throwable? = null,
    ) : MeshLinkException(ErrorCode.PERMISSION_DENIED, "Permission denied: $permission", cause)
    
    // =========================================================================
    // Internal Errors
    // =========================================================================
    
    /** Unexpected internal error (bug). */
    public data class InternalError(
        public val context: String,
        cause: Throwable? = null,
    ) : MeshLinkException(ErrorCode.INTERNAL_ERROR, "Internal error in $context", cause)
    
    /** Crypto operation failed unexpectedly. */
    public data class CryptoOperationFailedError(
        public val operation: String,
        cause: Throwable? = null,
    ) : MeshLinkException(ErrorCode.CRYPTO_OPERATION_FAILED, "Crypto operation failed: $operation", cause)
    
    /** Serialization/deserialization failed. */
    public data class SerializationFailedError(
        public val type: String,
        cause: Throwable? = null,
    ) : MeshLinkException(ErrorCode.SERIALIZATION_FAILED, "Serialization failed for $type", cause)
}

// =============================================================================
// Platform Wrapping Helpers (androidMain / iosMain)
// =============================================================================

/**
 * Android-specific exception wrapping.
 * 
 * Usage:
 * ```kotlin
 * try {
 *     gatt.writeCharacteristic(characteristic)
 * } catch (e: SecurityException) {
 *     throw MeshLinkException.PermissionDeniedError("android.permission.BLUETOOTH_CONNECT", e)
 * } catch (e: IllegalArgumentException) {
 *     throw MeshLinkException.InvalidParameterError("characteristic", e.message ?: "invalid", e)
 * } catch (e: Exception) {
 *     throw MeshLinkException.GattOperationFailedError("writeCharacteristic", BluetoothGatt.GATT_FAILURE, e)
 * }
 * ```
 */
 
/**
 * iOS-specific exception wrapping.
 * 
 * Usage:
 * ```swift
 * do {
 *     try peripheral.writeValue(data, for: characteristic, type: .withResponse)
 * } catch let error as NSError {
 *     switch error.domain {
 *     case CBErrorDomain:
 *         throw MeshLinkException.GattOperationFailedError("writeValue", error.code, error)
 *     case NSURLErrorDomain where error.code == NSURLErrorNotConnectedToInternet:
 *         // Not applicable for BLE
 *     default:
 *         throw MeshLinkException.InternalError("CoreBluetooth write", error)
 *     }
 * }
 * ```
 */

// =============================================================================
// Diagnostic Integration
// =============================================================================

/**
 * All MeshLinkException subtypes should emit a corresponding DiagnosticEvent
 * when caught at the public API boundary.
 * 
 * Example:
 * ```kotlin
 * suspend fun sendData(peer: PeerIdentity, data: ByteArray) {
 *     try {
 *         transferCoordinator.send(peer, data)
 *     } catch (e: MeshLinkException) {
 *         diagnosticEmitter.emit(DiagnosticEvent.TransferFailureEvent(
 *             sessionId = e.sessionId,
 *             peerIdentity = peer,
 *             reason = when (e) {
 *                 is MeshLinkException.TransferTimeoutError -> TransferFailureReason.Unrecoverable("timeout")
 *                 is MeshLinkException.TrustError -> TransferFailureReason.TrustFailure(peer)
 *                 else -> TransferFailureReason.Unrecoverable(e.message)
 *             }
 *         ))
 *         throw e
 *     }
 * }
 * ```
 */

// =============================================================================
// Testing
// =============================================================================

/**
 * Test matrix for exception hierarchy:
 * 
 * | Exception Type | Platform Trigger | Expected Code | Diagnostic Event |
 * |----------------|------------------|---------------|------------------|
 * | PeerNotFoundError | TrustStore.get() returns null | PEER_NOT_FOUND | TransferFailureEvent |
 * | TrustError | Noise handshake key mismatch | TRUST_VIOLATION | HandshakeEvent(verificationLevel=NONE) |
 * | KeyUnknownError | Routing table lacks destination key | KEY_UNKNOWN | HandshakeEvent(fallbackUsed=true) |
 * | NoRouteError | RouteCoordinator.getNextHop() returns null | NO_ROUTE | TransferFailureEvent |
 * | TransferTimeoutError | Retry budget exhausted | TRANSFER_TIMEOUT | TransferFailureEvent |
 * | ConnectionTimeoutError | GATT connect > 30s | CONNECTION_TIMEOUT | TransportFallbackEvent |
 * | CocNotSupportedError | L2CAP PSM not available | COC_NOT_SUPPORTED | TransportFallbackEvent |
 * | PermissionDeniedError | Missing BLUETOOTH_CONNECT | PERMISSION_DENIED | — |
 * 
 * All exceptions MUST be catchable as MeshLinkException and have a stable ErrorCode.
 */
