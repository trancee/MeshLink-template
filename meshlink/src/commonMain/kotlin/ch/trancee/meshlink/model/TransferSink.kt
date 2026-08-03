package ch.trancee.meshlink.model

/**
 * Sink for inbound transfer data.
 *
 * The SDK calls [write] sequentially in chunk order. Implementations should handle out-of-order
 * delivery via internal buffering if the protocol layer delivers chunks non-sequentially.
 */
public interface TransferSink {
    /**
     * Writes [data] at [offset]. Called in chunk order.
     *
     * @throws TransferException if write fails.
     */
    public suspend fun write(offset: Long, data: ByteArray)

    /** Called when all chunks are acknowledged and transfer completes successfully. */
    public suspend fun complete()

    /** Called when transfer fails or is cancelled. */
    public suspend fun fail(reason: TransferFailureReason)
}
