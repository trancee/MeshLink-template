package ch.trancee.meshlink.model

import ch.trancee.meshlink.crypto.Crypto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AppHashTest {

    @Test
    fun `ZERO has fixed representation`() {
        assertEquals("00000000000000000000000000000000", AppHash.ZERO.toString())
    }

    @Test
    fun `ZERO produces 16 zero bytes`() {
        val bytes = AppHash.ZERO.toBytes()
        assertEquals(16, bytes.size)
        bytes.forEach { assertEquals(0, it.toInt()) }
    }

    @Test
    fun `derive produces known hash for example app`() {
        assertEquals(
            "86a544a2a619ff089ed5557e0ebcbdac",
            AppHash.derive("com.example.app").toString(),
        )
    }

    @Test
    fun `derive produces known hash for meshlink app`() {
        assertEquals(
            "08affdd52fe3b55aa638518095444e27",
            AppHash.derive("ch.trancee.meshlink").toString(),
        )
    }

    @Test
    fun `derive produces known hash for test app`() {
        assertEquals("078a4f05ae890422842844c5f4a6c46c", AppHash.derive("test").toString())
    }

    @Test
    fun `derive produces known hash for empty app id`() {
        assertEquals("884445a29291ab204ff8e13f3e6384f8", AppHash.derive("").toString())
    }

    @Test
    fun `derive is deterministic across repeated calls`() {
        val appId = "com.example.app"
        assertEquals(AppHash.derive(appId), AppHash.derive(appId))
    }

    @Test
    fun `derive produces different hashes for different appIds`() {
        val hash1 = AppHash.derive("com.example.app")
        val hash2 = AppHash.derive("com.example.other")
        assertTrue(hash1 != hash2)
    }

    @Test
    fun `derive result matches manual SHA-256 computation`() {
        val appId = "com.example.app"
        val prefix = "MeshLink app-id v1".encodeToByteArray()
        val suffix = appId.encodeToByteArray()
        val digest = Crypto.sha256(prefix + suffix).getOrThrow()
        val expected = AppHash.fromBytes(digest.copyOfRange(0, 16))

        assertEquals(expected, AppHash.derive(appId))
    }

    @Test
    fun `toBytes returns 16 bytes in big-endian order`() {
        val source = ByteArray(16) { i -> (i + 1).toByte() }
        val id = AppHash.fromBytes(source)

        val bytes = id.toBytes()
        assertEquals(16, bytes.size)
        assertEquals(source.toList(), bytes.toList())
    }

    @Test
    fun `toBytes roundtrips through fromBytes`() {
        val original = AppHash.derive("ch.trancee.meshlink")
        val bytes = original.toBytes()
        val restored = AppHash.fromBytes(bytes)

        assertEquals(original, restored)
    }

    @Test
    fun `toString produces 32-char hex with zero-padding`() {
        val bytes = ByteArray(16)
        bytes[7] = 1.toByte() // first ULong = 1 (big-endian)
        bytes[15] = 2.toByte() // second ULong = 2 (big-endian)
        val id = AppHash.fromBytes(bytes)

        assertEquals(32, id.toString().length)
        assertEquals("00000000000000010000000000000002", id.toString())
    }

    @Test
    fun `toString for nontrivial values is correct`() {
        val bytes = ByteArray(16) { i -> (i * 7 + 3).toByte() }
        val id = AppHash.fromBytes(bytes)

        assertEquals(bytes.toHexString(), id.toString())
    }

    @Test
    fun `fromBytes throws for wrong size`() {
        assertFailsWith<IllegalArgumentException> { AppHash.fromBytes(ByteArray(8)) }
    }
}
