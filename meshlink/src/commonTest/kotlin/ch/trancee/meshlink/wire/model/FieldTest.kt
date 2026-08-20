package ch.trancee.meshlink.wire.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FieldTest {

    @Test
    fun `defaults byteCount from type`() {

        val field = Field("hopLimit", FieldType.UBYTE)

        assertEquals(FieldType.UBYTE.byteCount, field.byteCount)
    }

    @Test
    fun `BYTE_ARRAY defaults to null byteCount`() {

        val field = Field("payload", FieldType.BYTE_ARRAY)

        assertNull(field.byteCount)
    }

    @Test
    fun `allows explicit byteCount override`() {

        val field = Field("payload", FieldType.BYTE_ARRAY, byteCount = 32)

        assertEquals(32, field.byteCount)
    }

    @Test
    fun `allows explicit byteCount that differs from type default`() {

        val field = Field("short", FieldType.UBYTE, byteCount = 4)

        assertEquals(4, field.byteCount)
    }

    @Test
    fun `defaults byteOrder to BIG_ENDIAN`() {

        val field = Field("hopLimit", FieldType.UBYTE)

        assertEquals(ByteOrder.BIG_ENDIAN, field.byteOrder)
    }

    @Test
    fun `allows explicit byteOrder`() {

        val field = Field("hopLimit", FieldType.UBYTE, byteOrder = ByteOrder.LITTLE_ENDIAN)

        assertEquals(ByteOrder.LITTLE_ENDIAN, field.byteOrder)
    }

    @Test
    fun `defaults presence to null`() {

        val field = Field("hopLimit", FieldType.UBYTE)

        assertNull(field.presence)
    }

    @Test
    fun `allows explicit presence`() {

        val field = Field("hopLimit", FieldType.UBYTE, presence = "always")

        assertEquals("always", field.presence)
    }

    @Test
    fun `equality considers all properties`() {

        val a = Field("hopLimit", FieldType.UBYTE, byteCount = 1, byteOrder = ByteOrder.BIG_ENDIAN, presence = null)
        val b = Field("hopLimit", FieldType.UBYTE)

        assertEquals(a, b)
    }

    @Test
    fun `equality distinguishes different names`() {

        val a = Field("hopLimit", FieldType.UBYTE)
        val b = Field("ttl", FieldType.UBYTE)

        assertTrue(a != b)
    }

    @Test
    fun `equality distinguishes different types`() {

        val a = Field("value", FieldType.UBYTE)
        val b = Field("value", FieldType.USHORT)

        assertTrue(a != b)
    }

    @Test
    fun `equality distinguishes different byteOrder`() {

        val a = Field("value", FieldType.UBYTE, byteOrder = ByteOrder.BIG_ENDIAN)
        val b = Field("value", FieldType.UBYTE, byteOrder = ByteOrder.LITTLE_ENDIAN)

        assertTrue(a != b)
    }

    @Test
    fun `data class componentN exposes all properties`() {

        val field = Field("hopLimit", FieldType.UBYTE, byteCount = 1, byteOrder = ByteOrder.LITTLE_ENDIAN, presence = "always")

        assertEquals("hopLimit", field.component1())
        assertEquals(FieldType.UBYTE, field.component2())
        assertEquals(1, field.component3())
        assertEquals(ByteOrder.LITTLE_ENDIAN, field.component4())
        assertEquals("always", field.component5())
    }
}
