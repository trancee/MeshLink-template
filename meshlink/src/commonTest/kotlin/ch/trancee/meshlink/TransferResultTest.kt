package ch.trancee.meshlink

import ch.trancee.meshlink.model.PeerIdentity
import ch.trancee.meshlink.model.TransferFailureReason
import ch.trancee.meshlink.model.TransferResult
import ch.trancee.meshlink.model.mapTransferResult
import kotlin.test.Test
import kotlin.test.assertEquals

class TransferResultTest {

    @Test
    fun `Unrecoverable failure reason maps to UNRECOVERABLE_FAILURE`() {
        assertEquals(
            TransferResult.UNRECOVERABLE_FAILURE,
            mapTransferResult(TransferFailureReason.Unrecoverable("error")),
        )
    }

    @Test
    fun `Trust reason maps to TRUST_FAILURE`() {
        assertEquals(
            TransferResult.TRUST_FAILURE,
            mapTransferResult(TransferFailureReason.Trust(PeerIdentity.ZERO)),
        )
    }

    @Test
    fun `null failure reason has no terminal outcome`() {
        assertEquals(null, mapTransferResult(null))
    }
}
