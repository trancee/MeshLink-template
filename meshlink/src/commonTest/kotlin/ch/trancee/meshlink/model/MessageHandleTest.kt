package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow

class MessageHandleTest {

    @Test
    fun `MessageHandle has required API`() {
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
            MessageHandle(id = MessageId(1u), status = status, outcome = channel, cancel = {})

        assertEquals(MessageId(1u), handle.id)
        assertEquals(TransferState.AWAITING_DECISION, handle.status.value.state)
        assertEquals(0L, handle.status.value.offset)
        assertEquals(100L, handle.status.value.total)
    }

    @Test
    fun `MessageHandle await returns terminal outcome`() =
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
                MessageHandle(id = MessageId(1u), status = status, outcome = channel, cancel = {})

            // Send outcome to buffered channel (won't suspend)
            channel.send(TransferResult.Completed)

            val result = handle.await()

            assertEquals(TransferResult.Completed, result)
        }

    @Test
    fun `await returns Cancelled when channel sends cancelled result`() =
        kotlinx.coroutines.runBlocking {
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
                MessageHandle(id = MessageId(1u), status = status, outcome = channel, cancel = {})

            channel.send(TransferResult.Cancelled)
            val result = handle.await()

            assertEquals(TransferResult.Cancelled, result)
        }

    @Test
    fun `await returns UnrecoverableFailure when channel sends failure result`() =
        kotlinx.coroutines.runBlocking {
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
                MessageHandle(id = MessageId(1u), status = status, outcome = channel, cancel = {})

            val failure = TransferResult.UnrecoverableFailure("timeout exceeded")
            channel.send(failure)
            val result = handle.await()

            // Assert — verify the error payload type and message content, not just that an
            // exception was thrown
            assertTrue(
                result is TransferResult.UnrecoverableFailure,
                "Expected UnrecoverableFailure type",
            )
            assertEquals("timeout exceeded", result.message)
        }

    @Test
    fun `cancel lambda can be invoked without error`() =
        kotlinx.coroutines.runBlocking {
            var invoked = false
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
                MessageHandle(
                    id = MessageId(1u),
                    status = status,
                    outcome = channel,
                    cancel = { invoked = true },
                )

            // Act
            handle.cancel()

            // Assert — cancel lambda was invoked (side-effect verification)
            assertTrue(invoked, "cancel lambda should have been invoked")
        }
}
