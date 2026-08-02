package ch.trancee.meshlink.model

import kotlin.time.Instant

/** State that a transfer session can be in. */
public enum class TransferState {
    AWAITING_DECISION,
    TRANSFERRING,
    ROUTE_UNAVAILABLE,
    RETRANSMITTING,
    COMPLETED,
    CANCELLED,
    FAILED,
    EXPIRED,
}

/**
 * Drives chunked transfer with selective ACK and cut-through relay support.
 *
 * The [chunkSize] is selected by the local [PowerMode] at session start and bounded by the peer's
 * advertised MTU.
 *
 * The [scoreboard] uses a dynamic bitfield whose length is derived from [totalChunks] — bit N = 1
 * means chunk N is received (standard SACK).
 *
 * SPEC-ANCHOR: transfer-session-model
 */
public data class TransferSession(
    /** Origin-scoped 32-bit identifier identifying this finite payload. */
    public val transferId: TransferId,
    /** Final destination peer for this transfer. */
    public val destination: PeerIdentity,
    /** QoS priority inherited from the originating RoutingMessage. */
    public val priority: Priority,
    /** Current lifecycle state of this transfer. */
    public val state: TransferState,
    /** Selected chunk size in bytes (bounded by peer MTU). */
    public val chunkSize: Int,
    /** Total number of chunks in this transfer. */
    public val totalChunks: UInt,
    /** Tracks which chunks have been received via SACK bitfield. */
    public val scoreboard: Scoreboard,
    /** Total payload size in bytes. */
    public val total: Long,
    /** Highest contiguous payload boundary accepted or acknowledged. */
    public val offset: Long,
    /** When this transfer session was started. */
    public val startedAt: Instant,
    /** Deadline for WAITING_FOR_ROUTE state; null when in-progress or retrying. */
    public val expiresAt: Instant?,
    /** Number of retransmit attempts made so far. */
    public val retryCount: Int,
    /** Why this session reached a terminal failure state. */
    public val failureReason: TransferFailureReason?,
)
