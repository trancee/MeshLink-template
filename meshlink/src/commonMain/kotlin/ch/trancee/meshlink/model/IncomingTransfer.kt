package ch.trancee.meshlink.model

import kotlin.time.Instant
import kotlinx.coroutines.flow.StateFlow

/**
 * Incoming finite payload awaiting host decision.
 *
 * Emitted via [MeshLink.transfers] with state [TransferState.AWAITING_DECISION]. Host must call
 * [accept] or [reject] within the decision window.
 */
public class IncomingTransfer
internal constructor(
    /** Discriminates MESSAGE vs PAYLOAD wire formats. */
    public val kind: TransferKind,

    /** Origin-scoped identifier. */
    public val id: UInt,

    /** Origin peer identity. */
    public val origin: PeerIdentity,

    /** Priority inherited from manifest. */
    public val priority: Priority,

    /** Total payload size (bytes). */
    public val total: Long,

    /** Chunk size (bytes). */
    public val chunkSize: Int,

    /** Time-to-live deadline. */
    public val expiresAt: Instant,

    /** Current status. */
    public val status: StateFlow<TransferStatus>,
) {
    /**
     * Accepts the incoming transfer with the provided sink. Idempotent — subsequent calls are
     * no-ops after first acceptance.
     */
    @Suppress("UNUSED_PARAMETER")
    public suspend fun accept(sink: TransferSink) {
        TODO("Not implemented — scaffold for BCV baseline")
    }

    /**
     * Rejects the incoming transfer. Idempotent — subsequent calls are no-ops after first
     * rejection.
     */
    public suspend fun reject() {
        TODO("Not implemented — scaffold for BCV baseline")
    }
}
