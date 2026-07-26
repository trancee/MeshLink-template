package ch.trancee.meshlink.util

import kotlin.random.Random

/** Cryptographically secure random number generator (multiplatform). */
internal val secureRandom = Random.Default

/** Generates a random ULong. */
public fun randomULong(): ULong {
    val bytes = ByteArray(8)
    secureRandom.nextBytes(bytes)
    var result: ULong = 0u
    for (b in bytes) {
        result = (result shl 8) or b.toULong()
    }
    return result
}
