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
    public val lower: ULong
        get() = parts.first

    /** Upper 8 bytes of the 16-byte identity. */
    public val upper: ULong
        get() = parts.second

    /** Hex-encoded representation for diagnostics and display. */
    public val hex: String
        get() =
            "${lower.toHexString(HexFormat { number.minLength = 16 })}${upper.toHexString(HexFormat { number.minLength = 16 })}"

    public companion object {
        /** The zero identity (all bytes zero) — for initialization and comparison. */
        public val ZERO: PeerIdentity = PeerIdentity(0UL to 0UL)

        /** Generates a cryptographically random 16-byte peer identity. */
        public fun generate(): PeerIdentity {
            val lower = randomULong()
            val upper = randomULong()
            return PeerIdentity(lower to upper)
        }

        /** Creates a [PeerIdentity] from a 16-byte [ByteArray]. */
        public fun fromBytes(bytes: ByteArray): PeerIdentity {
            require(bytes.size == 16) { "PeerIdentity must be exactly 16 bytes" }
            val lower = bytes.toULongBE(0)
            val upper = bytes.toULongBE(8)
            return PeerIdentity(lower to upper)
        }
    }

    /** Converts this identity to a 16-byte [ByteArray]. */
    public fun toByteArray(): ByteArray {
        val result = ByteArray(16)
        val lowerBytes = lower.toBytesBE()
        val upperBytes = upper.toBytesBE()
        lowerBytes.copyInto(result, 0, 0, 8)
        upperBytes.copyInto(result, 8, 0, 8)
        return result
    }
}
