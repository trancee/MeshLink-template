package ch.trancee.meshlink.wire.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class FieldTypeTest {

    @Test
    fun `entries has exactly six values`() {
        assertEquals(6, FieldType.entries.size)
    }

    @Test
    fun `entry names match spec`() {

        val names = FieldType.entries.map { it.name }

        assertEquals(
            listOf(
                "UBYTE",
                "USHORT",
                "UINT",
                "ULONG",
                "BYTE_ARRAY",
                "PEER_IDENTITY",
            ),
            names,
        )
    }

    @Test
    fun `byteCount matches spec for each fixed type`() {

        assertEquals(1, FieldType.UBYTE.byteCount)
        assertEquals(2, FieldType.USHORT.byteCount)
        assertEquals(4, FieldType.UINT.byteCount)
        assertEquals(8, FieldType.ULONG.byteCount)
        assertEquals(16, FieldType.PEER_IDENTITY.byteCount)
    }

    @Test
    fun `BYTE_ARRAY has null byteCount`() {

        assertNull(FieldType.BYTE_ARRAY.byteCount)
    }

    @Test
    fun `valueOf resolves each entry by name`() {

        for (entry in FieldType.entries) {
            assertEquals(entry, FieldType.valueOf(entry.name))
        }
    }

    @Test
    fun `valueOf throws for unknown name`() {

        assertFailsWith<IllegalArgumentException> { FieldType.valueOf("UNKNOWN") }
    }
}
