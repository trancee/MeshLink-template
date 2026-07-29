# MeshLink Technical Specification — Index

This directory contains **thin navigation facades** for each specification layer. Each page links to the authoritative content in [SPEC.md](../../SPEC.md), relevant ADRs, and machine-readable specs.

## Reference Documents

| Layer | Facade | SPEC.md Section | ADR(s) | Machine-Readable Spec |
|-------|--------|-----------------|--------|----------------------|
| Vision & Product Pillars | [vision.md](vision.md) | §1 | — | — |
| Architecture Overview | [architecture.md](architecture.md) | §2 | [Module Structure](../explanation/module-structure.md) | — |
| Core Data Models | — | §3 | [Data Model](../decisions/model/data-model.md) | [specs/data-models.yaml](../../specs/data-models.yaml) |
| Discovery & Identity | [discovery.md](discovery.md) | §4 | [Mesh Hash Derivation](../decisions/discovery/mesh-hash-derivation.md) | [specs/wire-frames.yaml#discovery_advertisement](../../specs/wire-frames.yaml) |
| Trust Model (TOFU) | [trust-model.md](trust-model.md) | §5 | [Crypto Design](../decisions/crypto/crypto-design.md) | [specs/enums.yaml](../../specs/enums.yaml), [specs/state-machines.yaml](../../specs/state-machines.yaml) |
| Transport Layer | [transport.md](transport.md) | §6 | [MTU Negotiation](../decisions/transport/mtu-negotiation.md) | — |
| Security Layer | [security.md](security.md) | §7 | [Crypto Design](../decisions/crypto/crypto-design.md), [Constant-Time](../decisions/crypto/constant-time-policy.md), [Replay Window](../decisions/crypto/replay-window.md), [Key Rotation](../decisions/crypto/key-rotation-propagation.md), [Error Hierarchy](../decisions/model/error-hierarchy.md) | [specs/enums.yaml](../../specs/enums.yaml), [specs/state-machines.yaml](../../specs/state-machines.yaml) |
| Routing Layer | [routing.md](routing.md) | §8 | [Routing Design](../decisions/routing/routing-design.md) | [specs/state-machines.yaml](../../specs/state-machines.yaml), [specs/enums.yaml](../../specs/enums.yaml) |
| Transfer Layer | [transfer.md](transfer.md) | §9 | [Data Model](../decisions/model/data-model.md) | [specs/data-models.yaml](../../specs/data-models.yaml), [specs/state-machines.yaml](../../specs/state-machines.yaml), [specs/wire-frames.yaml](../../specs/wire-frames.yaml) |
| Power Management | [power.md](power.md) | §10 | [Power Mode Behavior](../decisions/power/power-mode-behavior.md) | [specs/enums.yaml](../../specs/enums.yaml), [specs/settings.yaml](../../specs/settings.yaml) |
| Diagnostics & Events | [diagnostics.md](diagnostics.md) | §11 | [Callback Threading](../decisions/diagnostics/callback-threading.md) | [specs/diagnostic-events.yaml](../../specs/diagnostic-events.yaml), [specs/enums.yaml](../../specs/enums.yaml) |
| Build & Quality Constraints | [build-quality.md](build-quality.md) | §12 | — | [meshlink/build.gradle.kts](../../meshlink/build.gradle.kts) |
| Testing & Verification | [testing.md](testing.md) | §13 | — | — |
| Configuration Model | [settings.md](settings.md) | §14 | [DSL Design](../decisions/model/settings-model.md) | [specs/settings.yaml](../../specs/settings.yaml) |
| Future Work | [future-work.md](future-work.md) | §15 | [PQ-Hybrid](../decisions/crypto/pq-hybrid-candidate-matrix.md) | — |

## Machine-Readable Specs (Generated — Do Not Edit Manually)

Located in [`specs/`](../../specs/):

| File | Generated From | Purpose |
|------|----------------|---------|
| `enums.yaml` | `TypeModel.kt`, `PowerMode.kt` | All public enums with values/metadata |
| `data-models.yaml` | Model classes in `model/` | Data class schemas |
| `state-machines.yaml` | SPEC.md §5, §8, §9, §11 | State machine definitions |
| `wire-frames.yaml` | SPEC.md §4, §6, §8, §9 | Wire format definitions |
| `diagnostic-events.yaml` | `DiagnosticEvent.kt` | Diagnostic event catalog |
| `settings.yaml` | `MeshLinkSettings.kt` | Configuration DSL schemas |
| `cross-ref-index.yaml` | SPEC.md + ADRs + code | SPEC ↔ ADR ↔ Code traceability |

**Generation script**: `scripts/generate-specs.sh` (run at build time)

## Quick Links

- **Full Specification**: [SPEC.md](../../SPEC.md)
- **Constitution (Binding Rules)**: [CONSTITUTION.md](../../CONSTITUTION.md)
- **Agent Instructions**: [AGENTS.md](../../AGENTS.md)
- **Project Summary**: [PROJECT.md](../../PROJECT.md)
- **Documentation Guide**: [docs/README.md](../README.md)
