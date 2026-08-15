package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class TransferSinkTest {

    @Test
    fun `TransferSink write records offset and bytes`() {
        // Arrange — a tracking sink that records every write
        val writes = mutableListOf<Pair<Long, ByteArray>>()
        val sink =
            object : TransferSink {
                override suspend fun write(offset: Long, bytes: ByteArray) {
                    writes += offset to bytes.copyOf()
                }

                override suspend fun complete() = Unit

                override suspend fun abort(cause: MeshLinkException?) = Unit
            }
        val payload = byteArrayOf(1, 2, 3, 4)

        // Act
        runBlocking {
            sink.write(0L, payload)
            sink.write(10L, payload)
        }

        // Assert
        assertEquals(2, writes.size)
        assertEquals(0L, writes[0].first)
        assertTrue(writes[0].second.contentEquals(payload))
        assertEquals(10L, writes[1].first)
        assertTrue(writes[1].second.contentEquals(payload))
    }

    @Test
    fun `TransferSink complete can be called after writes`() {
        // Arrange — full lifecycle: write then complete
        var completed = false
        val writes = mutableListOf<Pair<Long, ByteArray>>()
        val sink =
            object : TransferSink {
                override suspend fun write(offset: Long, bytes: ByteArray) {
                    writes += offset to bytes.copyOf()
                }

                override suspend fun complete() {
                    completed = true
                }

                override suspend fun abort(cause: MeshLinkException?) = Unit
            }
        val payload = byteArrayOf(0x01, 0x02, 0x03)

        // Act — write data, then complete, then complete again (idempotent)
        runBlocking {
            sink.write(0L, payload)
            sink.complete()
            sink.complete()
        }

        // Assert — both write and complete were invoked; complete is idempotent
        assertEquals(1, writes.size)
        assertEquals(0L, writes[0].first)
        assertTrue(writes[0].second.contentEquals(payload))
        assertTrue(completed)
    }

    @Test
    fun `TransferSink abort passes cause with error code`() {
        // Arrange
        var capturedCause: MeshLinkException? = null
        val sink =
            object : TransferSink {
                override suspend fun write(offset: Long, bytes: ByteArray) = Unit

                override suspend fun complete() = Unit

                override suspend fun abort(cause: MeshLinkException?) {
                    capturedCause = cause
                }
            }
        val exception = TransferException(ErrorCode.TRANSFER_CORRUPTED, "corrupted frame")

        // Act
        runBlocking { sink.abort(exception) }

        // Assert — the exact exception instance and its error code are forwarded
        assertSame(exception, capturedCause)
        assertEquals(ErrorCode.TRANSFER_CORRUPTED, capturedCause!!.errorCode)
        assertEquals("corrupted frame", capturedCause!!.message)
    }

    @Test
    fun `TransferSink abort accepts null cause`() {
        // Arrange
        var capturedCause: MeshLinkException? = null
        val sink =
            object : TransferSink {
                override suspend fun write(offset: Long, bytes: ByteArray) = Unit

                override suspend fun complete() = Unit

                override suspend fun abort(cause: MeshLinkException?) {
                    capturedCause = cause
                }
            }

        // Act
        runBlocking { sink.abort(null) }

        // Assert — null is a valid cause (e.g. uncancelled transfer)
        assertNull(capturedCause)
    }
}
