package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow

class TransferTest {

    @Test
    fun `Transfer has required properties`() {
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

        val transfer = Transfer(id = 123u, kind = TransferKind.PAYLOAD, status = status)

        assertEquals(123u, transfer.id)
        assertEquals(TransferKind.PAYLOAD, transfer.kind)
        assertEquals(TransferState.AWAITING_DECISION, transfer.status.value.state)
    }
}
