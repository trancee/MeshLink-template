---
name: android-ble-gatt-operations
description: "Android BLE GATT operations reference. Covers serialized operation queue (Mutex/Channel/CompletableDeferred patterns), memory-safe API 33+ callbacks, notification subscription state machine, advertising packet limits, and unit-test boundaries for BLE logic. Use when implementing GATT clients, debugging race conditions, or designing BLE protocol layers."
---

# Android BLE GATT Operations

The Bluetooth stack handles exactly one in-flight GATT operation. Queue or serialize all calls.

## When to Use

- Debugging intermittent/non-deterministic BLE failures
- GATT operations returning `false` or silently failing  
- BluetoothGatt disconnecting under load
- Designing a GattClient wrapper for sequential GATT access
- Modeling notification subscription state

## Essential Principles

- **Stack limitation**: `BluetoothGatt` internal state machine handles one in-flight operation only
- **Non-blocking calls**: `writeCharacteristic`, `readCharacteristic`, etc. return immediately; results arrive via callback
- **Race condition**: firing a second operation before the first callback lands drops operations, corrupts state, or disconnects
- **Timeout required**: Unresponsive peripherals need 5-15s timeout (8s observed on Polar H10)
- **Notifications are unsolicited**: `onCharacteristicChanged` is not a response; don't block queue on it
- **Memory-safe callbacks on API 33+**: Prefer `onCharacteristicRead(gatt, char, value, status)` overloads

## Patterns

### Single-operation Mutex (Simplest)

Use when operations are infrequent or UI-driven:

```kotlin
private val operationMutex = Mutex()

override suspend fun readCharacteristic(char: BluetoothGattCharacteristic): ByteArray =
    operationMutex.withLock {
        val deferred = CompletableDeferred<ByteArray>()
        pendingRead = deferred
        check(gatt?.readCharacteristic(char) != false) { "Read could not start" }
        withTimeout(10_000) { deferred.await() }
    }
```

### Channel-based Queue (Production)

Use for high-throughput or pipelined operations:

```kotlin
sealed class GattOperation {
    data class Read(val char: BluetoothGattCharacteristic) : GattOperation()
    data class Write(val char: BluetoothGattCharacteristic, val value: ByteArray) : GattOperation()
    // Override equals/hashCode for ByteArray variants with contentEquals
}

private val operationChannel = Channel<GattOperation>(Channel.UNLIMITED)
private val completions = mutableMapOf<String, CompletableDeferred<Unit>>()

// Consumer loop
scope.launch {
    for (op in operationChannel) {
        when (op) {
            is GattOperation.Read -> gatt.readCharacteristic(op.char)
            is GattOperation.Write -> gatt.writeCharacteristic(op.char, op.value, WRITE_TYPE_DEFAULT)
        }
        // Await callback in BluetoothGattCallback
    }
}
```

### Notification Subscription State

Notifications require both local routing AND remote CCCD write:

```kotlin
enum class ObservationStatus { Starting, Active, Stopping }

data class CharacteristicObservation(
    val values: Flow<ByteArray>,
    val subscribed: Deferred<Unit>
)

// Enable: setCharacteristicNotification + CCCD write
// subscribed Deferred completes only after CCCD write callback succeeds
```

### Advertising Limits

Primary advertising packet: 31 bytes max. Split when needed:

```kotlin
// Service UUID in advertisement, name in scan response
val data = AdvertiseData.Builder().addServiceUuid(uuid).setIncludeDeviceName(false).build()
val scanResponse = AdvertiseData.Builder().setIncludeDeviceName(true).build()
```

## References

| Topic | Reference |
|-------|-----------|
| Permissions (scanner/central/peripheral profiles) | `../android-ble/references/permissions.md` |
| Full service pattern (bound service, BroadcastReceiver, background BLE) | `../android-ble/references/service-pattern-and-background.md` |
| Hello/Rate parsing (Heart Rate 2A37, Blood Pressure 2A35) | `references/health-characteristics.md` |
| Unit test boundaries (what to test on JVM vs real hardware) | `references/testing-boundaries.md` |

<!-- story: consolidation of android-ble-gatt-sequential-queue + android-ble-inspector-patterns -->