package ch.trancee.meshlink.model

import ch.trancee.meshlink.util.*
import kotlin.jvm.JvmInline
import kotlin.text.HexFormat

/**
 * Stable 16-byte peer identifier. Generated ONCE at install/first launch, stored permanently. NOT
 * derived from public key — remains stable across key rotations.
 *
 * Backed by two [ULong] fields to avoid [ByteArray] boxing overhead in value-class contexts.
 *
 * SPEC-ANCHOR: peer-identity-model
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
        get() =
            "${lo.toHexString(HexFormat { number.minLength = 16 })}${hi.toHexString(HexFormat { number.minLength = 16 })}"

    public companion object {
        /** The zero identity (all bytes zero) — for initialization and comparison. */
        public val ZERO: PeerIdentity = PeerIdentity(0UL to 0UL)

        /** Generates a cryptographically random 16-byte peer identity. */
        public fun generate(): PeerIdentity {
            val lo = randomULong()
            val hi = randomULong()
            return PeerIdentity(lo to hi)
        }

        /** Creates a [PeerIdentity] from a 16-byte [ByteArray]. */
        public fun fromBytes(bytes: ByteArray): PeerIdentity {
            require(bytes.size == 16) { "PeerIdentity must be exactly 16 bytes" }
            val lo = bytes.toULongBE(0)
            val hi = bytes.toULongBE(8)
            return PeerIdentity(lo to hi)
        }
    }

    /** Converts this identity to a 16-byte [ByteArray]. */
    public fun toByteArray(): ByteArray {
        val result = ByteArray(16)
        val loBytes = lo.toBytesBE()
        val hiBytes = hi.toBytesBE()
        loBytes.copyInto(result, 0, 0, 8)
        hiBytes.copyInto(result, 8, 0, 8)
        return result
    }
}
