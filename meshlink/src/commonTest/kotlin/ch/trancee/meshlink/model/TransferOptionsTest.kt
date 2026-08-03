package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TransferOptionsTest {

    @Test
    fun `TransferOptions defaults and custom`() {
        val defaults = TransferOptions.DEFAULT
        assertEquals(Priority.NORMAL, defaults.priority)
        assertNull(defaults.ttlMillis)

        val custom = TransferOptions(priority = Priority.HIGH, ttlMillis = 300_000L)
        assertEquals(Priority.HIGH, custom.priority)
        assertEquals(300_000L, custom.ttlMillis!!)
    }
}
