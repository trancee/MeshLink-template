package ch.trancee.meshlink.model

import kotlinx.serialization.Serializable

/** Route entry in the routing table. Managed by RouteCoordinator; updates via RouteDigest. */
@Serializable
data class RouteEntry(
    /** Final destination peer in the mesh. */
    val destination: PeerIdentity,
    /** Immediate next hop toward the destination (null = unreachable). */
    val nextHop: PeerIdentity?,
    /** The peer from whom this route was learned (for loop detection). */
    val source: PeerIdentity,
    /** Route metric (RSSI + flags, see LinkMetric). */
    val metric: UInt,
    /** Destination-self-reported sequence number, wrapped for safe comparison. */
    val seqNo: SeqNo,
    /** Destination's public key, learned via route updates (may be null on cold start). */
    val identityKey: Ed25519Key?,
    /** Expiration instant for this route entry. */
    val expiresAt: kotlinx.datetime.Instant,
)

/** Link quality metric for routing decisions. */
@Serializable
data class LinkMetric(
    /** RSSI normalized to 0-255 scale (0 = unusable, 255 = excellent). */
    val rssiNormalized: UInt,
    /** Whether the link supports L2CAP Connection-oriented Channels. */
    val supportsCoc: Boolean,
    /** Whether the connection interval is fast (<= 15 ms). */
    val fastInterval: Boolean,
    /** Whether the high power tier is active on this link. */
    val highPowerTier: Boolean,
) {
    /** Composite metric value: low byte = RSSI, high bits = flags. */
    val composite: UInt =
        ((supportsCoc.bit(8) or fastInterval.bit(9) or highPowerTier.bit(10)) shl 8) or
            rssiNormalized
}

private fun Boolean.bit(position: Int): UInt = if (this) 1u shl position else 0u
