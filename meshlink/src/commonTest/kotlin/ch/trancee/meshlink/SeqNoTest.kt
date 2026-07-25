package ch.trancee.meshlink

import ch.trancee.meshlink.model.SeqNo
import kotlin.test.Test
import kotlin.test.assertEquals

class SeqNoTest {
    @Test
    fun `fromRaw creates correct value`() {
        val seqNo = SeqNo.fromRaw(42u)
        assertEquals(42u, seqNo.raw)
    }

    @Test
    fun `ZERO equals zero`() {
        assertEquals(0u, SeqNo.ZERO.raw)
    }
}
