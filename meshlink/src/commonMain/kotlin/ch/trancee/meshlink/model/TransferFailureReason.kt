package ch.trancee.meshlink.model

import kotlinx.serialization.Serializable

/** Why a transfer session reached a terminal failure state. */
@Serializable
sealed interface TransferFailureReason {
    /** The failure is permanent and cannot be recovered from without external intervention. */
    data class Unrecoverable(val message: String) : TransferFailureReason

    /** The failure is due to a trust policy violation (e.g. identity mismatch, revoked peer). */
    data class TrustFailure(val peerIdentity: PeerIdentity) : TransferFailureReason
}
