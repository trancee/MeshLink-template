# Android Dual Role vs iOS Central/Peripheral Constraints

**Status:** Locked — 2026-07-26

## Decision

**Android: Full dual-role (Central + Peripheral simultaneously)**
**iOS: Central in background, Peripheral only in foreground**

This asymmetry is a platform constraint, not a design choice. MeshLink adapts by:

- Android: Advertises + scans concurrently in all states
- iOS: Advertises only when app is foreground; scans in background via state restoration

## Platform Capabilities

| Capability | Android | iOS |
|------------|---------|-----|
| **Central (scan/connect)** | ✅ Always | ✅ Foreground + Background (with restoration) |
| **Peripheral (advertise/GATT server)** | ✅ Always | ⚠️ Foreground only; background = suspended |
| **Simultaneous Central + Peripheral** | ✅ Full | ❌ No (background peripheral suspended) |
| **L2CAP CoC (Central)** | ✅ | ✅ |
| **L2CAP CoC (Peripheral)** | ✅ | ✅ |
| **Background scan (screen off)** | ✅ (with PendingIntent) | ✅ (throttled, with restoration) |
| **Background advertise** | ✅ | ❌ (app suspended) |

## Android Dual-Role Implementation

```kotlin
// meshlink/src/androidMain/kotlin/ch/trancee/meshlink/android/ble/DualRoleManager.kt

class DualRoleManager @Inject constructor(
    private val context: Context,
    private val bluetoothManager: BluetoothManager,
    private val settings: MeshLinkSettings
) {
    
    private val peripheral: BluetoothGattServer
    private val scanner: BluetoothLeScanner
    private val advertiser: BluetoothLeAdvertiser
    
    init {
        // Peripheral: GATT Server
        peripheral = bluetoothManager.openGattServer(context, gattServerCallback)
        setupGattServices(peripheral)
        
        // Central: Scanner
        scanner = bluetoothManager.adapter.bluetoothLeScanner
        
        // Peripheral: Advertiser
        advertiser = bluetoothManager.adapter.bluetoothLeAdvertiser
    }
    
    fun start() {
        // Start advertising (Peripheral)
        val advSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()
        
        val advData = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(GattProfile.SERVICE_UUID))
            .setIncludeDeviceName(false)
            .build()
        
        advertiser.startAdvertising(advSettings, advData, advertiseCallback)
        
        // Start scanning (Central) - duty cycled per power mode
        startScanning()
    }
    
    fun stop() {
        advertiser.stopAdvertising(advertiseCallback)
        scanner.stopScan(scanCallback)
        peripheral.clearServices()
        peripheral.close()
    }
    
    private fun startScanning() {
        val settings = ScanSettings.Builder()
            .setScanMode(scanModeForPowerMode())
            .setReportDelay(0)
            .setLegacy(false)
            .build()
        
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(GattProfile.SERVICE_UUID))
                .build()
        )
        
        scanner.startScan(filters, settings, scanCallback)
    }
}
```

## iOS Role Constraints

```swift
// meshlink/src/iosMain/kotlin/ch/trancee/meshlink/ios/ble/RoleManager.kt

import CoreBluetooth
import UIKit

class RoleManager : NSObject, CBCentralManagerDelegate, CBPeripheralManagerDelegate {
    
    private var centralManager: CBCentralManager!
    private var peripheralManager: CBPeripheralManager!
    private var isPeripheralActive = false
    
    override init() {
        super.init()
        
        // Central: Always available (with background restoration)
        centralManager = CBCentralManager(
            delegate: self,
            queue: nil,
            options: [
                CBCentralManagerOptionRestoreIdentifierKey: "meshlink-central",
                CBCentralManagerOptionShowPowerAlertKey: true
            ]
        )
        
        // Peripheral: Only when foreground
        peripheralManager = CBPeripheralManager(
            delegate: self,
            queue: nil,
            options: [
                CBPeripheralManagerOptionRestoreIdentifierKey: "meshlink-peripheral",
                CBPeripheralManagerOptionShowPowerAlertKey: true
            ]
        )
    }
    
    // MARK: - App Lifecycle
    
    func applicationDidBecomeActive() {
        // Foreground: Start peripheral advertising
        startPeripheral()
    }
    
    func applicationWillResignActive() {
        // Background: Stop peripheral, central continues
        stopPeripheral()
    }
    
    // MARK: - Peripheral Management
    
    private func startPeripheral() {
        guard !isPeripheralActive, peripheralManager.state == .poweredOn else { return }
        
        let service = CBMutableService(type: GattProfile.SERVICE_UUID, primary: true)
        // ... add characteristics ...
        peripheralManager.add(service)
        
        let advData: [String: Any] = [
            CBAdvertisementDataServiceUUIDsKey: [GattProfile.SERVICE_UUID],
            CBAdvertisementDataLocalNameKey: "MeshLink"
        ]
        peripheralManager.startAdvertising(advData)
        isPeripheralActive = true
    }
    
    private func stopPeripheral() {
        guard isPeripheralActive else { return }
        peripheralManager.stopAdvertising()
        peripheralManager.removeAllServices()
        isPeripheralActive = false
    }
    
    // MARK: - Central Management (Background-Ready)
    
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn {
            startScanning()
        }
    }
    
    private func startScanning() {
        centralManager.scanForPeripherals(
            withServices: [GattProfile.SERVICE_UUID],
            options: [
                CBCentralManagerScanOptionAllowDuplicatesKey: false
            ]
        )
    }
    
    // State restoration handles background central
    func centralManager(
        _ central: CBCentralManager,
        willRestoreState dict: [String: Any]
    ) {
        // Restore scan state, reconnect peripherals
        if let services = dict[CBCentralManagerRestoredStateScanServicesKey] as? [CBUUID] {
            centralManager.scanForPeripherals(withServices: services, options: nil)
        }
        if let peripherals = dict[CBCentralManagerRestoredStatePeripheralsKey] as? [CBPeripheral] {
            for peripheral in peripherals {
                central.connect(peripheral, options: nil)
            }
        }
    }
}
```

