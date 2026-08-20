package ch.trancee.meshlink.wire.model

import ch.trancee.meshlink.model.FrameType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WireFrameTest {

    @Test
    fun `constructs with type version and payload`() {

        val payload = byteArrayOf(0x01, 0x02, 0x03)
        val frame = WireFrame(FrameType.PAYLOAD_CHUNK, 0u, payload)

        assertEquals(FrameType.PAYLOAD_CHUNK, frame.type)
        assertEquals(0u, frame.version)
        assertTrue(payload.contentEquals(frame.payload))
    }

    @Test
    fun `constructs with non-zero version`() {

        val frame = WireFrame(FrameType.PAYLOAD_CHUNK, 3u, byteArrayOf())

        assertEquals(3u, frame.version)
    }

    @Test
    fun `empty payload is allowed`() {

        val frame = WireFrame(FrameType.MESH_ENVELOPE, 1u, byteArrayOf())

        assertEquals(0, frame.payload.size)
    }

    @Test
    fun `equals compares payload content not identity`() {

        val a = WireFrame(FrameType.PAYLOAD_CHUNK, 0u, byteArrayOf(0x01, 0x02))
        val b = WireFrame(FrameType.PAYLOAD_CHUNK, 0u, byteArrayOf(0x01, 0x02))

        assertEquals(a, b)
    }

    @Test
    fun `equals returns false for different payload content`() {

        val a = WireFrame(FrameType.PAYLOAD_CHUNK, 0u, byteArrayOf(0x01, 0x02))
        val b = WireFrame(FrameType.PAYLOAD_CHUNK, 0u, byteArrayOf(0x01, 0x03))

        assertNotEquals(a, b)
    }

    @Test
    fun `equals returns false for different type`() {

        val a = WireFrame(FrameType.PAYLOAD_CHUNK, 0u, byteArrayOf())
        val b = WireFrame(FrameType.PAYLOAD_ACKNOWLEDGEMENT, 0u, byteArrayOf())

        assertNotEquals(a, b)
    }

    @Test
    fun `equals returns false for different version`() {

        val a = WireFrame(FrameType.PAYLOAD_CHUNK, 0u, byteArrayOf())
        val b = WireFrame(FrameType.PAYLOAD_CHUNK, 1u, byteArrayOf())

        assertNotEquals(a, b)
    }

    @Test
    fun `hashCode is consistent with equals`() {

        val a = WireFrame(FrameType.PAYLOAD_CHUNK, 0u, byteArrayOf(0x01, 0x02))
        val b = WireFrame(FrameType.PAYLOAD_CHUNK, 0u, byteArrayOf(0x01, 0x02))

        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `hashCode differs for different payload content`() {

        val a = WireFrame(FrameType.PAYLOAD_CHUNK, 0u, byteArrayOf(0x01, 0x02))
        val b = WireFrame(FrameType.PAYLOAD_CHUNK, 0u, byteArrayOf(0x01, 0x03))

        assertNotEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `copy preserves payload content equality`() {

        val original = WireFrame(FrameType.PAYLOAD_CHUNK, 0u, byteArrayOf(0x01, 0x02))
        val copied = original.copy()

        assertEquals(original, copied)
    }

    @Test
    fun `copy with new type produces different frame`() {

        val original = WireFrame(FrameType.PAYLOAD_CHUNK, 0u, byteArrayOf())
        val copied = original.copy(type = FrameType.PAYLOAD_ACKNOWLEDGEMENT)

        assertNotEquals(original, copied)
        assertEquals(FrameType.PAYLOAD_ACKNOWLEDGEMENT, copied.type)
    }


    @Test
    fun `equals returns true for same instance`() {

        val frame = WireFrame(FrameType.PAYLOAD_CHUNK, 0u, byteArrayOf(0x01))

        assertEquals(frame, frame)
    }

    @Test
    fun `equals returns false for non-WireFrame type`() {

        val frame = WireFrame(FrameType.PAYLOAD_CHUNK, 0u, byteArrayOf(0x01))

        assertFalse(frame.equals("not a wire frame"))
    }
}
