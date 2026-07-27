# GATT Service UUID — Public API Status

**Status:** Locked — 2026-07-27

## Context

The GATT Service UUID (`4853454D-0000-1000-8000-00805F9B34FB`) is defined in the `gatt-service-uuid.md` ADR. The question is whether this UUID is part of the public API (and therefore subject to BCV tracking and deprecation policies) or an internal implementation detail.

## Decision

**The GATT Service UUID is INTERNAL — not part of the public API.**

### Rationale

1. **The public API of MeshLink is the protocol layer**, not the BLE transport details. Callers of `MeshLink.start()` and `MeshLink.send()` do not need to know the GATT Service UUID — that concern is handled by the platform BLE glue.

2. **The UUID is a transport-layer implementation detail.** If MeshLink later supports additional transports (e.g., Wi-Fi Direct,Thread), the GATT UUID becomes irrelevant to consumers. Exposing it in the public API would couple the public contract to a specific BLE implementation.

3. **BCV would need to track it.** Making it public would require a deprecation policy if it ever changes, adding unnecessary governance overhead for an identifier that has no semantic meaning to the caller.

### Where It Lives

The UUID is an `internal` constant in the platform glue code:

```kotlin
// meshlink/src/androidMain/kotlin/ch/trancee/meshlink/android/ble/GattProfile.kt
internal object GattProfile {
    internal const val SERVICE_UUID = "4853454D-0000-1000-8000-00805F9B34FB"
    // ... characteristic UUIDs ...
}
```

```swift
// meshlink/src/iosMain/kotlin/ch/trancee/meshlink/ios/ble/GattProfile.kt
internal object GattProfile {
    internal val SERVICE_UUID = CBUUID(string: "4853454D-0000-1000-8000-00805F9B34FB")
    // ... characteristic UUIDs ...
}
```

### What IS Public API

The only transport-related public API surfaces are:

- `MeshLink.start(settings: MeshLinkSettings)` / `MeshLink.stop()`
- Diagnostic events that include transport information (e.g., `TransportFallbackEvent.reason`)
- The `DataPlaneBearer` enum (GATT vs L2CAP) — this is consumed by the host app to observe which bearer was selected

### When the UUID Might Become Public

The GATT Service UUID would only become public API if there is a documented use case for a host app to interact directly with the GATT characteristics outside the MeshLink library. At that point, it should be gated behind a public API surface and tracked by BCV.

## Related

- [GATT Service UUID ADR](gatt-service-uuid.md)
- [GATT/L2CAP Transport Selection](gatt-l2cap-transport-selection.md)
