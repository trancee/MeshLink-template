# Power Management

> **Specification**: [SPEC.md §10](../../SPEC.md#power-management)  
> **Design rationale**: [Power Mode Behavior](../decisions/power/power-mode-behavior.md)  
> **Machine-readable**: [specs/catalogs/settings.yaml](../../specs/catalogs/settings.yaml#power_mode_parameter_mapping), [specs/codecs/enums.yaml](../../specs/codecs/enums.yaml#power-mode)

## Platform-Specific Notes

### Android

- Scan duty cycle implemented via `BluetoothLeScanner.startScan()` with `ScanSettings.setScanMode()` and periodic enable/disable
- Advertisement interval via `BluetoothLeAdvertiser.startAdvertising()` with `AdvertiseSettings.setInterval()`
- Connection interval requested via `BluetoothGatt.requestConnectionPriority()` (HIGH/MEDIUM/LOW map to `CONNECTION_PRIORITY_HIGH`/`BALANCED`/`LOW_POWER`)
- Idle connection interval requested after 5s inactivity via same API
- Background operation requires `ForegroundService` with `foregroundServiceType="connectedDevice"` and ongoing notification
- Doze mode: scans suspended; use `PendingIntent` scanning for background discovery

### iOS

- Scan duty cycle implemented via `CBCentralManager.scanForPeripherals()` with manual timer-based enable/disable
- Advertisement interval via `CBPeripheralManager.startAdvertising()` with `CBAdvertisementIntervalKey`
- Connection interval requested via `CBPeripheral.setNotifyValue()` / L2CAP channel options
- Idle connection interval requested after 5s inactivity
- Background operation requires `bluetooth-central` and `bluetooth-peripheral` background modes + state restoration
- iOS may throttle background scans; `CBCentralManager` restoration handles resume

## Quick Links

- [SPEC.md §10 — Full power spec](../../SPEC.md#power-management)
- [Power Mode Behavior ADR](../decisions/power/power-mode-behavior.md)
- [SPEC.md §11 — Diagnostics (PowerModeEffectiveEvent)](../../SPEC.md#diagnostics--events)
- [MTU Negotiation ADR](../decisions/transport/mtu-negotiation.md)
- [Settings Spec](../../specs/catalogs/settings.yaml)
- [Enums Spec](../../specs/codecs/enums.yaml)
