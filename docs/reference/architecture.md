# Architecture Overview

> **Specification**: [SPEC.md §2](../../SPEC.md#architecture-overview)  
> **Design rationale**: [Module Structure](../explanation/module-structure.md)

## Module Structure

| Module | Purpose | Runs On |
|--------|---------|---------|
| `meshlink` | Shipped library (public API + implementation) | JVM + Android + iOS |
| `meshlink-reference` | Reference app (public API only, Compose Multiplatform) | Android + iOS |
| `meshlink-proof` | Real-device validation (needs internal access) | Real Android/iOS devices |
| `meshlink-benchmark` | Performance benchmarking | JVM + device fleet |

## Source Set Structure

| Source Set | Contents |
|------------|----------|
| `commonMain` | Shared business logic (security, routing, transfer, diagnostics) |
| `androidMain` | BLE glue, fallback crypto for API 26-32 |
| `iosMain` | BLE glue |
| `commonTest` | Pure JVM tests (protocol logic, wire codec, crypto) |
| `androidHostTest` | Host-side Android tests (crypto fallback paths) |

## Platform Minimums

- Android API 26 (Android 8.0)
- iOS 14
- Higher APIs guarded at runtime

---

## Quick Links

- [SPEC.md §2 — Full architecture details](../../SPEC.md#architecture-overview)
- [Module Structure Explanation](../explanation/module-structure.md)
- [CONSTITUTION.md Technical Constraints](../../CONSTITUTION.md#technical-constraints)
