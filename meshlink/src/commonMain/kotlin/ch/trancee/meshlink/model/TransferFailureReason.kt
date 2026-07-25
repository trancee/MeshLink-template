package ch.trancee.meshlink.model

/** Why a transfer session reached a terminal failure state. */
public sealed interface TransferFailureReason {
    /** The failure is permanent and cannot be recovered from without external intervention. */
    public data class Unrecoverable(val message: String) : TransferFailureReason

    /** The failure is due to a trust policy violation (e.g. identity mismatch, revoked peer). */
    public data class TrustFailure(val peerIdentity: PeerIdentity) : TransferFailureReason
}
