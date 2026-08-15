package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class TransferSourceTest {

    @Test
    fun `TransferSource exposes total matching payload size`() {
        // Arrange — total must match the backing payload
        val payload = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        val source =
            object : TransferSource {
                override val total: Long = payload.size.toLong()

                override suspend fun read(offset: Long, length: Int): ByteArray =
                    payload.copyOfRange(offset.toInt(), offset.toInt() + length)
            }

        // Act + Assert — total is positive and matches payload
        assertEquals(10L, source.total)
        assertTrue(source.total > 0)
    }

    @Test
    fun `TransferSource read returns requested bytes at offset`() {
        // Arrange — a source backed by a known payload
        val payload = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        val source =
            object : TransferSource {
                override val total: Long = payload.size.toLong()

                override suspend fun read(offset: Long, length: Int): ByteArray =
                    payload.copyOfRange(offset.toInt(), offset.toInt() + length)
            }

        // Act — read the last 3 bytes starting at offset 7
        val result = runBlocking { source.read(7L, 3) }
        // Act — read the first 3 bytes starting at offset 0
        val firstBytes = runBlocking { source.read(0L, 3) }

        // Assert
        assertEquals(3, result.size)
        assertTrue(result.copyOf().contentEquals(byteArrayOf(7, 8, 9)))
        assertEquals(3, firstBytes.size)
        assertTrue(firstBytes.copyOf().contentEquals(byteArrayOf(0, 1, 2)))
    }

    @Test
    fun `TransferSource read returns empty for zero length`() {
        // Arrange
        val source =
            object : TransferSource {
                override val total: Long = 10L

                override suspend fun read(offset: Long, length: Int): ByteArray = ByteArray(length)
            }

        // Act — read zero bytes at different offsets
        val resultAtZero = runBlocking { source.read(0L, 0) }
        val resultAtFive = runBlocking { source.read(5L, 0) }

        // Assert — both return empty arrays
        assertEquals(0, resultAtZero.size)
        assertTrue(resultAtZero.isEmpty())
        assertEquals(0, resultAtFive.size)
        assertTrue(resultAtFive.isEmpty())
    }
}
