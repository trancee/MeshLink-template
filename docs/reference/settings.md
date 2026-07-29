# Settings Model

> **Specification**: [SPEC.md §14](../../SPEC.md#configuration-model)  
> **Source of truth**: MeshLinkSettings.kt  
> **Design rationale**: [Lambda DSL ADR](../decisions/model/settings-model.md)

## Lambda DSL (Primary API)

```kotlin
val settings = meshLinkSettings {
    appId = "com.example.myapp"
    powerMode = PowerMode.HIGH
    regulatoryRegion = RegulatoryRegion.EU
    
    keyRotation {
        interval = Duration.days(1)
        rotationGracePeriod = Duration.minutes(30)
        compromiseGracePeriod = Duration.ZERO
    }
    
    transfer {
        maxRetries = 3
        chunkSize = 512
        maxConcurrentSessionsPerPeer = 2
    }
    
    routing {
        routeUpdateMinInterval = Duration.seconds(1)
        routeUpdateMaxInterval = Duration.seconds(30)
        routeUpdateChangeThreshold = 3
        fullTableSyncInterval = Duration.minutes(5)
        routeEntryExpiry = Duration.minutes(15)
        feasibilityConditionEnabled = true
        maxRouteEntries = 256
    }
    
    security {
        fallbackMaxAttemptsPerMinute = 3
        fallbackTimeout = Duration.seconds(10)
        requireSignatureOnRouteUpdates = true
        defaultHandshakePattern = HandshakePattern.IX
    }
    
    diagnostics {
        eventBufferSize = 1000
    }
    
    emitToLog = true
    eventCallback = { event -> println(event) }
}
```

## Settings Classes (Immutable)

| Class | Purpose |
|-------|---------|
| `MeshLinkSettings` | Top-level configuration |
| `KeyRotationSettings` | Interval, grace periods |
| `TransferSettings` | Retries, chunk size, concurrency, scoreboard |
| `RoutingSettings` | Update intervals, thresholds, sync, expiry, feasibility, max entries |
| `SecuritySettings` | NX fallback limits, timeout, route update signatures, handshake pattern |
| `DiagnosticsSettings` | Event buffer size |

## Imperative Builder

Retained for programmatic construction (e.g., from settings file). Both paths produce identical `MeshLinkSettings` instances.

**Source of truth**: `MeshLinkSettings.kt` — `specs/settings.yaml` is generated from it.

---

## Quick Links

- [SPEC.md §14 — Full config spec](../../SPEC.md#configuration-model)
- MeshLinkSettings.kt
- [Lambda DSL ADR](../decisions/model/settings-model.md)
- [Power Mode Behavior ADR](../decisions/power/power-mode-behavior.md)
