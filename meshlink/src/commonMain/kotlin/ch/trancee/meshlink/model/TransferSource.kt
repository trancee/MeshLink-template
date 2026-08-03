package ch.trancee.meshlink.model

/**
 * Random-access source for outbound transfer data.
 *
 * Implementations must be thread-safe. The SDK will call [read] concurrently from multiple
 * coroutines during retransmission; implement locking internally if needed.
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
