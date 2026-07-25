package ch.trancee.meshlink

import ch.trancee.meshlink.model.PeerIdentity
import kotlin.test.Test
import kotlin.test.assertEquals

class PeerIdentityTest {
    @Test
    fun `has lo and hi components`() {
        val id = PeerIdentity.ZERO
        assertEquals(0UL, id.lo)
        assertEquals(0UL, id.hi)
    }

    @Test
    fun `hex getter works`() {
        assertEquals("00000000000000000000000000000000", PeerIdentity.ZERO.hex)
        val id = PeerIdentity.generate()
        assertEquals(32, id.hex.length)
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
        assertEquals(bytes.toHexString(), id.hex)
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
}
