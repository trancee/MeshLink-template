package ch.trancee.meshlink.wire.codec

import ch.trancee.meshlink.model.PeerIdentity

private const val UBYTE_SIZE: Int = 1
private const val USHORT_SIZE: Int = 2
private const val UINT_SIZE: Int = 4
private const val ULONG_SIZE: Int = 8
private const val BITS_PER_BYTE: Int = 8
private const val BYTE_MASK: UInt = 0xFFu
private const val BYTE_MASK_ULONG: ULong = 0xFFu

/**
 * Bounded cursor over a growable byte buffer.
 *
 * The writer pre-allocates [capacity] bytes and throws when a write would
 * exceed that bound. Use [toByteArray] to obtain a compact copy of the written
 * bytes.
 *
 * This class is an internal implementation detail of
 * [FrameCodec]; tests exercise it directly via golden and malformed vectors.
 */
internal class FrameWriter(
    private val capacity: Int,
) {
    private val bytes: ByteArray = ByteArray(capacity)
    private var offset: Int = 0

    public val size: Int get() = offset

    public fun requireRemaining(count: Int) {
        if (offset + count > bytes.size) {
            throw IllegalArgumentException(
                "FrameWriter: require $count bytes at offset $offset, " +
                    "only ${bytes.size - offset} capacity remaining"
            )
        }
    }

    public fun writeUByte(value: UByte) {
        requireRemaining(UBYTE_SIZE)
        bytes[offset++] = value.toByte()
    }

    public fun writeUShortLE(value: UShort) {
        requireRemaining(USHORT_SIZE)
        val v = value.toUInt()
        bytes[offset] = (v and BYTE_MASK).toByte()
        bytes[offset + 1] = ((v shr BITS_PER_BYTE) and BYTE_MASK).toByte()
        offset += USHORT_SIZE
    }

    public fun writeUShortBE(value: UShort) {
        requireRemaining(USHORT_SIZE)
        val v = value.toUInt()
        bytes[offset] = ((v shr BITS_PER_BYTE) and BYTE_MASK).toByte()
        bytes[offset + 1] = (v and BYTE_MASK).toByte()
        offset += USHORT_SIZE
    }

    public fun writeUIntLE(value: UInt) {
        requireRemaining(UINT_SIZE)
        for (i in 0 until UINT_SIZE) {
            bytes[offset + i] = ((value shr (i * BITS_PER_BYTE)) and BYTE_MASK).toByte()
        }
        offset += UINT_SIZE
    }

    public fun writeUIntBE(value: UInt) {
        requireRemaining(UINT_SIZE)
        for (i in 0 until UINT_SIZE) {
            bytes[offset + UINT_SIZE - 1 - i] =
                ((value shr (i * BITS_PER_BYTE)) and BYTE_MASK).toByte()
        }
        offset += UINT_SIZE
    }

    public fun writeULongLE(value: ULong) {
        requireRemaining(ULONG_SIZE)
        for (i in 0 until ULONG_SIZE) {
            bytes[offset + i] =
                ((value shr (i * BITS_PER_BYTE)) and BYTE_MASK_ULONG).toByte()
        }
        offset += ULONG_SIZE
    }

    public fun writeULongBE(value: ULong) {
        requireRemaining(ULONG_SIZE)
        for (i in 0 until ULONG_SIZE) {
            bytes[offset + ULONG_SIZE - 1 - i] =
                ((value shr (i * BITS_PER_BYTE)) and BYTE_MASK_ULONG).toByte()
        }
        offset += ULONG_SIZE
    }

    public fun writeBytes(value: ByteArray) {
        requireRemaining(value.size)
        value.copyInto(bytes, offset)
        offset += value.size
    }

    public fun writePeerIdentity(value: PeerIdentity) {
        writeBytes(value.toBytes())
    }

    public fun toByteArray(): ByteArray = bytes.copyOfRange(0, offset)
}
