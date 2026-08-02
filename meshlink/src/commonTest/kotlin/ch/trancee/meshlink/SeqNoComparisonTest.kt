package ch.trancee.meshlink

import ch.trancee.meshlink.model.SeqNo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeqNoComparisonTest {
    @Test
    fun `modular comparisons handle ordinary and wrapped values`() {
        // Arrange
        val older = SeqNo(41u)
        val newer = SeqNo(42u)
        val wrappedOlder = SeqNo(UInt.MAX_VALUE)
        val wrappedNewer = SeqNo(1u)

        // Act
        val ordinaryNewer = newer.isNewerThan(older)
        val wrappedResult = wrappedNewer.isNewerThan(wrappedOlder)

        // Assert
        assertTrue(ordinaryNewer)
        assertTrue(wrappedResult)
        assertTrue(older.isOlderThan(newer))
        assertTrue(newer.isNewerThanOrEqualTo(older))
        assertTrue(older.isOlderThanOrEqualTo(newer))
    }

    @Test
    fun `newer or equal and subtraction cover modular boundaries`() {
        // Arrange
        val newer = SeqNo(2u)
        val older = SeqNo(1u)

        // Act
        val isNewerOrEqual = newer.isNewerThanOrEqualTo(older)
        val difference = newer.distanceFrom(older)

        // Assert
        assertTrue(isNewerOrEqual)
        assertEquals(1u, difference)
    }

    @Test
    fun `newer or equal returns false when older`() {
        // Arrange
        val older = SeqNo(1u)
        val newer = SeqNo(2u)

        // Act
        val result = older.isNewerThanOrEqualTo(newer)

        // Assert
        assertFalse(result)
    }

    @Test
    fun `half range ordering is ambiguous`() {
        // Arrange
        val first = SeqNo(0u)
        val second = SeqNo(0x80000000u)

        // Act
        val firstNewer = first.isNewerThan(second)
        val secondNewer = second.isNewerThan(first)

        // Assert
        assertFalse(firstNewer)
        assertFalse(secondNewer)
    }

    @Test
    fun `distanceFrom uses unsigned modular forward distance`() {
        // Arrange
        val value = SeqNo(1u)
        val previous = SeqNo(UInt.MAX_VALUE)

        // Act
        val distance = value.distanceFrom(previous)

        // Assert
        assertEquals(2u, distance)
    }
}
