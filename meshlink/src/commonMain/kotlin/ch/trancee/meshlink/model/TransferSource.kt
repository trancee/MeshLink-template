package ch.trancee.meshlink.model

/**
 * Random-access source for outbound transfer data.
 *
 * MeshLink does not read concurrently from one source. The [read] function is called sequentially
 * for distinct missing chunks after selective acknowledgement; re-reads for arbitrary offset ranges
 * are permitted but never concurrent. Implementations must return exactly the requested bytes for
 * each non-final range and must not block indefinitely. Coroutine cancellation propagates through
 * the read.
 */
public interface TransferSource {
    /** Total payload size in bytes. */
    public val total: Long

    /**
     * Reads [length] bytes at [offset].
     *
     * @return ByteArray of exactly [length] bytes, or fewer if end of stream.
     * @throws TransferException if read fails.
     */
    public suspend fun read(offset: Long, length: Int): ByteArray
}
