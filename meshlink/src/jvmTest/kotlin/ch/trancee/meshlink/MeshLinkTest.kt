package ch.trancee.meshlink

import ch.trancee.meshlink.model.KnownPeer
import ch.trancee.meshlink.model.MeshLinkState
import ch.trancee.meshlink.model.PeerIdentity
import ch.trancee.meshlink.model.PowerMode
import ch.trancee.meshlink.model.Transfer
import ch.trancee.meshlink.model.TransferSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking

class MeshLinkTest {

    private fun createMeshLink(): MeshLink =
        MeshLink.create(MeshLinkSettings(appId = "test-app"), JvmMeshLinkEnvironment())

    @Test
    fun `create rejects a blank appId`() {
        val settings = MeshLinkSettings(appId = "")
        val environment = JvmMeshLinkEnvironment()

        assertFailsWith<IllegalArgumentException> { MeshLink.create(settings, environment) }
    }

    @Test
    fun `create rejects a whitespace-only appId`() {
        val settings = MeshLinkSettings(appId = "   ")
        val environment = JvmMeshLinkEnvironment()

        assertFailsWith<IllegalArgumentException> { MeshLink.create(settings, environment) }
    }

    @Test
    fun `create rejects an appId exceeding 255 UTF-8 bytes`() {
        val settings = MeshLinkSettings(appId = "x".repeat(256))
        val environment = JvmMeshLinkEnvironment()

        assertFailsWith<IllegalArgumentException> { MeshLink.create(settings, environment) }
    }

    @Test
    fun `create accepts an appId of exactly 255 UTF-8 bytes`() {
        val settings = MeshLinkSettings(appId = "x".repeat(255))
        val environment = JvmMeshLinkEnvironment()

        val meshLink = MeshLink.create(settings, environment)

        assertEquals(MeshLinkState.CONFIGURED, meshLink.state.value)
    }

    @Test
    fun `create with valid settings returns a CONFIGURED instance`() {
        val settings = MeshLinkSettings(appId = "test-app")
        val environment = JvmMeshLinkEnvironment()

        val meshLink = MeshLink.create(settings, environment)

        assertEquals(MeshLinkState.CONFIGURED, meshLink.state.value)
        assertEquals(emptyList<KnownPeer>(), meshLink.peers.value)
        assertEquals(emptyList<Transfer>(), meshLink.transfers.value)
        assertEquals(PowerMode.MEDIUM, meshLink.powerMode.value)
        assertNotNull(meshLink.powerModeSettings.value)
    }

    @Test
    fun `start throws NotImplementedError`() {
        val meshLink = createMeshLink()

        assertFailsWith<NotImplementedError> { runBlocking { meshLink.start() } }
    }

    @Test
    fun `pause throws NotImplementedError`() {
        val meshLink = createMeshLink()

        assertFailsWith<NotImplementedError> { runBlocking { meshLink.pause() } }
    }

    @Test
    fun `resume throws NotImplementedError`() {
        val meshLink = createMeshLink()

        assertFailsWith<NotImplementedError> { runBlocking { meshLink.resume() } }
    }

    @Test
    fun `stop throws NotImplementedError`() {
        val meshLink = createMeshLink()

        assertFailsWith<NotImplementedError> { runBlocking { meshLink.stop() } }
    }

    @Test
    fun `setPowerMode throws NotImplementedError`() {
        val meshLink = createMeshLink()

        assertFailsWith<NotImplementedError> {
            runBlocking { meshLink.setPowerMode(PowerMode.HIGH) }
        }
    }

    @Test
    fun `sendMessage without options throws NotImplementedError`() {
        val meshLink = createMeshLink()
        val peer = PeerIdentity.generate()
        val payload = byteArrayOf(1, 2, 3)

        assertFailsWith<NotImplementedError> { runBlocking { meshLink.sendMessage(peer, payload) } }
    }

    @Test
    fun `sendPayload without options throws NotImplementedError`() {
        val meshLink = createMeshLink()
        val peer = PeerIdentity.generate()
        val source =
            object : TransferSource {
                override val total: Long = 0L

                override suspend fun read(offset: Long, length: Int): ByteArray = byteArrayOf()
            }

        assertFailsWith<NotImplementedError> { runBlocking { meshLink.sendPayload(peer, source) } }
    }

    @Test
    fun `revokeTrust throws NotImplementedError`() {
        val meshLink = createMeshLink()
        val peer = PeerIdentity.generate()

        assertFailsWith<NotImplementedError> { runBlocking { meshLink.revokeTrust(peer) } }
    }

    @Test
    fun `resetTrust throws NotImplementedError`() {
        val meshLink = createMeshLink()
        val peer = PeerIdentity.generate()

        assertFailsWith<NotImplementedError> { runBlocking { meshLink.resetTrust(peer) } }
    }
}
