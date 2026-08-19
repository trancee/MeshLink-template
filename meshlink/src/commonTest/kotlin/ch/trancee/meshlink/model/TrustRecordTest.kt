package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

class TrustRecordTest {
    @Test
    fun `TrustRecord creates with default state`() {
        val identity =
            IdentityKey.fromHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val handshake =
            HandshakeKey.fromHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val now = Clock.System.now()
        val record =
            TrustRecord(
                identity = PeerIdentity.ZERO,
                identityKey = identity,
                handshakeKey = handshake,
                seenAt = now,
                verifiedAt = now,
            )
        assertEquals(PeerTrust.UNVERIFIED, record.state)
        assertEquals(0u, record.generation)
        assertEquals(PeerIdentity.ZERO, record.identity)
    }

    @Test
    fun `TrustRecord creates with TRUSTED state`() {
        val identity =
            IdentityKey.fromHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val handshake =
            HandshakeKey.fromHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val now = Clock.System.now()
        val record =
            TrustRecord(
                identity = PeerIdentity.ZERO,
                identityKey = identity,
                handshakeKey = handshake,
                seenAt = now,
                verifiedAt = now,
                state = PeerTrust.TRUSTED,
            )
        assertEquals(PeerTrust.TRUSTED, record.state)
    }

    @Test
    fun `TrustRecord creates with REVOKED state`() {
        val identity =
            IdentityKey.fromHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val handshake =
            HandshakeKey.fromHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val now = Clock.System.now()
        val record =
            TrustRecord(
                identity = PeerIdentity.ZERO,
                identityKey = identity,
                handshakeKey = handshake,
                seenAt = now,
                verifiedAt = now,
                state = PeerTrust.REVOKED,
                generation = 3u,
            )
        assertEquals(PeerTrust.REVOKED, record.state)
        assertEquals(3u, record.generation)
    }
}
