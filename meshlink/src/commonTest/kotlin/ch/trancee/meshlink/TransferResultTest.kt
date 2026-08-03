package ch.trancee.meshlink

import ch.trancee.meshlink.model.PeerIdentity
import ch.trancee.meshlink.model.TransferFailureReason
import ch.trancee.meshlink.model.TransferResult
import ch.trancee.meshlink.model.TransferState
import ch.trancee.meshlink.model.mapTransferResult
import kotlin.test.Test
import kotlin.test.assertEquals

class TransferResultTest {

    @Test
    fun `COMPLETED maps to COMPLETED`() {
        assertEquals(TransferResult.COMPLETED, mapTransferResult(TransferState.COMPLETED, null))
    }

    @Test
    fun `AWAITING_DECISION has no terminal outcome`() {
        assertEquals(null, mapTransferResult(TransferState.AWAITING_DECISION, null))
    }

    @Test
    fun `TRANSFERRING has no terminal outcome`() {
        assertEquals(null, mapTransferResult(TransferState.TRANSFERRING, null))
    }

    @Test
    fun `RETRANSMITTING has no terminal outcome`() {
        assertEquals(null, mapTransferResult(TransferState.RETRANSMITTING, null))
    }

    @Test
    fun `ROUTE_UNAVAILABLE has no terminal outcome`() {
        assertEquals(null, mapTransferResult(TransferState.ROUTE_UNAVAILABLE, null))
    }

    @Test
    fun `CANCELLED maps to CANCELLED`() {
        assertEquals(TransferResult.CANCELLED, mapTransferResult(TransferState.CANCELLED, null))
    }

    @Test
    fun `EXPIRED maps to EXPIRED`() {
        assertEquals(TransferResult.EXPIRED, mapTransferResult(TransferState.EXPIRED, null))
    }

    @Test
    fun `FAILED with Unrecoverable maps to UNRECOVERABLE_FAILURE`() {
        assertEquals(
            TransferResult.UNRECOVERABLE_FAILURE,
            mapTransferResult(TransferState.FAILED, TransferFailureReason.Unrecoverable("error")),
        )
    }

    @Test
    fun `FAILED with TrustFailure maps to TRUST_FAILURE`() {
        assertEquals(
            TransferResult.TRUST_FAILURE,
            mapTransferResult(
                TransferState.FAILED,
                TransferFailureReason.TrustFailure(PeerIdentity.ZERO),
            ),
        )
    }

    @Test
    fun `FAILED with null reason maps to UNRECOVERABLE_FAILURE`() {
        assertEquals(
            TransferResult.UNRECOVERABLE_FAILURE,
            mapTransferResult(TransferState.FAILED, null),
        )
    }
}
