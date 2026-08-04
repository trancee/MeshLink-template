package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MutableScoreboardTest {
    // ---- Mutation tests ----

    @Test
    fun `MutableScoreboard markReceived works`() {
        // Arrange
        val msb = MutableScoreboard(8u)

        // Act
        msb.markReceived(2)

        // Assert
        assertTrue(msb.isReceived(2))
        assertEquals(1, msb.receivedCount())
    }

    @Test
    fun `MutableScoreboard toImmutable creates Scoreboard`() {
        // Arrange
        val msb = MutableScoreboard(4u)
        msb.markReceived(0)
        msb.markReceived(1)

        // Act
        val immutable = msb.toImmutable()

        // Assert
        assertTrue(immutable.isReceived(0))
        assertTrue(immutable.isReceived(1))
    }

    @Test
    fun `MutableScoreboard missingCount returns correct count`() {
        // Arrange
        val msb = MutableScoreboard(8u)
        msb.markReceived(0)
        msb.markReceived(1)
        msb.markReceived(2)

        // Act
        val missing = msb.missingCount()

        // Assert
        assertEquals(5, missing)
    }

    @Test
    fun `MutableScoreboard markMissing clears a bit`() {
        // Arrange
        val msb = MutableScoreboard(8u)
        msb.markReceived(3)

        // Act
        msb.markMissing(3)

        // Assert
        assertFalse(msb.isReceived(3))
        assertEquals(0, msb.receivedCount())
    }

    // ---- isComplete tests ----

    @Test
    fun `MutableScoreboard isComplete false when empty`() {
        // Arrange
        val msb = MutableScoreboard(4u)

        // Act
        val complete = msb.isComplete()

        // Assert
        assertFalse(complete)
    }

    @Test
    fun `MutableScoreboard isComplete true when all received`() {
        // Arrange
        val msb = MutableScoreboard(4u)

        // Act
        msb.markReceived(0)
        msb.markReceived(1)
        msb.markReceived(2)
        msb.markReceived(3)

        // Assert
        assertTrue(msb.isComplete())
    }

    // ---- State query tests ----

    @Test
    fun `MutableScoreboard isReceived returns false for absent chunk and true for received chunk`() {
        // Arrange
        val msb = MutableScoreboard(8u)
        msb.markReceived(3)

        // Act & Assert
        assertTrue(msb.isReceived(3))
        assertFalse(msb.isReceived(4))
    }

    @Test
    fun `MutableScoreboard isMissing returns true for absent chunk and false for received chunk`() {
        // Arrange
        val msb = MutableScoreboard(8u)
        msb.markReceived(3)

        // Act & Assert
        assertTrue(msb.isMissing(4))
        assertFalse(msb.isMissing(3))
    }

    // ---- Idempotency tests ----

    @Test
    fun `MutableScoreboard markMissing absent is idempotent`() {
        // Arrange
        val msb = MutableScoreboard(8u)

        // Act — bit is already missing, received should not go negative
        msb.markMissing(3)

        // Assert
        assertEquals(0, msb.receivedCount())
        assertFalse(msb.isReceived(3))
    }

    @Test
    fun `MutableScoreboard duplicate marks preserve counts`() {
        // Arrange
        val scoreboard = MutableScoreboard(2u)

        // Act
        scoreboard.markReceived(0)
        scoreboard.markReceived(0)
        scoreboard.markMissing(0)
        scoreboard.markMissing(0)

        // Assert
        assertEquals(0, scoreboard.receivedCount())
    }

    // ---- Bounds checking tests ----

    @Test
    fun `MutableScoreboard markReceived out-of-bounds throws`() {
        // Arrange
        val msb = MutableScoreboard(4u)

        // Act & Assert
        assertFailsWith<IndexOutOfBoundsException> { msb.markReceived(5) }
    }

    @Test
    fun `MutableScoreboard bounds reject negative and upper indexes`() {
        // Arrange
        val scoreboard = MutableScoreboard(2u)

        // Act / Assert
        assertFailsWith<IndexOutOfBoundsException> { scoreboard.markReceived(-1) }
        assertFailsWith<IndexOutOfBoundsException> { scoreboard.markMissing(2) }
    }
}
