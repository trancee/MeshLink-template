package ch.trancee.meshlink

import ch.trancee.meshlink.util.MeshHash
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MeshHashTest {
    @Test
    fun `derive is stable across runs`() {
        val appId = "com.example.app"
        val hash1 = MeshHash.derive(appId)
        val hash2 = MeshHash.derive(appId)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `derive produces 16-bit value`() {
        val hash = MeshHash.derive("com.example.app")
        assertTrue(hash in 0u..0xFFFFu, "MeshHash must fit in 16 bits but was $hash")
    }

    @Test
    fun `derive produces different hashes for different appIds`() {
        val hash1 = MeshHash.derive("com.example.app")
        val hash2 = MeshHash.derive("com.example.other")
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `empty app id produces FNV offset basis truncated to 16 bits`() {
        // FNV-1a 32-bit: offset basis is 0x811c9dc5, no bytes to process,
        // so the result is the offset basis masked to 16 bits.
        val expected: UInt = 0x811c9dc5u and 0xFFFFu
        assertEquals(expected, MeshHash.derive(""))
    }

    @Test
    fun `hash distribution is spread across the 16-bit range`() {
        // Arrange — a spread of realistic app IDs
        val appIds =
            (0..19).map { i -> "com.example.app$i" } + (0..19).map { i -> "com.example.other$i" }

        val hashes = appIds.map { MeshHash.derive(it) }
        val uniqueHashes = hashes.toSet()

        // Assert — at least 80% of the 40 inputs produce unique hashes
        // (FNV-1a has good distribution; collisions should be minimal)
        val uniquenessRatio = uniqueHashes.size.toDouble() / hashes.size.toDouble()
        assertTrue(
            uniquenessRatio >= 0.8,
            "Expected >=80% unique hashes, got ${uniqueHashes.size}/${hashes.size} unique",
        )
    }
}
