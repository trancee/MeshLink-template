package ch.trancee.meshlink.model

/**
 * Maps an optional [TransferFailureReason] to a [TransferResult].
 *
 * Terminal outcomes (COMPLETED, CANCELLED, EXPIRED) are sourced from external conditions
 * (completion, cancellation, expiration) outside this function. Only failure-derived results are
 * produced here: Trust → TRUST_FAILURE, Unrecoverable → UNRECOVERABLE_FAILURE. No failure reason
 * (null) means no terminal failure result has occurred.
 *
 * See SPEC.md §7.6 and specs/protocol/state-machines.yaml (TransferResult).
 *
 * SPEC-ANCHOR: transfer-result
 */
internal fun mapTransferResult(failureReason: TransferFailureReason?): TransferResult? =
    when (failureReason) {
        is TransferFailureReason.Trust -> TransferResult.TRUST_FAILURE
        is TransferFailureReason.Unrecoverable -> TransferResult.UNRECOVERABLE_FAILURE
        null -> null
    }
