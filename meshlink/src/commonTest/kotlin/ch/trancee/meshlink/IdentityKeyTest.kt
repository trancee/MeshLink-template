package ch.trancee.meshlink

import ch.trancee.meshlink.model.IdentityKey
import kotlin.test.Test
import kotlin.test.assertEquals

class IdentityKeyTest {
    @Test
    fun `fromBytes and to raw returns same bytes`() {
        val bytes = ByteArray(32) { i -> i.toByte() }
        val key = IdentityKey.fromBytes(bytes)
        assertEquals(bytes.toList(), key.raw.toList())
    }

    @Test
    fun `fromHex roundtrip`() {
        val hex = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
        val key = IdentityKey.fromHex(hex)
        assertEquals(hex, key.hex)
    }
}
