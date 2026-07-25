package ch.trancee.meshlink

import ch.trancee.meshlink.model.MutableScoreboard
import ch.trancee.meshlink.model.Scoreboard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScoreboardTest {
    @Test
    fun `Scoreboard marks chunks correctly`() {
        val sb = Scoreboard(10u)
        val marked = sb.markReceived(5)
        assertTrue(marked.isReceived(5))
        assertFalse(marked.isReceived(6))
    }

    @Test
    fun `Scoreboard missing chunks list`() {
        val sb = Scoreboard(5u)
        val marked = sb.markReceived(0).markReceived(2)
        assertEquals(listOf(1, 3, 4), marked.missingChunks())
    }

    @Test
    fun `Scoreboard received count`() {
        val sb = Scoreboard(8u)
        val marked = sb.markReceived(0).markReceived(2).markReceived(4).markReceived(6)
        assertEquals(4, marked.receivedCount())
    }

    @Test
    fun `Scoreboard missing count`() {
        val sb = Scoreboard(10u)
        val marked = sb.markReceived(0).markReceived(1).markReceived(2)
        assertEquals(7, marked.missingCount())
    }

    @Test
    fun `Scoreboard toByteArray returns copy`() {
        val sb = Scoreboard(4u)
        val bytes = sb.toByteArray()
        assertEquals(1, bytes.size)
    }

    @Test
    fun `Scoreboard markMissing works`() {
        val sb = Scoreboard(8u).markReceived(3).markReceived(5)
        val cleared = sb.markMissing(3)
        assertFalse(cleared.isReceived(3))
        assertTrue(cleared.isReceived(5))
    }

    @Test
    fun `MutableScoreboard markReceived works`() {
        val msb = MutableScoreboard(8u)
        msb.markReceived(2)
        assertTrue(msb.isReceived(2))
        assertEquals(1, msb.receivedCount())
    }

    @Test
    fun `MutableScoreboard toImmutable creates Scoreboard`() {
        val msb = MutableScoreboard(4u)
        msb.markReceived(0)
        msb.markReceived(1)
        val immutable = msb.toImmutable()
        assertTrue(immutable.isReceived(0))
        assertTrue(immutable.isReceived(1))
    }

    @Test
    fun `MutableScoreboard missingCount returns correct count`() {
        val msb = MutableScoreboard(8u)
        msb.markReceived(0)
        msb.markReceived(1)
        msb.markReceived(2)
        assertEquals(5, msb.missingCount())
    }

    @Test
    fun `MutableScoreboard markMissing clears a bit`() {
        val msb = MutableScoreboard(8u)
        msb.markReceived(3)
        assertTrue(msb.isReceived(3))
        msb.markMissing(3)
        assertFalse(msb.isReceived(3))
        assertEquals(0, msb.receivedCount())
    }
}
