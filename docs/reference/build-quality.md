# Build & Quality Constraints

> **Specification**: [SPEC.md §12](../../SPEC.md#build--quality-constraints)  
> **Binding rules**: [CONSTITUTION.md](../../CONSTITUTION.md)

## Performance Budgets (CI-Enforced)

| Target | Budget | Measurement |
|--------|--------|-------------|
| Throughput (1-hop L2CAP) | ≥80 KB/s (Android Pixel 6+), ≥60 KB/s (iOS iPhone 12+) | `meshlink-benchmark` |
| Latency (1-hop, 256B, p95) | <50 ms after connection established | `meshlink-benchmark` |
| Memory (steady state, 8 peers) | ≤8 MB heap | `meshlink-benchmark` |
| Battery (LOW/background idle) | Target ≤5% scan duty; request 500–1000 ms idle interval after 5 s without queued work | Derived from effective power settings |
| Cold start | <500 ms from `mesh.start()` to first advertisement | `meshlink-benchmark` |
| Routing convergence | ≤3 s for 10-node topology change (virtual transport) | `meshlink-benchmark` |
| Wire codec encode/decode | <1 μs/message (JVM benchmark) | `kotlinx-benchmark` |

**Regression gate**: >10% vs last committed benchmark blocks merge.

## Code Quality (Per CONSTITUTION.md §I)

- Detekt: zero suppressions (test suppressions require justification comment)
- ktfmt: formatting before every commit
- Full descriptive identifiers (no `cfg`, `mgr`, `idx`, `tmp`, `msg`)
- BCV tracks public API; `.api` diff requires version-bump rationale
- `explicitApi()` enabled
- No `TODO` comments in merged code
- Tooling at latest stable releases
- Dependencies pinned, upgraded promptly

## Platform Minimums

- Android API 26 (Android 8.0)
- iOS 14
- Higher APIs guarded at runtime

## Runtime Dependencies

Only `kotlinx-coroutines-core` in shipped `:meshlink` artifact.
`kotlinx-datetime` for `Duration` in settings DSL is acknowledged exception.

## Dokka / SKI / Coverage

Apply to `:meshlink` **only** — not reference/proof/benchmark modules.

---

## Quick Links

- [SPEC.md §12 — Full build/quality spec](../../SPEC.md#build--quality-constraints)
- [CONSTITUTION.md — Binding Rules](../../CONSTITUTION.md)
- [meshlink/build.gradle.kts](../../meshlink/build.gradle.kts)
- [Kover Config](../../meshlink/build.gradle.kts)
