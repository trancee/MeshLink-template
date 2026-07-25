package ch.trancee.meshlink.model

import kotlin.time.Instant

/** State that a transfer session can be in. */
public enum class TransferState {
    /** Transfer is actively in progress. */
    IN_PROGRESS,
    /** Route was lost; waiting for route recovery before resuming. */
    WAITING_FOR_ROUTE,
    /** Actively retrying: retransmitting missing chunks with backoff. */
    RETRYING,
    /** All chunks received and scoreboard complete. */
    COMPLETED,
    /** Terminal failure — unrecoverable or trust-related. */
    FAILED,
    /** Retry budget or grace period exhausted without completion. */
    TIMED_OUT,
}

/**
 * Drives chunked transfer with selective ACK and cut-through relay support.
 *
 * The [chunkSize] is selected by the local [PowerTier] at session start and bounded by the peer's
 * advertised MTU.
 *
 * The [scoreboard] uses a dynamic bitfield whose length is derived from [totalChunks] — bit N = 1
 * means chunk N is received (standard SACK).
 */
public data class TransferSession(
    /** Unique 64-bit token identifying this transfer session. */
    public val sessionId: SessionId,
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
    public val totalBytes: Long,
    /** Bytes received so far. */
    public val bytesReceived: Long,
    /** When this transfer session was started. */
    public val startedAt: Instant,
    /** Deadline for WAITING_FOR_ROUTE state; null when in-progress or retrying. */
    public val expiresAt: Instant?,
    /** Number of retransmit attempts made so far. */
    public val retryCount: Int,
    /** Why this session reached a terminal failure state. */
    public val failureReason: TransferFailureReason?,
)
