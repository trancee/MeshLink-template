package ch.trancee.meshlink.util

private const val ULONG_SIZE = 8
private const val UINT_SIZE = 4
private const val ULONG_BYTE_MASK: ULong = 0xFFu
private const val UINT_BYTE_MASK: UInt = 0xFFu
private const val BYTE_SHIFT: Int = 8

/** Reads 8 bytes from [offset] in big-endian order as ULong. */
public fun ByteArray.toULongBE(offset: Int = 0): ULong =
    (0 until ULONG_SIZE).fold(0uL) { result, i ->
        (result shl BYTE_SHIFT) or this[offset + i].toUByte().toULong()
    }

/** Converts ULong to big-endian byte array. */
public fun ULong.toBytesBE(): ByteArray =
    (ULONG_SIZE - 1 downTo 0)
        .map { shift -> ((this shr (shift * BYTE_SHIFT)) and ULONG_BYTE_MASK).toByte() }
        .toByteArray()

/** Reads 4 bytes from [offset] in big-endian order as UInt. */
public fun ByteArray.toUIntBE(offset: Int = 0): UInt =
    (0 until UINT_SIZE).fold(0u) { result, i ->
        (result shl BYTE_SHIFT) or this[offset + i].toUByte().toUInt()
    }

/** Converts UInt to a 4-byte big-endian byte array. */
public fun UInt.toBytesBE(): ByteArray =
    (UINT_SIZE - 1 downTo 0)
        .map { shift -> ((this shr (shift * BYTE_SHIFT)) and UINT_BYTE_MASK).toByte() }
        .toByteArray()
