package ch.trancee.meshlink.util

import kotlin.random.Random

/** Cryptographically secure random number generator (multiplatform). */
internal val secureRandom = Random.Default

/** Generates a random ULong. */
public fun randomULong(): ULong {
    val bytes = ByteArray(RANDOM_ULONG_BYTE_LENGTH)
    secureRandom.nextBytes(bytes)
    return bytes.toULongBE(0)
}

private const val RANDOM_ULONG_BYTE_LENGTH: Int = 8