## MeshLink Adaptation Strategy

### Connection Establishment

| Scenario | Android (Dual) | iOS (Asymmetric) |
|----------|----------------|------------------|
| App A foreground, App B foreground | A←scan→B, A←adv→B | Same |
| App A foreground, App B background | A←scan→B, A←adv→B | A←scan→B (B not advertising) |
| Both background | Both scan + adv | Both scan only |

**Result:** iOS background→background connections require one app to come foreground briefly, or use **state restoration** to re-establish.

### Discovery Advertisement Differences

```kotlin
// Platform-specific advertisement content
data class AdvertisementConfig(
    val platform: UInt,  // 0=Android, 1=iOS
    val powerMode: PowerMode,
    val supportsPeripheralInBackground: Boolean
) {
    // iOS in background: platform=1, supportsPeripheralInBackground=false
    // Android always: platform=0, supportsPeripheralInBackground=true
}
```

**Peer Interpretation:**

- If `supportsPeripheralInBackground=false` AND peer is background → don't expect incoming connections
- Always scan for peers (Central role works on both)

## Power Mode Impact

| Power Mode | Android Central | Android Peripheral | iOS Central | iOS Peripheral |
|------------|-----------------|-------------------|-------------|----------------|
| HIGH | 20% duty, 100ms adv | 100ms adv | 20% duty | 100ms adv (fg only) |
| MEDIUM | 10% duty, 500ms adv | 500ms adv | 10% duty | 500ms adv (fg only) |
| LOW | 5% duty, 1000ms adv | 1000ms adv | 5% duty | 1000ms adv (fg only) |

**iOS Peripheral:** Only active when app is foreground (UIApplication.shared.applicationState == .active)

## Diagnostics

```yaml
# specs/diagnostic_events.yaml
- name: RoleStateChangeEvent
  fields:
    - platform: String  # "android" | "ios"
    - centralActive: Boolean
    - peripheralActive: Boolean
    - appState: String  # "foreground" | "background"
    - powerMode: PowerMode
    - timestamp: Instant
```

## Testing Matrix

| Test | Android | iOS |
|------|---------|-----|
| Foreground↔Foreground connect | ✅ | ✅ |
| Foreground↔Background connect | ✅ | ⚠️ (iOS bg not advertising) |
| Background↔Background connect | ✅ | ❌ (requires state restore) |
| Peripheral restart on foreground | N/A | ✅ |
| State restoration after kill | ✅ | ✅ |

## Developer Guidance

```kotlin
// meshlink/src/commonMain/kotlin/ch/trancee/meshlink/MeshLinkSettings.kt

data class MeshLinkSettings(
    // ... existing ...
    
    /** 
     * iOS only: When backgrounded, stop advertising to save battery.
     * Default: true. 
     * Set false to attempt background advertising (will not work on iOS). 
     */
    val iosStopAdvertisingInBackground: Boolean = true,
)
```

## Related

- [Android BLE Skill](../../../.agents/skills/android-ble/SKILL.md)
- [Core Bluetooth Skill](../../../.agents/skills/core-bluetooth/SKILL.md)
- [iOS Background BLE](./ios-background-ble.md)
- [Android Foreground Service](./android-foreground-service.md)
- [Power Management Spec](../../../docs/reference/10-power-management.md)
