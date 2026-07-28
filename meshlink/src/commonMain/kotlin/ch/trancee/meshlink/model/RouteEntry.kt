package ch.trancee.meshlink.model

import kotlin.time.Instant

/**
 * Route entry in the routing table. Managed by RouteCoordinator; updates via RouteDigest.
 *
 * SPEC-ANCHOR: route-entry-model
 */
public data class RouteEntry(
    /** The peer from whom this route was learned (for loop detection). */
    public val source: PeerIdentity,
    /** Final destination peer in the mesh. */
    public val destination: PeerIdentity,
    /** Immediate next hop toward the destination (null = unreachable). */
    public val nextHop: PeerIdentity?,
    /** Route metric (RSSI + flags, see LinkMetric). */
    public val metric: UInt,
    /** Destination-self-reported sequence number, wrapped for safe comparison. */
    public val seqNo: SeqNo,
    /**
     * Destination's Ed25519 identity key (for IX handshake verification), learned via route updates
     * (may be null on cold start).
     */
    public val identityKey: IdentityKey? = null,
    /**
     * Destination's X25519 handshake key (for DM key exchange), learned via route updates (may be
     * null on cold start).
     */
    public val handshakeKey: HandshakeKey? = null,
    /** Expiration instant for this route entry. */
    public val expiresAt: Instant,
)
