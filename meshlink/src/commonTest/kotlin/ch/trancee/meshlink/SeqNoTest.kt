package ch.trancee.meshlink

import ch.trancee.meshlink.model.SeqNo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeqNoTest {
    @Test
    fun `constructor creates correct value`() {
        val seqNo = SeqNo(42u)
        kotlin.test.assertEquals(SeqNo(42u), seqNo)
    }

    @Test
    fun `ZERO equals zero`() {
        kotlin.test.assertEquals(SeqNo.ZERO, SeqNo.ZERO)
    }

    @Test
    fun `toString returns decimal`() {
        kotlin.test.assertEquals("42", SeqNo(42u).toString())
        kotlin.test.assertEquals("0", SeqNo.ZERO.toString())
    }

    @Test
    fun `isNewerThan returns true for higher value`() {
        assertTrue(SeqNo(42u).isNewerThan(SeqNo(41u)))
    }

    @Test
    fun `isNewerThan returns false for lower value`() {
        assertFalse(SeqNo(41u).isNewerThan(SeqNo(42u)))
    }

    @Test
    fun `isNewerThan returns false for equal value`() {
        assertFalse(SeqNo(42u).isNewerThan(SeqNo(42u)))
    }

    @Test
    fun `isOlderThan is symmetric to isNewerThan`() {
        val a = SeqNo(10u)
        val b = SeqNo(20u)
        assertTrue(a.isOlderThan(b))
        assertTrue(b.isNewerThan(a))
    }

    @Test
    fun `isNewerThan handles wrap-around correctly`() {
        // old = 0xFFFFFFFE (4294967294), new = 1 (wrapped)
        // 1 - 0xFFFFFFFE = 3 (signed) > 0 → newer
        val old = SeqNo(0xFFFFFFFEu)
        val new = SeqNo(1u)
        assertTrue(new.isNewerThan(old))
        assertFalse(old.isNewerThan(new))
    }

    @Test
    fun `minus returns signed difference`() {
        kotlin.test.assertEquals(5, (SeqNo(10u) - SeqNo(5u)))
        kotlin.test.assertEquals(-5, (SeqNo(5u) - SeqNo(10u)))
    }

    @Test
    fun `increment wraps at 2^32`() {
        val max = SeqNo(0xFFFFFFFFu)
        kotlin.test.assertEquals(SeqNo.ZERO, max.increment())
    }

    @Test
    fun `increment produces next value`() {
        kotlin.test.assertEquals(SeqNo(43u), SeqNo(42u).increment())
    }
}
