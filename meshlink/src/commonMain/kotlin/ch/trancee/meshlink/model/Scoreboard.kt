package ch.trancee.meshlink.model

/**
 * Immutable bitfield for selective acknowledgment of received chunks. Bit N = 1 means chunk N is
 * received (standard SACK convention). Length is ceil(totalChunks / 8) bytes derived from
 * [totalChunks].
 *
 * This class is immutable — [markReceived] and [markMissing] return a new [Scoreboard] instance
 * rather than mutating the original, ensuring thread-safe reads without synchronization overhead.
 *
 * Counts ([receivedCount], [missingCount]) and [isComplete] are O(1) — cached at construction and
 * updated incrementally on each [markReceived] / [markMissing].
 *
 * SPEC-ANCHOR: scoreboard-model
 */
public class Scoreboard
private constructor(
    public val totalChunks: UInt,
    public val byteSize: Int,
    private val bytes: ByteArray,
    private val received: Int,
) {
    public constructor(
        totalChunks: UInt
    ) : this(
        totalChunks = totalChunks,
        byteSize = ((totalChunks.toInt() + 7) / 8),
        bytes = ByteArray(((totalChunks.toInt() + 7) / 8)),
        received = 0,
    )

    /**
     * Pre-allocated for [ScoreboardEncoding.FIXED] — byte array sized for [maxChunksPerSession] but
     * only bits 0..[totalChunks-1] are meaningful.
     */
    public constructor(
        totalChunks: UInt,
        maxChunksPerSession: UInt,
    ) : this(
        totalChunks = totalChunks,
        byteSize = ((maxChunksPerSession.toInt() + 7) / 8),
        bytes = ByteArray(((maxChunksPerSession.toInt() + 7) / 8)),
        received = 0,
    )

    public companion object {
        /** Constructs a [Scoreboard] from a raw byte array and chunk count. */
        public fun fromBytes(totalChunks: UInt, bytes: ByteArray): Scoreboard {
            val expectedSize = ((totalChunks.toInt() + 7) / 8)
            require(bytes.size == expectedSize) {
                "Scoreboard byte array size ${bytes.size} does not match expected size $expectedSize for totalChunks=$totalChunks"
            }
            val maskedBytes = maskBits(totalChunks, bytes)
            return Scoreboard(
                totalChunks,
                expectedSize,
                maskedBytes,
                computePopcount(maskedBytes, totalChunks),
            )
        }
    }

    /** Total number of chunks marked as received. O(1). */
    public fun receivedCount(): Int = received

    /** Total number of chunks not yet received. O(1). */
    public fun missingCount(): Int = totalChunks.toInt() - received

    /** True when every chunk has been received (missingCount == 0). O(1). */
    public fun isComplete(): Boolean = received == totalChunks.toInt()

    /** Marks chunk [index] as received. Returns a new [Scoreboard]. */
    public fun markReceived(index: Int): Scoreboard {
        checkIndex(index)
        val byteIndex = index / 8
        val bitIndex = index % 8
        val oldByte = bytes[byteIndex]
        val newBytes = bytes.copyOf()
        newBytes[byteIndex] = oldByte.setBit(bitIndex)
        val delta = if (oldByte.isBitSet(bitIndex)) 0 else 1
        return Scoreboard(totalChunks, byteSize, newBytes, received + delta)
    }

    /** Marks chunk [index] as missing. Returns a new [Scoreboard]. */
    public fun markMissing(index: Int): Scoreboard {
        checkIndex(index)
        val byteIndex = index / 8
        val bitIndex = index % 8
        val oldByte = bytes[byteIndex]
        val newBytes = bytes.copyOf()
        newBytes[byteIndex] = oldByte.clearBit(bitIndex)
        val delta = if (oldByte.isBitSet(bitIndex)) -1 else 0
        return Scoreboard(totalChunks, byteSize, newBytes, received + delta)
    }

    /** Returns true if chunk [index] has been received. */
    public fun isReceived(index: Int): Boolean {
        checkIndex(index)
        return bytes[index / 8].isBitSet(index % 8)
    }

    /** Returns true if chunk [index] has not yet been received. */
    public fun isMissing(index: Int): Boolean {
        checkIndex(index)
        return !bytes[index / 8].isBitSet(index % 8)
    }

    /**
     * Returns the list of chunk indices that have not yet been received. Allocates a new list — use
     * [forEachMissing] for zero-allocation iteration.
     */
    public fun missingChunks(): List<Int> = (0 until totalChunks.toInt()).filter { isMissing(it) }

    /**
     * Lazily iterates missing chunk indices without allocating a list. Prefer over [missingChunks]
     * when only iterating (no list needed).
     */
    public fun missingSequence(): Sequence<Int> =
        (0 until totalChunks.toInt()).asSequence().filter { isMissing(it) }

    /** Calls [action] for each missing chunk index without allocating a collection. */
    public inline fun forEachMissing(action: (index: Int) -> Unit) {
        for (i in 0 until totalChunks.toInt()) {
            if (isMissing(i)) action(i)
        }
    }

    /**
     * Merges this scoreboard with [other] — a bit N is set in the result if it is set in either
     * receiver. Useful for combining ACK bitfields from multiple mesh peers. Both scoreboards must
     * have the same [totalChunks] and byte size.
     */
    public fun or(other: Scoreboard): Scoreboard {
        requireCompatible(other)
        val merged = ByteArray(byteSize)
        var mergedReceived = 0
        for (i in 0 until byteSize) {
            val mergedByte = this.bytes[i].intOr(other.bytes[i])
            merged[i] = mergedByte
            mergedReceived += mergedByte.popcount()
        }
        return Scoreboard(totalChunks, byteSize, merged, mergedReceived)
    }

    /**
     * Intersects this scoreboard with [other] — a bit N is set in the result only if it is set in
     * both receivers. Useful for finding chunks all relay peers have confirmed. Both scoreboards
     * must have the same [totalChunks] and byte size.
     */
    public fun and(other: Scoreboard): Scoreboard {
        requireCompatible(other)
        val merged = ByteArray(byteSize)
        var mergedReceived = 0
        for (i in 0 until byteSize) {
            val mergedByte = this.bytes[i].intAnd(other.bytes[i])
            merged[i] = mergedByte
            mergedReceived += mergedByte.popcount()
        }
        return Scoreboard(totalChunks, byteSize, merged, mergedReceived)
    }

    /**
     * Symmetric difference of this scoreboard and [other] — bits set in exactly one receiver. Both
     * scoreboards must have the same [totalChunks] and byte size.
     */
    public fun xor(other: Scoreboard): Scoreboard {
        requireCompatible(other)
        val merged = ByteArray(byteSize)
        var mergedReceived = 0
        for (i in 0 until byteSize) {
            val mergedByte = this.bytes[i].intXor(other.bytes[i])
            merged[i] = mergedByte
            mergedReceived += mergedByte.popcount()
        }
        return Scoreboard(totalChunks, byteSize, merged, mergedReceived)
    }

    /** Returns the raw bitfield as a [ByteArray]. */
    public fun toByteArray(): ByteArray = bytes.copyOf()

    // -------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------

    private fun checkIndex(index: Int) {
        if (index < 0 || index >= totalChunks.toInt()) {
            throw IndexOutOfBoundsException(
                "Chunk index $index is out of range [0, ${totalChunks.toInt()})"
            )
        }
    }

    private fun requireCompatible(other: Scoreboard) {
        require(this.totalChunks == other.totalChunks) {
            "Scoreboard.or/and/xor require matching totalChunks: ${this.totalChunks} vs ${other.totalChunks}"
        }
        require(this.byteSize == other.byteSize) {
            "Scoreboard.or/and/xor require matching byte sizes: ${this.byteSize} vs ${other.byteSize}"
        }
    }
}

