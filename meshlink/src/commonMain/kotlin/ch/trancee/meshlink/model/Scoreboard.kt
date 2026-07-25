package ch.trancee.meshlink.model

import kotlinx.serialization.Serializable

/**
 * Immutable bitfield for selective acknowledgment of received chunks. Bit N = 1 means chunk N is
 * received (standard SACK convention). Length is ceil(totalChunks / 8) bytes derived from
 * [totalChunks].
 *
 * This class is immutable — [markReceived] and [markMissing] return a new [Scoreboard] instance
 * rather than mutating the original, ensuring thread-safe reads without synchronization overhead.
 */
@Serializable
class Scoreboard private constructor(val totalChunks: UInt, private val bytes: ByteArray) {
    constructor(totalChunks: UInt) : this(totalChunks, ByteArray(((totalChunks.toInt() + 7) / 8)))

    /** Marks chunk [index] as received. Returns a new [Scoreboard]. */
    fun markReceived(index: Int): Scoreboard {
        val new = bytes.copyOf()
        new[index / 8] = new[index / 8].setBit(index % 8)
        return Scoreboard(totalChunks, new)
    }

    /** Marks chunk [index] as missing. Returns a new [Scoreboard]. */
    fun markMissing(index: Int): Scoreboard {
        val new = bytes.copyOf()
        new[index / 8] = new[index / 8].clearBit(index % 8)
        return Scoreboard(totalChunks, new)
    }

    /** Returns true if chunk [index] has been received. */
    fun isReceived(index: Int): Boolean = bytes[index / 8].isBitSet(index % 8)

    /** Returns true if chunk [index] has not yet been received. */
    fun isMissing(index: Int): Boolean = !isReceived(index)

    /** Returns the list of chunk indices that have not yet been received. */
    fun missingChunks(): List<Int> = (0 until totalChunks.toInt()).filter { isMissing(it) }

    /** Returns the count of chunks that have been received. */
    fun receivedCount(): Int = (0 until totalChunks.toInt()).count { isReceived(it) }

    /** Returns the count of chunks that have not yet been received. */
    fun missingCount(): Int = totalChunks.toInt() - receivedCount()

    /** Returns the raw bitfield as a [ByteArray]. */
    fun toByteArray(): ByteArray = bytes.copyOf()
}

// ---------------------------------------------------------------------------
// Mutable companion for hot-path accumulators
// ---------------------------------------------------------------------------

/**
 * High-performance mutable accumulator for chunk receipt tracking. Use in hot paths (e.g. receiving
 * a burst of ACKs) where allocating a new immutable [Scoreboard] per update would cause excessive
 * GC pressure. Call [toImmutable] to obtain a thread-safe snapshot for the transfer state.
 */
class MutableScoreboard(totalChunks: UInt) {
    private val bytes = ByteArray(((totalChunks.toInt() + 7) / 8))

    /** Marks chunk [index] as received in-place. */
    fun markReceived(index: Int) {
        bytes[index / 8] = bytes[index / 8].setBit(index % 8)
    }

    /** Marks chunk [index] as missing in-place. */
    fun markMissing(index: Int) {
        bytes[index / 8] = bytes[index / 8].clearBit(index % 8)
    }

    /** Returns true if chunk [index] has been received. */
    fun isReceived(index: Int): Boolean = bytes[index / 8].isBitSet(index % 8)

    /** Returns the count of received chunks. */
    fun receivedCount(): Int = (0 until totalChunks.toInt()).count { isReceived(it) }

    /** Returns the count of missing chunks. */
    fun missingCount(): Int = totalChunks.toInt() - receivedCount()

    /** Converts this mutable scoreboard to an immutable snapshot. */
    fun toImmutable(): Scoreboard = Scoreboard(totalChunks, bytes.copyOf())
}

// ---------------------------------------------------------------------------
// Internal bit manipulation helpers
// ---------------------------------------------------------------------------

private infix fun Byte.setBit(bit: Int): Byte = ((this.toInt() or (1 shl bit)) and 0xFF).toByte()

private infix fun Byte.clearBit(bit: Int): Byte =
    ((this.toInt() and (1 shl bit).inv()) and 0xFF).toByte()

private fun Byte.isBitSet(bit: Int): Boolean = (this.toInt() shr bit) and 1 == 1
