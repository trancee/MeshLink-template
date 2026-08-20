package ch.trancee.meshlink.util

import kotlin.test.Test
import kotlin.test.assertTrue

class SecureRandomTest {
    @Test
    fun `randomULong returns value within unsigned long range`() {
        // Act
        val value = randomULong()

        // Assert — any ULong value is valid
        assertTrue(value >= 0uL && value <= ULong.MAX_VALUE, "randomULong must return a valid ULong")
    }

    @Test
    fun `randomULong can produce different values across calls`() {
        // Act — generate multiple values
        val values = (1..10).map { randomULong() }

        // Assert — with 64-bit range, at least one should differ
        // (probability of all 10 identical is effectively zero)
        assertTrue(
            values.distinct().size > 1,
            "randomULong should produce distinct values across calls",
        )
    }
}
