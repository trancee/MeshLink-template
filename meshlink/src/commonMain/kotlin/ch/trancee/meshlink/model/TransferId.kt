package ch.trancee.meshlink.model

import ch.trancee.meshlink.util.toBytesBE
import ch.trancee.meshlink.util.toUIntBE
import kotlin.jvm.JvmInline

/**
 * Four-byte identifier for a finite payload, scoped by its authenticated origin.
 *
 * Shares the same 32-bit wire slot as [MessageId]; [TransferType] determines interpretation.
 *
 * SPEC-ANCHOR: transfer-id-model
 */
@JvmInline
public value class TransferId(public val value: UInt) : Comparable<TransferId> {
    /** Raw 32-bit unsigned value, for wire serialization and deserialization. */
    public fun rawValue(): UInt = value

    /** Returns the 4-byte big-endian wire representation of this transfer ID. */
    public fun toByteArray(): ByteArray = value.toBytesBE()

    /**
     * Increments this transfer ID by 1, wrapping at 2^32. Operator form for idiomatic
     * `transferId++` usage.
     */
    public operator fun inc(): TransferId = TransferId(value + TRANSFER_ID_INCREMENT)

    public override fun compareTo(other: TransferId): Int = value.compareTo(other.value)

    public companion object {
        /** Invalid transfer ID (zero). */
        public val ZERO: TransferId = TransferId(0u)

        /**
         * Creates a [TransferId] from a raw [UInt] value (e.g., read from the wire).
         *
         * This is the deserialization counterpart to [rawValue], used when decoding payload frames.
         */
        public fun fromUInt(value: UInt): TransferId = TransferId(value)

        /**
         * Creates a [TransferId] from a 4-byte big-endian representation, for wire deserialization.
         *
         * This is the deserialization counterpart to [toByteArray], used when decoding payload
         * frames from a byte stream.
         *
         * @param bytes exactly 4 bytes; throws [IllegalArgumentException] if [bytes.size] is not 4.
         */
        public fun fromBytes(bytes: ByteArray): TransferId {
            require(bytes.size == TRANSFER_ID_BYTE_LENGTH) {
                "Expected $TRANSFER_ID_BYTE_LENGTH bytes, got ${bytes.size}"
            }
            return TransferId(bytes.toUIntBE())
        }
    }

    override fun toString(): String = value.toString()
}

private const val TRANSFER_ID_BYTE_LENGTH: Int = 4
private const val TRANSFER_ID_INCREMENT: UInt = 1u
