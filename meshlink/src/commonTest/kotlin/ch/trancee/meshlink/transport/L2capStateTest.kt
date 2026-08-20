package ch.trancee.meshlink.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class L2capStateTest {
    @Test
    fun `has six states matching spec ordinal values`() {
        // Arrange — spec defines 6 states with ordinals UNSUPPORTED=0 through DISABLED=5
        val expectedNames =
            listOf("UNSUPPORTED", "AVAILABLE", "CONNECTING", "ACTIVE", "BACKING_OFF", "DISABLED")

        // Act
        val entries = L2capState.entries

        // Assert
        assertEquals(6, entries.size, "L2capState must have 6 entries")
        assertEquals(expectedNames, entries.map { it.name })
        entries.forEach { state -> assertNotNull(state.ordinal) }
    }

    @Test
    fun `ordinals match spec values`() {
        // Arrange
        val expectedOrdinals =
            mapOf(
                "UNSUPPORTED" to 0,
                "AVAILABLE" to 1,
                "CONNECTING" to 2,
                "ACTIVE" to 3,
                "BACKING_OFF" to 4,
                "DISABLED" to 5,
            )

        // Act + Assert
        expectedOrdinals.forEach { (name, expected) ->
            val state = L2capState.valueOf(name)
            assertEquals(expected, state.ordinal, "Ordinal mismatch for $name")
        }
    }
}
