# iOS Background BLE Execution Strategy

**Status:** Locked — 2026-07-26

## Decision

**Support background BLE on iOS via Core Bluetooth state preservation/restoration** with explicit user consent. Full mesh operation in background; reduced scan duty cycle per power mode.

## Info.plist Requirements

```xml
<!-- Required for any BLE background operation -->
<key>UIBackgroundModes</key>
<array>
    <string>bluetooth-central</string>
    <string>bluetooth-peripheral</string>
</array>

<!-- Optional: show Bluetooth prompt if off -->
<key>CBManagerOptionShowPowerAlertKey</key>
<true/>

<!-- Optional: restore identifier for state preservation -->
<key>CBManagerOptionRestoreIdentifierKey</key>
<string>ch.trancee.meshlink.cbmanager</string>
```

## State Preservation & Restoration

### Central Manager

```swift
// meshlink/src/iosMain/kotlin/ch/trancee/meshlink/ios/ble/CentralManagerDelegate.kt

class CentralManagerDelegate : NSObject(), CBCentralManagerDelegate {
    
    // Called when app is relaunched to restore Bluetooth state
    func centralManager(
        _ central: CBCentralManager,
        willRestoreState dict: [String: Any]
    ) {
        // Restore discovered peripherals
        if let peripherals = dict[CBCentralManagerRestoredStatePeripheralsKey] as? [CBPeripheral] {
            for peripheral in peripherals {
                // Reconnect to restore connections
                central.connect(peripheral, options: nil)
            }
        }
        
        // Restore scan state
        if let scanServices = dict[CBCentralManagerRestoredStateScanServicesKey] as? [CBUUID] {
            // Resume scanning for these services
        }
        
        // Restore connection state
        // Note: Connections are NOT automatically restored; we must reconnect
    }
    
    // Called when state changes (poweredOn/off/unauthorized/etc)
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            // Resume scanning, reconnect to peers
        case .unauthorized, .unsupported, .poweredOff:
            // Stop all operations, notify MeshLink
        @unknown default:
            break
        }
    }
}
```

### Peripheral Manager

```swift
// meshlink/src/iosMain/kotlin/ch/trancee/meshlink/ios/ble/PeripheralManagerDelegate.kt

class PeripheralManagerDelegate : NSObject(), CBPeripheralManagerDelegate {
    
    func peripheralManager(
        _ peripheral: CBPeripheralManager,
        willRestoreState dict: [String: Any]
    ) {
        // Restore advertised services
        if let services = dict[CBPeripheralManagerRestoredStateServicesKey] as? [CBMutableService] {
            for service in services {
                peripheral.add(service)
            }
        }
        
        // Restore advertisement data
        if let advData = dict[CBPeripheralManagerRestoredStateAdvertisementDataKey] as? [String: Any] {
            peripheral.startAdvertising(advData)
        }
    }
}
```

## Background Operation Constraints

| Operation | Foreground | Background |
|-----------|------------|------------|
| Scan (central) | Full duty cycle | **Reduced**: iOS throttles to ~1 scan/30s; use PendingIntent equivalent via `CBCentralManager.scanForPeripherals(withServices:options:)` with `CBCentralManagerScanOptionAllowDuplicatesKey = false` |
| Advertise (peripheral) | Full interval | **Allowed**: `CBPeripheralManager.startAdvertising(_:)` continues |
| GATT connections | Unlimited | **Limited**: ~3-4 concurrent; system may terminate |
| L2CAP CoC | Full | **Allowed** if connection exists |
| Data transfer | Full throughput | **Reduced**: iOS throttles write/notify rate |

## Power Mode Adaptation (Background)

| Power Mode | Foreground Scan Duty | Background Scan Duty | Adv Interval (fg/bg) |
|------------|---------------------|---------------------|---------------------|
| HIGH | 20% | 5% (iOS minimum) | 100ms / 500ms |
| MEDIUM | 10% | 2% | 500ms / 1000ms |
| LOW | 5% | 1% | 1000ms / 2000ms |

**Implementation:** When `UIApplication.shared.applicationState == .background`, apply background duty cycle multipliers from settings.

## Background Task Completion

```kotlin
// meshlink/src/iosMain/kotlin/ch/trancee/meshlink/ios/BackgroundTask.kt

fun beginBackgroundTask(name: String): UIBackgroundTaskIdentifier {
    return UIApplication.shared.beginBackgroundTask(withName: name) {
        // Expiration handler - clean up
        endBackgroundTask(identifier)
    }
}

fun endBackgroundTask(identifier: UIBackgroundTaskIdentifier) {
    if (identifier != UIBackgroundTaskIdentifier.invalid) {
        UIApplication.shared.endBackgroundTask(identifier)
    }
}
```

Use for: completing handshakes, flushing transfer chunks, sending route updates before suspension.

## State Restoration Flow

```text
App killed by iOS (memory pressure)
         ↓
User reopens app / location update triggers relaunch
         ↓
iOS launches app with UIApplicationLaunchOptionsBluetoothCentralsKey / PeripheralsKey
         ↓
MeshLink.start() called
         ↓
CBCentralManager/CBPeripheralManager created with restoreIdentifier
         ↓
willRestoreState called with saved state
         ↓
MeshLink restores: peer connections, scan state, advertisement
         ↓
Normal operation resumes
```

## Limitations & Mitigations

| Limitation | Impact | Mitigation |
|------------|--------|------------|
| iOS may terminate background app | Mesh goes down | Acceptable; peer re-discovers on relaunch |
| Scan throttling in background | Slower discovery | Reduce expectations; HIGH mode still scans |
| Max ~3-4 concurrent connections | Limits mesh degree | Power mode LOW limits to 2 anyway |
| No raw socket access | L2CAP CoC only via CBL2CAPChannel | Use GATT as fallback (always works) |
| No Bluetooth state change in background | Can't react to power off | Handle on foreground resume |

## Testing

- `meshlink-proof/ios/`: Real device tests for background scan/advertise/connect
- Simulate: memory pressure kill → relaunch → state restoration
- Verify: connections restored, scan resumed, advertisement active

## Related

- [Core Bluetooth Skill](../../../.agents/skills/core-bluetooth/SKILL.md)
- [KMP iOS Integration Skill](../../../.agents/skills/kmp-ios-integration/SKILL.md)
- [Power Management Spec](../../../docs/reference/10-power-management.md)
