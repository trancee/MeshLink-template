# Routing Layer

> **Specification**: [SPEC.md §8](../../SPEC.md#8-routing-layer)  
> **Design rationale**: [Routing Design](../decisions/routing/routing-design.md)  
> **Machine-readable**: [specs/protocol/state-machines.yaml](../../specs/protocol/state-machines.yaml), [specs/codecs/enums.yaml](../../specs/codecs/enums.yaml), [specs/codecs/frames.yaml](../../specs/codecs/frames.yaml)

## Platform-Specific Notes

### Android

- Route computation runs on `Dispatchers.Default`; RSSI sampling from `BluetoothGattCallback.onReadRemoteRssi()`
- L2CAP CoC preferred for data plane; GATT fallback automatic — see [Transport Layer](transport.md)
- Background routing requires `ForegroundService` with `foregroundServiceType="connectedDevice"`

### iOS

- Route computation runs on background `OperationQueue`; RSSI from `CBPeripheral.readRSSI()`
- L2CAP CoC via `CBPeripheral.openL2CAPChannel(_:)`; GATT fallback automatic
- Background routing requires `bluetooth-central` background mode and state restoration

## Quick Links

- [SPEC.md §8 — Full routing spec](../../SPEC.md#8-routing-layer)
- [Routing Design ADR](../decisions/routing/routing-design.md)
- [State Machines Spec](../../specs/protocol/state-machines.yaml)
- [Enums Spec](../../specs/codecs/enums.yaml)
- [Wire Frames Spec](../../specs/codecs/frames.yaml)
- [Peer Hint and Identity Races ADR](../decisions/discovery/peer-hint-and-identity-races.md)
