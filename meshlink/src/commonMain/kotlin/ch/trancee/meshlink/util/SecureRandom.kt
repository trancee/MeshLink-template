package ch.trancee.meshlink.util

import kotlin.random.Random

/** Cryptographically secure random number generator (multiplatform). */
internal val secureRandom = Random.Default

/** Generates a random ULong. */
public fun randomULong(): ULong {
    val bytes = ByteArray(RANDOM_ULONG_BYTE_LENGTH)
    secureRandom.nextBytes(bytes)
    var result: ULong = 0u
    for (byte in bytes) {
        result = (result shl BYTE_SHIFT) or byte.toUByte().toULong()
    }
    return result
}

private const val RANDOM_ULONG_BYTE_LENGTH: Int = 8
private const val BYTE_SHIFT: Int = 8
