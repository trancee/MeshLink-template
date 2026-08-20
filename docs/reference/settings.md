# Settings Model

> **Specification**: [SPEC.md §14](../../SPEC.md#14-settings-model)
> **Source of truth**: MeshLinkSettings.kt  
> **Design rationale**: [Lambda DSL ADR](../decisions/model/settings-model.md)

## Application Identifier

`appId` is required, non-empty, normalized UTF-8 of at most 255 bytes, and
stable across app updates and process restarts. It determines `meshHash` and the
128-bit `appHash` security context. Changing it requires a new MeshLink instance
and creates a separate application/profile namespace.

## Lambda DSL (Primary API)

```kotlin
val settings = meshLinkSettings {
    appId = "com.example.myapp"
    powerMode = PowerMode.HIGH
    regulatoryRegion = RegulatoryRegion.EU
    enableBackground = true
    
    keyRotation {
        interval = Duration.days(1)
        rotationGracePeriod = Duration.minutes(30)
        compromiseGracePeriod = Duration.ZERO
    }
    
    transfer {
        maxRetries = 3
        chunkSize = 512
        maxTransfersPerPeer = 2
    }
    
    routing {
        routeAdvertisementChangeThreshold = 3
        routeDigestInterval = Duration.minutes(5)
        routeExpiry = Duration.minutes(15)
        maxRoutes = 256
    }
    
    diagnostics {
        eventBufferSize = 1000
        emitLog = true
    }
}
```

## Settings Classes (Immutable)

| Class | Purpose |
|-------|---------|
| `MeshLinkSettings` | Top-level configuration, including immutable enableBackground opt-in |
| `KeyRotationSettings` | Interval, grace periods |
| `TransferSettings` | Retries, chunk size, concurrency, scoreboard |
| `RoutingSettings` | Update intervals, thresholds, sync, expiry, feasibility, max entries |
| `DiagnosticsSettings` | Event buffer size and optional platform logging |

## Runtime Mutability

Settings are immutable for a `MeshLink` instance except for power mode:

```kotlin
meshLink.setPowerMode(PowerMode.LOW)
```

`meshLink.powerMode` exposes the successfully selected mode.
`meshLink.powerModeSettings` exposes resolved parameters after regulatory
and platform clamping. Existing transfers retain their established chunk
framing; new transfers and connections use the updated values.

Changing any other setting requires stopping the instance and constructing a
new one.

## Imperative Builder

Retained for programmatic construction (e.g., from a settings file). Both paths
produce identical `MeshLinkSettings` instances. Diagnostics are collected from
`MeshLink.diagnostics`; settings do not contain an event callback.

**Source of truth**: `MeshLinkSettings.kt` — `specs/catalogs/settings.yaml` is generated from it.

---

## Quick Links

- [SPEC.md §14 — Full config spec](../../SPEC.md#14-settings-model)
- MeshLinkSettings.kt
- [Lambda DSL ADR](../decisions/model/settings-model.md)
- [Power Mode Behavior ADR](../decisions/power/power-mode-behavior.md)
