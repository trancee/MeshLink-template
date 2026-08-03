package ch.trancee.meshlink.model

import ch.trancee.meshlink.diagnostics.DiagnosticCode

/**
 * Mutable status of a transfer operation.
 *
 * `offset` = highest contiguous payload boundary acknowledged (outgoing) or accepted by sink
 * (incoming). Out-of-order progress is represented by SACK state in the internal transfer session.
 */
public data class TransferStatus(
    /** Current lifecycle state. */
    public val state: TransferState,

    /** Highest contiguous payload boundary (bytes). */
    public val offset: Long,

    /** Total payload size (bytes). */
    public val total: Long,

    /** Number of retransmission attempts made. */
    public val retryCount: Int,

    /** Terminal outcome; null for non-terminal states. */
    public val transferResult: TransferResult?,

    /** Diagnostic event code, if any. */
    public val diagnosticCode: DiagnosticCode?,

    /** Severity of diagnostic event. */
    public val diagnosticSeverity: DiagnosticSeverity? = null,
)
