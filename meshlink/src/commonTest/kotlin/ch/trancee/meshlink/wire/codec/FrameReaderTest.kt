package ch.trancee.meshlink.wire.codec

import ch.trancee.meshlink.model.PeerIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FrameReaderTest {

    @Test
    fun `readUByte returns single byte value`() {

        val reader = FrameReader(byteArrayOf(0x42))

        assertEquals(0x42, reader.readUByte().toInt())
    }

    @Test
    fun `readUByte advances offset`() {

        val reader = FrameReader(byteArrayOf(0x01, 0x02))

        assertEquals(0x01, reader.readUByte().toInt())
        assertEquals(0x02, reader.readUByte().toInt())
    }

    @Test
    fun `readUShortLE reads low byte first`() {

        val reader = FrameReader(byteArrayOf(0x34, 0x12))

        assertEquals(0x1234, reader.readUShortLE().toInt())
    }

    @Test
    fun `readUShortBE reads high byte first`() {

        val reader = FrameReader(byteArrayOf(0x12, 0x34))

        assertEquals(0x1234, reader.readUShortBE().toInt())
    }

    @Test
    fun `readUIntLE reads little-endian`() {

        val reader = FrameReader(byteArrayOf(0x78, 0x56, 0x34, 0x12))

        assertEquals(0x12345678u, reader.readUIntLE())
    }

    @Test
    fun `readUIntBE reads big-endian`() {

        val reader = FrameReader(byteArrayOf(0x12, 0x34, 0x56, 0x78))

        assertEquals(0x12345678u, reader.readUIntBE())
    }

    @Test
    fun `readULongLE reads little-endian`() {

        val reader = FrameReader(byteArrayOf(0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01))

        assertEquals(0x0102030405060708uL, reader.readULongLE())
    }

    @Test
    fun `readULongBE reads big-endian`() {

        val reader = FrameReader(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08))

        assertEquals(0x0102030405060708uL, reader.readULongBE())
    }

    @Test
    fun `max values round-trip for each unsigned type`() {

        val reader = FrameReader(byteArrayOf(
            0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
        ))

        assertEquals(0xFF, reader.readUByte().toInt())
        assertEquals(0xFFFF, reader.readUShortLE().toInt())
        assertEquals(0xFFFFFFFFu, reader.readUIntLE())
        assertEquals(0xFFFFFFFFFFFFFFFFuL, reader.readULongLE())
    }

    @Test
    fun `readBytes returns requested count and advances`() {

        val reader = FrameReader(byteArrayOf(0x01, 0x02, 0x03, 0x04))

        val first = reader.readBytes(2)
        assertEquals(listOf<Byte>(0x01, 0x02), first.toList())

        val second = reader.readBytes(2)
        assertEquals(listOf<Byte>(0x03, 0x04), second.toList())
    }

    @Test
    fun `readBytes returns new array not internal buffer`() {

        val data = byteArrayOf(0x01, 0x02)
        val reader = FrameReader(data)

        val result = reader.readBytes(2)
        result[0] = 0x99.toByte()

        assertEquals(0x01, data[0].toInt())
    }

    @Test
    fun `readToEnd returns remaining bytes`() {

        val reader = FrameReader(byteArrayOf(0x01, 0x02, 0x03))

        reader.readUByte()
        val rest = reader.readToEnd()

        assertEquals(listOf<Byte>(0x02, 0x03), rest.toList())
    }

    @Test
    fun `readToEnd on exhausted reader returns empty`() {

        val reader = FrameReader(byteArrayOf(0x01))

        reader.readUByte()
        val rest = reader.readToEnd()

        assertEquals(0, rest.size)
    }

    @Test
    fun `readPeerIdentity reads 16 bytes`() {

        val raw = ByteArray(16) { i -> (i + 1).toByte() }
        val reader = FrameReader(raw)

        val identity = reader.readPeerIdentity()
        val expected = PeerIdentity.fromBytes(raw)

        assertEquals(expected.toBytes().toList(), identity.toBytes().toList())
    }

    @Test
    fun `readPeerIdentity advances offset by 16`() {

        val raw = ByteArray(18) { i -> i.toByte() }
        val reader = FrameReader(raw)

        reader.readPeerIdentity()
        assertEquals(2, reader.remaining)
    }

    @Test
    fun `requireRemaining throws for insufficient data`() {

        val reader = FrameReader(byteArrayOf(0x01))

        assertFailsWith<IllegalArgumentException> { reader.readUIntLE() }
    }

    @Test
    fun `requireRemaining throws for readPeerIdentity with less than 16 bytes`() {

        val reader = FrameReader(ByteArray(10))

        assertFailsWith<IllegalArgumentException> { reader.readPeerIdentity() }
    }

    @Test
    fun `offset advances across mixed reads`() {

        val reader = FrameReader(byteArrayOf(
            0x01,
            0x04, 0x03,
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
        ))

        reader.readUByte()
        reader.readUShortLE()
        reader.readUIntLE()

        assertEquals(0, reader.remaining)
    }
}
