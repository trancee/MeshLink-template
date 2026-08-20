package ch.trancee.meshlink.wire.codec

import ch.trancee.meshlink.model.PeerIdentity
import ch.trancee.meshlink.wire.model.FieldType

private const val UBYTE_SIZE: Int = 1
private const val USHORT_SIZE: Int = 2
private const val UINT_SIZE: Int = 4
private const val ULONG_SIZE: Int = 8
private const val BITS_PER_BYTE: Int = 8
private const val PEER_IDENTITY_SIZE: Int = 16
private const val BYTE_MASK: UInt = 0xFFu
private const val BYTE_MASK_ULONG: ULong = 0xFFu

/**
 * Bounded cursor over a read-only byte buffer.
 *
 * Every read advances the internal offset and throws when insufficient data
 * remains. Numeric reads support both little-endian and big-endian layouts
 * as declared by [FieldType] and [ch.trancee.meshlink.wire.model.ByteOrder].
 *
 * This class is an internal implementation detail of
 * [FrameCodec]; tests exercise it directly via golden and malformed vectors.
 */
internal class FrameReader(
    private val bytes: ByteArray,
    private var offset: Int = 0,
) {
    public val remaining: Int get() = bytes.size - offset

    public fun requireRemaining(count: Int) {
        if (offset + count > bytes.size) {
            throw IllegalArgumentException(
                "FrameReader: require $count bytes at offset $offset, only $remaining remaining"
            )
        }
    }

    public fun readUByte(): UByte {
        requireRemaining(UBYTE_SIZE)
        return bytes[offset++].toUByte()
    }

    public fun readUShortLE(): UShort {
        requireRemaining(USHORT_SIZE)
        val low = bytes[offset].toUByte().toUInt()
        val high = bytes[offset + 1].toUByte().toUInt()
        offset += USHORT_SIZE
        return (low or (high shl BITS_PER_BYTE)).toUShort()
    }

    public fun readUShortBE(): UShort {
        requireRemaining(USHORT_SIZE)
        val high = bytes[offset].toUByte().toUInt()
        val low = bytes[offset + 1].toUByte().toUInt()
        offset += USHORT_SIZE
        return (low or (high shl BITS_PER_BYTE)).toUShort()
    }

    public fun readUIntLE(): UInt {
        requireRemaining(UINT_SIZE)
        var value = 0u
        for (i in 0 until UINT_SIZE) {
            value = value or (bytes[offset + i].toUByte().toUInt() shl (i * BITS_PER_BYTE))
        }
        offset += UINT_SIZE
        return value
    }

    public fun readUIntBE(): UInt {
        requireRemaining(UINT_SIZE)
        var value = 0u
        for (i in 0 until UINT_SIZE) {
            value = (value shl BITS_PER_BYTE) or bytes[offset + i].toUByte().toUInt()
        }
        offset += UINT_SIZE
        return value
    }

    public fun readULongLE(): ULong {
        requireRemaining(ULONG_SIZE)
        var value = 0uL
        for (i in 0 until ULONG_SIZE) {
            value = value or (bytes[offset + i].toUByte().toULong() shl (i * BITS_PER_BYTE))
        }
        offset += ULONG_SIZE
        return value
    }

    public fun readULongBE(): ULong {
        requireRemaining(ULONG_SIZE)
        var value = 0uL
        for (i in 0 until ULONG_SIZE) {
            value = (value shl BITS_PER_BYTE) or bytes[offset + i].toUByte().toULong()
        }
        offset += ULONG_SIZE
        return value
    }

    public fun readBytes(count: Int): ByteArray {
        requireRemaining(count)
        val result = bytes.copyOfRange(offset, offset + count)
        offset += count
        return result
    }

    public fun readToEnd(): ByteArray = readBytes(remaining)

    public fun readPeerIdentity(): PeerIdentity {
        requireRemaining(PEER_IDENTITY_SIZE)
        val data = bytes.copyOfRange(offset, offset + PEER_IDENTITY_SIZE)
        offset += PEER_IDENTITY_SIZE
        return PeerIdentity.fromBytes(data)
    }
}
