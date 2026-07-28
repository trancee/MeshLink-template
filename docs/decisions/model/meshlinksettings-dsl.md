# MeshLinkSettings Lambda DSL — API Design

**Status:** Locked — 2026-07-27

## Context

The SPEC.md §14 shows the canonical usage as a lambda DSL:

```kotlin
val settings = meshLinkSettings {
  powerMode = PowerMode.HIGH
  regulatoryRegion = RegulatoryRegion.EU
  keyRotation { interval = Duration.days(1) }
}
```

However, the current `MeshLinkSettingsBuilder` in the codebase uses imperative property assignment:

```kotlin
val builder = MeshLinkSettingsBuilder()
builder.powerMode = PowerMode.HIGH
// ...
val settings = builder.build()
```

The spec's lambda DSL with nested builders (`keyRotation { ... }`) is the documented API shape.

## Decision

**Adopt the lambda DSL as the primary API.** The imperative `MeshLinkSettingsBuilder` is a temporary implementation detail that will be replaced by the lambda DSL in the next milestone.

### Lambda DSL Implementation

```kotlin
// meshlink/src/commonMain/kotlin/ch/trancee/meshlink/MeshLinkSettings.kt

/**
 * MeshLink settings DSL builder.
 *
 * Usage:
 * ```kotlin
 * val settings = meshLinkSettings {
 *   powerMode = PowerMode.HIGH
 *   regulatoryRegion = RegulatoryRegion.EU
 *   keyRotation {
 *     interval = Duration.days(1)
 *     rotationGracePeriod = Duration.minutes(30)
 *     compromiseGracePeriod = Duration.ZERO
 *   }
 *   transfer {
 *     maxRetries = 3
 *     chunkSize = 512
 *     maxConcurrentSessionsPerPeer = 2
 *   }
 *   routing {
 *     routeUpdateMinInterval = Duration.seconds(1)
 *     routeUpdateMaxInterval = Duration.seconds(30)
 *     routeUpdateChangeThreshold = 3
 *     fullTableSyncInterval = Duration.minutes(5)
 *     routeEntryExpiry = Duration.minutes(15)
 *     feasibilityConditionEnabled = true
 *     maxRouteEntries = 256
 *   }
 *   security {
 *     fallbackMaxAttemptsPerMinute = 3
 *     fallbackTimeout = Duration.seconds(10)
 *     requireSignatureOnRouteUpdates = true
 *     defaultHandshakePattern = HandshakePattern.IX
 *   }
 *   diagnostics {
 *     emitToLog = true
 *     eventBufferSize = 1000
 *   }
 * }
 * ```
 */
public fun meshLinkSettings(block: MeshLinkSettingsBuilder.() -> Unit): MeshLinkSettings {
    return MeshLinkSettingsBuilder().apply(block).build()
}
```

### Nested Builder Pattern

Each settings group gets its own builder with named parameters matching the `data class` fields:

```kotlin
public fun MeshLinkSettingsBuilder.keyRotation(block: KeyRotationSettingsBuilder.() -> Unit) {
    val builder = KeyRotationSettingsBuilder()
    builder.block()
    this.keyRotation = KeyRotationSettings(
        interval = builder.interval,
        rotationGracePeriod = builder.rotationGracePeriod,
        compromiseGracePeriod = builder.compromiseGracePeriod,
    )
}

public class KeyRotationSettingsBuilder {
    public var interval: Duration = 3.days
    public var rotationGracePeriod: Duration = 1.hours
    public var compromiseGracePeriod: Duration = Duration.ZERO
}
```

### Coexistence with Imperative Builder

The imperative `MeshLinkSettingsBuilder` (top-level property assignment) is retained as a secondary API for programmatic construction where the lambda DSL is awkward (e.g., settings computed from a configuration file). Both paths produce identical `MeshLinkSettings` instances.

### Why the Lambda DSL?

1. **Kotlin idiom**: Lambda-with-receiver is the idiomatic Kotlin DSL pattern (used by Gradle, Ktor, Coil, etc.)
2. **Readability**: Nested blocks visually group related settings, matching the mental model of a settings bundle
3. **Type safety**: The compiler enforces that only valid `MeshLinkSettingsBuilder` properties are used inside each block
4. **Kover traceability**: Each builder's properties are individually testable
5. **BCV stability**: The lambda DSL can be evolved independently of the imperative builder (which is internal)

### BCV Impact

The `meshLinkSettings` function is the entry point for the lambda DSL. As a public function, it is tracked by Binary Compatibility Validator. Changes to the lambda DSL (adding/removing settings blocks) are considered API changes and require a version bump rationale.

## Related

- [SPEC.md §14](../../../SPEC.md#configuration-model)
- [Data Model ADR](data-model.md)
- [Settings Model](../../reference/settings.md)
