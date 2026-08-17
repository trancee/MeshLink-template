
package ch.trancee.meshlink.model
/**
 * Immutable bitfield for selective acknowledgement of received chunks. Bit N = 1 means chunk N is
 * received (standard SACK convention). Length is derived from totalChunks and the byte width.
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
internal class Scoreboard
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
        byteSize = ((totalChunks.toInt() + LAST_BIT_INDEX) / BITS_PER_BYTE),
        bytes = ByteArray(((totalChunks.toInt() + LAST_BIT_INDEX) / BITS_PER_BYTE)),
        received = 0,
    )

    public companion object {
        /** Constructs a [Scoreboard] from a raw byte array and chunk count. */
        public fun fromBytes(totalChunks: UInt, bytes: ByteArray): Scoreboard {
            val expectedSize = ((totalChunks.toInt() + LAST_BIT_INDEX) / BITS_PER_BYTE)
            require(bytes.size == expectedSize) {
                "Scoreboard byte array size ${bytes.size} does not match " +
                    "expected size $expectedSize for totalChunks=$totalChunks"
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
        val byteIndex = index / BITS_PER_BYTE
        val bitIndex = index % BITS_PER_BYTE
        val oldByte = bytes[byteIndex]
        val newBytes = bytes.copyOf()
        newBytes[byteIndex] = oldByte.setBit(bitIndex)
        val delta = if (oldByte.isBitSet(bitIndex)) 0 else 1
        return Scoreboard(totalChunks, byteSize, newBytes, received + delta)
    }

    /** Marks chunk [index] as missing. Returns a new [Scoreboard]. */
    public fun markMissing(index: Int): Scoreboard {
        checkIndex(index)
        val byteIndex = index / BITS_PER_BYTE
        val bitIndex = index % BITS_PER_BYTE
        val oldByte = bytes[byteIndex]
        val newBytes = bytes.copyOf()
        newBytes[byteIndex] = oldByte.clearBit(bitIndex)
        val delta = if (oldByte.isBitSet(bitIndex)) -1 else 0
        return Scoreboard(totalChunks, byteSize, newBytes, received + delta)
    }

    /** Returns true if chunk [index] has been received. */
    public fun isReceived(index: Int): Boolean {
        checkIndex(index)
        return bytes[index / BITS_PER_BYTE].isBitSet(index % BITS_PER_BYTE)
    }

    /** Returns true if chunk [index] has not yet been received. */
    public fun isMissing(index: Int): Boolean {
        checkIndex(index)
        return !bytes[index / BITS_PER_BYTE].isBitSet(index % BITS_PER_BYTE)
    }

    /** Returns the raw bitfield as a [ByteArray]. */
    public fun toBytes(): ByteArray = bytes.copyOf()

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
internal class MutableScoreboard(public val totalChunks: UInt) {
    private val bytes = ByteArray(((totalChunks.toInt() + LAST_BIT_INDEX) / BITS_PER_BYTE))
    private var received: Int = 0

    /** Marks chunk [index] as received in-place. */
    public fun markReceived(index: Int) {
        checkIndex(index)
        val byteIndex = index / BITS_PER_BYTE
        val bitIndex = index % BITS_PER_BYTE
        val oldByte = bytes[byteIndex]
        bytes[byteIndex] = oldByte.setBit(bitIndex)
        if (!oldByte.isBitSet(bitIndex)) received++
    }

    /** Marks chunk [index] as missing in-place. */
    public fun markMissing(index: Int) {
        checkIndex(index)
        val byteIndex = index / BITS_PER_BYTE
        val bitIndex = index % BITS_PER_BYTE
        val oldByte = bytes[byteIndex]
        bytes[byteIndex] = oldByte.clearBit(bitIndex)
        if (oldByte.isBitSet(bitIndex)) received--
    }

    /** Returns true if chunk [index] has been received. */
    public fun isReceived(index: Int): Boolean {
        checkIndex(index)
        return bytes[index / BITS_PER_BYTE].isBitSet(index % BITS_PER_BYTE)
    }

    /** Returns true if chunk [index] has not yet been received. */
    public fun isMissing(index: Int): Boolean {
        checkIndex(index)
        return !bytes[index / BITS_PER_BYTE].isBitSet(index % BITS_PER_BYTE)
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

private infix fun Byte.setBit(bit: Int): Byte = (this.toInt() or (ONE shl bit)).toByte()

private infix fun Byte.clearBit(bit: Int): Byte = (this.toInt() and (ONE shl bit).inv()).toByte()

private fun Byte.isBitSet(bit: Int): Boolean = (this.toInt() shr bit) and 1 == 1

/** Computes the total number of set bits across [bytes] for the first [totalChunks] bits. */
private fun computePopcount(bytes: ByteArray, totalChunks: UInt): Int {
    var count = 0
    val fullBytes = totalChunks.toInt() / BITS_PER_BYTE
    val remainderBits = totalChunks.toInt() % BITS_PER_BYTE
    for (i in 0 until fullBytes) {
        count += (bytes[i].toInt() and BYTE_MASK).countOneBits()
    }
    if (remainderBits > 0) {
        // Mask off bits beyond totalChunks in the partial byte
        val mask = (ONE shl remainderBits) - ONE
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
    val remainderBits = totalChunks.toInt() % BITS_PER_BYTE
    if (remainderBits > 0) {
        val mask = (ONE shl remainderBits) - ONE
        masked[totalChunks.toInt() / BITS_PER_BYTE] =
            (masked[totalChunks.toInt() / BITS_PER_BYTE].toInt() and mask).toByte()
    }
    return masked
}

private const val BITS_PER_BYTE: Int = 8
private const val LAST_BIT_INDEX: Int = 7
private const val ONE: Int = 1
private const val BYTE_MASK: Int = 0xFF

// ---------------------------------------------------------------------------
// Scoreboard operations: chunk iteration and bitfield merging
// ---------------------------------------------------------------------------

/** Returns missing chunk indices as a newly allocated list. */
internal fun Scoreboard.missingChunks(): List<Int> =
    (0 until totalChunks.toInt()).filter { isMissing(it) }

/** Lazily iterates missing chunk indices. */
internal fun Scoreboard.missingSequence(): Sequence<Int> =
    (0 until totalChunks.toInt()).asSequence().filter { isMissing(it) }

/** Visits missing chunk indices without allocating a collection. */
internal inline fun Scoreboard.forEachMissing(action: (index: Int) -> Unit) {
    for (index in 0 until totalChunks.toInt()) {
        if (isMissing(index)) {
            action(index)
        }
    }
}

/** Returns the union of two compatible acknowledgement bitfields. */
internal fun Scoreboard.or(other: Scoreboard): Scoreboard =
    merge(other) { left, right -> left.toInt() or right.toInt() }

/** Returns the intersection of two compatible acknowledgement bitfields. */
internal fun Scoreboard.and(other: Scoreboard): Scoreboard =
    merge(other) { left, right -> left.toInt() and right.toInt() }

/** Returns the symmetric difference of two compatible acknowledgement bitfields. */
internal fun Scoreboard.xor(other: Scoreboard): Scoreboard =
    merge(other) { left, right -> left.toInt() xor right.toInt() }

private fun Scoreboard.merge(other: Scoreboard, operation: (Byte, Byte) -> Int): Scoreboard {
    require(totalChunks == other.totalChunks) {
        "Scoreboard operations require matching totalChunks: $totalChunks vs ${other.totalChunks}"
    }
    val left = toBytes()
    val right = other.toBytes()
    val merged = ByteArray(left.size)
    for (index in left.indices) {
        merged[index] = operation(left[index], right[index]).toByte()
    }
    return Scoreboard.fromBytes(totalChunks, merged)
}
