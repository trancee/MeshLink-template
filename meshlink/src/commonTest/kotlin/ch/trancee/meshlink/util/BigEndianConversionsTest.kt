package ch.trancee.meshlink.util

import kotlin.test.Test
import kotlin.test.assertEquals

class BigEndianConversionsTest {
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

    @Test
    fun `toUIntBE reads 4 bytes correctly`() {
        // Arrange
        val bytes = byteArrayOf(0x12, 0x34, 0x56, 0x78)

        // Act
        val result = bytes.toUIntBE(0)

        // Assert
        assertEquals(0x12345678u, result)
    }

    @Test
    fun `toUIntBE with default offset reads from start`() {
        // Arrange
        val bytes = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())

        // Act
        val result = bytes.toUIntBE()

        // Assert
        assertEquals(0xDEADBEEFu, result)
    }

    @Test
    fun `toUIntBE reads from non-zero offset`() {
        // Arrange
        val bytes =
            byteArrayOf(0x00, 0x00, 0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte(), 0x01, 0x02, 0x03)

        // Act
        val result = bytes.toUIntBE(2)

        // Assert
        assertEquals(0xABCDEF01u, result)
    }

    @Test
    fun `UInt toBytesBE converts to 4 bytes`() {
        // Arrange
        val value = 0x12345678u

        // Act
        val bytes = value.toBytesBE()

        // Assert
        assertEquals(4, bytes.size)
        assertEquals(0x12, bytes[0])
        assertEquals(0x34, bytes[1])
        assertEquals(0x56, bytes[2])
        assertEquals(0x78, bytes[3])
    }

    @Test
    fun `UInt toBytesBE handles max value`() {
        // Arrange
        val value = UInt.MAX_VALUE

        // Act
        val bytes = value.toBytesBE()

        // Assert
        assertEquals(4, bytes.size)
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xFF.toByte(), bytes[1])
        assertEquals(0xFF.toByte(), bytes[2])
        assertEquals(0xFF.toByte(), bytes[3])
    }

    @Test
    fun `UInt roundtrip toBytesBE and toUIntBE`() {
        // Arrange
        val original = 0xDEADBEEFu

        // Act
        val bytes = original.toBytesBE()
        val restored = bytes.toUIntBE(0)

        // Assert
        assertEquals(original, restored)
    }

    @Test
    fun `UInt roundtrip for zero`() {
        // Arrange
        val original = 0u

        // Act
        val bytes = original.toBytesBE()
        val restored = bytes.toUIntBE(0)

        // Assert
        assertEquals(original, restored)
        assertEquals(0, bytes[0])
        assertEquals(0, bytes[1])
        assertEquals(0, bytes[2])
        assertEquals(0, bytes[3])
    }
}
