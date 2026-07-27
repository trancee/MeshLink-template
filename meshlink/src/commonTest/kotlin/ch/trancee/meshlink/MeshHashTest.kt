package ch.trancee.meshlink

import ch.trancee.meshlink.util.MeshHash
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertTrue(hash <= 0xFFFFu)
    }

    @Test
    fun `derive produces different hashes for different appIds`() {
        val hash1 = MeshHash.derive("com.example.app")
        val hash2 = MeshHash.derive("com.example.other")
        assertTrue(hash1 != hash2)
    }
}
