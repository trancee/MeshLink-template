package ch.trancee.meshlink.model

/**
 * Sink for inbound transfer data.
 *
 * Writes may arrive out of order because route paths and chunks may reorder. MeshLink serializes
 * calls for one sink, but implementations should treat each [write] as potentially non-sequential
 * and assemble the final payload via the [offset] parameter.
 *
 * An identical duplicate write is idempotent; conflicting bytes for the same range fail the
 * transfer.
 */
public interface TransferSink {
    /**
     * Writes [bytes] at [offset]. May arrive out of order.
     *
     * @throws TransferException if write fails.
     */
    public suspend fun write(offset: Long, bytes: ByteArray)

    /** Called when all chunks are acknowledged and transfer completes successfully. */
    public suspend fun complete()

    /**
     * Called when the transfer fails or is cancelled. Application exceptions are wrapped into typed
     * [TransferResult] failures by MeshLink and never leak platform exception text. Occurs at most
     * once and may follow partial writes.
     */
    public suspend fun abort(cause: MeshLinkException?)
}
