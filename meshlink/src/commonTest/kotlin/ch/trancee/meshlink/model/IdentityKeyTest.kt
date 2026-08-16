package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IdentityKeyTest {
    @Test
    fun `fromBytes and to raw returns same bytes`() {
        val bytes = ByteArray(32) { i -> i.toByte() }
        val key = IdentityKey.fromBytes(bytes)
        assertEquals(IdentityKey.fromBytes(bytes), key)
    }

    @Test
    fun `fromHex roundtrip`() {
        val hex = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
        val key = IdentityKey.fromHex(hex)
        assertEquals(hex, key.toString())
    }

    @Test
    fun `fromHex throws on wrong length`() {
        val hex = "000102030405060708090a0b0c0d0e0f" // 16 hex chars = 8 bytes
        try {
            IdentityKey.fromHex(hex)
            kotlin.test.fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("IdentityKey must be 64 hex chars (32 bytes)", e.message)
        }
    }

    @Test
    fun `fromBytes throws on wrong size`() {
        val bytes = ByteArray(16) { 0 }
        try {
            IdentityKey.fromBytes(bytes)
            kotlin.test.fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("IdentityKey must be 32 bytes", e.message)
        }
    }

    @Test
    fun `toString returns hex`() {
        val hex = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
        val key = IdentityKey.fromHex(hex)
        assertEquals(hex, key.toString())
        assertEquals(key.toString(), key.toString())
    }

    @Test
    fun `toString for different keys are not equal`() {
        val key1 =
            IdentityKey.fromHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val key2 =
            IdentityKey.fromHex("ff0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        assertNotEquals(key1.toString(), key2.toString())
    }

    @Test
    fun `toByteArray returns defensive copy of raw bytes`() {
        val bytes = ByteArray(32) { i -> i.toByte() }
        val key = IdentityKey.fromBytes(bytes)
        val extracted = key.toByteArray()
        assertEquals(bytes.toList(), extracted.toList())
        // mutating the extracted copy must not affect the key
        extracted[0] = (extracted[0] + 1).toByte()
        assertEquals(bytes.toList(), key.toByteArray().toList())
    }

    @Test
    fun `fromBytes and toByteArray roundtrip`() {
        val hex = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
        val key = IdentityKey.fromHex(hex)
        assertEquals(hex, key.toByteArray().toHexString())
    }
}
