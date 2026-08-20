package ch.trancee.meshlink.wire.codec

import ch.trancee.meshlink.model.FrameType
import ch.trancee.meshlink.wire.model.WireFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class FrameCodecTest {

    @Test
    fun `encode produces correct envelope for known FrameType`() {

        val payload = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())

        val encoded = FrameCodec.encode(FrameType.PAYLOAD_CHUNK, 1u, payload)

        assertEquals(FrameType.PAYLOAD_CHUNK.code.toInt(), encoded[0].toInt())
        assertEquals(1, encoded[1].toInt())
        assertEquals(payload.size, encoded[2].toInt())
        assertTrue(encoded.copyOfRange(4, 4 + payload.size).contentEquals(payload))
    }

    @Test
    fun `decode round-trips encode`() {

        val original = WireFrame(FrameType.PAYLOAD_ACKNOWLEDGEMENT, 2u, byteArrayOf(0x01, 0x02, 0x03))

        val encoded = FrameCodec.encode(original.type, original.version, original.payload)
        val decoded = FrameCodec.decode(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `encode uses little-endian length`() {

        val payload = ByteArray(256) { 0xAB.toByte() }

        val encoded = FrameCodec.encode(FrameType.PAYLOAD_CHUNK, 0u, payload)

        assertEquals(0, encoded[2].toInt())
        assertEquals(1, encoded[3].toInt())
    }

    @Test
    fun `decode preserves type version and payload`() {

        val payload = byteArrayOf(0x01, 0x02)

        val encoded = FrameCodec.encode(FrameType.KEY_ROTATION, 5u, payload)
        val decoded = FrameCodec.decode(encoded)

        assertEquals(FrameType.KEY_ROTATION, decoded.type)
        assertEquals(5, decoded.version.toInt())
        assertTrue(payload.contentEquals(decoded.payload))
    }

    @Test
    fun `empty payload round-trips`() {

        val encoded = FrameCodec.encode(FrameType.MESH_ENVELOPE, 0u, byteArrayOf())
        val decoded = FrameCodec.decode(encoded)

        assertEquals(FrameType.MESH_ENVELOPE, decoded.type)
        assertEquals(0, decoded.version.toInt())
        assertEquals(0, decoded.payload.size)
    }

    @Test
    fun `max payload size round-trips`() {

        val payload = ByteArray(255) { it.toByte() }

        val encoded = FrameCodec.encode(FrameType.MESH_ENVELOPE, 0u, payload)
        val decoded = FrameCodec.decode(encoded)

        assertEquals(FrameType.MESH_ENVELOPE, decoded.type)
        assertTrue(payload.contentEquals(decoded.payload))
    }

    @Test
    fun `payload exceeding max throws`() {

        val payload = ByteArray(65536) { 0x01.toByte() }

        assertFailsWith<IllegalArgumentException> { FrameCodec.encode(FrameType.MESH_ENVELOPE, 0u, payload) }
    }

    @Test
    fun `decode throws for unknown frame code`() {

        val writer = FrameWriter(4)
        writer.writeUByte(0xFFu)
        writer.writeUByte(0u)
        writer.writeUShortLE(0u)
        val data = writer.toByteArray()

        assertFailsWith<IllegalArgumentException> { FrameCodec.decode(data) }
    }

    @Test
    fun `decode throws for too-short header`() {

        val data = byteArrayOf()

        assertFailsWith<IllegalArgumentException> { FrameCodec.decode(data) }
    }

    @Test
    fun `decode throws when length exceeds remaining bytes`() {

        val writer = FrameWriter(4)
        writer.writeUByte(FrameType.PAYLOAD_CHUNK.code)
        writer.writeUByte(0u)
        writer.writeUShortLE(10u)
        val data = writer.toByteArray()

        assertFailsWith<IllegalArgumentException> { FrameCodec.decode(data) }
    }

    @Test
    fun `version is preserved through round-trip`() {

        val versions = listOf<UByte>(0u, 1u, 2u, 3u, 4u, 5u, 6u, 7u)

        for (versionByte in versions) {
            val encoded = FrameCodec.encode(FrameType.MESH_ENVELOPE, versionByte, byteArrayOf(0x01))
            val decoded = FrameCodec.decode(encoded)

            assertEquals(versionByte, decoded.version, "version $versionByte did not round-trip")
        }
    }
}
