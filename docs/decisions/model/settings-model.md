# MeshLinkSettings Lambda DSL — API Design Rationale

**Status:** Locked — 2026-07-27

**Specification content** (DSL syntax, defaults, nested builders, all parameter values) lives in [SPEC.md §14](../../../SPEC.md). This ADR captures only the *why*.

---

## Context

The initial implementation used an imperative builder:

```kotlin
val builder = MeshLinkSettingsBuilder()
builder.powerMode = PowerMode.HIGH
// ...
val settings = builder.build()
```

The design goal was a **lambda DSL with nested builders** matching Kotlin idioms:

```kotlin
val settings = meshLinkSettings {
  powerMode = PowerMode.HIGH
  keyRotation { interval = Duration.days(1) }
}
```

---

## Decision

**Adopt the lambda DSL as the primary public API.** The imperative builder is retained as an internal/secondary API for programmatic construction (e.g., from settings files).

### Why Lambda DSL?

1. **Kotlin idiom**: Lambda-with-receiver is the standard Kotlin DSL pattern (Gradle, Ktor, Coil, kotlinx.html). Developers expect it.
2. **Readability**: Nested blocks visually group related settings, matching the mental model of a settings bundle.
3. **Type safety**: Compiler enforces that only valid `MeshLinkSettingsBuilder` properties are used inside each block.
4. **Kover traceability**: Each builder's properties are individually testable.
5. **BCV stability**: The lambda DSL (`meshLinkSettings` function) can be evolved independently of the imperative builder (which is internal).

### Nested Builder Pattern

Each settings group gets its own builder class with named parameters matching the target `data class` fields. The top-level builder delegates to nested builders:

```kotlin
fun MeshLinkSettingsBuilder.keyRotation(block: KeyRotationSettingsBuilder.() -> Unit) { ... }
```

### Coexistence with Imperative Builder

The imperative `MeshLinkSettingsBuilder` (flat property assignment) is retained for cases where the lambda DSL is awkward — e.g., settings computed dynamically from a configuration file. Both paths produce identical `MeshLinkSettings` instances.

### BCV Impact

The `meshLinkSettings` function is the public entry point for the lambda DSL. As a public function, it is tracked by Binary Compatibility Validator. Changes to the lambda DSL (adding/removing settings blocks) are considered API changes and require a version bump rationale.

---

## Related

- [SPEC.md §14](../../../SPEC.md#settings-model) — Full DSL specification with all defaults
- [Data Model ADR](data-model.md)
