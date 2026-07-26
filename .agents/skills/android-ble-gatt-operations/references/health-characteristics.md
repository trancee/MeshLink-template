# Health Characteristics Parsing

Standard Bluetooth SIG characteristics use IEEE-11073 SFLOAT encoding.

## Heart Rate Measurement (0x2A37)

Flags byte bit 0 selects format:

```kotlin
fun parseHeartRate(bytes: ByteArray): Int? {
    if (bytes.size < 2) return null
    val flags = bytes[0].toInt() and 0xFF
    return if ((flags and 0x01) != 0) {
        // 16-bit value
        if (bytes.size < 3) return null
        ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
    } else {
        // 8-bit value
        bytes[1].toInt() and 0xFF
    }
}
```

## Blood Pressure Measurement (0x2A35)

IEEE-11073 SFLOAT: 12-bit signed mantissa, 4-bit signed exponent:

```kotlin
internal fun parseSFloat(bytes: ByteArray, offset: Int): Float {
    val raw = ((bytes[offset + 1].toInt() and 0xFF) shl 8) or (bytes[offset].toInt() and 0xFF)
    val mantissa = if ((raw and 0x0FFF) >= 0x0800) (raw and 0x0FFF) - 0x1000 else (raw and 0x0FFF)
    val exponent = if (((raw ushr 12) and 0x0F) >= 0x08) ((raw ushr 12) and 0x0F) - 0x10 else ((raw ushr 12) and 0x0F)
    return (mantissa * 10.0.pow(exponent.toDouble())).toFloat()
}
```

## Unit Test Coverage

- UUID normalization/matching
- SFLOAT decoding
- characteristic-name lookup
- Raw hex/ASCII formatting

The Bluetooth stack timing cannot be unit tested — use real hardware for GATT serialization behavior.