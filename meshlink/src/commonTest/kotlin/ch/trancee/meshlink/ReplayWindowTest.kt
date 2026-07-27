package ch.trancee.meshlink

import ch.trancee.meshlink.security.ReplayWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReplayWindowTest {

    @Test
    fun `first nonce is accepted`() {
        val window = ReplayWindow()
        assertTrue(window.checkNonce(0L))
    }

    @Test
    fun `duplicate nonce is rejected`() {
        val window = ReplayWindow()
        assertTrue(window.checkNonce(0L))
        assertFalse(window.checkNonce(0L))
    }

    @Test
    fun `sequential nonces are accepted`() {
        val window = ReplayWindow()
        assertTrue(window.checkNonce(0L))
        assertTrue(window.checkNonce(1L))
        assertTrue(window.checkNonce(2L))
    }

    @Test
    fun `out-of-order nonce within window is accepted`() {
        val window = ReplayWindow()
        assertTrue(window.checkNonce(0L))
        assertTrue(window.checkNonce(2L))
        assertTrue(window.checkNonce(1L))
    }

    @Test
    fun `nonce behind window is rejected`() {
        val window = ReplayWindow()
        assertTrue(window.checkNonce(70L))
        // Window base is now 7, so nonce 3 is behind the window
        assertFalse(window.checkNonce(3L))
    }

    @Test
    fun `nonce beyond window advances base`() {
        val window = ReplayWindow()
        assertTrue(window.checkNonce(0L))
        assertTrue(window.checkNonce(70L))
        // Old nonce now behind window
        assertFalse(window.checkNonce(5L))
    }

    @Test
    fun `reset clears the window and base nonce`() {
        val rw = ReplayWindow()
        assertTrue(rw.checkNonce(0L))
        rw.reset()
        assertEquals(0L, rw.baseNonce)
        assertEquals(0uL, rw.window)
        assertTrue(rw.checkNonce(0L))
    }

    @Test
    fun `baseNonce is 0 for fresh window`() {
        val window = ReplayWindow()
        assertEquals(0L, window.baseNonce)
    }

    @Test
    fun `window bitmap is non-zero after checkNonce`() {
        val window = ReplayWindow()
        window.checkNonce(0L)
        assertTrue(window.window != 0uL)
    }
}
