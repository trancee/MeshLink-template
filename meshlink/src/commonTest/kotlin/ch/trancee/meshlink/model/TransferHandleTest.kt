package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow

class TransferHandleTest {

    @Test
    fun `TransferHandle has required API`() {
        val channel = Channel<TransferResult>()
        val status =
            MutableStateFlow(
                TransferStatus(
                    state = TransferState.AWAITING_DECISION,
                    offset = 0L,
                    total = 100L,
                    retryCount = 0,
                    result = null,
                    diagnosticCode = null,
                    diagnosticSeverity = null,
                )
            )

        val handle =
            TransferHandle(id = TransferId(1u), status = status, outcome = channel, cancel = {})

        assertEquals(TransferId(1u), handle.id)
        assertNotNull(handle.status)
        assertNotNull(handle.outcome)
        assertNotNull(handle.cancel)
    }

    @Test
    fun `TransferHandle await returns terminal outcome`() =
        kotlinx.coroutines.runBlocking {
            // Use buffered channel to avoid deadlock on rendezvous send/receive
            val channel = Channel<TransferResult>(1)
            val status =
                MutableStateFlow(
                    TransferStatus(
                        state = TransferState.AWAITING_DECISION,
                        offset = 0L,
                        total = 100L,
                        retryCount = 0,
                        result = null,
                        diagnosticCode = null,
                        diagnosticSeverity = null,
                    )
                )

            val handle =
                TransferHandle(id = TransferId(1u), status = status, outcome = channel, cancel = {})

            // Send outcome to buffered channel (won't suspend)
            channel.send(TransferResult.Completed)

            val result = handle.await()

            assertEquals(TransferResult.Completed, result)
        }
}
