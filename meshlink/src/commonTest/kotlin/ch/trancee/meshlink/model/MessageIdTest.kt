package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MessageIdTest {

    @Test
    fun `zero id has fixed representation`() {
        val id = MessageId(0u)
        assertEquals(0u, id.rawValue())
    }

    @Test
    fun `nonzero id exposes decimal representation`() {
        val id = MessageId(42u)
        assertEquals(42u, id.rawValue())
    }

    @Test
    fun `fromBytes rejects invalid byte array size`() {
        assertFailsWith<IllegalArgumentException> { MessageId.fromBytes(ByteArray(3)) }
        assertFailsWith<IllegalArgumentException> { MessageId.fromBytes(ByteArray(5)) }
    }

    @Test
    fun `rawValue returns raw value`() {
        val id = MessageId(0xDEADBEEFu)
        assertEquals(0xDEADBEEFu, id.rawValue())
    }

    @Test
    fun `toBytes produces 4-byte big-endian`() {
        val id = MessageId(0x01020304u)
        val bytes = id.toBytes()
        assertEquals(4, bytes.size)
        assertEquals(0x01.toByte(), bytes[0])
        assertEquals(0x02.toByte(), bytes[1])
        assertEquals(0x03.toByte(), bytes[2])
        assertEquals(0x04.toByte(), bytes[3])
    }

    @Test
    fun `fromBytes roundtrips through toBytes`() {
        val original = MessageId(0xAABBCCDDu)
        val bytes = original.toBytes()
        val restored = MessageId.fromBytes(bytes)
        assertEquals(original, restored)
    }

    @Test
    fun `fromBytes throws for invalid byte array size`() {
        assertFailsWith<IllegalArgumentException> { MessageId.fromBytes(ByteArray(3)) }
        assertFailsWith<IllegalArgumentException> { MessageId.fromBytes(ByteArray(5)) }
    }

    @Test
    fun `inc operator increments with wrap`() {
        var id = MessageId(0u)
        id = id.inc()
        assertEquals(1u, id.rawValue())

        id = MessageId(0xFFFFFFFFu)
        id = id.inc()
        assertEquals(0u, id.rawValue())
    }

    @Test
    fun `ZERO is accessible`() {
        assertEquals(0u, MessageId.ZERO.rawValue())
    }

    @Test
    fun `fromBytes creates id from byte array`() {
        val id =
            MessageId.fromBytes(
                ByteArray(4).also {
                    it[0] = 1
                    it[1] = 2
                    it[2] = 3
                    it[3] = 4
                }
            )
        assertEquals(0x01020304u, id.rawValue())
    }
}
