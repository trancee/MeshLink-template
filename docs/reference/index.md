# MeshLink Technical Specification — Index

This directory contains the split reference specification. The root `SPEC.md` now serves as an index linking to each layer's reference document.

## Reference Documents

| Layer | File | SPEC.md Section |
|-------|------|-----------------|
| Vision & Product Pillars | [01-vision.md](01-vision.md) | §1 |
| Architecture Overview | [02-architecture.md](02-architecture.md) | §2 |
| Core Data Models | [03-data-models.md](03-data-models.md) | §3 |
| Discovery & Identity | [04-discovery.md](04-discovery.md) | §4 |
| Trust Model (TOFU) | [05-trust-model.md](05-trust-model.md) | §5 |
| Transport Layer | [06-transport.md](06-transport.md) | §6 |
| Security Layer | [07-security.md](07-security.md) | §7 |
| Routing Layer | [08-routing.md](08-routing.md) | §8 |
| Transfer Layer | [09-transfer.md](09-transfer.md) | §9 |
| Power Management | [10-power-management.md](10-power-management.md) | §10 |
| Diagnostics & Events | [11-diagnostics.md](11-diagnostics.md) | §11 |
| Build & Quality Constraints | [12-build-quality.md](12-build-quality.md) | §12 |
| Testing & Verification | [13-testing.md](13-testing.md) | §13 |
| Settings Model | [14-settings.md](14-settings.md) | §14 |
| Future Work | [15-future-work.md](15-future-work.md) | §15 |

## Machine-Readable Specs

For programmatic access, see `../specs/`:

- `enums.yaml` — All public enums with values and metadata
- `data_models.yaml` — All data class schemas
- `state_machines.yaml` — State machine definitions
- `wire_frames.yaml` — Wire format definitions
- `diagnostic_events.yaml` — Diagnostic event catalog
- `settings.yaml` — Configuration DSL schemas
- `cross_ref_index.yaml` — SPEC ↔ ADR ↔ Code traceability
