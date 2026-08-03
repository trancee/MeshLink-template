package ch.trancee.meshlink

import ch.trancee.meshlink.model.PeerIdentity
import ch.trancee.meshlink.model.TransferDeliveryOutcome
import ch.trancee.meshlink.model.TransferFailureReason
import ch.trancee.meshlink.model.TransferState
import ch.trancee.meshlink.model.mapTransferDeliveryOutcome
import kotlin.test.Test
import kotlin.test.assertEquals

class TransferDeliveryOutcomeTest {

    @Test
    fun `COMPLETED maps to COMPLETED`() {
        assertEquals(
            TransferDeliveryOutcome.COMPLETED,
            mapTransferDeliveryOutcome(TransferState.COMPLETED, null),
        )
    }

    @Test
    fun `AWAITING_DECISION has no terminal outcome`() {
        assertEquals(null, mapTransferDeliveryOutcome(TransferState.AWAITING_DECISION, null))
    }

    @Test
    fun `TRANSFERRING has no terminal outcome`() {
        assertEquals(null, mapTransferDeliveryOutcome(TransferState.TRANSFERRING, null))
    }

    @Test
    fun `RETRANSMITTING has no terminal outcome`() {
        assertEquals(null, mapTransferDeliveryOutcome(TransferState.RETRANSMITTING, null))
    }

    @Test
    fun `ROUTE_UNAVAILABLE has no terminal outcome`() {
        assertEquals(null, mapTransferDeliveryOutcome(TransferState.ROUTE_UNAVAILABLE, null))
    }

    @Test
    fun `CANCELLED maps to CANCELLED`() {
        assertEquals(
            TransferDeliveryOutcome.CANCELLED,
            mapTransferDeliveryOutcome(TransferState.CANCELLED, null),
        )
    }

    @Test
    fun `EXPIRED maps to EXPIRED`() {
        assertEquals(
            TransferDeliveryOutcome.EXPIRED,
            mapTransferDeliveryOutcome(TransferState.EXPIRED, null),
        )
    }

    @Test
    fun `FAILED with Unrecoverable maps to UNRECOVERABLE_FAILURE`() {
        assertEquals(
            TransferDeliveryOutcome.UNRECOVERABLE_FAILURE,
            mapTransferDeliveryOutcome(
                TransferState.FAILED,
                TransferFailureReason.Unrecoverable("error"),
            ),
        )
    }

    @Test
    fun `FAILED with TrustFailure maps to TRUST_FAILURE`() {
        assertEquals(
            TransferDeliveryOutcome.TRUST_FAILURE,
            mapTransferDeliveryOutcome(
                TransferState.FAILED,
                TransferFailureReason.TrustFailure(PeerIdentity.ZERO),
            ),
        )
    }

    @Test
    fun `FAILED with null reason maps to UNRECOVERABLE_FAILURE`() {
        assertEquals(
            TransferDeliveryOutcome.UNRECOVERABLE_FAILURE,
            mapTransferDeliveryOutcome(TransferState.FAILED, null),
        )
    }
}
