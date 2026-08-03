package ch.trancee.meshlink.model

/**
 * Maps a [TransferState] (and optional [TransferFailureReason]) to a [TransferResult].
 *
 * See SPEC.md §7.6 and specs/protocol/state-machines.yaml (TransferResult).
 *
 * SPEC-ANCHOR: transfer-result
 */
internal fun mapTransferResult(
    state: TransferState,
    failureReason: TransferFailureReason?,
): TransferResult? =
    when (state) {
        TransferState.COMPLETED -> TransferResult.COMPLETED
        TransferState.CANCELLED -> TransferResult.CANCELLED
        TransferState.EXPIRED -> TransferResult.EXPIRED
        TransferState.AWAITING_DECISION,
        TransferState.TRANSFERRING,
        TransferState.ROUTE_UNAVAILABLE,
        TransferState.RETRANSMITTING -> null
        TransferState.FAILED ->
            when (failureReason) {
                is TransferFailureReason.TrustFailure -> TransferResult.TRUST_FAILURE
                else -> TransferResult.UNRECOVERABLE_FAILURE
            }
    }
