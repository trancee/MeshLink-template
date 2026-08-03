package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow

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
                    transferResult = null,
                    diagnosticCode = null,
                    diagnosticSeverity = null,
                )
            )

        val incoming =
            IncomingTransfer(
                kind = TransferKind.MESSAGE,
                id = 456u,
                origin = PeerIdentity.generate(),
                priority = Priority.NORMAL,
                total = 1024L,
                chunkSize = 256,
                expiresAt = Clock.System.now() + 60.seconds,
                status = status,
            )

        assertEquals(TransferKind.MESSAGE, incoming.kind)
        assertEquals(456u, incoming.id)
        assertNotNull(incoming.origin)
        assertEquals(Priority.NORMAL, incoming.priority)
        assertEquals(1024L, incoming.total)
        assertEquals(256, incoming.chunkSize)
        assertNotNull(incoming.expiresAt)
        assertEquals(TransferState.AWAITING_DECISION, incoming.status.value.state)
    }
}
