package ch.trancee.meshlink

import ch.trancee.meshlink.model.Priority
import ch.trancee.meshlink.model.RoutingPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class RoutingPolicyTest {

    @Test
    fun `ttl maps each priority to its expected delivery lifetime`() {
        // Arrange — expected TTL per priority (SPEC-ANCHOR: ttl-by-priority)
        val expected =
            mapOf(
                Priority.HIGH to 10.minutes,
                Priority.NORMAL to 5.minutes,
                Priority.LOW to 1.minutes,
            )

        // Act + Assert — verify each mapping individually, including positivity
        for ((priority, expectedTtl) in expected) {
            val actual = RoutingPolicy.ttl(priority)
            assertEquals(expectedTtl, actual, "TTL mismatch for $priority")
            assertTrue(actual > Duration.ZERO, "TTL for $priority must be positive")
        }
    }

    @Test
    fun `ttl produces distinct values for each priority`() {
        // Arrange
        val ttlValues = Priority.entries.map(RoutingPolicy::ttl)

        // Act — check for duplicates
        val unique = ttlValues.toSet()

        // Assert — each priority maps to a distinct duration
        assertEquals(Priority.entries.size, unique.size)
        // And TTLs are ordered by priority (HIGH > NORMAL > LOW)
        assertTrue(ttlValues[0] > ttlValues[1])
        assertTrue(ttlValues[1] > ttlValues[2])
    }

    @Test
    fun `ttl returns positive duration for every priority`() {
        // Arrange + Act
        val allTtls = Priority.entries.associateWith { RoutingPolicy.ttl(it) }

        // Assert — every priority has a positive duration
        assertEquals(Priority.entries.size, allTtls.size)
        assertTrue(allTtls.values.all { it > Duration.ZERO })
    }

    @Test
    fun `routing hop limit is correct and bounded`() {
        // Assert — the constant is 16, positive, and within a reasonable mesh routing range
        assertEquals(16, RoutingPolicy.MAXIMUM_HOP_COUNT)
        assertTrue(RoutingPolicy.MAXIMUM_HOP_COUNT > 0)
        assertTrue(RoutingPolicy.MAXIMUM_HOP_COUNT >= 8)
        assertTrue(RoutingPolicy.MAXIMUM_HOP_COUNT <= 32)
    }
}
