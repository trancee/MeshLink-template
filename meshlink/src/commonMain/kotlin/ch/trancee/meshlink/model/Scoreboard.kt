package ch.trancee.meshlink.model

/**
 * Immutable bitfield for selective acknowledgment of received chunks. Bit N = 1 means chunk N is
 * received (standard SACK convention). Length is ceil(totalChunks / 8) bytes derived from
 * [totalChunks].
 *
 * This class is immutable — [markReceived] and [markMissing] return a new [Scoreboard] instance
 * rather than mutating the original, ensuring thread-safe reads without synchronization overhead.
 *
 * SPEC-ANCHOR: scoreboard-model
 */
public class Scoreboard
private constructor(public val totalChunks: UInt, private val bytes: ByteArray) {
    public constructor(
        totalChunks: UInt
    ) : this(totalChunks, ByteArray(((totalChunks.toInt() + 7) / 8)))

    internal companion object {
        public fun fromBytes(totalChunks: UInt, bytes: ByteArray): Scoreboard =
            Scoreboard(totalChunks, bytes)
    }

    /** Marks chunk [index] as received. Returns a new [Scoreboard]. */
    public fun markReceived(index: Int): Scoreboard {
        val new = bytes.copyOf()
        new[index / 8] = new[index / 8].setBit(index % 8)
        return fromBytes(totalChunks, new)
    }

    /** Marks chunk [index] as missing. Returns a new [Scoreboard]. */
    public fun markMissing(index: Int): Scoreboard {
        val new = bytes.copyOf()
        new[index / 8] = new[index / 8].clearBit(index % 8)
        return fromBytes(totalChunks, new)
    }

    /** Returns true if chunk [index] has been received. */
    public fun isReceived(index: Int): Boolean = bytes[index / 8].isBitSet(index % 8)

    /** Returns true if chunk [index] has not yet been received. */
    public fun isMissing(index: Int): Boolean = !isReceived(index)

    /** Returns the list of chunk indices that have not yet been received. */
    public fun missingChunks(): List<Int> = (0 until totalChunks.toInt()).filter { isMissing(it) }

    /** Returns the count of chunks that have been received. */
    public fun receivedCount(): Int = (0 until totalChunks.toInt()).count { isReceived(it) }

    /** Returns the count of chunks that have not yet been received. */
    public fun missingCount(): Int = totalChunks.toInt() - receivedCount()

    /** Returns the raw bitfield as a [ByteArray]. */
    public fun toByteArray(): ByteArray = bytes.copyOf()
}

// ---------------------------------------------------------------------------
// Mutable companion for hot-path accumulators
// ---------------------------------------------------------------------------

/**
 * High-performance mutable accumulator for chunk receipt tracking. Use in hot paths (e.g. receiving
 * a burst of ACKs) where allocating a new immutable [Scoreboard] per update would cause excessive
 * GC pressure. Call [toImmutable] to obtain a thread-safe snapshot for the transfer state.
 */
public class MutableScoreboard(public val totalChunks: UInt) {
    private val bytes = ByteArray(((totalChunks.toInt() + 7) / 8))

    /** Marks chunk [index] as received in-place. */
    public fun markReceived(index: Int) {
        bytes[index / 8] = bytes[index / 8].setBit(index % 8)
    }

    /** Marks chunk [index] as missing in-place. */
    public fun markMissing(index: Int) {
        bytes[index / 8] = bytes[index / 8].clearBit(index % 8)
    }

    /** Returns true if chunk [index] has been received. */
    public fun isReceived(index: Int): Boolean = bytes[index / 8].isBitSet(index % 8)

    /** Returns the count of received chunks. */
    public fun receivedCount(): Int = (0 until totalChunks.toInt()).count { isReceived(it) }

    /** Returns the count of missing chunks. */
    public fun missingCount(): Int = totalChunks.toInt() - receivedCount()

    /** Converts this mutable scoreboard to an immutable snapshot. */
    public fun toImmutable(): Scoreboard = Scoreboard.fromBytes(totalChunks, bytes.copyOf())
}

// ---------------------------------------------------------------------------
// Internal bit manipulation helpers
// ---------------------------------------------------------------------------

private infix fun Byte.setBit(bit: Int): Byte = ((this.toInt() or (1 shl bit)) and 0xFF).toByte()

private infix fun Byte.clearBit(bit: Int): Byte =
    ((this.toInt() and (1 shl bit).inv()) and 0xFF).toByte()

private fun Byte.isBitSet(bit: Int): Boolean = (this.toInt() shr bit) and 1 == 1
