package ch.trancee.meshlink.model

import kotlin.time.Instant

/**
 * Sequence number for route versioning. Wrapped for safe comparison to handle wrap-around. Values
 * are 32-bit unsigned to match Babel protocol semantics.
 */
@JvmInline
public value class SeqNo(private val value: UInt) {
    /** Raw sequence number value. */
    public val raw: UInt
        get() = value

    /** Sequence number zero. */
    public companion object {
        public val ZERO: SeqNo = SeqNo(0u)

        /** Creates a SeqNo from a raw value. */
        public fun fromRaw(value: UInt): SeqNo = SeqNo(value)
    }
}

/**
 * Ed25519 public key for mesh identity verification. Stored as a 32-byte array, serialized as hex
 * string.
 */
@JvmInline
public value class Ed25519Key(private val hexString: String) {
    /** Raw 32-byte key data. */
    public val raw: ByteArray
        get() = hexStringToByteArray(hexString)

    /** Hex-encoded representation. */
    public val hex: String
        get() = hexString

    public companion object {
        /** Creates an Ed25519Key from a hex string. */
        public fun fromHex(hex: String): Ed25519Key {
            require(hex.length == 64) { "Ed25519Key must be 64 hex chars (32 bytes)" }
            return Ed25519Key(hex)
        }

        /** Creates an Ed25519Key from raw bytes. */
        public fun fromBytes(bytes: ByteArray): Ed25519Key {
            require(bytes.size == 32) { "Ed25519Key must be 32 bytes" }
            return Ed25519Key(bytes.byteArrayToHexString())
        }
    }

    private fun hexStringToByteArray(hex: String): ByteArray {
        val bytes = ByteArray(hex.length / 2)
        for (i in hex.indices step 2) {
            bytes[i / 2] = hex.substring(i, i + 2).toInt(16).toByte()
        }
        return bytes
    }
}

private fun ByteArray.byteArrayToHexString(): String = joinToString("") { "%02x".format(it) }

/** Route entry in the routing table. Managed by RouteCoordinator; updates via RouteDigest. */
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
    public val identityKey: Ed25519Key?,
    /** Expiration instant for this route entry. */
    public val expiresAt: Instant,
)

/** Link quality metric for routing decisions. */
public data class LinkMetric(
    /** RSSI normalized to 0-255 scale (0 = unusable, 255 = excellent). */
    public val rssiNormalized: UInt,
    /** Whether the link supports L2CAP Connection-oriented Channels. */
    public val supportsCoc: Boolean,
    /** Whether the connection interval is fast (<= 15 ms). */
    public val fastInterval: Boolean,
    /** Whether the high power tier is active on this link. */
    public val highPowerTier: Boolean,
) {
    /** Composite metric value: low byte = RSSI, high bits = flags. */
    public val composite: UInt =
        ((supportsCoc.bit(8) or fastInterval.bit(9) or highPowerTier.bit(10)) shl 8) or
            rssiNormalized
}

private fun Boolean.bit(position: Int): UInt = if (this) 1u shl position else 0u