// ---------------------------------------------------------------------------
// Mutable companion for hot-path accumulators
// ---------------------------------------------------------------------------

/**
 * High-performance mutable accumulator for chunk receipt tracking. Use in hot paths (e.g. receiving
 * a burst of ACKs) where allocating a new immutable [Scoreboard] per update would cause excessive
 * GC pressure. Call [toImmutable] to obtain a thread-safe snapshot for the transfer state.
 *
 * Counts are O(1) — tracked incrementally on each mutation.
 */
public class MutableScoreboard(public val totalChunks: UInt) {
    private val bytes = ByteArray(((totalChunks.toInt() + 7) / 8))
    private var received: Int = 0

    /** Marks chunk [index] as received in-place. */
    public fun markReceived(index: Int) {
        checkIndex(index)
        val byteIndex = index / 8
        val bitIndex = index % 8
        val oldByte = bytes[byteIndex]
        bytes[byteIndex] = oldByte.setBit(bitIndex)
        if (!oldByte.isBitSet(bitIndex)) received++
    }

    /** Marks chunk [index] as missing in-place. */
    public fun markMissing(index: Int) {
        checkIndex(index)
        val byteIndex = index / 8
        val bitIndex = index % 8
        val oldByte = bytes[byteIndex]
        bytes[byteIndex] = oldByte.clearBit(bitIndex)
        if (oldByte.isBitSet(bitIndex)) received--
    }

