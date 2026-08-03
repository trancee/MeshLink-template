package ch.trancee.meshlink.model

import ch.trancee.meshlink.util.toBytesBE
import ch.trancee.meshlink.util.toUIntBE
import kotlin.jvm.JvmInline

/**
 * Four-byte identifier for a message payload, scoped by its authenticated origin.
 *
 * Shares the same 32-bit wire slot as [TransferId]; [TransferKind] determines interpretation. Zero
 * is reserved as invalid.
 *
 * SPEC-ANCHOR: message-id-model
 */
@JvmInline
public value class MessageId(public val value: UInt) : Comparable<MessageId> {

    /** Raw 32-bit unsigned value, for wire serialization and deserialization. */
    public fun toUInt(): UInt = value

    /** Returns the 4-byte big-endian wire representation of this message ID. */
    public fun toByteArray(): ByteArray = value.toBytesBE()

    /**
     * Increments this message ID by 1, wrapping at 2^32. Operator form for idiomatic `messageId++`
     * usage.
     */
    public operator fun inc(): MessageId = MessageId(value + MESSAGE_ID_INCREMENT)

    public override fun compareTo(other: MessageId): Int = value.compareTo(other.value)

    public companion object {
        /** Invalid message ID (zero). */
        public val ZERO: MessageId = MessageId(0u)

        /** Creates a [MessageId] from a raw [UInt] value (e.g., read from the wire). */
        public fun fromUInt(value: UInt): MessageId = MessageId(value)

        /**
         * Creates a [MessageId] from a 4-byte big-endian representation, for wire deserialization.
         *
         * @param bytes exactly 4 bytes; throws [IllegalArgumentException] if [bytes.size] is not 4.
         */
        public fun fromBytes(bytes: ByteArray): MessageId {
            require(bytes.size == MESSAGE_ID_BYTE_LENGTH) {
                "Expected $MESSAGE_ID_BYTE_LENGTH bytes, got ${bytes.size}"
            }
            return MessageId(bytes.toUIntBE())
        }
    }

    override fun toString(): String = value.toString()
}

private const val MESSAGE_ID_BYTE_LENGTH: Int = 4
private const val MESSAGE_ID_INCREMENT: UInt = 1u
