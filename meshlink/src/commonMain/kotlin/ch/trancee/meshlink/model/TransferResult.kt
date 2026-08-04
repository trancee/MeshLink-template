package ch.trancee.meshlink.model

/**
 * Terminal payload transfer result surfaced to host apps.
 *
 * Non-terminal progress is represented by [TransferState] (AWAITING_DECISION, TRANSFERRING,
 * ROUTE_UNAVAILABLE, RETRANSMITTING); transfer status returns null until a terminal condition
 * (completion, cancellation, expiration, or failure) occurs.
 *
 * Terminal outcomes (Completed, Cancelled, Expired) come from external conditions (completion,
 * cancellation, expiration) outside the transfer protocol. Failure outcomes (UnrecoverableFailure,
 * TrustFailure) carry the detailed reason directly — no separate [TransferFailureReason] type is
 * needed.
 *
 * See SPEC.md §3.7 and specs/protocol/state-machines.yaml (TransferResult).
 *
 * SPEC-ANCHOR: transfer-result
 */
public sealed interface TransferResult {
    /** All chunks acknowledged and delivered. */
    public data object Completed : TransferResult

    /** Transfer was cancelled by the sender before completion. */
    public data object Cancelled : TransferResult

    /** Transfer exceeded its time-to-live without completion. */
    public data object Expired : TransferResult

    /**
     * Protocol, sink, or non-trust failure — retry budget exhausted, malformed frame, sink error,
     * or any other unrecoverable condition.
     *
     * @param message redacted diagnostic describing the failure
     */
    public data class UnrecoverableFailure(public val message: String) : TransferResult

    /**
     * Trust-related failure — identity mismatch, revoked peer, or similar security policy
     * violation.
     *
     * @param identity the peer whose trust was violated
     */
    public data class TrustFailure(public val identity: PeerIdentity) : TransferResult
}
