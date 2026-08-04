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
    fun `Trust carries peer identity`() {
        val peer = PeerIdentity.ZERO
        val reason = TransferFailureReason.Trust(peer)
        assertEquals(peer, reason.peerIdentity)
    }
}
