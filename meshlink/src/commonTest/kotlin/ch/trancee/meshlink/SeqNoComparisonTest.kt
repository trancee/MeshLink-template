package ch.trancee.meshlink

import ch.trancee.meshlink.model.SeqNo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeqNoComparisonTest {
    // ---- isNewerThan / isOlderThan ----

    @Test
    fun `isNewerThan returns true for higher value`() {
        // Arrange
        val newer = SeqNo(42u)
        val older = SeqNo(41u)

        // Act & Assert
        assertTrue(newer.isNewerThan(older))
    }

    @Test
    fun `isNewerThan returns false for lower value`() {
        // Arrange
        val older = SeqNo(41u)
        val newer = SeqNo(42u)

        // Act & Assert
        assertFalse(older.isNewerThan(newer))
    }

    @Test
    fun `isNewerThan returns false for equal value`() {
        // Arrange
        val a = SeqNo(42u)
        val b = SeqNo(42u)

        // Act & Assert
        assertFalse(a.isNewerThan(b))
    }

    @Test
    fun `isOlderThan is symmetric to isNewerThan`() {
        // Arrange
        val a = SeqNo(10u)
        val b = SeqNo(20u)

        // Act & Assert
        assertTrue(a.isOlderThan(b))
        assertTrue(b.isNewerThan(a))
    }

    @Test
    fun `isNewerThan handles wrap-around correctly`() {
        // Arrange — old = 0xFFFFFFFE (4294967294), new = 1 (wrapped)
        val old = SeqNo(0xFFFFFFFEu)
        val new = SeqNo(1u)

        // Act & Assert
        assertTrue(new.isNewerThan(old))
        assertFalse(old.isNewerThan(new))
    }

    @Test
    fun `isNewerThan handles full wrap-around boundary`() {
        // Arrange — distance exactly 2^31 (boundary of modular comparison)
        val old = SeqNo(0x80000000u)
        val new = SeqNo(0u)

        // Act & Assert — 0 - 0x80000000 = 0x80000000 = Int.MIN_VALUE (not > 0)
        assertFalse(new.isNewerThan(old))
    }

    @Test
    fun `isNewerThan handles distance just within window`() {
        // Arrange — distance 2^31 - 1 (just within "newer" window)
        val old = SeqNo(0x80000001u)
        val new = SeqNo(0u)

        // Act & Assert — 0 - 0x80000001 = 0x7FFFFFFF = Int.MAX_VALUE (> 0)
        assertTrue(new.isNewerThan(old))
    }

    // ---- isNewerThanOrEqualTo / isOlderThanOrEqualTo ----

    @Test
    fun `isNewerThanOrEqualTo returns true for strictly newer`() {
        // Arrange
        val newer = SeqNo(42u)
        val older = SeqNo(41u)

        // Act & Assert
        assertTrue(newer.isNewerThanOrEqualTo(older))
    }

    @Test
    fun `isNewerThanOrEqualTo returns true for equal`() {
        // Arrange
        val a = SeqNo(42u)
        val b = SeqNo(42u)

        // Act & Assert
        assertTrue(a.isNewerThanOrEqualTo(b))
    }

    @Test
    fun `isNewerThanOrEqualTo returns false for older`() {
        // Arrange
        val older = SeqNo(41u)
        val newer = SeqNo(42u)

        // Act & Assert
        assertFalse(older.isNewerThanOrEqualTo(newer))
    }

    @Test
    fun `isNewerThanOrEqualTo handles wrap-around`() {
        // Arrange
        val old = SeqNo(0xFFFFFFFEu)
        val new = SeqNo(1u)

        // Act & Assert
        assertTrue(new.isNewerThanOrEqualTo(old))
        assertFalse(old.isNewerThanOrEqualTo(new))
    }

    @Test
    fun `isOlderThanOrEqualTo returns true for strictly older`() {
        // Arrange
        val older = SeqNo(41u)
        val newer = SeqNo(42u)

        // Act & Assert
        assertTrue(older.isOlderThanOrEqualTo(newer))
    }

    @Test
    fun `isOlderThanOrEqualTo returns true for equal`() {
        // Arrange
        val a = SeqNo(42u)
        val b = SeqNo(42u)

        // Act & Assert
        assertTrue(a.isOlderThanOrEqualTo(b))
    }

    @Test
    fun `isOlderThanOrEqualTo returns false for newer`() {
        // Arrange
        val newer = SeqNo(42u)
        val older = SeqNo(41u)

        // Act & Assert
        assertFalse(newer.isOlderThanOrEqualTo(older))
    }

    // ---- minus operator ----

    @Test
    fun `minus returns signed difference`() {
        // Arrange
        val a = SeqNo(10u)
        val b = SeqNo(5u)

        // Act & Assert
        assertEquals(5, (a - b))
        assertEquals(-5, (b - a))
    }

    @Test
    fun `minus returns zero for equal values`() {
        // Arrange
        val a = SeqNo(42u)
        val b = SeqNo(42u)

        // Act & Assert
        assertEquals(0, (a - b))
    }

    @Test
    fun `minus handles wrap-around`() {
        // Arrange — 1 - 0xFFFFFFFE = 3 (mod 2^32, signed)
        val old = SeqNo(0xFFFFFFFEu)
        val new = SeqNo(1u)

        // Act & Assert
        assertEquals(3, (new - old))
    }

    // ---- max / min ----

    @Test
    fun `max returns the newer seqno`() {
        // Arrange
        val older = SeqNo(10u)
        val newer = SeqNo(20u)

        // Act
        val result = older.max(newer)

        // Assert
        assertEquals(newer, result)
    }

    @Test
    fun `max returns self when equal`() {
        // Arrange
        val a = SeqNo(42u)
        val b = SeqNo(42u)

        // Act
        val result = a.max(b)

        // Assert — when equal, max returns the other (b)
        assertEquals(b, result)
    }

    @Test
    fun `max handles wrap-around`() {
        // Arrange
        val old = SeqNo(0xFFFFFFFEu)
        val new = SeqNo(1u)

        // Act
        val result = old.max(new)

        // Assert
        assertEquals(new, result)
    }

    @Test
    fun `min returns the older seqno`() {
        // Arrange
        val older = SeqNo(10u)
        val newer = SeqNo(20u)

        // Act
        val result = older.min(newer)

        // Assert
        assertEquals(older, result)
    }

    @Test
    fun `min returns self when equal`() {
        // Arrange
        val a = SeqNo(42u)
        val b = SeqNo(42u)

        // Act
        val result = a.min(b)

        // Assert — when equal, min returns the other (b)
        assertEquals(b, result)
    }

    @Test
    fun `min handles wrap-around`() {
        // Arrange
        val old = SeqNo(0xFFFFFFFEu)
        val new = SeqNo(1u)

        // Act
        val result = old.min(new)

        // Assert
        assertEquals(old, result)
    }

    @Test
    fun `max and min are consistent`() {
        // Arrange
        val a = SeqNo(100u)
        val b = SeqNo(200u)

        // Act
        val maxVal = a.max(b)
        val minVal = a.min(b)

        // Assert
        assertEquals(b, maxVal)
        assertEquals(a, minVal)
    }

    // ---- Comparable ----

    @Test
    fun `compareTo returns negative when this is older`() {
        // Arrange
        val older = SeqNo(10u)
        val newer = SeqNo(20u)

        // Act
        val result = older.compareTo(newer)

        // Assert
        assertTrue(result < 0)
    }

    @Test
    fun `compareTo returns positive when this is newer`() {
        // Arrange
        val newer = SeqNo(20u)
        val older = SeqNo(10u)

        // Act
        val result = newer.compareTo(older)

        // Assert
        assertTrue(result > 0)
    }

    @Test
    fun `compareTo returns zero for equal seqnos`() {
        // Arrange
        val a = SeqNo(42u)
        val b = SeqNo(42u)

        // Act
        val result = a.compareTo(b)

        // Assert
        assertEquals(0, result)
    }

    @Test
    fun `compareTo handles wrap-around`() {
        // Arrange — 1 is newer than 0xFFFFFFFE (modular comparison)
        val older = SeqNo(0xFFFFFFFEu)
        val newer = SeqNo(1u)

        // Act
        val result = newer.compareTo(older)

        // Assert
        assertTrue(result > 0)
    }

    @Test
    fun `compareTo is consistent with isNewerThan`() {
        // Arrange
        val values = listOf(0u, 1u, 0x7FFFFFFFu, 0x80000000u, 0xFFFFFFFFu, 0xFFFFFFFEu)

        // Act & Assert
        for (i in values.indices) {
            for (j in values.indices) {
                val a = SeqNo(values[i])
                val b = SeqNo(values[j])
                val cmp = a.compareTo(b)
                if (i == j) {
                    assertEquals(0, cmp)
                } else if (a.isNewerThan(b)) {
                    assertTrue(
                        cmp > 0,
                        "compareTo should be positive when $values[i] is newer than $values[j]",
                    )
                } else if (a.isOlderThan(b)) {
                    assertTrue(
                        cmp < 0,
                        "compareTo should be negative when $values[i] is older than $values[j]",
                    )
                }
                // At the ±2^31 boundary, comparison is ambiguous — either direction is fine
            }
        }
    }

    @Test
    fun `sorted() orders SeqNo correctly`() {
        // Arrange
        val unsorted: List<SeqNo> =
            listOf(SeqNo(0xFFFFFFFFu), SeqNo(0u), SeqNo(0x80000000u), SeqNo(1u))

        // Act
        val sorted = unsorted.sorted()

        // Assert
        assertEquals(SeqNo(0x80000000u), sorted[0])
        assertEquals(SeqNo(0xFFFFFFFFu), sorted[1])
        assertEquals(SeqNo(0u), sorted[2])
        assertEquals(SeqNo(1u), sorted[3])
    }
}
