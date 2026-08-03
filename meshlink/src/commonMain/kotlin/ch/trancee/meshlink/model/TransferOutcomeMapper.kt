package ch.trancee.meshlink.model

/**
 * Maps a [TransferState] (and optional [TransferFailureReason]) to a [TransferDeliveryOutcome].
 *
 * See SPEC.md §7.6 and specs/protocol/state-machines.yaml (TransferDeliveryOutcome).
 *
 * SPEC-ANCHOR: delivery-outcome
 */
internal fun mapTransferDeliveryOutcome(
    state: TransferState,
    failureReason: TransferFailureReason?,
): TransferDeliveryOutcome? =
    when (state) {
        TransferState.COMPLETED -> TransferDeliveryOutcome.COMPLETED
        TransferState.CANCELLED -> TransferDeliveryOutcome.CANCELLED
        TransferState.EXPIRED -> TransferDeliveryOutcome.EXPIRED
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
