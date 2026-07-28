package ch.trancee.meshlink

import ch.trancee.meshlink.model.HandshakeKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class HandshakeKeyTest {
    @Test
    fun `fromBytes and to raw returns same bytes`() {
        val bytes = ByteArray(32) { i -> i.toByte() }
        val key = HandshakeKey.fromBytes(bytes)
        assertEquals(HandshakeKey.fromBytes(bytes), key)
    }

    @Test
    fun `fromHex roundtrip`() {
        val hex = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
        val key = HandshakeKey.fromHex(hex)
        assertEquals(hex, key.toString())
    }

    @Test
    fun `fromBytes throws on wrong size`() {
        val bytes = ByteArray(16) { 0 }
        try {
            HandshakeKey.fromBytes(bytes)
            kotlin.test.fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("HandshakeKey must be exactly 32 bytes", e.message)
        }
    }

    @Test
    fun `fromHex throws on wrong length`() {
        val hex = "000102030405060708090a0b0c0d0e0f" // 16 hex chars = 8 bytes
        try {
            HandshakeKey.fromHex(hex)
            kotlin.test.fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("HandshakeKey must be 64 hex chars (32 bytes)", e.message)
        }
    }

    @Test
    fun `toString returns hex`() {
        val hex = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
        val key = HandshakeKey.fromHex(hex)
        assertEquals(hex, key.toString())
        assertEquals(key.toString(), key.toString())
    }

    @Test
    fun `toString for different keys are not equal`() {
        val key1 =
            HandshakeKey.fromHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val key2 =
            HandshakeKey.fromHex("ff0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        assertNotEquals(key1.toString(), key2.toString())
    }
}
