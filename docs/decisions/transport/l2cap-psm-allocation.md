# L2CAP PSM Allocation & Dynamic Assignment

**Status:** Locked — 2026-07-26

## Decision

**L2CAP PSMs are dynamically assigned by the OS** when the L2CAP server is published. MeshLink advertises the **assigned PSM** in the discovery advertisement so peers can connect directly via L2CAP CoC.

**Both Android and iOS use the same dynamic range: `0x0080`–`0x00FF` (128 values).**

## PSM Ranges by Platform

| Platform | Dynamic CoC Range | Assignment Mechanism |
|----------|-------------------|---------------------|
| Android (API 26+) | `0x0080`–`0x00FF` | `BluetoothServerSocket.listenUsingL2capChannel()` — OS assigns, returns via `serverSocket.psm` |
| iOS | `0x0080`–`0x00FF` | `CBPeripheralManager.publishL2CAPChannel(PSM:)` — you provide preferred PSM; OS grants or fails with collision |

**Key difference:**

- **Android**: Call `listenUsingL2capChannel()` **without** PSM argument → OS assigns → read back `serverSocket.psm`
- **iOS**: Call `publishL2CAPChannel(PSM:)` **with** preferred PSM → OS grants that PSM or returns collision error

## Discovery Advertisement

Per `specs/wire_frames.yaml`:

```yaml
- name: psm
  type: UByte
  size_bytes: 1
  description: "Assigned L2CAP PSM from dynamic range 0x0080–0x00FF. 0 = L2CAP CoC not supported."
```

**Encoding:** The assigned PSM value directly (fits in 1 byte since max is `0xFF`).

| Platform | Assigned PSM | Advertised Byte |
|----------|--------------|-----------------|
| Android | `0x0080`–`0x00FF` | PSM value (0x80–0xFF) |
| iOS | `0x0080`–`0x00FF` | PSM value (0x80–0xFF) |

**Receiver reconstructs full PSM (identical on both platforms):**

```kotlin
fun advertisedByte.toInt()  // 0x80–0xFF
```

## Android Implementation

```kotlin
// meshlink/src/androidMain/kotlin/ch/trancee/meshlink/android/ble/L2capManager.kt

class L2capManager @Inject constructor(
    private val bluetoothManager: BluetoothManager
) {
    
    /** Start L2CAP server. Returns the OS-assigned PSM. */
    fun startServer(): Result<Int> = runCatching {
        // No PSM argument = OS assigns from dynamic range 0x0080-0x00FF
        val serverSocket = bluetoothManager.adapter.listenUsingL2capChannel()
        val assignedPsM = serverSocket.psm  // Available API 26+
        serverSocket.close() // We'll re-open with the assigned PSM for actual use
        assignedPsM
    }
    
    /** Open L2CAP channel to peer using their advertised PSM. */
    fun connect(peer: BluetoothDevice, peerAdvertisedPsM: Int): L2capChannel {
        // PSM is directly the advertised byte value (0x80-0xFF)
        val fullPsM = peerAdvertisedPsM
        return peer.createL2capChannel(fullPsM)
    }
}
```

## iOS Implementation

```swift
// meshlink/src/iosMain/kotlin/ch/trancee/meshlink/ios/ble/L2capManager.swift

import CoreBluetooth

class L2capManager : NSObject, CBPeripheralManagerDelegate {
    
    private var peripheralManager: CBPeripheralManager!
    private var assignedPsM: CBL2CAPPSM = 0
    private var preferredPsM: CBL2CAPPSM = 0x0080  // Start at bottom of dynamic range
    
    override init() {
        super.init()
        peripheralManager = CBPeripheralManager(delegate: self, queue: nil, options: [
            CBPeripheralManagerOptionRestoreIdentifierKey: "meshlink-peripheral"
        ])
    }
    
    func startServer() {
        // Request preferred PSM; OS grants it or fails with collision
        peripheralManager.publishL2CAPChannel(withEncryption: false, PSM: preferredPsM)
    }
    
    // MARK: - CBPeripheralManagerDelegate
    
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        if peripheral.state == .poweredOn {
            startServer()
        }
    }
    
    func peripheralManager(
        _ peripheral: CBPeripheralManager,
        didPublishL2CAPChannel channel: CBL2CAPChannel?,
        error: Error?
    ) {
        if let error = error {
            // Collision: try next PSM in dynamic range
            preferredPsM += 1
            if preferredPsM <= 0x00FF {
                peripheralManager.publishL2CAPChannel(withEncryption: false, PSM: preferredPsM)
            } else {
                // Exhausted range - log error, stay on GATT
                logError("L2CAP PSM range exhausted: \(error)")
            }
        } else if let channel = channel {
            // Success: channel.psm == preferredPsM (granted)
            self.assignedPsM = channel.psm
            updateAdvertisement(psm: assignedPsM)
        }
    }
    
    /** Connect to peer using their advertised PSM. */
    func connect(to peripheral: CBPeripheral, peerAdvertisedPsM: UInt8) {
        // PSM is directly the advertised byte value (0x80-0xFF)
        let fullPsM = CBL2CAPPSM(peerAdvertisedPsM)
        peripheral.openL2CAPChannel(fullPsM)
    }
}
```

## PSM Collision Handling

| Scenario | Behavior |
|----------|----------|
| Android: PSM in use | `listenUsingL2capChannel()` throws `IOException` → retry (OS assigns different) |
| iOS: PSM collision | `didPublishL2CAPChannel` returns error → increment preferred PSM → retry (up to 0x00FF) |
| Peer advertised PSM but connect fails | `TransportFallbackEvent.reason = L2CAP_CONNECT_FAILED` → fallback to GATT |

**Retry Strategy:** On collision, increment preferred PSM by 1 and retry (up to 3 attempts).

## Advertisement Update Flow

```text
1. MeshLink.start() → L2capManager.startServer()
2. OS assigns PSM (e.g., 0x00A3 on Android, 0x00A3 on iOS)
3. Update BLE advertisement with assigned PSM byte (0xA3)
4. Peers discover → see PSM in advertisement
5. Peer connects GATT → completes Noise handshake
6. Peer attempts L2CAP CoC using advertised PSM
7. On success: promote data plane to L2CAP
8. On failure: TransportFallbackEvent, stay on GATT
```

## Credit-Based Flow Control

| Power Mode | Initial Credits | Replenish Threshold |
|------------|-----------------|---------------------|
| HIGH | 10 | 3 |
| MEDIUM | 5 | 2 |
| LOW | 3 | 1 |

```kotlin
// Android
l2capChannel.configureCreditBasedFlowControl(credits = initialCredits)

// iOS
channel.setCreditBasedFlowControl(credit: initialCredits)
```

## Diagnostics

```yaml
# specs/diagnostic_events.yaml
- name: L2capPsMAssignedEvent
  fields:
    - assignedPsM: Int          # 0x0080–0x00FF
    - platform: String          # "android" | "ios"
    - collisionRetries: Int     # 0 = first try succeeded
    - timestamp: Instant
```

## Related

- [GATT as Control Plane, L2CAP CoC as Data Plane](../transport/gatt-l2cap-transport-selection.md)
- [GATT Service UUID](./gatt-service-uuid.md)
- [Wire Frames: Discovery Advertisement](../../../specs/wire_frames.yaml)
- [Android Bluetooth Sockets Skill](../../../.agents/skills/android-bluetooth-sockets/SKILL.md)
- [Core Bluetooth Skill](../../../.agents/skills/core-bluetooth/SKILL.md)
