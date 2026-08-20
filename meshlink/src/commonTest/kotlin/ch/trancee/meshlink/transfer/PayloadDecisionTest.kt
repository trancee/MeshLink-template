package ch.trancee.meshlink.transfer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PayloadDecisionTest {
    @Test
    fun `wire codes match spec values`() {
        // Arrange — spec: ACCEPTED=0x00, REJECTED=0x01
        val expectedCodes: Map<String, UByte> =
            mapOf(
                "ACCEPTED" to 0.toUByte(),
                "REJECTED" to 1.toUByte(),
            )

        // Act
        val entries = PayloadDecision.entries

        // Assert
        assertEquals(2, entries.size, "PayloadDecision must have 2 entries")
        expectedCodes.forEach { (name, expectedCode) ->
            val decision = PayloadDecision.valueOf(name)
            assertEquals(expectedCode, decision.code, "Wire code mismatch for $name")
        }
    }

    @Test
    fun `all entries have non-null name`() {
        // Act + Assert
        PayloadDecision.entries.forEach { assertNotNull(it.name) }
    }
}
