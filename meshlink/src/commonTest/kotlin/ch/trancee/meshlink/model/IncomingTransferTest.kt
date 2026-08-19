package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

class IncomingTransferTest {

    @Test
    fun `IncomingTransfer has required API`() {
        // Arrange
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

        // Act
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

        // Assert — all fields carry the expected values
        assertEquals(TransferId(456u), incoming.id)
        assertEquals(TransferKind.MESSAGE, incoming.kind)
        assertNotNull(incoming.origin)
        assertEquals(Priority.NORMAL, incoming.priority)
        assertEquals(1024L, incoming.total)
        assertEquals(256, incoming.chunkSize)
        assertNotNull(incoming.expiresAt)
        assertEquals(TransferState.AWAITING_DECISION, incoming.status.value.state)
        assertTrue(incoming.status.value.offset == 0L)
        assertTrue(incoming.status.value.total == 1024L)
        assertTrue(incoming.status.value.retryCount == 0)
    }

    @Test
    fun `IncomingTransfer with zero total and minimal expiry`() {
        // Arrange
        val status =
            MutableStateFlow(
                TransferStatus(
                    state = TransferState.AWAITING_DECISION,
                    offset = 0L,
                    total = 0L,
                    retryCount = 0,
                )
            )

        // Act
        val incoming =
            IncomingTransfer(
                id = TransferId(0u),
                kind = TransferKind.PAYLOAD,
                origin = PeerIdentity.ZERO,
                priority = Priority.LOW,
                total = 0L,
                chunkSize = 0,
                expiresAt = Clock.System.now(),
                status = status,
            )

        // Assert — edge case values are preserved
        assertEquals(TransferId(0u), incoming.id)
        assertEquals(TransferKind.PAYLOAD, incoming.kind)
        assertEquals(Priority.LOW, incoming.priority)
        assertEquals(0L, incoming.total)
        assertEquals(0, incoming.chunkSize)
        assertEquals(PeerIdentity.ZERO, incoming.origin)
        assertEquals(TransferState.AWAITING_DECISION, incoming.status.value.state)
    }

    // Scaffold verification: verifies accept() exists and throws its scaffold TODO.
    // Replace this test with behavioral idempotency assertions when accept() is implemented.
    @Test
    fun `accept throws NotImplementedError with stub message`() {
        // Arrange
        val incoming = createIncomingTransfer()
        val sink =
            object : TransferSink {
                override suspend fun write(offset: Long, bytes: ByteArray) = Unit

                override suspend fun complete() = Unit

                override suspend fun abort(cause: MeshLinkException?) = Unit
            }

        // Act
        val exception =
            assertFailsWith<NotImplementedError> { runBlocking { incoming.accept(sink) } }

        // Assert — the stub throws with a scaffold message
        assertTrue(exception.message?.contains("Not implemented") == true)
    }

    // Scaffold verification: verifies accept() idempotency at the scaffold level.
    // Replace this test with behavioral idempotency assertions when accept() is implemented.
    @Test
    fun `accept is idempotent — repeated calls still throw`() {
        // Arrange — the KDoc says accept is idempotent; the stub always throws
        val incoming = createIncomingTransfer()
        val sink =
            object : TransferSink {
                override suspend fun write(offset: Long, bytes: ByteArray) = Unit

                override suspend fun complete() = Unit

                override suspend fun abort(cause: MeshLinkException?) = Unit
            }

        // Act + Assert — first call throws
        assertFailsWith<NotImplementedError> { runBlocking { incoming.accept(sink) } }

        // Act + Assert — second call also throws (idempotent error behavior)
        assertFailsWith<NotImplementedError> { runBlocking { incoming.accept(sink) } }
    }

    // Scaffold verification: verifies reject() exists and throws its scaffold TODO.
    // Replace this test with behavioral idempotency assertions when reject() is implemented.
    @Test
    fun `reject throws NotImplementedError with stub message`() {
        // Arrange
        val incoming = createIncomingTransfer()

        // Act
        val exception = assertFailsWith<NotImplementedError> { runBlocking { incoming.reject() } }

        // Assert — the stub throws with a scaffold message
        assertTrue(exception.message?.contains("Not implemented") == true)
    }

    // Scaffold verification: verifies reject() idempotency at the scaffold level.
    // Replace this test with behavioral idempotency assertions when reject() is implemented.
    @Test
    fun `reject is idempotent — repeated calls still throw`() {
        // Arrange
        val incoming = createIncomingTransfer()

        // Act + Assert — first call throws
        assertFailsWith<NotImplementedError> { runBlocking { incoming.reject() } }

        // Act + Assert — second call also throws (idempotent error behavior)
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
