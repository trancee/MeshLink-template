package ch.trancee.meshlink.model

import kotlin.jvm.JvmInline

/** Four-byte identifier for a finite payload, scoped by its authenticated origin. */
@JvmInline
public value class TransferId private constructor(private val value: UInt) {
    public companion object {
        public val ZERO: TransferId = TransferId(0u)

        public fun fromHex(hex: String): TransferId {
            require(hex.length <= TRANSFER_ID_HEX_LENGTH) {
                "TransferId hex must be at most $TRANSFER_ID_HEX_LENGTH chars (32-bit)"
            }
            return TransferId(hex.toUInt(HEX_RADIX))
        }
    }

    override fun toString(): String =
        value.toString(HEX_RADIX).padStart(TRANSFER_ID_HEX_LENGTH, '0')
}

private const val TRANSFER_ID_HEX_LENGTH: Int = 8
private const val HEX_RADIX: Int = 16
