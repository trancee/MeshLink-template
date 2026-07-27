package ch.trancee.meshlink

import ch.trancee.meshlink.model.PeerConnectionState
import ch.trancee.meshlink.model.PeerEvent
import ch.trancee.meshlink.model.PeerIdentity
import kotlin.test.Test
import kotlin.test.assertEquals

class PeerLifecycleTest {
    @Test
    fun `PeerConnectionState has CONNECTED and DISCONNECTED`() {
        assertEquals(2, PeerConnectionState.entries.size)
        assertEquals(PeerConnectionState.CONNECTED, PeerConnectionState.CONNECTED)
        assertEquals(PeerConnectionState.DISCONNECTED, PeerConnectionState.DISCONNECTED)
    }

    @Test
    fun `PeerEvent Found delivers CONNECTED state`() {
        val peerId = PeerIdentity.fromBytes(ByteArray(16) { 0x01 })
        val event = PeerEvent.Found(peerId, PeerConnectionState.CONNECTED)
        assertEquals(PeerConnectionState.CONNECTED, event.state)
        assertEquals(peerId, event.peerId)
    }

    @Test
    fun `PeerEvent StateChanged delivers DISCONNECTED state`() {
        val peerId = PeerIdentity.fromBytes(ByteArray(16) { 0x02 })
        val event = PeerEvent.StateChanged(peerId, PeerConnectionState.DISCONNECTED)
        assertEquals(PeerConnectionState.DISCONNECTED, event.state)
        assertEquals(peerId, event.peerId)
    }

    @Test
    fun `PeerEvent Lost emits with peer id`() {
        val peerId = PeerIdentity.fromBytes(ByteArray(16) { 0x03 })
        val event = PeerEvent.Lost(peerId)
        assertEquals(peerId, event.peerId)
    }
}
