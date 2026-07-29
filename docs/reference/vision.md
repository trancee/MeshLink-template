# Vision & Product Pillars

> **Specification**: [SPEC.md §1](../../SPEC.md#vision--product-pillars)

## Problem Statement

Mobile devices need to communicate securely without internet, backend servers, or user accounts. BLE mesh networking requires handling peer discovery, trust establishment, routing, and reliable transfer. Both Android and iOS must offer identical public API behavior.

## Product Pillars

| # | Pillar | Description |
|---|--------|-------------|
| 1 | **Zero-infrastructure trust** | Trust On First Use (TOFU): first mutually-authenticated handshake pins peer identity keys; subsequent mismatches require explicit reset/revocation |
| 2 | **Two-layer encryption** | Hop-by-hop link encryption (relays forward without reading) layered under end-to-end encryption (origin/destination only) |
| 3 | **Proactive multi-hop routing** | Distance-vector-style routing control plane maintains live route tables; host app never selects intermediate hops manually |
| 4 | **Reliable large-payload transfer** | Chunked transfer with selective acknowledgment (SACK), retransmission, and reassembly over small-frame BLE radio |
| 5 | **Power-aware operation** | Discrete power modes governing scan duty cycle, advertisement interval, connection interval, concurrent connections, and transfer chunk size |
| 6 | **Deterministic cross-platform parity** | Identical lifecycle states, sealed error/exception category hierarchy, and one shared, fixed-size diagnostic code catalog with consistent severity tiers and payload shapes |

## Non-Functional Requirements

| Requirement | Constraint |
|-------------|------------|
| Offline operation | Zero connectivity required once permissions granted |
| Persisted state | Only trust pin (identity material + first/verified instants); no plaintext, no full identifiers |
| Pending state | In-memory only; does not survive process restart |
| Delivery outcomes | Explicit: success, in-progress, retrying, route-waiting, unreachable, trust-failure, timeout, unrecoverable-failure |
| Wire compatibility | Backward-compatible evolution; breaking changes require major version bump + migration |
| Performance budgets | See [Build & Quality Constraints](build-quality.md) |
| Runtime dependency | Maximum one Maven artifact: `kotlinx-coroutines-core`. Crypto via platform APIs or pure-Kotlin fallbacks |
| Test coverage | 100% line/branch for `:meshlink`; crypto validated against Wycheproof vectors |

---

## Quick Links

- [SPEC.md §1 — Full vision spec](../../SPEC.md#vision--product-pillars)
- [CONSTITUTION.md — Binding rules](../../CONSTITUTION.md)
- [PROJECT.md — Project summary](../../PROJECT.md)
