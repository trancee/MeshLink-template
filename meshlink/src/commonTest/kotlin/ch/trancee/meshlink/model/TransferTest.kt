package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
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
                    result = null,
                    diagnosticCode = null,
                    diagnosticSeverity = null,
                )
            )

        val transfer = Transfer(id = TransferId(123u), kind = TransferKind.PAYLOAD, status = status)

        assertEquals(TransferId(123u), transfer.id)
        assertEquals(TransferKind.PAYLOAD, transfer.kind)
        assertEquals(TransferState.AWAITING_DECISION, transfer.status.value.state)
    }

    @Test
    fun `different transfers have different ids and kinds`() {
        val status =
            MutableStateFlow(
                TransferStatus(
                    state = TransferState.AWAITING_DECISION,
                    offset = 0L,
                    total = 0L,
                    retryCount = 0,
                    result = null,
                    diagnosticCode = null,
                    diagnosticSeverity = null,
                )
            )

        val payloadTransfer =
            Transfer(id = TransferId(1u), kind = TransferKind.PAYLOAD, status = status)
        val messageTransfer =
            Transfer(id = TransferId(2u), kind = TransferKind.MESSAGE, status = status)

        // Negative: different IDs and kinds produce unequal transfers
        assertNotEquals(payloadTransfer.id, messageTransfer.id)
        assertNotEquals(payloadTransfer.kind, messageTransfer.kind)
        assertNotEquals(payloadTransfer, messageTransfer)
    }

    @Test
    fun `Transfer toString includes id and kind`() {
        val status =
            MutableStateFlow(
                TransferStatus(
                    state = TransferState.TRANSFERRING,
                    offset = 0L,
                    total = 512L,
                    retryCount = 0,
                    result = null,
                    diagnosticCode = null,
                    diagnosticSeverity = null,
                )
            )

        val transfer = Transfer(id = TransferId(42u), kind = TransferKind.PAYLOAD, status = status)

        // Structural: toString exposes key identifying fields
        val str = transfer.toString()
        assertTrue(str.contains("42"), "Transfer.toString() should contain the transfer ID")
        assertTrue(str.contains("PAYLOAD"), "Transfer.toString() should contain the transfer kind")
    }
}
