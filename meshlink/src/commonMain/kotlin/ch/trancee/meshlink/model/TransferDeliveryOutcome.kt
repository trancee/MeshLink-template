package ch.trancee.meshlink.model

/**
 * Maps [TransferState] to explicit delivery outcomes surfaced to host apps.
 *
 * See SPEC.md §3.4.1 and specs/state-machines.yaml (TransferDeliveryOutcome).
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
    ): TransferDeliveryOutcome =
        when (state) {
            TransferState.COMPLETED -> TransferDeliveryOutcome.SUCCESS
            TransferState.IN_PROGRESS -> TransferDeliveryOutcome.IN_PROGRESS
            TransferState.RETRYING -> TransferDeliveryOutcome.RETRYING
            TransferState.WAITING_FOR_ROUTE -> TransferDeliveryOutcome.ROUTE_WAITING
            TransferState.TIMED_OUT -> TransferDeliveryOutcome.TIMEOUT
            TransferState.FAILED ->
                when (failureReason) {
                    is TransferFailureReason.TrustFailure -> TransferDeliveryOutcome.TRUST_FAILURE
                    else -> TransferDeliveryOutcome.UNRECOVERABLE_FAILURE
                }
        }
}
