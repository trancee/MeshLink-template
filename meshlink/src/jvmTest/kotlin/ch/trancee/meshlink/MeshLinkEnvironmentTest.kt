package ch.trancee.meshlink

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.runBlocking

class MeshLinkEnvironmentTest {

    // --- JvmSecureStorage ---

    @Test
    fun `JvmSecureStorage put and get round trips data`() {
        val storage = JvmSecureStorage()
        val data = byteArrayOf(1, 2, 3)

        runBlocking { storage.put("key", data) }
        val result = runBlocking { storage.get("key") }

        assertTrue(data.contentEquals(result))
    }

    @Test
    fun `JvmSecureStorage get returns null for missing key`() {
        val storage = JvmSecureStorage()

        val result = runBlocking { storage.get("nonexistent") }

        assertNull(result)
    }

    @Test
    fun `JvmSecureStorage get returns a copy not the original reference`() {
        val storage = JvmSecureStorage()
        val data = byteArrayOf(1, 2, 3)

        runBlocking { storage.put("key", data) }
        val retrieved = runBlocking { storage.get("key") }!!
        retrieved[0] = 99

        val again = runBlocking { storage.get("key") }
        assertTrue(data.contentEquals(again))
        assertEquals(99, retrieved[0])
    }

    @Test
    fun `JvmSecureStorage delete removes data`() {
        val storage = JvmSecureStorage()

        runBlocking {
            storage.put("key", byteArrayOf(1, 2, 3))
            storage.delete("key")
        }

        val result = runBlocking { storage.get("key") }
        assertNull(result)
        val contains = runBlocking { storage.contains("key") }
        assertFalse(contains)
    }

    @Test
    fun `JvmSecureStorage contains returns false for missing key`() {
        val storage = JvmSecureStorage()

        val result = runBlocking { storage.contains("nonexistent") }

        assertFalse(result)
    }

    @Test
    fun `JvmSecureStorage contains returns true for stored key`() {
        val storage = JvmSecureStorage()

        runBlocking { storage.put("key", byteArrayOf(1, 2, 3)) }
        val result = runBlocking { storage.contains("key") }

        assertTrue(result)
    }

    // --- JvmMonotonicClock ---

    @Test
    fun `JvmMonotonicClock now returns a valid Instant`() {
        val clock = JvmMonotonicClock()

        val now = clock.now()

        assertNotNull(now)
    }

    @Test
    fun `JvmMonotonicClock elapsedSince returns non-negative Duration`() {
        val clock = JvmMonotonicClock()
        val start = Clock.System.now()

        val elapsed = clock.elapsedSince(start)

        assertTrue(elapsed >= Duration.ZERO)
    }

    // --- JvmMeshLinkEnvironment ---

    @Test
    fun `JvmMeshLinkEnvironment properties are initialized`() {
        val env = JvmMeshLinkEnvironment()

        assertNotNull(env.secureStorage)
        assertNotNull(env.monotonicClock)
        assertNotNull(env.radioDispatcher)
        assertNotNull(env.computeDispatcher)
    }

    @Test
    fun `JvmMeshLinkEnvironment acquireRadioLease returns a lease on first call`() {
        val env = JvmMeshLinkEnvironment()

        val lease = runBlocking { env.acquireRadioLease() }

        assertNotNull(lease)
    }

    @Test
    fun `JvmMeshLinkEnvironment acquireRadioLease throws on second call`() {
        val env = JvmMeshLinkEnvironment()

        assertFailsWith<IllegalArgumentException> {
            runBlocking {
                env.acquireRadioLease()
                env.acquireRadioLease()
            }
        }
    }

    @Test
    fun `JvmMeshLinkEnvironment releaseRadioLease accepts the acquired lease`() {
        val env = JvmMeshLinkEnvironment()

        runBlocking {
            val lease = env.acquireRadioLease()
            env.releaseRadioLease(lease)
        }
    }

    @Test
    fun `JvmMeshLinkEnvironment releaseRadioLease rejects a foreign lease`() {
        val env = JvmMeshLinkEnvironment()
        val foreignLease = RadioLease()

        assertFailsWith<IllegalArgumentException> {
            runBlocking {
                env.acquireRadioLease()
                env.releaseRadioLease(foreignLease)
            }
        }
    }
}
