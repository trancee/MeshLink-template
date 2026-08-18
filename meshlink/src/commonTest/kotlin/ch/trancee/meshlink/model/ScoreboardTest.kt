package ch.trancee.meshlink.model

import ch.trancee.meshlink.model.and
import ch.trancee.meshlink.model.or
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScoreboardTest {
    // ---- Original tests (backward compat) ----

    @Test
    fun `Scoreboard marks chunks correctly`() {
        // Arrange
        val sb = Scoreboard(10u)

        // Act
        val marked = sb.markReceived(5)

        // Assert
        assertTrue(marked.isReceived(5))
        assertFalse(marked.isReceived(6))
    }

    @Test
    fun `Scoreboard missing chunks list`() {
        // Arrange
        val sb = Scoreboard(5u)

        // Act
        val marked = sb.markReceived(0).markReceived(2)

        // Assert
        assertEquals(listOf(1, 3, 4), marked.missingChunks())
    }

    @Test
    fun `Scoreboard received count`() {
        // Arrange
        val sb = Scoreboard(8u)

        // Act
        val marked = sb.markReceived(0).markReceived(2).markReceived(4).markReceived(6)

        // Assert
        assertEquals(4, marked.receivedCount())
    }

    @Test
    fun `Scoreboard missing count`() {
        // Arrange
        val sb = Scoreboard(10u)

        // Act
        val marked = sb.markReceived(0).markReceived(1).markReceived(2)

        // Assert
        assertEquals(7, marked.missingCount())
    }

    @Test
    fun `Scoreboard toBytes returns copy`() {
        // Arrange
        val sb = Scoreboard(4u)

        // Act
        val bytes = sb.toBytes()

        // Assert
        assertEquals(1, bytes.size)
    }

    @Test
    fun `Scoreboard markMissing works`() {
        // Arrange
        val sb = Scoreboard(8u).markReceived(3).markReceived(5)

        // Act
        val cleared = sb.markMissing(3)

        // Assert
        assertFalse(cleared.isReceived(3))
        assertTrue(cleared.isReceived(5))
    }

    // ---- isComplete tests ----

    @Test
    fun `Scoreboard isComplete false when empty`() {
        // Arrange
        val sb = Scoreboard(4u)

        // Act
        val complete = sb.isComplete()

        // Assert
        assertFalse(complete)
    }

    @Test
    fun `Scoreboard isComplete true when all chunks received`() {
        // Arrange
        val sb = Scoreboard(4u)

        // Act
        val full = sb.markReceived(0).markReceived(1).markReceived(2).markReceived(3)

        // Assert
        assertTrue(full.isComplete())
    }

    @Test
    fun `Scoreboard isComplete false when not all received`() {
        // Arrange
        val sb = Scoreboard(4u).markReceived(0).markReceived(1)

        // Act
        val complete = sb.isComplete()

        // Assert
        assertFalse(complete)
    }

    @Test
    fun `Scoreboard isComplete true for empty board`() {
        // Arrange
        val sb = Scoreboard(0u)

        // Act
        val complete = sb.isComplete()

        // Assert
        assertTrue(complete)
    }

    // ---- Bitwise merge tests ----

    @Test
    fun `Scoreboard or merges ACK bitfields from multiple peers`() {
        // Arrange
        val sb1 = Scoreboard(8u).markReceived(0).markReceived(2).markReceived(4)
        val sb2 = Scoreboard(8u).markReceived(1).markReceived(2).markReceived(5)

        // Act
        val merged = sb1.or(sb2)

        // Assert
        assertTrue(merged.isReceived(0))
        assertTrue(merged.isReceived(1))
        assertTrue(merged.isReceived(2))
        assertTrue(merged.isReceived(4))
        assertTrue(merged.isReceived(5))
        assertFalse(merged.isReceived(3))
        assertFalse(merged.isReceived(6))
        assertFalse(merged.isReceived(7))
        assertEquals(5, merged.receivedCount())
        assertFalse(merged.isComplete())
    }

    @Test
    fun `Scoreboard and intersects ACK bitfields`() {
        // Arrange
        val sb1 = Scoreboard(8u).markReceived(0).markReceived(2).markReceived(4)
        val sb2 = Scoreboard(8u).markReceived(2).markReceived(4).markReceived(6)

        // Act
        val intersection = sb1.and(sb2)

        // Assert
        assertFalse(intersection.isReceived(0))
        assertTrue(intersection.isReceived(2))
        assertTrue(intersection.isReceived(4))
        assertFalse(intersection.isReceived(6))
        assertEquals(2, intersection.receivedCount())
    }

    @Test
    fun `Scoreboard xor symmetric difference`() {
        // Arrange
        val sb1 = Scoreboard(8u).markReceived(0).markReceived(2)
        val sb2 = Scoreboard(8u).markReceived(2).markReceived(4)

        // Act
        val diff = sb1.xor(sb2)

        // Assert
        assertTrue(diff.isReceived(0))
        assertFalse(diff.isReceived(2)) // in both, so not in xor
        assertTrue(diff.isReceived(4))
        assertEquals(2, diff.receivedCount())
    }

    // ---- Phantom bits masking tests ----

    @Test
    fun `Scoreboard fromBytes masks bits beyond totalChunks`() {
        // Arrange — 5 chunks but byte has bits 5-7 set (phantom bits from untrusted wire data)
        val bytes = byteArrayOf(0b11111111.toByte())

        // Act
        val sb = Scoreboard.fromBytes(5u, bytes)

        // Assert — only bits 0-4 are meaningful; received count must be 5, not 8
        assertEquals(5, sb.receivedCount())
        assertTrue(sb.isComplete())
        assertEquals(0b00011111.toByte(), sb.toBytes()[0])
    }

    @Test
    fun `Scoreboard or with phantom bits produces correct count`() {
        // Arrange
        val sbWithPhantom = Scoreboard.fromBytes(5u, byteArrayOf(0b11111111.toByte()))
        val sbEmpty = Scoreboard.fromBytes(5u, byteArrayOf(0b00000000.toByte()))

        // Act
        val merged = sbWithPhantom.or(sbEmpty)

        // Assert
        assertEquals(5, merged.receivedCount())
        assertTrue(merged.isComplete())
    }

    @Test
    fun `Scoreboard fromBytes with totalChunks multiple of 8 has no masking`() {
        // Arrange
        val bytes = byteArrayOf(0b11111111.toByte())

        // Act
        val sb = Scoreboard.fromBytes(8u, bytes)

        // Assert
        assertEquals(8, sb.receivedCount())
        assertEquals(0b11111111.toByte(), sb.toBytes()[0])
    }

    // ---- Compatibility check tests ----

    @Test
    fun `Scoreboard or fails when totalChunks mismatch`() {
        // Arrange
        val sb1 = Scoreboard(8u).markReceived(0)
        val sb2 = Scoreboard(4u).markReceived(0)

        // Act & Assert
        assertFailsWith<IllegalArgumentException> { sb1.or(sb2) }
    }

    @Test
    fun `Scoreboard and fails when totalChunks mismatch`() {
        // Arrange
        val sb1 = Scoreboard(8u).markReceived(0)
        val sb2 = Scoreboard(4u).markReceived(0)

        // Act & Assert
        assertFailsWith<IllegalArgumentException> { sb1.and(sb2) }
    }

    @Test
    fun `Scoreboard xor fails when totalChunks mismatch`() {
        // Arrange
        val sb1 = Scoreboard(8u).markReceived(0)
        val sb2 = Scoreboard(4u).markReceived(0)

        // Act & Assert
        assertFailsWith<IllegalArgumentException> { sb1.xor(sb2) }
    }

    // ---- fromBytes and byteSize tests ----

    @Test
    fun `Scoreboard fromBytes counts set bits`() {
        // Arrange
        val bytes = byteArrayOf(0xFF.toByte())

        // Act
        val scoreboard = Scoreboard.fromBytes(8u, bytes)

        // Assert
        assertEquals(8, scoreboard.receivedCount())
    }

    @Test
    fun `Scoreboard fromBytes deserializes correctly`() {
        // Arrange — chunks 1 and 3 received
        val bytes = byteArrayOf(0b00001010.toByte())

        // Act
        val sb = Scoreboard.fromBytes(8u, bytes)

        // Assert
        assertFalse(sb.isReceived(0))
        assertTrue(sb.isReceived(1))
        assertFalse(sb.isReceived(2))
        assertTrue(sb.isReceived(3))
        assertEquals(2, sb.receivedCount())
        assertEquals(6, sb.missingCount())
    }

    @Test
    fun `Scoreboard fromBytes validates byte array size`() {
        // Arrange
        val bytes = byteArrayOf(0b00000001.toByte())

        // Act & Assert — needs 2 bytes for 16 chunks
        assertFailsWith<IllegalArgumentException> { Scoreboard.fromBytes(16u, bytes) }
    }

    @Test
    fun `Scoreboard byteSize exposes bitfield size`() {
        assertEquals(1, Scoreboard(1u).byteSize) // 1 chunk → 1 byte
        assertEquals(1, Scoreboard(8u).byteSize) // 8 chunks → 1 byte
        assertEquals(2, Scoreboard(9u).byteSize) // 9 chunks → 2 bytes
    }

    // ---- Bounds checking tests ----

    @Test
    fun `Scoreboard markReceived out-of-bounds throws`() {
        // Arrange
        val sb = Scoreboard(4u)

        // Act & Assert
        assertFailsWith<IndexOutOfBoundsException> { sb.markReceived(5) }
    }

    // ---- Idempotency tests ----

    @Test
    fun `Scoreboard markReceived duplicate is idempotent`() {
        // Arrange
        val sb = Scoreboard(8u).markReceived(3)

        // Act — duplicate mark
        val sb2 = sb.markReceived(3)

        // Assert
        assertEquals(1, sb2.receivedCount())
        assertTrue(sb2.isReceived(3))
    }

    @Test
    fun `Scoreboard markMissing absent is idempotent`() {
        // Arrange
        val sb = Scoreboard(8u)

        // Act — already missing
        val sb2 = sb.markMissing(3)

        // Assert
        assertEquals(0, sb2.receivedCount())
        assertFalse(sb2.isReceived(3))
    }

    // ---- Missing/chunk iteration tests ----

    @Test
    fun `Scoreboard missingSequence is lazy`() {
        // Arrange
        val sb = Scoreboard(5u).markReceived(2)

        // Act
        val missing = sb.missingSequence().toList()

        // Assert
        assertEquals(listOf(0, 1, 3, 4), missing)
    }

    @Test
    fun `Scoreboard forEachMissing calls action for each missing chunk`() {
        // Arrange
        val sb = Scoreboard(5u).markReceived(1).markReceived(3)
        val collected = mutableListOf<Int>()

        // Act
        sb.forEachMissing { collected.add(it) }

        // Assert
        assertEquals(listOf(0, 2, 4), collected)
    }

    @Test
    fun `Scoreboard bounds reject negative and upper indexes`() {
        // Arrange
        val scoreboard = Scoreboard(2u)

        // Act / Assert
        assertFailsWith<IndexOutOfBoundsException> { scoreboard.isReceived(-1) }
        assertFailsWith<IndexOutOfBoundsException> { scoreboard.isReceived(2) }
    }
}
