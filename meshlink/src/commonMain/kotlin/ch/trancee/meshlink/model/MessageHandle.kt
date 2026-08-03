package ch.trancee.meshlink.model

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.StateFlow

/**
 * Handle for an outbound MESSAGE operation.
 *
 * Returned by [MeshLink.sendMessage]. Provides status observation and cancellation.
 */
public class MessageHandle
internal constructor(
    /** Unique message identifier for correlation. */
    public val id: MessageId,

    /** Current transfer status as a StateFlow. */
    public val status: StateFlow<TransferStatus>,

    /** Awaitable terminal outcome. */
    public val outcome: ReceiveChannel<TransferResult>,

    /** Cancels the message. Idempotent. */
    public val cancel: suspend () -> Unit,
) {
    /** Suspends until terminal outcome. */
    public suspend fun await(): TransferResult = outcome.receive()
}
