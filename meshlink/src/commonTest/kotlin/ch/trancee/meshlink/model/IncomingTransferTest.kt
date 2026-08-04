package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

class IncomingTransferTest {

    @Test
    fun `IncomingTransfer has required API`() {
        val status =
            MutableStateFlow(
                TransferStatus(
                    state = TransferState.AWAITING_DECISION,
                    offset = 0L,
                    total = 1024L,
                    retryCount = 0,
                    result = null,
                    diagnosticCode = null,
                    diagnosticSeverity = null,
                )
            )

        val incoming =
            IncomingTransfer(
                id = TransferId(456u),
                kind = TransferKind.MESSAGE,
                origin = PeerIdentity.generate(),
                priority = Priority.NORMAL,
                total = 1024L,
                chunkSize = 256,
                expiresAt = Clock.System.now() + 60.seconds,
                status = status,
            )

        assertEquals(TransferId(456u), incoming.id)
        assertEquals(TransferKind.MESSAGE, incoming.kind)
        assertNotNull(incoming.origin)
        assertEquals(Priority.NORMAL, incoming.priority)
        assertEquals(1024L, incoming.total)
        assertEquals(256, incoming.chunkSize)
        assertNotNull(incoming.expiresAt)
        assertEquals(TransferState.AWAITING_DECISION, incoming.status.value.state)
    }

    @Test
    fun `accept throws NotImplementedError`() {
        val incoming = createIncomingTransfer()
        val sink =
            object : TransferSink {
                override suspend fun write(offset: Long, data: ByteArray) = Unit

                override suspend fun complete() = Unit

                override suspend fun fail(result: TransferResult) = Unit
            }

        assertFailsWith<NotImplementedError> { runBlocking { incoming.accept(sink) } }
    }

    @Test
    fun `reject throws NotImplementedError`() {
        val incoming = createIncomingTransfer()

        assertFailsWith<NotImplementedError> { runBlocking { incoming.reject() } }
    }

    private fun createIncomingTransfer(): IncomingTransfer {
        val status =
            MutableStateFlow(
                TransferStatus(
                    state = TransferState.AWAITING_DECISION,
                    offset = 0L,
                    total = 1024L,
                    retryCount = 0,
                    result = null,
                    diagnosticCode = null,
                    diagnosticSeverity = null,
                )
            )
        return IncomingTransfer(
            id = TransferId(456u),
            kind = TransferKind.MESSAGE,
            origin = PeerIdentity.generate(),
            priority = Priority.NORMAL,
            total = 1024L,
            chunkSize = 256,
            expiresAt = Clock.System.now() + 60.seconds,
            status = status,
        )
    }
}
