package ch.trancee.meshlink

import ch.trancee.meshlink.model.HandshakeKey
import ch.trancee.meshlink.model.IdentityKey
import ch.trancee.meshlink.model.PeerIdentity
import ch.trancee.meshlink.model.RouteEntry
import ch.trancee.meshlink.model.SeqNo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock

class RouteEntryTest {
    @Test
    fun `with null nextHop and null identityKey`() {
        val entry =
            RouteEntry(
                destination = PeerIdentity.ZERO,
                nextHop = null,
                source = PeerIdentity.ZERO,
                metric = 0u,
                seqNo = SeqNo.ZERO,
                identityKey = null,
                expiresAt = Clock.System.now(),
            )
        assertNull(entry.nextHop)
        assertNull(entry.identityKey)
        assertEquals(PeerIdentity.ZERO, entry.destination)
    }

    @Test
    fun `with null nextHop but valid identityKey`() {
        val key =
            IdentityKey.fromHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val entry =
            RouteEntry(
                destination = PeerIdentity.ZERO,
                nextHop = null,
                source = PeerIdentity.ZERO,
                metric = 0u,
                seqNo = SeqNo.ZERO,
                identityKey = key,
                expiresAt = Clock.System.now(),
            )
        assertNull(entry.nextHop)
        assertNotNull(entry.identityKey)
        assertEquals(key, entry.identityKey)
    }

    @Test
    fun `with null nextHop but valid handshakeKey`() {
        val hKey =
            HandshakeKey.fromHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val entry =
            RouteEntry(
                destination = PeerIdentity.ZERO,
                nextHop = null,
                source = PeerIdentity.ZERO,
                metric = 0u,
                seqNo = SeqNo.ZERO,
                handshakeKey = hKey,
                expiresAt = Clock.System.now(),
            )
        assertNull(entry.nextHop)
        assertNotNull(entry.handshakeKey)
        assertEquals(hKey, entry.handshakeKey)
    }

    @Test
    fun `with both identityKey and handshakeKey`() {
        val iKey =
            IdentityKey.fromHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val hKey =
            HandshakeKey.fromHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val entry =
            RouteEntry(
                destination = PeerIdentity.ZERO,
                nextHop = PeerIdentity.ZERO,
                source = PeerIdentity.ZERO,
                metric = 0u,
                seqNo = SeqNo.ZERO,
                identityKey = iKey,
                handshakeKey = hKey,
                expiresAt = Clock.System.now(),
            )
        assertEquals(iKey, entry.identityKey)
        assertEquals(hKey, entry.handshakeKey)
    }

    @Test
    fun `handshakeKey defaults to null`() {
        val entry =
            RouteEntry(
                destination = PeerIdentity.ZERO,
                nextHop = null,
                source = PeerIdentity.ZERO,
                metric = 0u,
                seqNo = SeqNo.ZERO,
                identityKey = null,
                expiresAt = Clock.System.now(),
            )
        assertNull(entry.handshakeKey)
    }
}
