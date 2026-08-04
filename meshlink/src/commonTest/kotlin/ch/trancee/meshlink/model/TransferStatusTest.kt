package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TransferStatusTest {

    @Test
    fun `TransferStatus has all fields`() {
        val status =
            TransferStatus(
                state = TransferState.TRANSFERRING,
                offset = 512L,
                total = 1024L,
                retryCount = 2,
                result = null,
                diagnosticCode = null,
                diagnosticSeverity = null,
            )

        assertEquals(TransferState.TRANSFERRING, status.state)
        assertEquals(512L, status.offset)
        assertEquals(1024L, status.total)
        assertEquals(2, status.retryCount)
        assertNull(status.result)
    }
}
