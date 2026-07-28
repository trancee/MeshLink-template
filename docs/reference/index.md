# MeshLink Technical Specification — Index

This directory contains the split reference specification. The root `SPEC.md` now serves as an index linking to each layer's reference document.

## Reference Documents

| Layer | File | SPEC.md Section |
|-------|------|-----------------|
| Vision & Product Pillars | [vision.md](vision.md) | §1 |
| Architecture Overview | [architecture.md](architecture.md) | §2 |
| Core Data Models | [specs/data-models.yaml](../../specs/data-models.yaml) | §3 |
| Discovery & Identity | [discovery.md](discovery.md) | §4 |
| Trust Model (TOFU) | [trust-model.md](trust-model.md) | §5 |
| Transport Layer | [transport.md](transport.md) | §6 |
| Security Layer | [security.md](security.md) | §7 |
| Routing Layer | [decisions/routing/routing-design.md](../decisions/routing/routing-design.md) | §8 |
| Transfer Layer | [transfer.md](transfer.md) | §9 |
| Power Management | [decisions/power/power-mode-behavior.md](../decisions/power/power-mode-behavior.md) | §10 |
| Diagnostics & Events | [diagnostics.md](diagnostics.md) | §11 |
| Build & Quality Constraints | [build-quality.md](build-quality.md) | §12 |
| Testing & Verification | [testing.md](testing.md) | §13 |
| Settings Model | [settings.md](settings.md) | §14 |
| Future Work | [future-work.md](future-work.md) | §15 |

## Machine-Readable Specs

For programmatic access, see `../specs/`:

- `enums.yaml` — All public enums with values and metadata
- `data-models.yaml` — All data class schemas
- `state-machines.yaml` — State machine definitions
- `wire-frames.yaml` — Wire format definitions
- `diagnostic-events.yaml` — Diagnostic event catalog
- `settings.yaml` — Configuration DSL schemas
- `cross-ref-index.yaml` — SPEC ↔ ADR ↔ Code traceability
