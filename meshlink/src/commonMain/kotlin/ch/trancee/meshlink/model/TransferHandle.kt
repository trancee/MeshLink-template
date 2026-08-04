package ch.trancee.meshlink.model

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.StateFlow

/**
 * Handle for an outbound PAYLOAD operation.
 *
 * Returned by [MeshLink.sendPayload]. Provides status observation and cancellation.
 */
public class TransferHandle
internal constructor(
    /** Unique transfer identifier for correlation. */
    public val id: TransferId,

    /** Current transfer status as a StateFlow. */
    public val status: StateFlow<TransferStatus>,

    /** Awaitable terminal outcome. */
    public val outcome: ReceiveChannel<TransferResult>,

    /** Cancels the transfer. Idempotent. */
    public val cancel: suspend () -> Unit,
) {
    /** Suspends until terminal outcome. */
    public suspend fun await(): TransferResult = outcome.receive()
}
