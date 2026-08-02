package ch.trancee.meshlink.model

import ch.trancee.meshlink.util.randomULong
import ch.trancee.meshlink.util.toBytesBE
import ch.trancee.meshlink.util.toULongBE
import kotlin.jvm.JvmInline
import kotlin.text.HexFormat

/** Stable 16-byte per-installation peer identifier. */
@JvmInline
public value class PeerIdentity(private val parts: Pair<ULong, ULong>) {
    override fun toString(): String = toHexString()

    private fun toHexString(): String =
        parts.first.toHexString(HexFormat { number.minLength = ID_HALF_HEX_LENGTH }) +
            parts.second.toHexString(HexFormat { number.minLength = ID_HALF_HEX_LENGTH })

    public companion object {
        public val ZERO: PeerIdentity = PeerIdentity(0UL to 0UL)

        public fun generate(): PeerIdentity = PeerIdentity(randomULong() to randomULong())

        public fun fromHex(hex: String): PeerIdentity {
            require(hex.length == ID_HEX_LENGTH) {
                "PeerIdentity must be $ID_HEX_LENGTH hex chars ($ID_BYTE_LENGTH bytes)"
            }
            return fromBytes(hex.hexToByteArray())
        }

        public fun fromBytes(bytes: ByteArray): PeerIdentity {
            require(bytes.size == ID_BYTE_LENGTH) {
                "PeerIdentity must be exactly $ID_BYTE_LENGTH bytes"
            }
            return PeerIdentity(bytes.toULongBE(0) to bytes.toULongBE(ID_HALF_BYTE_LENGTH))
        }
    }

    public fun toByteArray(): ByteArray {
        val result = ByteArray(ID_BYTE_LENGTH)
        parts.first.toBytesBE().copyInto(result, 0)
        parts.second.toBytesBE().copyInto(result, ID_HALF_BYTE_LENGTH)
        return result
    }
}

private const val ID_BYTE_LENGTH: Int = 16
private const val ID_HALF_BYTE_LENGTH: Int = 8
private const val ID_HALF_HEX_LENGTH: Int = 16
private const val ID_HEX_LENGTH: Int = 32
