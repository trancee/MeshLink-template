package ch.trancee.meshlink.model

import kotlin.time.Instant

/**
 * Route entry in the routing table. Managed by RouteCoordinator; updates via RouteDigest.
 *
 * SPEC-ANCHOR: route-entry-model
 */
public data class RouteEntry(
    /** Final destination peer in the mesh. */
    public val destination: PeerIdentity,
    /** Immediate next hop toward the destination (null = unreachable). */
    public val nextHop: PeerIdentity?,
    /** The peer from whom this route was learned (for loop detection). */
    public val source: PeerIdentity,
    /** Route metric (RSSI + flags, see LinkMetric). */
    public val metric: UInt,
    /** Destination-self-reported sequence number, wrapped for safe comparison. */
    public val seqNo: SeqNo,
    /** Destination's public key, learned via route updates (may be null on cold start). */
    public val identityKey: IdentityKey?,
    /** Expiration instant for this route entry. */
    public val expiresAt: Instant,
)
