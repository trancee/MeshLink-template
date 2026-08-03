package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransferKindTest {

    @Test
    fun `TransferKind has MESSAGE and PAYLOAD`() {
        assertEquals(2, TransferKind.values().size)
        assertTrue(TransferKind.MESSAGE != TransferKind.PAYLOAD)
    }
}
