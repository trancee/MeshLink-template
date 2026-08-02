package ch.trancee.meshlink.model

/**
 * Maps [TransferState] to explicit delivery outcomes surfaced to host apps.
 *
 * See SPEC.md §3.4.1 and specs/protocol/state-machines.yaml (TransferDeliveryOutcome).
 *
 * SPEC-ANCHOR: delivery-outcome
 */
public object TransferOutcomeMapper {

    /**
     * Maps a [TransferState] (and optional [TransferFailureReason]) to a [TransferDeliveryOutcome].
     */
    public fun map(
        state: TransferState,
        failureReason: TransferFailureReason?,
    ): TransferDeliveryOutcome? =
        when (state) {
            TransferState.COMPLETED -> TransferDeliveryOutcome.SUCCESS
            TransferState.CANCELLED -> TransferDeliveryOutcome.CANCELLED
            TransferState.EXPIRED -> TransferDeliveryOutcome.TIMEOUT
            TransferState.AWAITING_DECISION,
            TransferState.TRANSFERRING,
            TransferState.ROUTE_UNAVAILABLE,
            TransferState.RETRANSMITTING -> null
            TransferState.FAILED ->
                when (failureReason) {
                    is TransferFailureReason.TrustFailure -> TransferDeliveryOutcome.TRUST_FAILURE
                    else -> TransferDeliveryOutcome.UNRECOVERABLE_FAILURE
                }
        }
}
