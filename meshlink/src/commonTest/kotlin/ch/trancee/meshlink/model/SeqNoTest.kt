package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeqNoTest {
    // ---- Construction & value access ----

    @Test
    fun `constructor creates correct value`() {
        // Arrange
        val expected = 42u

        // Act
        val seqNo = SeqNo(expected)

        // Assert
        assertEquals(SeqNo(expected), seqNo)
        assertEquals(expected, seqNo.rawValue())
    }

    @Test
    fun `fromUInt creates SeqNo from raw UInt`() {
        // Arrange
        val raw = 0xDEADBEEFu

        // Act
        val seqNo = SeqNo.fromUInt(raw)

        // Assert
        assertEquals(raw, seqNo.rawValue())
    }

    @Test
    fun `rawValue roundtrips through fromUInt`() {
        // Arrange
        val raw = 0xCAFEBABEu

        // Act
        val seqNo = SeqNo.fromUInt(raw)
        val restored = seqNo.rawValue()

        // Assert
        assertEquals(raw, restored)
    }

    @Test
    fun `ZERO equals zero`() {
        // Arrange
        val zero = SeqNo.ZERO

        // Act & Assert
        assertEquals(SeqNo(0u), zero)
        assertEquals(0u, zero.rawValue())
        assertTrue(zero.isZero)
    }

    @Test
    fun `MAX_VALUE equals UInt max`() {
        // Arrange & Act
        val max = SeqNo.MAX_VALUE

        // Assert
        assertEquals(UInt.MAX_VALUE, max.rawValue())
        assertEquals(0xFFFFFFFFu, max.rawValue())
        assertFalse(max.isZero)
    }

    @Test
    fun `isZero is true for ZERO and false for non-zero`() {
        // Arrange
        val zero = SeqNo.ZERO
        val nonZero = SeqNo(1u)

        // Act & Assert
        assertTrue(zero.isZero)
        assertFalse(nonZero.isZero)
    }

    // ---- operator inc (increments by 1, wraps at 2^32) ----

    @Test
    fun `operator inc wraps at 2^32`() {
        // Arrange
        var max = SeqNo(0xFFFFFFFFu)

        // Act
        max++

        // Assert
        assertEquals(SeqNo.ZERO, max)
    }

    @Test
    fun `operator inc produces next value`() {
        // Arrange
        var seqNo = SeqNo(42u)

        // Act
        seqNo++

        // Assert
        assertEquals(SeqNo(43u), seqNo)
    }

    @Test
    fun `operator inc from zero produces one`() {
        // Arrange
        var zero = SeqNo.ZERO

        // Act
        zero++

        // Assert
        assertEquals(SeqNo(1u), zero)
    }

    @Test
    fun `operator inc works same as increment`() {
        // Arrange
        var seqNo = SeqNo(42u)

        // Act
        val result = seqNo++

        // Assert
        assertEquals(SeqNo(42u), result)
        assertEquals(SeqNo(43u), seqNo)
    }

    @Test
    fun `postIncrement returns old value and advances seqNo`() {
        // Arrange
        var seqNo = SeqNo(42u)

        // Act — post-increment returns the OLD value
        val resultOp = seqNo++

        // Assert — seqNo is now 43
        assertEquals(SeqNo(42u), resultOp)
        assertEquals(SeqNo(43u), seqNo)
    }

    // ---- Equality ----

    @Test
    fun `equal seqnos are equal`() {
        // Arrange
        val a = SeqNo(42u)
        val b = SeqNo(42u)

        // Act & Assert
        assertEquals(a, b)
    }

    @Test
    fun `different seqnos are not equal`() {
        // Arrange
        val a = SeqNo(42u)
        val b = SeqNo(43u)

        // Act & Assert
        assertFalse(a == b)
    }
}
