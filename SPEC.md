# MeshLink Technical Specification

This document is the top-level specification index. The detailed reference specification has been split into per-layer documents under `docs/reference/`.

## Quick Reference

| Layer | Document | Key Decisions (ADRs) |
|-------|----------|---------------------|
| Vision & Product Pillars | [vision.md](docs/reference/vision.md) | — |
| Architecture Overview | [architecture.md](docs/reference/architecture.md) | [Module Structure](docs/explanation/module-structure.md) |
| Core Data Models | [specs/data-models.yaml](specs/data-models.yaml) | [Data Model](docs/decisions/model/data-model.md), [Mesh Size Limits](docs/decisions/model/mesh-size-limits.md), [Persistence Strategy](docs/decisions/storage/persistence-strategy.md) |
| Discovery & Identity | [discovery.md](docs/reference/discovery.md) | [Data Model](docs/decisions/model/data-model.md), [Mesh Hash Derivation](docs/decisions/discovery/mesh-hash-derivation.md) |
| Trust Model (TOFU) | [trust-model.md](docs/reference/trust-model.md) | [Crypto Design](docs/decisions/crypto/crypto-design.md) |
| Transport Layer | [transport.md](docs/reference/transport.md) | [MTU Negotiation](docs/decisions/transport/mtu-negotiation.md) |
| Security Layer | [security.md](docs/reference/security.md) | [Crypto Design](docs/decisions/crypto/crypto-design.md), [Constant-Time](docs/decisions/crypto/constant-time-policy.md), [Replay Window](docs/decisions/crypto/replay-window.md) |
| Routing Layer | [decisions/routing/routing-design.md](docs/decisions/routing/routing-design.md) | — |
| Transfer Layer | [transfer.md](docs/reference/transfer.md) | [Data Model](docs/decisions/model/data-model.md) |
| Power Management | [decisions/power/power-mode-behavior.md](docs/decisions/power/power-mode-behavior.md) | — |
| Diagnostics & Events | [diagnostics.md](docs/reference/diagnostics.md) | [Callback Threading](docs/decisions/diagnostics/callback-threading.md) |
| Build & Quality Constraints | [build-quality.md](docs/reference/build-quality.md) | — |
| Testing & Verification | [testing.md](docs/reference/testing.md) | — |
| Settings Model | [settings.md](docs/reference/settings.md) | [DSL Design](docs/decisions/model/meshlinksettings-dsl.md) |
| Future Work | [future-work.md](docs/reference/future-work.md) | [PQ-Hybrid](docs/decisions/crypto/pq-hybrid-candidate-matrix.md) |

## Machine-Readable Specs

For programmatic access, see `specs/`:

- `enums.yaml` — All public enums with values and metadata
- `data-models.yaml` — All data class schemas
- `state-machines.yaml` — State machine definitions
- `wire-frames.yaml` — Wire format definitions
- `diagnostic-events.yaml` — Diagnostic event catalog
- `settings.yaml` — Configuration DSL schemas
- `cross-ref-index.yaml` — SPEC ↔ ADR ↔ Code traceability

## SPEC.md vs ADRs

**SPEC.md** (this directory) is the top-level specification — a single document covering all layers. It is the primary reference for implementers.

**ADRs** (`docs/decisions/`) are design decision records for specific areas. Each ADR captures the *why* behind a decision. SPEC.md references the authoritative ADR for each decision via `[Decision: ...]` links.

When SPEC.md and an ADR conflict, the ADR is authoritative for the *decision*, but SPEC.md is authoritative for the *specification* (tables, state machines, parameter values). In practice, they should be consistent — if you find a discrepancy, file an issue.
