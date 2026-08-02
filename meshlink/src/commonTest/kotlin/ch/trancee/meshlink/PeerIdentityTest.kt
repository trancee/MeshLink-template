package ch.trancee.meshlink

import ch.trancee.meshlink.model.PeerIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PeerIdentityTest {
    @Test
    fun `ZERO has zero components`() {
        assertEquals("00000000000000000000000000000000", PeerIdentity.ZERO.toString())
    }

    @Test
    fun `hex getter works`() {
        assertEquals("00000000000000000000000000000000", PeerIdentity.ZERO.toString())
        val id = PeerIdentity.generate()
        assertEquals(32, id.toString().length)
    }

    @Test
    fun `generate creates valid identity`() {
        val id = PeerIdentity.generate()
        assertEquals(16, id.toByteArray().size)
    }

    @Test
    fun `fromBytes roundtrips`() {
        val bytes = ByteArray(16) { i -> i.toByte() }
        val id = PeerIdentity.fromBytes(bytes)
        assertEquals(bytes.toList(), id.toByteArray().toList())
        assertEquals(bytes.toHexString(), id.toString())
    }

    @Test
    fun `fromBytes throws on wrong size`() {
        val bytes = ByteArray(8) { 0 }
        try {
            PeerIdentity.fromBytes(bytes)
            kotlin.test.fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("PeerIdentity must be exactly 16 bytes", e.message)
        }
    }

    @Test
    fun `toString returns hex`() {
        assertEquals("00000000000000000000000000000000", PeerIdentity.ZERO.toString())
        assertEquals(PeerIdentity.ZERO.toString(), PeerIdentity.ZERO.toString())
    }

    @Test
    fun `toString runtime zero identity`() {
        // Create zero identity at runtime to ensure toString() is executed
        val zeroId = PeerIdentity.fromBytes(ByteArray(16))
        assertEquals("00000000000000000000000000000000", zeroId.toString())
    }

    @Test
    fun `toString for different identities are not equal`() {
        val id1 = PeerIdentity.generate()
        val id2 = PeerIdentity.generate()
        assertNotEquals(id1.toString(), id2.toString())
    }

    @Test
    fun `toString with non-trivial bytes`() {
        val bytes = ByteArray(16) { i -> (i * 7 + 3).toByte() }
        val id = PeerIdentity.fromBytes(bytes)
        assertEquals(bytes.toHexString(), id.toString())
    }

    @Test
    fun `fromHex roundtrips through toString`() {
        val hex = "0102030405060708090a0b0c0d0e0f10"
        val id = PeerIdentity.fromHex(hex)
        assertEquals(hex, id.toString())
        assertEquals(id, PeerIdentity.fromBytes(id.toByteArray()))
    }

    @Test
    fun `fromHex throws on wrong length`() {
        try {
            PeerIdentity.fromHex("00") // too short
            kotlin.test.fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("PeerIdentity must be 32 hex chars (16 bytes)", e.message)
        }
        try {
            PeerIdentity.fromHex("00" + "0".repeat(31)) // too long
            kotlin.test.fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("PeerIdentity must be 32 hex chars (16 bytes)", e.message)
        }
    }
}
