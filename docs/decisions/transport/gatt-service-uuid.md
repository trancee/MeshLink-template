# GATT Service UUID & Characteristic Allocation

**Status:** Locked — 2026-07-26

## Decision

**Service UUID:** `0000MESH-0000-1000-8000-00805F9B34FB` (base: `00000000-0000-1000-8000-00805F9B34FB` with `MESH` in first 4 bytes)

| Characteristic | UUID Suffix | Properties | Purpose |
|---|---|---|---|
| Noise Handshake | `0001` | Write (no response) + Notify | Noise XX/IK/IX/NX handshake messages |
| Routing Control | `0002` | Write (no response) + Notify | ROUTE_UPDATE, ROUTE_WITHDRAWAL, ROUTE_DIGEST |
| Transfer Data | `0003` | Write (no response) + Notify | TRANSFER_CHUNK, TRANSFER_ACKNOWLEDGMENT, TRANSFER_CANCEL |
| Key Rotation | `0004` | Write (no response) + Notify | KEY_ROTATION announcement |
| MTU Negotiation | `0005` | Read + Write | Request/confirm MTU size (optional) |

**Base UUID:** `00000000-0000-1000-8000-00805F9B34FB` (Bluetooth Base UUID)

**128-bit Service UUID Construction:**

```text
Time-low:     0x4D455348  ("MESH" in little-endian ASCII)
Time-mid:     0x0000
Time-high:    0x1000
Clock-seq:    0x8000
Node:         0x00805F9B34FB
```

Result: `4853454D-0000-1000-8000-00805F9B34FB` (RFC 4122 string representation).

**BLE wire format (little-endian byte order):**

```text
4D 45 53 48 00 00 10 00 80 00 00 80 5F 9B 34 FB
↑ MESH in little-endian bytes
```

Characteristic UUIDs share the same base, varying only the 16-bit UUID portion:

| Characteristic | 16-bit UUID | BLE wire bytes 12-13 (little-endian) |
|---|---|---|
| Noise Handshake | 0x0001 | 01 00 |
| Routing Control | 0x0002 | 02 00 |
| Transfer Data | 0x0003 | 03 00 |
| Key Rotation | 0x0004 | 04 00 |
| MTU Negotiation | 0x0005 | 05 00 |

## Android Implementation

```kotlin
// meshlink/src/androidMain/kotlin/ch/trancee/meshlink/android/ble/GattProfile.kt

object GattProfile {
    // Service UUID: 4D455348-0000-1000-8000-00805F9B34FB
    val SERVICE_UUID = ParcelUuid.fromString("4853454D-0000-1000-8000-00805F9B34FB")
    
    // Characteristic UUIDs (16-bit suffix on base)
    val HANDSHAKE_UUID        = uuidFrom16Bit(0x0001)
    val ROUTING_CONTROL_UUID  = uuidFrom16Bit(0x0002)
    val TRANSFER_DATA_UUID    = uuidFrom16Bit(0x0003)
    val KEY_ROTATION_UUID     = uuidFrom16Bit(0x0004)
    val MTU_NEGOTIATION_UUID  = uuidFrom16Bit(0x0005)
    
    private fun uuidFrom16Bit(u16bit(uuid16: Int): ParcelUuid {
        // Base: 4853454D-0000-1000-8000-00805F9B34FB
        // Replace 16-bit at offset 0 (time-low low bytes)
        return ParcelUuid.fromString(
            String.format("4853454D-%04X-1000-8000-00805F9B34FB", uuid16)
        )
    }
}

// GATT Server (Peripheral)
class MeshLinkGattServer(private val context: Context) {
    private val server = bluetoothManager.openGattServer(context, callback)
    
    init {
        val service = BluetoothGattService(GattProfile.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                GattProfile.HANDSHAKE_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or 
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            ).apply { addDescriptor(CCCD) }
        )
        // ... repeat for other characteristics
        
        server.addService(service)
    }
}
```

## iOS Implementation

