package ch.trancee.meshlink

import ch.trancee.meshlink.model.SeqNo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeqNoTest {
    @Test
    fun `fromRaw creates correct value`() {
        val seqNo = SeqNo.fromRaw(42u)
        kotlin.test.assertEquals(42u, seqNo.raw)
    }

    @Test
    fun `ZERO equals zero`() {
        kotlin.test.assertEquals(0u, SeqNo.ZERO.raw)
    }

    @Test
    fun `isNewerThan returns true for higher value`() {
        assertTrue(SeqNo.fromRaw(42u).isNewerThan(SeqNo.fromRaw(41u)))
    }

    @Test
    fun `isNewerThan returns false for lower value`() {
        assertFalse(SeqNo.fromRaw(41u).isNewerThan(SeqNo.fromRaw(42u)))
    }

    @Test
    fun `isNewerThan returns false for equal value`() {
        assertFalse(SeqNo.fromRaw(42u).isNewerThan(SeqNo.fromRaw(42u)))
    }

    @Test
    fun `isOlderThan is symmetric to isNewerThan`() {
        val a = SeqNo.fromRaw(10u)
        val b = SeqNo.fromRaw(20u)
        assertTrue(a.isOlderThan(b))
        assertTrue(b.isNewerThan(a))
    }

    @Test
    fun `isNewerThan handles wrap-around correctly`() {
        // old = 0xFFFFFFFE (4294967294), new = 1 (wrapped)
        // 1 - 0xFFFFFFFE = 3 (signed) > 0 → newer
        val old = SeqNo.fromRaw(0xFFFFFFFEu)
        val new = SeqNo.fromRaw(1u)
        assertTrue(new.isNewerThan(old))
        assertFalse(old.isNewerThan(new))
    }

    @Test
    fun `minus returns signed difference`() {
        kotlin.test.assertEquals(5, (SeqNo.fromRaw(10u) - SeqNo.fromRaw(5u)))
        kotlin.test.assertEquals(-5, (SeqNo.fromRaw(5u) - SeqNo.fromRaw(10u)))
    }

    @Test
    fun `increment wraps at 2^32`() {
        val max = SeqNo.fromRaw(0xFFFFFFFFu)
        kotlin.test.assertEquals(0u, max.increment().raw)
    }

    @Test
    fun `increment produces next value`() {
        kotlin.test.assertEquals(43u, SeqNo.fromRaw(42u).increment().raw)
    }
}
