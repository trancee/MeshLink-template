package ch.trancee.meshlink

import ch.trancee.meshlink.util.ConstantTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConstantTimeTest {

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
}
