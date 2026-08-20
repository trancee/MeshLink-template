package ch.trancee.meshlink.wire.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ByteOrderTest {

    @Test
    fun `entries has exactly two values`() {
        assertEquals(2, ByteOrder.entries.size)
    }

    @Test
    fun `entry names match spec`() {

        val names = ByteOrder.entries.map { it.name }

        assertEquals(listOf("BIG_ENDIAN", "LITTLE_ENDIAN"), names)
    }

    @Test
    fun `valueOf resolves each entry by name`() {

        assertEquals(ByteOrder.BIG_ENDIAN, ByteOrder.valueOf("BIG_ENDIAN"))
        assertEquals(ByteOrder.LITTLE_ENDIAN, ByteOrder.valueOf("LITTLE_ENDIAN"))
    }

    @Test
    fun `valueOf throws for unknown name`() {

        assertFailsWith<IllegalArgumentException> { ByteOrder.valueOf("MIDDLE_ENDIAN") }
    }
}
