package ch.trancee.meshlink

import ch.trancee.meshlink.model.SeqNo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeqNoWireTest {
    // ---- toByteArray / fromByteArray ----

    @Test
    fun `toByteArray produces 4-byte big-endian`() {
        // Arrange
        val seqNo = SeqNo(0x12345678u)

        // Act
        val bytes = seqNo.toByteArray()

        // Assert
        assertEquals(4, bytes.size)
        assertEquals(0x12.toUByte(), bytes[0].toUByte())
        assertEquals(0x34.toUByte(), bytes[1].toUByte())
        assertEquals(0x56.toUByte(), bytes[2].toUByte())
        assertEquals(0x78.toUByte(), bytes[3].toUByte())
    }

    @Test
    fun `toByteArray for ZERO produces all zeros`() {
        // Act
        val bytes = SeqNo.ZERO.toByteArray()

        // Assert
        assertEquals(4, bytes.size)
        assertEquals(0x00.toUByte(), bytes[0].toUByte())
        assertEquals(0x00.toUByte(), bytes[1].toUByte())
        assertEquals(0x00.toUByte(), bytes[2].toUByte())
        assertEquals(0x00.toUByte(), bytes[3].toUByte())
    }

    @Test
    fun `toByteArray for MAX_VALUE produces 0xFFFFFFFF`() {
        // Act
        val bytes = SeqNo.MAX_VALUE.toByteArray()

        // Assert
        assertEquals(4, bytes.size)
        assertEquals(0xFF.toUByte(), bytes[0].toUByte())
        assertEquals(0xFF.toUByte(), bytes[1].toUByte())
        assertEquals(0xFF.toUByte(), bytes[2].toUByte())
        assertEquals(0xFF.toUByte(), bytes[3].toUByte())
    }

    @Test
    fun `fromByteArray roundtrips through toByteArray`() {
        // Arrange
        val seqNo = SeqNo(0xDEADBEEFu)

        // Act
        val bytes = seqNo.toByteArray()
        val restored = SeqNo.fromByteArray(bytes)

        // Assert
        assertEquals(seqNo, restored)
    }

    @Test
    fun `fromByteArray for ZERO produces ZERO`() {
        // Act
        val seqNo = SeqNo.fromByteArray(ByteArray(4))

        // Assert
        assertEquals(SeqNo.ZERO, seqNo)
    }

    @Test
    fun `fromByteArray for MAX_VALUE produces MAX_VALUE`() {
        // Act
        val seqNo = SeqNo.fromByteArray(ByteArray(4) { 0xFF.toByte() })

        // Assert
        assertEquals(SeqNo.MAX_VALUE, seqNo)
    }

    @Test
    fun `toByteArray for seqNo 1 produces correct bytes`() {
        // Arrange
        val seqNo = SeqNo(1u)

        // Act
        val bytes = seqNo.toByteArray()

        // Assert
        assertEquals(4, bytes.size)
        assertEquals(0x00.toByte(), bytes[0])
        assertEquals(0x00.toByte(), bytes[1])
        assertEquals(0x00.toByte(), bytes[2])
        assertEquals(0x01.toByte(), bytes[3])
    }

    @Test
    fun `fromByteArray for seqNo 1 produces correct SeqNo`() {
        // Act
        val seqNo = SeqNo.fromByteArray(byteArrayOf(0x00, 0x00, 0x00, 0x01))

        // Assert
        assertEquals(SeqNo(1u), seqNo)
    }

    @Test
    fun `fromByteArray throws for invalid byte array size`() {
        // Arrange
        val threeBytes = byteArrayOf(0x00, 0x00, 0x01)
        val fiveBytes = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x01)

        // Act & Assert
        assertFailsWith<IllegalArgumentException> { SeqNo.fromByteArray(threeBytes) }
        assertFailsWith<IllegalArgumentException> { SeqNo.fromByteArray(fiveBytes) }
    }

    // ---- unsignedDistance ----

    @Test
    fun `unsignedDistance returns 0 for equal values`() {
        // Arrange
        val a = SeqNo(42u)
        val b = SeqNo(42u)

        // Act
        val result = a.unsignedDistance(b)

        // Assert
        assertEquals(0u, result)
    }

    @Test
    fun `unsignedDistance returns forward distance`() {
        // Arrange
        val newer = SeqNo(20u)
        val older = SeqNo(10u)

        // Act
        val result = newer.unsignedDistance(older)

        // Assert
        assertEquals(10u, result)
    }

    @Test
    fun `unsignedDistance handles wrap-around`() {
        // Arrange — 1 to 0xFFFFFFFE: forward distance wrapping through 0 is 3
        val wrapped = SeqNo(1u)
        val old = SeqNo(0xFFFFFFFEu)

        // Act
        val result = wrapped.unsignedDistance(old)

        // Assert
        assertEquals(3u, result)
    }

    @Test
    fun `unsignedDistance is not symmetric`() {
        // Arrange
        val a = SeqNo(10u)
        val b = SeqNo(20u)

        // Act
        val dAB = a.unsignedDistance(b)
        val dBA = b.unsignedDistance(a)

        // Assert — a.unsignedDistance(b) = (10 - 20) mod 2^32 wraps to 2^32 - 10
        // b.unsignedDistance(a) = (20 - 10) = 10
        assertEquals(0xFFFFFFFFu - 9u, dAB)
        assertEquals(10u, dBA)
    }

    @Test
    fun `unsignedDistance from ZERO to MAX_VALUE`() {
        // Arrange — ZERO is one increment ahead of MAX_VALUE (wraps)
        // Act
        val result = SeqNo.ZERO.unsignedDistance(SeqNo.MAX_VALUE)

        // Assert
        assertEquals(1u, result)
    }

    // ---- boundary cases ----

    @Test
    fun `half-window boundary comparison is ambiguous`() {
        // Arrange — 0 and 0x80000000 are exactly 2^31 apart at the comparison window edge
        val a = SeqNo(0u)
        val b = SeqNo(0x80000000u)

        // Act
        val cmp = a.compareTo(b)

        // Assert — at boundary, isNewerThan and isOlderThan both return false (ambiguous)
        assertFalse(a.isNewerThan(b))
        assertFalse(b.isNewerThan(a))
        // compareTo uses signed difference (same as minus): 0 - 0x80000000 = Int.MIN_VALUE < 0.
        // This gives a deterministic ordering even when newer/older is ambiguous — consistent with
        // the modular signed comparison semantics used by all comparison methods.
        assertTrue(cmp < 0)
    }

    @Test
    fun `unsignedDistance for half-window boundary case`() {
        // Arrange — distance from 0 to 0x80000000 is exactly 2^31
        val a = SeqNo(0u)
        val b = SeqNo(0x80000000u)

        // Act
        val result = a.unsignedDistance(b)

        // Assert — modular unsigned distance wraps to half the range
        assertEquals(0x80000000u, result)
    }
}
