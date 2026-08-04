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
                identity = PeerIdentity.generate(),
                state = PeerState.CONNECTED,
                trust = PeerTrust.TRUSTED,
                routeCost = 100u,
                hopCount = 2.toUByte(),
                sessionCount = 1,
                diagnosticCode = null,
                diagnosticSeverity = null,
                seenAt = Clock.System.now(),
                verifiedAt = Clock.System.now(),
            )

        assertNotNull(peer.identity)
        assertEquals(PeerState.CONNECTED, peer.state)
        assertEquals(PeerTrust.TRUSTED, peer.trust)
        assertEquals(100u, peer.routeCost)
        assertEquals(2.toUByte(), peer.hopCount)
        assertEquals(1, peer.sessionCount)
        assertNotNull(peer.seenAt)
        assertNotNull(peer.verifiedAt)
    }

    @Test
    fun `KnownPeer minimal constructor`() {
        val peer =
            KnownPeer(
                identity = PeerIdentity.generate(),
                state = PeerState.DISCONNECTED,
                trust = PeerTrust.UNVERIFIED,
                seenAt = Clock.System.now(),
            )

        assertEquals(PeerState.DISCONNECTED, peer.state)
        assertEquals(PeerTrust.UNVERIFIED, peer.trust)
        assertNull(peer.routeCost)
        assertNull(peer.hopCount)
        assertEquals(0, peer.sessionCount)
        assertNull(peer.verifiedAt)
    }
}
