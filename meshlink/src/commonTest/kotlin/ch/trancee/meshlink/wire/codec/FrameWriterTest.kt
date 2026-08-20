package ch.trancee.meshlink.wire.codec

import ch.trancee.meshlink.model.PeerIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FrameWriterTest {

    @Test
    fun `writeUByte writes single byte`() {

        val writer = FrameWriter(1)

        writer.writeUByte(0x42u)

        assertEquals(byteArrayOf(0x42).toList(), writer.toByteArray().toList())
    }

    @Test
    fun `writeUShortLE writes low byte first`() {

        val writer = FrameWriter(2)

        writer.writeUShortLE(0x1234u)

        assertEquals(byteArrayOf(0x34, 0x12).toList(), writer.toByteArray().toList())
    }

    @Test
    fun `writeUShortBE writes high byte first`() {

        val writer = FrameWriter(2)

        writer.writeUShortBE(0x1234u)

        assertEquals(byteArrayOf(0x12, 0x34).toList(), writer.toByteArray().toList())
    }

    @Test
    fun `writeUIntLE writes little-endian`() {

        val writer = FrameWriter(4)

        writer.writeUIntLE(0x12345678u)

        assertEquals(byteArrayOf(0x78, 0x56, 0x34, 0x12).toList(), writer.toByteArray().toList())
    }

    @Test
    fun `writeUIntBE writes big-endian`() {

        val writer = FrameWriter(4)

        writer.writeUIntBE(0x12345678u)

        assertEquals(byteArrayOf(0x12, 0x34, 0x56, 0x78).toList(), writer.toByteArray().toList())
    }

    @Test
    fun `writeULongLE writes little-endian`() {

        val writer = FrameWriter(8)

        writer.writeULongLE(0x0102030405060708uL)

        assertEquals(byteArrayOf(0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01).toList(), writer.toByteArray().toList())
    }

    @Test
    fun `writeULongBE writes big-endian`() {

        val writer = FrameWriter(8)

        writer.writeULongBE(0x0102030405060708uL)

        assertEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08).toList(), writer.toByteArray().toList())
    }

    @Test
    fun `max values round-trip for each unsigned type`() {

        val writer = FrameWriter(15)

        writer.writeUByte(0xFFu)
        writer.writeUShortLE(0xFFFFu)
        writer.writeUIntLE(0xFFFFFFFFu)
        writer.writeULongLE(0xFFFFFFFFFFFFFFFFuL)

        assertEquals(15, writer.size)
    }

    @Test
    fun `writeBytes copies and advances`() {

        val writer = FrameWriter(4)
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)

        writer.writeBytes(data)

        assertEquals(data.toList(), writer.toByteArray().toList())
    }

    @Test
    fun `writeBytes copies not references`() {

        val writer = FrameWriter(2)
        val data = byteArrayOf(0x01, 0x02)

        writer.writeBytes(data)
        data[0] = 0xFF.toByte()

        assertEquals(byteArrayOf(0x01, 0x02).toList(), writer.toByteArray().toList())
    }

    @Test
    fun `writePeerIdentity writes 16 bytes`() {

        val writer = FrameWriter(16)
        val identity = PeerIdentity.fromBytes(ByteArray(16) { i -> (i + 1).toByte() })

        writer.writePeerIdentity(identity)

        assertEquals(16, writer.toByteArray().size)
    }

    @Test
    fun `size reflects written bytes`() {

        val writer = FrameWriter(10)

        writer.writeUByte(0x01u)
        assertEquals(1, writer.size)

        writer.writeUShortLE(0x0201u)
        assertEquals(3, writer.size)

        writer.writeUIntBE(0x03040506u)
        assertEquals(7, writer.size)
    }

    @Test
    fun `toByteArray returns compact copy`() {

        val writer = FrameWriter(10)

        writer.writeUByte(0x42u)

        val snapshot = writer.toByteArray()
        writer.writeUByte(0x99u)

        assertEquals(byteArrayOf(0x42).toList(), snapshot.toList())
        assertEquals(byteArrayOf(0x42, 0x99.toByte()).toList(), writer.toByteArray().toList())
    }

    @Test
    fun `requireRemaining throws on overflow`() {

        val writer = FrameWriter(2)

        writer.writeUByte(0x01u)
        assertFailsWith<IllegalArgumentException> { writer.writeUIntLE(0x02030405u) }
    }

    @Test
    fun `write beyond capacity throws`() {

        val writer = FrameWriter(1)

        writer.writeUByte(0x01u)
        assertFailsWith<IllegalArgumentException> { writer.writeUByte(0x02u) }
    }

    @Test
    fun `zero-capacity writer allows no writes`() {

        val writer = FrameWriter(0)

        assertFailsWith<IllegalArgumentException> { writer.writeUByte(0x01u) }
    }
}
