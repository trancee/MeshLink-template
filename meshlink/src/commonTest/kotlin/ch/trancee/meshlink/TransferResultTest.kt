package ch.trancee.meshlink

import ch.trancee.meshlink.model.PeerIdentity
import ch.trancee.meshlink.model.TransferResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransferResultTest {

    @Test
    fun `Completed is a terminal outcome`() {
        val result: TransferResult = TransferResult.Completed

        assertTrue(result is TransferResult.Completed)
    }

    @Test
    fun `Cancelled is a terminal outcome`() {
        val result: TransferResult = TransferResult.Cancelled

        assertTrue(result is TransferResult.Cancelled)
    }

    @Test
    fun `Expired is a terminal outcome`() {
        val result: TransferResult = TransferResult.Expired

        assertTrue(result is TransferResult.Expired)
    }

    @Test
    fun `UnrecoverableFailure carries message`() {
        val result = TransferResult.UnrecoverableFailure("error")

        assertEquals("error", result.message)
    }

    @Test
    fun `TrustFailure carries peer identity`() {
        val peer = PeerIdentity.ZERO
        val result = TransferResult.TrustFailure(peer)

        assertEquals(peer, result.identity)
    }
}
