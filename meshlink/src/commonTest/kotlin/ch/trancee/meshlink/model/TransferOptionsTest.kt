package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class TransferOptionsTest {

    @Test
    fun `TransferOptions defaults and custom`() {
        val defaults = TransferOptions.DEFAULT
        assertEquals(Priority.NORMAL, defaults.priority)
        assertNull(defaults.timeToLive)

        val custom = TransferOptions(priority = Priority.HIGH, timeToLive = 5.minutes)
        assertEquals(Priority.HIGH, custom.priority)
        assertEquals(5.minutes, custom.timeToLive)
    }
}
