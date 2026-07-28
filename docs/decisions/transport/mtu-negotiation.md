# GATT MTU Negotiation & L2CAP Credit Flow Control

**Status:** Locked — 2026-07-26

## Decision

**Request MTU 517 on every GATT connection** (maximum for BLE 4.2+). L2CAP CoC uses credit-based flow control with default MTU 251 (BLE 4.0) or negotiated MTU.

## MTU Values

| Transport | Min MTU | Target MTU | Max MTU | Notes |
|-----------|---------|------------|---------|-------|
| GATT (BLE 4.0) | 23 | 23 | 23 | No MTU exchange |
| GATT (BLE 4.1+) | 23 | 247 | 517 | Request 517; peer may grant less |
| GATT (BLE 5.0+) | 23 | 517 | 517 | Typically granted |
| L2CAP CoC (BLE 4.0) | 23 | 251 | 251 | Default initial MTU |
| L2CAP CoC (BLE 4.2+) | 23 | 517 | 517 | Negotiated via CoC config |

**Chunk size bounds per power mode (from specs/enums.yaml):**

| Power Mode | Chunk Size | Max GATT Payload (MTU-3) | Fits in MTU? |
|------------|------------|--------------------------|--------------|
| HIGH | 512 bytes | 514 (at MTU 517) | ✅ Yes |
| MEDIUM | 256 bytes | 244 (at MTU 247) | ✅ Yes |
| LOW | 128 bytes | 20 (at MTU 23) | ⚠️ Requires MTU ≥ 131 |

**Rule:** If negotiated MTU < `chunkSize + 3` (ATT header + L2CAP), reduce chunk size for that session.

## GATT MTU Negotiation (Android)

```kotlin
// meshlink/src/androidMain/kotlin/ch/trancee/meshlink/android/ble/GattMtuManager.kt

class GattMtuManager {
    companion object {
        const val TARGET_MTU = 517
        const val MIN_MTU_FOR_HIGH_MODE = 515  // 512 + 3
        const val MIN_MTU_FOR_MEDIUM_MODE = 259 // 256 + 3
        const val MIN_MTU_FOR_LOW_MODE = 131   // 128 + 3
    }
    
    fun requestMtu(gatt: BluetoothGatt, powerMode: PowerMode): Boolean {
        val minMtu = when (powerMode) {
            PowerMode.HIGH -> MIN_MTU_FOR_HIGH_MODE
            PowerMode.MEDIUM -> MIN_MTU_FOR_MEDIUM_MODE
            PowerMode.LOW -> MIN_MTU_FOR_LOW_MODE
        }
        
        // Request target MTU; onMtuChanged callback receives actual
        return gatt.requestMtu(TARGET_MTU)
    }
    
    fun effectiveChunkSize(negotiatedMtu: Int, powerMode: PowerMode): Int {
        val maxPayload = negotiatedMtu - 3 // ATT header
        return minOf(powerMode.settings.chunkSize, maxPayload)
    }
}

// BluetoothGattCallback override
override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
    if (status == BluetoothGatt.GATT_SUCCESS) {
        val session = activeSessions[gatt.device.address]
        session?.updateChunkSize(gattMtuManager.effectiveChunkSize(mtu, session.powerMode))
        session?.diagnosticCallback?.onMtuNegotiated(mtu)
    }
}
```

## GATT MTU Negotiation (iOS)

```swift
// meshlink/src/iosMain/kotlin/ch/trancee/meshlink/ios/ble/GattMtuManager.kt

class GattMtuManager {
    companion object {
        const val TARGET_MTU = 517
    }
    
    func peripheral(_ peripheral: CBPeripheral, 
                    didModifyServices invalidatedServices: [CBService]) {
        // Request MTU after service discovery
        peripheral.maximumWriteValueLength(for: .withResponse) // Informational
    }
    
    // iOS 11+: CBPeripheral.maximumWriteValueLength returns effective MTU - 3
    // No explicit MTU request API; negotiation happens automatically on first write
    func writeValue(_ data: Data, for characteristic: CBCharacteristic, type: CBCharacteristicWriteType) {
        // iOS handles MTU exchange transparently
        // Can check: peripheral.maximumWriteValueLength(for: .withResponse)
    }
}
```

**Note:** iOS does not expose MTU request API. MTU exchange occurs automatically on first write. Check `peripheral.maximumWriteValueLength(for: .withResponse)` after connection.

## L2CAP CoC MTU & Credits

```kotlin
// meshlink/src/commonMain/kotlin/ch/trancee/meshlink/l2cap/L2capConfig.kt

data class L2capConfig(
    val targetMtu: Int = 517,
    val initialCredits: Int = 10,
    val creditThreshold: Int = 3,  // Request more credits when below this
    val maxCredits: Int = 50,
)

// Android L2CAP Channel
fun BluetoothSocket.configureL2cap(config: L2capConfig) {
    // setReceiveBufferSize not directly exposed; credits managed by stack
}

// iOS CBL2CAPChannel
extension CBL2CAPChannel {
    func configure(config: L2capConfig) {
        // MTU negotiated during channel open
        // Credits: send writeCredit() when buffer has space
    }
}
```

**Credit Flow:**

1. Channel opens with `initialCredits` (10)
2. Sender transmits up to `credits` packets
3. Receiver calls `writeCredit(n)` when it can accept more
4. Sender replenishes credits, continues

**Monitoring:**

```kotlin
suspend fun L2capChannel.monitorCredits() {
    while (isOpen) {
        val available = currentCredits
        if (available <= config.creditThreshold) {
            requestCredits(config.initialCredits)
        }
        delay(100) // Check periodically
    }
}
```

## Power Mode Adaptation

```kotlin
// meshlink/src/commonMain/kotlin/ch/trancee/meshlink/transport/TransportSession.kt

data class TransportConfig(
    val negotiatedMtu: Int,
    val powerMode: PowerMode,
    val bearer: DataPlaneBearer,
) {
    val effectiveChunkSize: Int
        get() = minOf(
            powerMode.settings.chunkSize,
            when (bearer) {
                DataPlaneBearer.GATT -> negotiatedMtu - 3
                DataPlaneBearer.L2CAP -> negotiatedMtu - 4 // L2CAP header
            }
        )
}
```

## Diagnostics

```yaml
# specs/diagnostic-events.yaml
- name: MtuNegotiatedEvent
  fields:
    - peerIdentity: PeerIdentity
    - requestedMtu: Int
    - negotiatedMtu: Int
    - bearer: DataPlaneBearer
    - effectiveChunkSize: Int
    - timestamp: Instant

- name: L2capCreditEvent
  fields:
    - peerIdentity: PeerIdentity
    - creditsAvailable: Int
    - creditsRequested: Int
    - timestamp: Instant
```

## Testing

| Test | Description |
|------|-------------|
| `MtuNegotiationTest` | Verify MTU request on Android; check effective chunk size |
| `MtuFallbackTest` | Low MTU (23) → chunk size reduced to 20 |
| `L2capCreditFlowTest` | Credit exhaustion pauses sender; replenishment resumes |
| `CrossBearerMtuTest` | GATT MTU 247 vs L2CAP MTU 517 → consistent chunking |

## Related

- [MTU Negotiation](./mtu-negotiation.md)
- [Power Mode Behavior](../../../docs/decisions/power/power-mode-behavior.md)
- [specs/enums.yaml PowerMode settings](../../../specs/enums.yaml)
- [Android BLE Skill](../../../.agents/skills/android-ble/SKILL.md)
- [Core Bluetooth Skill](../../../.agents/skills/core-bluetooth/SKILL.md)
