package ch.trancee.meshlink

import ch.trancee.meshlink.model.PeerIdentity
import ch.trancee.meshlink.model.TransferDeliveryOutcome
import ch.trancee.meshlink.model.TransferFailureReason
import ch.trancee.meshlink.model.TransferOutcomeMapper
import ch.trancee.meshlink.model.TransferState
import kotlin.test.Test
import kotlin.test.assertEquals

class TransferDeliveryOutcomeTest {

    @Test
    fun `COMPLETED maps to SUCCESS`() {
        assertEquals(
            TransferDeliveryOutcome.SUCCESS,
            TransferOutcomeMapper.map(TransferState.COMPLETED, null),
        )
    }

    @Test
    fun `AWAITING_DECISION has no terminal outcome`() {
        assertEquals(null, TransferOutcomeMapper.map(TransferState.AWAITING_DECISION, null))
    }

    @Test
    fun `IN_PROGRESS maps to IN_PROGRESS`() {
        assertEquals(null, TransferOutcomeMapper.map(TransferState.TRANSFERRING, null))
    }

    @Test
    fun `RETRYING maps to RETRYING`() {
        assertEquals(null, TransferOutcomeMapper.map(TransferState.RETRANSMITTING, null))
    }

    @Test
    fun `WAITING_FOR_ROUTE maps to ROUTE_WAITING`() {
        assertEquals(null, TransferOutcomeMapper.map(TransferState.ROUTE_UNAVAILABLE, null))
    }

    @Test
    fun `CANCELLED maps to CANCELLED`() {
        assertEquals(
            TransferDeliveryOutcome.CANCELLED,
            TransferOutcomeMapper.map(TransferState.CANCELLED, null),
        )
    }

    @Test
    fun `TIMED_OUT maps to TIMEOUT`() {
        assertEquals(
            TransferDeliveryOutcome.TIMEOUT,
            TransferOutcomeMapper.map(TransferState.EXPIRED, null),
        )
    }

    @Test
    fun `FAILED with Unrecoverable maps to UNRECOVERABLE_FAILURE`() {
        assertEquals(
            TransferDeliveryOutcome.UNRECOVERABLE_FAILURE,
            TransferOutcomeMapper.map(
                TransferState.FAILED,
                TransferFailureReason.Unrecoverable("error"),
            ),
        )
    }

    @Test
    fun `FAILED with TrustFailure maps to TRUST_FAILURE`() {
        assertEquals(
            TransferDeliveryOutcome.TRUST_FAILURE,
            TransferOutcomeMapper.map(
                TransferState.FAILED,
                TransferFailureReason.TrustFailure(PeerIdentity.ZERO),
            ),
        )
    }

    @Test
    fun `FAILED with null reason maps to UNRECOVERABLE_FAILURE`() {
        assertEquals(
            TransferDeliveryOutcome.UNRECOVERABLE_FAILURE,
            TransferOutcomeMapper.map(TransferState.FAILED, null),
        )
    }
}