    /** Returns true if chunk [index] has been received. */
    public fun isReceived(index: Int): Boolean {
        checkIndex(index)
        return bytes[index / 8].isBitSet(index % 8)
    }

    /** Returns true if chunk [index] has not yet been received. */
    public fun isMissing(index: Int): Boolean {
        checkIndex(index)
        return !bytes[index / 8].isBitSet(index % 8)
    }

    /** Returns the count of received chunks. O(1). */
    public fun receivedCount(): Int = received

    /** Returns the count of missing chunks. O(1). */
    public fun missingCount(): Int = totalChunks.toInt() - received

    /** True when every chunk has been received. O(1). */
    public fun isComplete(): Boolean = received == totalChunks.toInt()

    /** Converts this mutable scoreboard to an immutable snapshot. */
    public fun toImmutable(): Scoreboard = Scoreboard.fromBytes(totalChunks, bytes.copyOf())

    private fun checkIndex(index: Int) {
        if (index < 0 || index >= totalChunks.toInt()) {
            throw IndexOutOfBoundsException(
                "Chunk index $index is out of range [0, ${totalChunks.toInt()})"
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Internal bit manipulation helpers
// ---------------------------------------------------------------------------

private infix fun Byte.setBit(bit: Int): Byte = (this.toInt() or (1 shl bit)).toByte()

private infix fun Byte.clearBit(bit: Int): Byte = (this.toInt() and (1 shl bit).inv()).toByte()

private fun Byte.isBitSet(bit: Int): Boolean = (this.toInt() shr bit) and 1 == 1

/** Counts the number of set bits in this byte (population count). */
private fun Byte.popcount(): Int = (this.toInt() and 0xFF).countOneBits()

/** Bitwise OR of two bytes, returning a Byte. */
private fun Byte.intOr(other: Byte): Byte = (this.toInt() or other.toInt()).toByte()

/** Bitwise AND of two bytes, returning a Byte. */
private fun Byte.intAnd(other: Byte): Byte = (this.toInt() and other.toInt()).toByte()

/** Bitwise XOR of two bytes, returning a Byte. */
private fun Byte.intXor(other: Byte): Byte = (this.toInt() xor other.toInt()).toByte()

/** Computes the total number of set bits across [bytes] for the first [totalChunks] bits. */
private fun computePopcount(bytes: ByteArray, totalChunks: UInt): Int {
    var count = 0
    val fullBytes = totalChunks.toInt() / 8
    val remainderBits = totalChunks.toInt() % 8
    for (i in 0 until fullBytes) {
        count += bytes[i].countOneBits()
    }
    if (remainderBits > 0) {
        // Mask off bits beyond totalChunks in the partial byte
        val mask = (1 shl remainderBits) - 1
        count += (bytes[fullBytes].toInt() and mask).countOneBits()
    }
    return count
}

/**
 * Returns a defensive copy of [bytes] with bits beyond [totalChunks] cleared in the last partial
 * byte. Ensures stored bitfields never carry phantom bits that would corrupt bitwise merge counts
 * in [or], [and], and [xor].
 */
private fun maskBits(totalChunks: UInt, bytes: ByteArray): ByteArray {
    val masked = bytes.copyOf()
    val remainderBits = totalChunks.toInt() % 8
    if (remainderBits > 0) {
        val mask = (1 shl remainderBits) - 1
        masked[totalChunks.toInt() / 8] =
            (masked[totalChunks.toInt() / 8].toInt() and mask).toByte()
    }
    return masked
}
