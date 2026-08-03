package ch.trancee.meshlink

import ch.trancee.meshlink.model.Priority
import ch.trancee.meshlink.model.RoutingPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class RoutingPolicyTest {
    @Test
    fun `priority supplies elapsed delivery lifetime`() {
        // Arrange

        // Act
        val lifetimes = Priority.entries.map(RoutingPolicy::ttl)

        // Assert
        assertEquals(listOf(10.minutes, 5.minutes, 1.minutes), lifetimes)
    }

    @Test
    fun `routing hop limit is sixteen`() {
        // Arrange

        // Act
        val actual = RoutingPolicy.MAX_HOPS

        // Assert
        assertEquals(16, actual)
    }
}
