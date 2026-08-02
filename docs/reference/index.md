# MeshLink Technical Specification — Index

This directory contains **thin navigation facades** for each specification layer. Each page links to the authoritative content in [SPEC.md](../../SPEC.md), relevant ADRs, and machine-readable specs.

## Reference Documents

| Layer | Facade | SPEC.md Section | ADR(s) | Machine-Readable Spec |
|-------|--------|-----------------|--------|----------------------|
| Vision & Product Pillars | [vision.md](vision.md) | §1 | — | — |
| Architecture Overview | [architecture.md](architecture.md) | §2 | [Module Structure](../explanation/module-structure.md) | — |
| Core Data Models | — | §3 | [Data Model](../decisions/model/data-model.md) | [specs/codecs/models.yaml](../../specs/codecs/models.yaml) |
| Discovery & Identity | [discovery.md](discovery.md) | §4 | [Connectable Advertisement](../decisions/discovery/connectable-advertisement.md), [Mesh Hash Derivation](../decisions/discovery/mesh-hash-derivation.md), [Peer Hints and Identity Races](../decisions/discovery/peer-hint-and-identity-races.md) | [specs/codecs/frames.yaml#discovery_advertisement](../../specs/codecs/frames.yaml) |
| Trust Model (TOFU) | [trust-model.md](trust-model.md) | §5 | [Crypto Design](../decisions/crypto/crypto-design.md) | [specs/codecs/enums.yaml](../../specs/codecs/enums.yaml), [specs/protocol/state-machines.yaml](../../specs/protocol/state-machines.yaml) |
| Transport Layer | [transport.md](transport.md) | §6 | [GATT Channel and Framing](../decisions/transport/gatt-channel-and-framing.md), [MTU Negotiation](../decisions/transport/mtu-negotiation.md), [Background Operation](../decisions/transport/background-operation.md) | — |
| Security Layer | [security.md](security.md) | §7 | [Crypto Design](../decisions/crypto/crypto-design.md), [Identity Binding and Fail-Closed](../decisions/crypto/identity-binding-and-fail-closed.md), [Constant-Time](../decisions/crypto/constant-time-policy.md), [Private-Key Handling](../decisions/crypto/private-key-handling.md), [Replay Window](../decisions/crypto/replay-window.md), [Session Renewal](../decisions/crypto/noise-session-renewal.md), [Key Rotation](../decisions/crypto/key-rotation-propagation.md), [Error Hierarchy](../decisions/model/error-hierarchy.md) | [specs/codecs/enums.yaml](../../specs/codecs/enums.yaml), [specs/protocol/state-machines.yaml](../../specs/protocol/state-machines.yaml) |
| Routing Layer | [routing.md](routing.md) | §8 | [Routing Design](../decisions/routing/routing-design.md) | [specs/protocol/state-machines.yaml](../../specs/protocol/state-machines.yaml), [specs/codecs/enums.yaml](../../specs/codecs/enums.yaml) |
| Transfer Layer | [transfer.md](transfer.md) | §9 | [Data Model](../decisions/model/data-model.md), [Transfer Identifier](../decisions/transfer/transfer-identifier.md), [Payload Transfer Protocol](../decisions/transfer/payload-transfer-protocol.md) | [specs/codecs/models.yaml](../../specs/codecs/models.yaml), [specs/protocol/state-machines.yaml](../../specs/protocol/state-machines.yaml), [specs/codecs/frames.yaml](../../specs/codecs/frames.yaml) |
| Power Management | [power.md](power.md) | §10 | [Power Mode Behavior](../decisions/power/power-mode-behavior.md) | [specs/codecs/enums.yaml](../../specs/codecs/enums.yaml), [specs/catalogs/settings.yaml](../../specs/catalogs/settings.yaml) |
| Diagnostics & Events | [diagnostics.md](diagnostics.md) | §11 | [Diagnostic Flow Delivery](../decisions/diagnostics/flow-delivery.md), [Public API and Lifecycle](../decisions/api/public-api-and-lifecycle.md) | [specs/catalogs/diagnostic-events.yaml](../../specs/catalogs/diagnostic-events.yaml), [specs/codecs/enums.yaml](../../specs/codecs/enums.yaml) |
| Build & Quality Constraints | [build-quality.md](build-quality.md) | §12 | — | [meshlink/build.gradle.kts](../../meshlink/build.gradle.kts) |
| Testing & Verification | [testing.md](testing.md) | §13 | — | — |
| Configuration Model | [settings.md](settings.md) | §14 | [DSL Design](../decisions/model/settings-model.md) | [specs/catalogs/settings.yaml](../../specs/catalogs/settings.yaml) |
| Future Work | [future-work.md](future-work.md) | §15 | [PQ-Hybrid](../decisions/crypto/pq-hybrid-candidate-matrix.md) | — |

## Machine-Readable Specifications

Located in [`specs/`](../../specs/):

| Path | Ownership | Purpose |
|------|-----------|---------|
| `codecs/frames.yaml` | Authored normative | MeshLink Wire Codec frames and fields |
| `codecs/enums.yaml` | Authored normative | Explicit enum codes and unknown handling |
| `codecs/models.yaml` | Authored normative | Reusable encoded values |
| `protocol/state-machines.yaml` | Authored normative | State transitions, timing, and invariants |
| `catalogs/diagnostic-events.yaml` | Source-derived | Diagnostic event catalog |
| `catalogs/settings.yaml` | Source-derived | Configuration/default catalog |
| `traceability/specification-map.yaml` | Generated | SPEC ↔ ADR ↔ codec ↔ code ↔ test map |
| `product/`, `epics/`, `tests/` | Authored planning | Scope, implementation stories, and test architecture |

## Quick Links

- **Full Specification**: [SPEC.md](../../SPEC.md)
- **Constitution (Binding Rules)**: [CONSTITUTION.md](../../CONSTITUTION.md)
- **Agent Instructions**: [AGENTS.md](../../AGENTS.md)
- **Project Summary**: [PROJECT.md](../../PROJECT.md)
- **Documentation Guide**: [docs/README.md](../README.md)
