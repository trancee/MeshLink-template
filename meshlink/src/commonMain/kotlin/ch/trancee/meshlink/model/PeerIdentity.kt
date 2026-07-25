package ch.trancee.meshlink.model

import kotlin.random.Random

/**
 * Stable 16-byte peer identifier. Generated ONCE at install/first launch, stored permanently. NOT
 * derived from public key — remains stable across key rotations.
 *
 * Backed by two [ULong] fields to avoid [ByteArray] boxing overhead in value-class contexts.
 */
@JvmInline
public value class PeerIdentity(private val parts: Pair<ULong, ULong>) {
    /** Lower 8 bytes of the 16-byte identity. */
    public val lo: ULong
        get() = parts.first

    /** Upper 8 bytes of the 16-byte identity. */
    public val hi: ULong
        get() = parts.second

    /** Hex-encoded representation for diagnostics and display. */
    public val hex: String
        get() = "%016x%016x".format(lo.toLong(), hi.toLong())

    public companion object {
        /** The zero identity (all bytes zero) — for initialization and comparison. */
        public val ZERO: PeerIdentity = PeerIdentity(0UL to 0UL)

        /** Generates a cryptographically random 16-byte peer identity. */
        public fun generate(): PeerIdentity {
            val lo = generateRandomULong()
            val hi = generateRandomULong()
            return PeerIdentity(lo to hi)
        }

        /** Creates a [PeerIdentity] from a 16-byte [ByteArray]. */
        public fun fromBytes(bytes: ByteArray): PeerIdentity {
            require(bytes.size == 16) { "PeerIdentity must be exactly 16 bytes" }
            val lo = bytesToULongBigEndian(bytes, 0)
            val hi = bytesToULongBigEndian(bytes, 8)
            return PeerIdentity(lo to hi)
        }
    }

    /** Converts this identity to a 16-byte [ByteArray]. */
    public fun toByteArray(): ByteArray =
        buildList(16) {
                addAll(lo.toBigEndianBytes())
                addAll(hi.toBigEndianBytes())
            }
            .toByteArray()

    private fun ULong.toBigEndianBytes(): List<Byte> =
        (7 downTo 0).map { shift -> ((this shr (shift * 8)) and 0xFFu).toByte() }
}

private fun generateRandomULong(): ULong {
    val bytes = ByteArray(8)
    Random.Default.nextBytes(bytes)
    var result: ULong = 0u
    for (b in bytes) {
        result = (result shl 8) or b.toULong()
    }
    return result
}

private fun bytesToULongBigEndian(bytes: ByteArray, offset: Int): ULong {
    var result: ULong = 0u
    for (i in 0..7) {
        result = (result shl 8) or bytes[offset + i].toULong()
    }
    return result
}
