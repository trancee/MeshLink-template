package ch.trancee.meshlink

import ch.trancee.meshlink.model.PeerIdentity
import ch.trancee.meshlink.model.TransferFailureReason
import kotlin.test.Test
import kotlin.test.assertEquals

class TransferFailureReasonTest {
    @Test
    fun `Unrecoverable carries message`() {
        val reason = TransferFailureReason.Unrecoverable("critical error")
        assertEquals("critical error", reason.message)
    }

    @Test
    fun `TrustFailure carries peer identity`() {
        val peer = PeerIdentity.ZERO
        val reason = TransferFailureReason.TrustFailure(peer)
        assertEquals(peer, reason.peerIdentity)
    }
}
