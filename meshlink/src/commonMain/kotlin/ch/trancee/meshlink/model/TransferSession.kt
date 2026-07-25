package ch.trancee.meshlink.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/** State that a transfer session can be in. */
@Serializable
enum class TransferState {
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
@Serializable
data class TransferSession(
    /** Unique 64-bit token identifying this transfer session. */
    val sessionId: SessionId,
    /** Final destination peer for this transfer. */
    val destination: PeerIdentity,
    /** Current lifecycle state of this transfer. */
    val state: TransferState,
    /** Selected chunk size in bytes (bounded by peer MTU). */
    val chunkSize: Int,
    /** Total number of chunks in this transfer. */
    val totalChunks: UInt,
    /** Tracks which chunks have been received via SACK bitfield. */
    val scoreboard: Scoreboard,
    /** Total payload size in bytes. */
    val totalBytes: Long,
    /** Bytes received so far. */
    val bytesReceived: Long,
    /** When this transfer session was started. */
    val startedAt: Instant,
    /** Deadline for WAITING_FOR_ROUTE state; null when in-progress or retrying. */
    val expiresAt: Instant?,
    /** Number of retransmit attempts made so far. */
    val retryCount: Int,
    /** Why this session reached a terminal failure state. */
    val failureReason: TransferFailureReason?,
)
