package ch.trancee.meshlink.model

/** Returns missing chunk indices as a newly allocated list. */
public fun Scoreboard.missingChunks(): List<Int> =
    (0 until totalChunks.toInt()).filter { isMissing(it) }

/** Lazily iterates missing chunk indices. */
public fun Scoreboard.missingSequence(): Sequence<Int> =
    (0 until totalChunks.toInt()).asSequence().filter { isMissing(it) }

/** Visits missing chunk indices without allocating a collection. */
public inline fun Scoreboard.forEachMissing(action: (index: Int) -> Unit) {
    for (index in 0 until totalChunks.toInt()) {
        if (isMissing(index)) {
            action(index)
        }
    }
}

/** Returns the union of two compatible acknowledgement bitfields. */
public fun Scoreboard.or(other: Scoreboard): Scoreboard =
    merge(other) { left, right -> left.toInt() or right.toInt() }

/** Returns the intersection of two compatible acknowledgement bitfields. */
public fun Scoreboard.and(other: Scoreboard): Scoreboard =
    merge(other) { left, right -> left.toInt() and right.toInt() }

/** Returns the symmetric difference of two compatible acknowledgement bitfields. */
public fun Scoreboard.xor(other: Scoreboard): Scoreboard =
    merge(other) { left, right -> left.toInt() xor right.toInt() }

private fun Scoreboard.merge(other: Scoreboard, operation: (Byte, Byte) -> Int): Scoreboard {
    require(totalChunks == other.totalChunks) {
        "Scoreboard operations require matching totalChunks: $totalChunks vs ${other.totalChunks}"
    }
    val left = toByteArray()
    val right = other.toByteArray()
    val merged = ByteArray(left.size)
    for (index in left.indices) {
        merged[index] = operation(left[index], right[index]).toByte()
    }
    return Scoreboard.fromBytes(totalChunks, merged)
}
