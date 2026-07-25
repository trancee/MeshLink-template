package ch.trancee.meshlink.model

import kotlinx.serialization.Serializable

/**
 * Stable 16-byte peer identifier. Generated ONCE at install/first launch, stored permanently. NOT
 * derived from public key — remains stable across key rotations.
 *
 * Backed by two [ULong] fields to avoid [ByteArray] boxing overhead in value-class contexts.
 */
@JvmInline
@Serializable
value class PeerIdentity(private val parts: Pair<ULong, ULong>) {
    /** Lower 8 bytes of the 16-byte identity. */
    val lo: ULong
        get() = parts.first

    /** Upper 8 bytes of the 16-byte identity. */
    val hi: ULong
        get() = parts.second

    /** Hex-encoded representation for diagnostics and display. */
    val hex: String
        get() = "%016x%016x".format(lo, hi)

    companion object {
        /** The zero identity (all bytes zero) — for initialization and comparison. */
        val ZERO: PeerIdentity = PeerIdentity(0UL to 0UL)

        /** Generates a cryptographically random 16-byte peer identity. */
        fun generate(): PeerIdentity {
            val lo = generateRandomULong()
            val hi = generateRandomULong()
            return PeerIdentity(lo to hi)
        }
    }

    /** Converts this identity to a 16-byte [ByteArray]. */
    fun toByteArray(): ByteArray =
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
    kotlin.random.Random.Default.nextBytes(bytes)
    var result: ULong = 0u
    for (b in bytes) {
        result = (result shl 8) or b.toULong()
    }
    return result
}

/** Creates a [PeerIdentity] from a 16-byte [ByteArray]. */
fun PeerIdentity.Companion.fromBytes(bytes: ByteArray): PeerIdentity {
    require(bytes.size == 16) { "PeerIdentity must be exactly 16 bytes, got ${bytes.size}" }
    val lo = bytesToULongBigEndian(bytes, 0)
    val hi = bytesToULongBigEndian(bytes, 8)
    return PeerIdentity(lo to hi)
}

private fun bytesToULongBigEndian(bytes: ByteArray, offset: Int): ULong {
    var result: ULong = 0u
    for (i in 0..7) {
        result = (result shl 8) or bytes[offset + i].toULong()
    }
    return result
}
