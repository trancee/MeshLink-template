package ch.trancee.meshlink

import ch.trancee.meshlink.util.toBytesBE
import ch.trancee.meshlink.util.toULongBE
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversionTest {
    @Test
    fun `toULongBE reads 8 bytes correctly`() {
        val bytes = ByteArray(16) { i -> (i * 17).toByte() }
        val lo = bytes.toULongBE(0)
        val hi = bytes.toULongBE(8)
        assertEquals(0x0011223344556677UL, lo)
        assertEquals(0x8899AABBCCDDEEFFUL, hi)
    }

    @Test
    fun `toBytesBE converts ULong to 8 bytes`() {
        val value = 0x0011223344556677UL
        val bytes = value.toBytesBE()
        assertEquals(8, bytes.size)
        assertEquals(0x00, bytes[0])
        assertEquals(0x11, bytes[1])
        assertEquals(0x22, bytes[2])
        assertEquals(0x33, bytes[3])
        assertEquals(0x44, bytes[4])
        assertEquals(0x55, bytes[5])
        assertEquals(0x66, bytes[6])
        assertEquals(0x77, bytes[7])
    }

    @Test
    fun `roundtrip ULong to bytes to ULong`() {
        val original = 0xDEADBEEFCAFEBABEUL
        val bytes = original.toBytesBE()
        val restored = bytes.toULongBE(0)
        assertEquals(original, restored)
    }

    @Test
    fun `toULongBE with default offset reads from start`() {
        val bytes = ByteArray(8) { i -> (i * 17).toByte() }
        val result = bytes.toULongBE() // uses default offset = 0
        assertEquals(0x0011223344556677UL, result)
    }
}
