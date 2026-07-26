# MeshLink Technical Specification

This document is the top-level specification index. The detailed reference specification has been split into per-layer documents under `docs/reference/`.

## Quick Reference

| Layer | Document | Key Decisions (ADRs) |
|-------|----------|---------------------|
| Vision & Product Pillars | [01-vision.md](docs/reference/01-vision.md) | — |
| Architecture Overview | [02-architecture.md](docs/reference/02-architecture.md) | [Module Structure](docs/explanation/module-structure.md) |
| Core Data Models | [03-data-models.md](docs/reference/03-data-models.md) | [Data Model](docs/decisions/model/data-model.md), [Power Mode](docs/decisions/power/power-mode-behavior.md) |
| Discovery & Identity | [04-discovery.md](docs/reference/04-discovery.md) | [Data Model](docs/decisions/model/data-model.md) |
| Trust Model (TOFU) | [05-trust-model.md](docs/reference/05-trust-model.md) | [Crypto Design](docs/decisions/crypto/crypto-design.md) |
| Transport Layer | [06-transport.md](docs/reference/06-transport.md) | [GATT/L2CAP](docs/decisions/transport/gatt-l2cap-transport-selection.md) |
| Security Layer | [07-security.md](docs/reference/07-security.md) | [Crypto Design](docs/decisions/crypto/crypto-design.md), [Vector Policy](docs/decisions/crypto/vector-policy.md) |
| Routing Layer | [08-routing.md](docs/reference/08-routing.md) | [Routing Design](docs/decisions/routing/routing-design.md) |
| Transfer Layer | [09-transfer.md](docs/reference/09-transfer.md) | [Data Model](docs/decisions/model/data-model.md), [Wire Format](docs/decisions/wire/wire-format-spec.md) |
| Power Management | [10-power-management.md](docs/reference/10-power-management.md) | [Power Mode](docs/decisions/power/power-mode-behavior.md) |
| Diagnostics & Events | [11-diagnostics.md](docs/reference/11-diagnostics.md) | — |
| Build & Quality | [12-build-quality.md](docs/reference/12-build-quality.md) | — |
| Testing & Verification | [13-testing.md](docs/reference/13-testing.md) | — |
| Settings Model | [14-settings.md](docs/reference/14-settings.md) | [Data Model](docs/decisions/model/data-model.md) |
| Future Work | [15-future-work.md](docs/reference/15-future-work.md) | [PQ-Hybrid](docs/decisions/crypto/pq-hybrid-candidate-matrix.md) |

## Machine-Readable Specs

For programmatic access, see `specs/`:

- `enums.yaml` — All public enums with values and metadata
- `data_models.yaml` — All data class schemas
- `state_machines.yaml` — State machine definitions
- `wire_frames.yaml` — Wire format definitions
- `diagnostic_events.yaml` — Diagnostic event catalog
- `settings.yaml` — Configuration DSL schemas
- `cross_ref_index.yaml` — SPEC ↔ ADR ↔ Code traceability

## SPEC.md vs ADRs

**SPEC.md** (this directory) is the top-level specification — a single document covering all layers. It is the primary reference for implementers.

**ADRs** (`docs/decisions/`) are design decision records for specific areas. Each ADR captures the *why* behind a decision. SPEC.md references the authoritative ADR for each decision via `[Decision: ...]` links.

When SPEC.md and an ADR conflict, the ADR is authoritative for the *decision*, but SPEC.md is authoritative for the *specification* (tables, state machines, parameter values). In practice, they should be consistent — if you find a discrepancy, file an issue.