```swift
// meshlink/src/iosMain/kotlin/ch/trancee/meshlink/ios/ble/GattProfile.kt

import CoreBluetooth

object GattProfile {
    // Base: 4853454D-0000-1000-8000-00805F9B34FB
    private val BASE_UUID = "4853454D-0000-1000-8000-00805F9B34FB"
    
    val SERVICE_UUID = CBUUID(string: BASE_UUID)
    val HANDSHAKE_UUID = CBUUID(string: BASE_UUID.replacingOccurrences(of: "0000", with: "0001"))
    val ROUTING_CONTROL_UUID = CBUUID(string: BASE_UUID.replacingOccurrences(of: "0000", with: "0002"))
    val TRANSFER_DATA_UUID = CBUUID(string: BASE_UUID.replacingOccurrences(of: "0000", with: "0003"))
    val KEY_ROTATION_UUID = CBUUID(string: BASE_UUID.replacingOccurrences(of: "0000", with: "0004"))
    val MTU_NEGOTIATION_UUID = CBUUID(string: BASE_UUID.replacingOccurrences(of: "0000", with: "0005"))
}

// Peripheral (Server)
func setupGattServer() {
    let service = CBMutableService(type: GattProfile.SERVICE_UUID, primary: true)
    
    let handshakeChar = CBMutableCharacteristic(
        type: GattProfile.HANDSHAKE_UUID,
        properties: [.writeWithoutResponse, .notify],
        value: nil,
        permissions: .writeable
    )
    // ... add CCCD descriptor
    
    service.characteristics = [handshakeChar, routingChar, transferChar, keyRotationChar, mtuChar]
    peripheralManager.add(service)
}

// Central (Client)
func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
    guard let service = peripheral.services?.first(where: { $0.uuid == GattProfile.SERVICE_UUID }) else { return }
    peripheral.discoverCharacteristics([
        GattProfile.HANDSHAKE_UUID,
        GattProfile.ROUTING_CONTROL_UUID,
        GattProfile.TRANSFER_DATA_UUID,
        GattProfile.KEY_ROTATION_UUID,
        GattProfile.MTU_NEGOTIATION_UUID
    ], for: service)
}
```

## MTU Negotiation

- **Default MTU**: 23 bytes (BLE 4.0 minimum)
- **Target MTU**: 517 bytes (BLE 4.2+ max ATT_MTU = 517)
- **Negotiation**: On GATT connect, central writes desired MTU to `MTU_NEGOTIATION` characteristic; peripheral responds with confirmed MTU (min of both)
- **Fallback**: If characteristic not found, use `requestMTU(517)` (Android) / automatic (iOS)

```kotlin
// Android: request max MTU after connection
bluetoothGatt.requestMtu(517)

// iOS: automatic negotiation to max supported by both sides
// No API needed; CBPeripheral.maximumWriteValueLengthForType updates
```

## Characteristic Value Format

All characteristics carry **WireFrame** encoded payloads (see `handwritten-codec.md`):

| Characteristic | Frame Types Carried |
|---|---|
| Noise Handshake | `MESH_ENVELOPE` (handshake messages only) |
| Routing Control | `ROUTE_UPDATE`, `ROUTE_WITHDRAWAL`, `ROUTE_DIGEST` |
| Transfer Data | `TRANSFER_CHUNK`, `TRANSFER_ACKNOWLEDGMENT`, `TRANSFER_CANCEL` |
| Key Rotation | `KEY_ROTATION` |
| MTU Negotiation | Custom: `{ requestedMtu: UInt16, confirmedMtu: UInt16 }` |

**Encryption:** All frames except `ROUTE_DIGEST` and `KEY_ROTATION` are AEAD-encrypted per Noise session (see `crypto-design.md`). The GATT layer sees only ciphertext.

## Diagnostics

```yaml
# specs/diagnostic_events.yaml
- name: GattCharacteristicDiscovered
  fields:
    - characteristicUuid: String
    - properties: Int
- name: MtuNegotiated
  fields:
    - requestedMtu: Int
    - confirmedMtu: Int
    - peerIdentity: PeerIdentity
```

## Related

- [GATT as Control Plane, L2CAP CoC as Data Plane](../transport/gatt-l2cap-transport-selection.md)
- [L2CAP PSM Allocation](./l2cap-psm-allocation.md)
- [Wire Codec: Hand-Written](../wire/handwritten-codec.md)
- [Android BLE Skill](../../../.agents/skills/android-ble/SKILL.md)
- [Core Bluetooth Skill](../../../.agents/skills/core-bluetooth/SKILL.md)
