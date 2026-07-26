# Unit Test Boundaries for BLE Logic

BLE stack timing and reliability behavior differs per peripheral and only shows up on physical devices.

## What to Unit Test (JVM)

Deterministic, hardware-independent logic:

- UUID normalization/matching
- Heart rate and blood pressure parsing (SFLOAT)
- Characteristic-name lookup
- Raw hex/ASCII formatting
- CRC or checksum verification

```kotlin
@Test fun parseSFloat_positiveValues() {
    // JVM tests for parsing algorithms — no Bluetooth involved
    val bytes = byteArrayOf(0x01, 0x00) // raw mantissa=1, exponent=0
    assertEquals(1.0f, parseSFloat(bytes, 0))
}
```

## What Requires Real Hardware

- GATT timing (peripheral response latency)
- Serialization behavior under load
- Subscription setup/teardown reliability
- MTU negotiation results
- Connection interval effects

## What Emulators Cover (Partial)

- UI logic for Composables
- Crypto correctness
- Routing over virtual transport
- Wire codec encode/decode

**Never** treat emulator test pass as BLE proof. Advertise/scan/GATT/L2CAP behavior only validates on real devices.