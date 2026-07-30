package ch.trancee.meshlink

import ch.trancee.meshlink.security.ReplayWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReplayWindowTest {

    // ── happy-path ──────────────────────────────────────────────────────

    @Test
    fun `first nonce is accepted`() {
        val window = ReplayWindow()
        assertTrue(window.consumeNonce(0L))
    }

    @Test
    fun `sequential nonces are accepted`() {
        val window = ReplayWindow()
        for (i in 0..63) {
            assertTrue(window.consumeNonce(i.toLong()), "nonce $i should be accepted")
        }
    }

    @Test
    fun `duplicate nonce is rejected`() {
        val window = ReplayWindow()
        assertTrue(window.consumeNonce(0L))
        assertFalse(window.consumeNonce(0L))
    }

    @Test
    fun `out-of-order nonce within window is accepted`() {
        val window = ReplayWindow()
        assertTrue(window.consumeNonce(0L))
        assertTrue(window.consumeNonce(2L))
        assertTrue(window.consumeNonce(1L))
    }

    // ── window boundary edges ──────────────────────────────────────────

    @Test
    fun `highest nonce within window is accepted`() {
        val window = ReplayWindow()
        assertTrue(window.consumeNonce(63L))
    }

    @Test
    fun `nonce at window boundary base plus 64 advances window`() {
        val window = ReplayWindow()
        assertTrue(window.consumeNonce(0L))
        assertTrue(window.consumeNonce(64L))
        // Nonce 0 is now behind the new baseNonce (1)
        assertFalse(window.consumeNonce(0L))
    }

    @Test
    fun `nonce just behind window base is rejected`() {
        val window = ReplayWindow()
        window.consumeNonce(70L) // advances baseNonce to 7
        assertFalse(window.consumeNonce(6L))
    }

    @Test
    fun `replays at window apex after advance are detected`() {
        val window = ReplayWindow()
        // Fill nonce 0 so it's tracked at bit 0 (base=0)
        assertTrue(window.consumeNonce(0L))
        // Advance window by sending nonce 64 — base becomes 1,
        // apex (bit 63) tracks nonce 64.
        assertTrue(window.consumeNonce(64L), "nonce 64 accepted when advancing window")
        // Replay of the apex nonce 64 — it's within-window (bitIndex=63),
        // apex bit is set → rejected.
        assertFalse(window.consumeNonce(64L), "apex nonce 64 replay is rejected")
        // Nonce 0 is now behind the window base (1) → rejected.
        assertFalse(window.consumeNonce(0L), "old nonce 0 behind new base is rejected")
    }

    @Test
    fun `apex replay is detected when nonce 63 set before advance`() {
        val window = ReplayWindow()
        // Set nonce 63 (bit 63, apex of base=0 window)
        assertTrue(window.consumeNonce(63L))
        // Advance to nonce 64 — base becomes 1, apex (bit 63) tracks nonce 64.
        // Old bit 63 (nonce 63) right-shifts to bit 62 (nonce 63, new base=1, 63-1=62).
        assertTrue(window.consumeNonce(64L), "nonce 64 accepted after advance")
        // Replay of nonce 64 (now at apex bit 63 with base=1)
        assertFalse(window.consumeNonce(64L), "apex nonce 64 replay is rejected")
    }

    // ── behind-window rejection ────────────────────────────────────────

    @Test
    fun `nonce behind window is rejected`() {
        val window = ReplayWindow()
        window.consumeNonce(70L) // baseNonce → 7
        assertFalse(window.consumeNonce(3L))
    }

    @Test
    fun `nonce far behind window is rejected`() {
        val window = ReplayWindow()
        window.consumeNonce(70L) // baseNonce → 7
        assertFalse(window.consumeNonce(3L)) // 3 is behind baseNonce 7
    }

    // ── epoch / key rotation ───────────────────────────────────────────

    @Test
    fun `advanceEpoch clears the window and increments epoch`() {
        val window = ReplayWindow()
        window.consumeNonce(0L)
        assertEquals(0L, window.epoch)
        window.advanceEpoch()
        assertEquals(1L, window.epoch)
        assertEquals(0L, window.baseNonce)
        assertEquals(0uL, window.window)
    }

    @Test
    fun `nonces accepted after epoch advance`() {
        val window = ReplayWindow()
        window.consumeNonce(0L)
        window.advanceEpoch()
        // After epoch advance, nonce 0 is fresh again.
        assertTrue(window.consumeNonce(0L), "nonce 0 accepted after epoch advance")
    }

    @Test
    fun `nonces from prior epoch are accepted in new epoch`() {
        val window = ReplayWindow()
        assertTrue(window.consumeNonce(5L))
        window.advanceEpoch()
        // Nonce 5 is no longer in the cleared window — fresh in new epoch.
        assertTrue(window.consumeNonce(5L), "nonce 5 accepted in new epoch")
    }

    @Test
    fun `advanceEpoch multiple times`() {
        val window = ReplayWindow()
        window.consumeNonce(0L)
        window.advanceEpoch()
        assertEquals(1L, window.epoch)
        window.advanceEpoch()
        assertEquals(2L, window.epoch)
        window.advanceEpoch()
        assertEquals(3L, window.epoch)
        // Window should still be clean after multiple advances
        assertTrue(window.consumeNonce(0L))
    }

    // ── reset ──────────────────────────────────────────────────────────

    @Test
    fun `reset clears window base nonce and epoch`() {
        val window = ReplayWindow()
        window.consumeNonce(0L)
        window.advanceEpoch()
        window.reset()
        assertEquals(0L, window.baseNonce)
        assertEquals(0uL, window.window)
        assertEquals(0L, window.epoch)
        assertTrue(window.consumeNonce(0L), "nonce 0 accepted after reset")
    }

    // ── large nonce jumps ──────────────────────────────────────────────

    @Test
    fun `large nonce jump beyond window accepts the nonce`() {
        val window = ReplayWindow()
        assertTrue(window.consumeNonce(0L))
        // Jump far beyond the window
        assertTrue(window.consumeNonce(500L))
        // Old nonces behind the new window are rejected
        assertFalse(window.consumeNonce(10L))
        assertFalse(window.consumeNonce(400L))
    }

    @Test
    fun `shift exceeding 63 bits clears all old bits and sets apex`() {
        val window = ReplayWindow()
        // Set bit 0 (nonce 0)
        assertTrue(window.consumeNonce(0L))
        // Jump 128 ahead — shift of 65 bits
        // wouldBeWindow = 0x1 shr 65 = 0uL (all old bits gone)
        // apexMask = bit 63 set for nonce 128
        assertTrue(window.consumeNonce(128L), "large jump accepted")
        // Nonce 0 should be behind the new window (baseNonce = 65)
        assertFalse(window.consumeNonce(0L))
        // The new window should only have the apex bit (63) set — nonce 128
        assertEquals(1uL shl 63, window.window)
    }

    @Test
    fun `shift exceeds 63 with full window clears all old bits and sets apex`() {
        val window = ReplayWindow()
        // Fill all 64 bits
        for (i in 0..63) {
            assertTrue(window.consumeNonce(i.toLong()))
        }
        // Jump 128 ahead — shift = 128 - 63 = 65 (>= 64)
        // Without the guard, shr 65 wraps to shr 1 and old bits survive.
        assertTrue(window.consumeNonce(128L), "large jump accepted")
        // Only the apex bit (63) should remain — all old bits evicted.
        assertEquals(1uL shl 63, window.window)
    }

    @Test
    fun `nonce exactly at baseNonce is accepted`() {
        val window = ReplayWindow()
        // Advance window first so baseNonce > 0
        window.consumeNonce(70L) // baseNonce → 7
        // baseNonce is 7, check nonce 7 — at bitIndex 0 (within window)
        assertTrue(window.consumeNonce(7L), "nonce equal to baseNonce should be accepted")
    }

    // ── input validation ───────────────────────────────────────────────

    @Test
    fun `negative nonce throws IllegalArgumentException`() {
        val window = ReplayWindow()
        assertFailsWith<IllegalArgumentException> { window.consumeNonce(-1L) }
    }

    // ── state exposure ─────────────────────────────────────────────────

    @Test
    fun `baseNonce is 0 for fresh window`() {
        val window = ReplayWindow()
        assertEquals(0L, window.baseNonce)
    }

    @Test
    fun `window bitmap is non-zero after consumeNonce`() {
        val window = ReplayWindow()
        window.consumeNonce(0L)
        assertTrue(window.window != 0uL)
    }

    @Test
    fun `window bitmap is zero after reset`() {
        val window = ReplayWindow()
        window.consumeNonce(0L)
        window.reset()
        assertEquals(0uL, window.window)
    }

    // ── full-window saturation ─────────────────────────────────────────

    @Test
    fun `all 64 bits can be set within one window`() {
        val window = ReplayWindow()
        for (i in 0..63) {
            assertTrue(window.consumeNonce(i.toLong()))
        }
        // All 64 bits set — window is fully saturated
        assertEquals(0xFFFFFFFFFFFFFFFFuL, window.window)
    }

    @Test
    fun `nonce after full window saturation at baseNonce edge`() {
        val window = ReplayWindow()
        // Fill all 64 bits
        for (i in 0..63) {
            window.consumeNonce(i.toLong())
        }
        // Advance by 1 — nonce 64 should be accepted, nonce 0 rejected
        assertTrue(window.consumeNonce(64L))
        assertFalse(window.consumeNonce(0L))
        // After advance: all 64 bits should be set again (63 bits from old window
        // shifted right by 1 + apex bit for nonce 64)
        assertEquals(0xFFFFFFFFFFFFFFFFuL, window.window)
        assertEquals(1L, window.baseNonce)
    }
}
