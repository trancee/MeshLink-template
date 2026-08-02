package ch.trancee.meshlink

import ch.trancee.meshlink.util.ConstantTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConstantTimeTest {

    // ── constantTimeEquals ──────────────────────────────────────

    @Test
    fun `constantTimeEquals returns zero for equal arrays`() {
        val a = byteArrayOf(1, 2, 3, 4)
        val b = byteArrayOf(1, 2, 3, 4)
        assertEquals(0, ConstantTime.constantTimeEquals(a, b))
    }

    @Test
    fun `constantTimeEquals returns non-zero for different arrays`() {
        val a = byteArrayOf(1, 2, 3, 4)
        val b = byteArrayOf(1, 2, 3, 5)
        assertTrue(ConstantTime.constantTimeEquals(a, b) != 0)
    }

    @Test
    fun `constantTimeEquals returns non-zero for different sizes`() {
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(1, 2, 3, 4)
        assertTrue(ConstantTime.constantTimeEquals(a, b) != 0)
    }

    @Test
    fun `constantTimeEquals covers longer left array tail`() {
        // Arrange
        val longer = byteArrayOf(1, 2)
        val shorter = byteArrayOf(1)

        // Act
        val result = ConstantTime.constantTimeEquals(longer, shorter)

        // Assert
        assertTrue(result != 0)
    }

    @Test
    fun `constantTimeEquals handles empty arrays`() {
        assertEquals(0, ConstantTime.constantTimeEquals(ByteArray(0), ByteArray(0)))
    }

    @Test
    fun `constantTimeEquals handles empty vs non-empty`() {
        assertTrue(ConstantTime.constantTimeEquals(ByteArray(0), byteArrayOf(1)) != 0)
    }

    @Test
    fun `constantTimeEquals single byte equal`() {
        assertEquals(0, ConstantTime.constantTimeEquals(byteArrayOf(0), byteArrayOf(0)))
    }

    @Test
    fun `constantTimeEquals single byte different`() {
        assertTrue(ConstantTime.constantTimeEquals(byteArrayOf(0), byteArrayOf(1)) != 0)
    }

    @Test
    fun `constantTimeEqualsBoolean returns true for equal arrays`() {
        val a = byteArrayOf(0, 0, 0)
        val b = byteArrayOf(0, 0, 0)
        assertTrue(ConstantTime.constantTimeEqualsBoolean(a, b))
    }

    @Test
    fun `constantTimeEqualsBoolean returns false for different arrays`() {
        val a = byteArrayOf(0, 0, 0)
        val b = byteArrayOf(0, 0, 1)
        assertFalse(ConstantTime.constantTimeEqualsBoolean(a, b))
    }

    // ── constantTimeIsZero ───────────────────────────────────────

    @Test
    fun `constantTimeIsZero returns true for all-zero array`() {
        val a = byteArrayOf(0, 0, 0, 0)
        assertTrue(ConstantTime.constantTimeIsZero(a))
    }

    @Test
    fun `constantTimeIsZero returns false for non-zero array`() {
        val a = byteArrayOf(0, 0, 0, 1)
        assertFalse(ConstantTime.constantTimeIsZero(a))
    }

    @Test
    fun `constantTimeIsZero returns true for empty array`() {
        assertTrue(ConstantTime.constantTimeIsZero(ByteArray(0)))
    }

    @Test
    fun `constantTimeIsZero returns false for first byte non-zero`() {
        assertFalse(ConstantTime.constantTimeIsZero(byteArrayOf(1, 0, 0, 0)))
    }

    @Test
    fun `constantTimeIsZero returns false for last byte non-zero`() {
        assertFalse(ConstantTime.constantTimeIsZero(byteArrayOf(0, 0, 0, 1)))
    }

    // ── constantTimeSelect ──────────────────────────────────────

    @Test
    fun `constantTimeSelect returns a when condition is zero`() {
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(4, 5, 6)
        val result = ConstantTime.constantTimeSelect(0, a, b)
        assertEquals(a.toList(), result.toList())
    }

    @Test
    fun `constantTimeSelect returns b when condition is non-zero`() {
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(4, 5, 6)
        val result = ConstantTime.constantTimeSelect(1, a, b)
        assertEquals(b.toList(), result.toList())
    }

    @Test
    fun `constantTimeSelect returns b when condition is negative`() {
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(4, 5, 6)
        val result = ConstantTime.constantTimeSelect(-1, a, b)
        assertEquals(b.toList(), result.toList())
    }

    @Test
    fun `constantTimeSelect works with empty arrays`() {
        val result = ConstantTime.constantTimeSelect(1, ByteArray(0), ByteArray(0))
        assertEquals(emptyList<Byte>(), result.toList())
    }

    @Test
    fun `constantTimeSelect throws for mismatched sizes`() {
        assertFailsWith<IllegalArgumentException> {
            ConstantTime.constantTimeSelect(0, byteArrayOf(1), byteArrayOf(1, 2))
        }
    }

    // ── constantTimeSwap ─────────────────────────────────────────

    @Test
    fun `constantTimeSwap swaps when condition is non-zero`() {
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(4, 5, 6)
        val (swappedA, swappedB) = ConstantTime.constantTimeSwap(1, a, b)
        assertEquals(b.toList(), swappedA.toList())
        assertEquals(a.toList(), swappedB.toList())
    }

    @Test
    fun `constantTimeSwap does not swap when condition is zero`() {
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(4, 5, 6)
        val (swappedA, swappedB) = ConstantTime.constantTimeSwap(0, a, b)
        assertEquals(a.toList(), swappedA.toList())
        assertEquals(b.toList(), swappedB.toList())
    }

    @Test
    fun `constantTimeSwap swaps when condition is negative`() {
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(4, 5, 6)
        val (swappedA, swappedB) = ConstantTime.constantTimeSwap(-1, a, b)
        assertEquals(b.toList(), swappedA.toList())
        assertEquals(a.toList(), swappedB.toList())
    }

    @Test
    fun `constantTimeSwap works with empty arrays`() {
        val (swappedA, swappedB) = ConstantTime.constantTimeSwap(1, ByteArray(0), ByteArray(0))
        assertEquals(emptyList<Byte>(), swappedA.toList())
        assertEquals(emptyList<Byte>(), swappedB.toList())
    }

    @Test
    fun `constantTimeSwap throws on mismatched sizes`() {
        assertFailsWith<IllegalArgumentException> {
            ConstantTime.constantTimeSwap(1, byteArrayOf(1), byteArrayOf(1, 2))
        }
    }

    // ── edge cases ────────────────────────────────────────────────

    @Test
    fun `constantTimeEquals equal 32-byte arrays`() {
        val bytes = UByteArray(32) { it.toUByte() }.toByteArray()
        assertEquals(0, ConstantTime.constantTimeEquals(bytes, bytes))
    }

    @Test
    fun `constantTimeEquals different 32-byte arrays at position 0`() {
        val a = UByteArray(32) { it.toUByte() }.toByteArray()
        val b = a.copyOf()
        b[0] = (b[0].toInt() xor 0xFF).toByte()
        assertTrue(ConstantTime.constantTimeEquals(a, b) != 0)
    }

    @Test
    fun `constantTimeEquals different 32-byte arrays at last position`() {
        val a = UByteArray(32) { it.toUByte() }.toByteArray()
        val b = a.copyOf()
        b[31] = (b[31].toInt() xor 0xFF).toByte()
        assertTrue(ConstantTime.constantTimeEquals(a, b) != 0)
    }

    @Test
    fun `constantTimeEquals is commutative`() {
        val a = byteArrayOf(0x01, 0x02, 0x03)
        val b = byteArrayOf(0x03, 0x02, 0x01)
        assertEquals(ConstantTime.constantTimeEquals(a, b), ConstantTime.constantTimeEquals(b, a))
    }
}
