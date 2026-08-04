package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TransferIdTest {

    @Test
    fun `zero id has fixed representation`() {
        val id = TransferId(0u)
        assertEquals(0u, id.rawValue())
    }

    @Test
    fun `nonzero id exposes decimal representation`() {
        val id = TransferId(42u)
        assertEquals(42u, id.rawValue())
    }

    @Test
    fun `decimal id round trips through toString and fromUInt`() {
        val expected = TransferId(42u)
        val actual = TransferId.fromUInt(expected.toString().toUInt())
        assertEquals(expected, actual)
        assertEquals(42u, expected.rawValue())
    }

    @Test
    fun `fromBytes rejects invalid byte array size`() {
        assertFailsWith<IllegalArgumentException> { TransferId.fromBytes(ByteArray(3)) }
        assertFailsWith<IllegalArgumentException> { TransferId.fromBytes(ByteArray(5)) }
    }

    @Test
    fun `rawValue returns raw value`() {
        val id = TransferId(0xDEADBEEFu)
        assertEquals(0xDEADBEEFu, id.rawValue())
    }

    @Test
    fun `fromUInt creates id from raw value`() {
        val id = TransferId.fromUInt(0xCAFEBABEu)
        assertEquals(0xCAFEBABEu, id.rawValue())
    }

    @Test
    fun `toByteArray produces 4-byte big-endian`() {
        val id = TransferId(0x01020304u)
        val bytes = id.toByteArray()
        assertEquals(4, bytes.size)
        assertEquals(0x01.toByte(), bytes[0])
        assertEquals(0x02.toByte(), bytes[1])
        assertEquals(0x03.toByte(), bytes[2])
        assertEquals(0x04.toByte(), bytes[3])
    }

    @Test
    fun `fromBytes roundtrips through toByteArray`() {
        val original = TransferId(0xAABBCCDDu)
        val bytes = original.toByteArray()
        val restored = TransferId.fromBytes(bytes)
        assertEquals(original, restored)
    }

    @Test
    fun `fromBytes throws for invalid byte array size`() {
        assertFailsWith<IllegalArgumentException> { TransferId.fromBytes(ByteArray(3)) }
        assertFailsWith<IllegalArgumentException> { TransferId.fromBytes(ByteArray(5)) }
    }

    @Test
    fun `inc operator increments with wrap`() {
        var id = TransferId(0u)
        id = id.inc()
        assertEquals(1u, id.rawValue())

        id = TransferId(0xFFFFFFFFu)
        id = id.inc()
        assertEquals(0u, id.rawValue())
    }

    @Test
    fun `compareTo orders by UInt value`() {
        val id1 = TransferId(1u)
        val id2 = TransferId(2u)
        val id3 = TransferId(0xFFFFFFFFu)

        assertEquals(-1, id1.compareTo(id2))
        assertEquals(1, id2.compareTo(id1))
        assertEquals(0, id1.compareTo(id1))
        assertEquals(-1, id1.compareTo(id3)) // 1 < UINT_MAX
    }

    @Test
    fun `ZERO is accessible`() {
        assertEquals(0u, TransferId.ZERO.rawValue())
    }

    @Test
    fun `fromBytes creates id from byte array`() {
        val id =
            TransferId.fromBytes(
                ByteArray(4).also {
                    it[0] = 1
                    it[1] = 2
                    it[2] = 3
                    it[3] = 4
                }
            )
        assertEquals(0x01020304u, id.rawValue())
    }

    @Test
    fun `fromUInt creates id from UInt`() {
        val id = TransferId.fromUInt(42u)
        assertEquals(42u, id.rawValue())
    }
}
