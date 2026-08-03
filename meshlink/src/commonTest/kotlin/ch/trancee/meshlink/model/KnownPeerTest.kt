package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock

class KnownPeerTest {

    @Test
    fun `KnownPeer has required properties`() {
        val peer =
            KnownPeer(
                peerIdentity = PeerIdentity.generate(),
                peerState = PeerState.CONNECTED,
                peerTrust = PeerTrust.TRUSTED,
                seenAt = Clock.System.now(),
                verifiedAt = Clock.System.now(),
                routeCost = 100u,
                hopCount = 2.toUByte(),
                activeSessionCount = 1,
                diagnosticCode = null,
                diagnosticSeverity = null,
            )

        assertNotNull(peer.peerIdentity)
        assertEquals(PeerState.CONNECTED, peer.peerState)
        assertEquals(PeerTrust.TRUSTED, peer.peerTrust)
        assertNotNull(peer.seenAt)
        assertNotNull(peer.verifiedAt)
        assertEquals(100u, peer.routeCost)
        assertEquals(2.toUByte(), peer.hopCount)
        assertEquals(1, peer.activeSessionCount)
    }

    @Test
    fun `KnownPeer minimal constructor`() {
        val peer =
            KnownPeer(
                peerIdentity = PeerIdentity.generate(),
                peerState = PeerState.DISCONNECTED,
                peerTrust = PeerTrust.UNVERIFIED,
                seenAt = Clock.System.now(),
            )

        assertEquals(PeerState.DISCONNECTED, peer.peerState)
        assertEquals(PeerTrust.UNVERIFIED, peer.peerTrust)
        assertNull(peer.verifiedAt)
        assertNull(peer.routeCost)
        assertNull(peer.hopCount)
        assertEquals(0, peer.activeSessionCount)
    }
}
