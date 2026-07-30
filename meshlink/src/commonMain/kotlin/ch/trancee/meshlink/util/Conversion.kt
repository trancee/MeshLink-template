package ch.trancee.meshlink.util

private const val ULONG_SIZE = 8
private const val UINT_SIZE = 4
private const val ULONG_BYTE_MASK: ULong = 0xFFu
private const val UINT_BYTE_MASK: UInt = 0xFFu

/** Reads 8 bytes from [offset] in big-endian order as ULong. */
public fun ByteArray.toULongBE(offset: Int = 0): ULong {
    var result: ULong = 0u
    for (i in 0..ULONG_SIZE - 1) {
        result = (result shl 8) or this[offset + i].toUByte().toULong()
    }
    return result
}

/** Converts ULong to big-endian byte array. */
public fun ULong.toBytesBE(): ByteArray =
    (ULONG_SIZE - 1 downTo 0)
        .map { shift -> ((this shr (shift * 8)) and ULONG_BYTE_MASK).toByte() }
        .toByteArray()

/** Reads 4 bytes from [offset] in big-endian order as UInt. */
public fun ByteArray.toUIntBE(offset: Int = 0): UInt {
    var result: UInt = 0u
    for (i in 0..UINT_SIZE - 1) {
        result = (result shl 8) or this[offset + i].toUByte().toUInt()
    }
    return result
}

/** Converts UInt to a 4-byte big-endian byte array. */
public fun UInt.toBytesBE(): ByteArray =
    (UINT_SIZE - 1 downTo 0)
        .map { shift -> ((this shr (shift * 8)) and UINT_BYTE_MASK).toByte() }
        .toByteArray()
