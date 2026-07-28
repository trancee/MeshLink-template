# Build & Quality Constraints

> Source: [SPEC.md §12](../../SPEC.md#12-build--quality-constraints)

## 12.1 Performance Budgets (CI-Enforced)

| Metric | Target | Measurement | Rationale |
|--------|--------|-------------|-----------|
| Throughput (1-hop L2CAP) | ≥80 KB/s Android, ≥60 KB/s iOS | Benchmark | Matches practical file transfer requirements while respecting BLE limitations |
| Latency (1-hop, 256B, p95) | <50 ms | Benchmark | Ensures responsive interactive applications (messaging, gaming) |
| Memory (steady state, 8 peers) | ≤8 MB heap | Benchmark | Targets <0.5% of typical 2GB RAM device, minimizing impact on host apps |
| Battery scan duty cycle | ≤5% | Instrumentation | Targets <5% additional drain beyond baseline for all-day operation |
| Cold start | <500 ms to first advertisement | Benchmark | Ensures responsive user experience when enabling mesh |
| Routing convergence (10 nodes) | ≤3 s | Virtual harness | Balances rapid topology adaptation with control plane overhead |
| Wire codec op | <1 μs/message | JMH | Ensures minimal CPU impact for high-throughput scenarios |

## 12.2 Code Quality Rules

- Detekt: Zero suppressions
- ktfmt: Auto-format before every commit
- BCV: Track public API, explicit versioning for breaking changes
- ExplicitApi(): All public declarations need explicit visibility/return types
- No TODO comments in merged code

## 12.3 Platform Minimums

- Android: API 26 (runtime crypto capability checks for 26-32)
- iOS: 14.0
- iOS: Native targets only on macOS host (cross-compilation limitation)
- CHANGELOG.md is auto-generated from Conventional Commits at release time, not hand-maintained
