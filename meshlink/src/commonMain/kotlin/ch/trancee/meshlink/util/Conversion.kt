package ch.trancee.meshlink.util

/** Reads 8 bytes from [offset] in big-endian order as ULong. */
public fun ByteArray.toULongBE(offset: Int = 0): ULong {
    var result: ULong = 0u
    for (i in 0..7) {
        result = (result shl 8) or this[offset + i].toUByte().toULong()
    }
    return result
}

/** Converts ULong to big-endian byte array. */
public fun ULong.toBytesBE(): ByteArray =
    (7 downTo 0).map { shift -> ((this shr (shift * 8)) and 0xFFu).toByte() }.toByteArray()

/** Reads 4 bytes from [offset] in big-endian order as UInt. */
public fun ByteArray.toUIntBE(offset: Int = 0): UInt {
    var result: UInt = 0u
    for (i in 0..3) {
        result = (result shl 8) or this[offset + i].toUByte().toUInt()
    }
    return result
}

/** Converts UInt to a 4-byte big-endian byte array. */
public fun UInt.toBytesBE(): ByteArray =
    (3 downTo 0).map { shift -> ((this shr (shift * 8)) and 0xFFu).toByte() }.toByteArray()
