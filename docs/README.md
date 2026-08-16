# MeshLink Documentation

Documentation follows the [Diátaxis](https://diataxis.fr) framework. Each doc serves one of four needs.

## Structure

| Directory | Type | Serves |
|---|---|---|
| `docs/tutorials/` | Tutorial | Learning MeshLink hands-on (planned) |
| `docs/how-to/` | How-to guide | Accomplishing specific tasks |
| `docs/reference/` | Reference | Thin facades linking to SPEC.md + ADRs |
| `docs/explanation/` | Explanation | Understanding design decisions |
| `docs/decisions/` | ADR | Design rationale (why, not what) |
| `docs/rfcs/` | Reference | Vendored IETF specifications |
| `SPEC.md` | **Specification** | **Single source of truth for all layers** |
| `specs/codecs/` | Normative codec contracts | Authored MeshLink Wire Codec frames, enums, and models |
| `specs/protocol/` | Normative protocol contracts | Authored state machines, timing, and invariants |
| `specs/catalogs/` | Source-derived catalogs | Settings and diagnostics projections |
| `specs/traceability/` | Generated mapping | Specification-to-decision/code/test coverage |
| `specs/product/` | Planning | Authored scope, vision, and success criteria |
| `specs/epics/` | Planning | Authored epic-level story plans |
| `specs/tests/` | Planning | Authored test architecture and strategy |

## SPEC.md vs ADRs vs Reference Docs

**SPEC.md** (root) is the **single authoritative specification** — one document covering all layers (data model, discovery, trust, transport, security, routing, transfer, power, diagnostics, testing, configuration). It contains tables, state machines, parameter values, and wire formats.

**ADRs** (`docs/decisions/`) capture **design rationale only** — the *why* behind decisions, alternatives considered, trade-offs. They do NOT repeat specification content (tables, parameter values, wire formats).

**Reference docs** (`docs/reference/`) are **thin navigation facades** — one per layer, linking to the relevant SPEC.md section, ADR(s), and machine-readable spec file. They contain only platform-specific notes.

**Codec and protocol contracts** (`specs/codecs/`, `specs/protocol/`) are authored normative sources. **Catalogs and traceability** are source-derived projections checked by tooling. Planning artifacts are authored and never generated.

### Conflict Resolution

When documents conflict:

1. **CONSTITUTION.md** wins for binding engineering/governance rules.
2. **SPEC.md** wins for protocol behavior.
3. **`specs/codecs/`** wins for exact binary representation.
4. **`specs/protocol/`** wins for machine-readable transitions/timing.
5. **The API dump** wins for committed binary-compatibility baseline.
6. **ADRs** win for design rationale.
7. Catalogs and traceability are derived projections.

Implementation must conform to authoritative sources; a discrepancy is a defect.

## ADR Index (Rationale Only)

| Area | ADR | Key Rationale |
|---|---|---|
| Crypto: consolidated | `decisions/crypto/crypto-design.md` | Why Noise patterns, handshake state, key rotation, and E2E routing |
| Crypto: identity binding | `decisions/crypto/identity-binding-and-fail-closed.md` | Why first contact binds stable identity and all subsystems fail closed |
| Crypto: constant-time | `decisions/crypto/constant-time-policy.md` | Why all crypto ops must be constant-time |
| Crypto: private keys | `decisions/crypto/private-key-handling.md` | Why private keys stay opaque, device-only, and fail closed |
| Crypto: replay window | `decisions/crypto/replay-window.md` | Why sliding bitmap per RFC 9147 |
| Crypto: session renewal | `decisions/crypto/noise-session-renewal.md` | Why Noise sessions renew with jittered time and record limits |
| Crypto: PQ-hybrid | `decisions/crypto/pq-hybrid-candidate-matrix.md` | Why conservative hybrid (C2) |
| Routing: consolidated | `decisions/routing/routing-design.md` | Why additive cost, feasibility recovery, signed statements, encrypted control, and per-neighbor synchronization |
| Transport: GATT framing | `decisions/transport/gatt-channel-and-framing.md` | Why one duplex characteristic uses compact connection-local fragments |
| Transport: MTU | `decisions/transport/mtu-negotiation.md` | Why GATT control plane, L2CAP CoC data plane |
| Transport: background | `decisions/transport/background-operation.md` | Why background BLE is explicit, host-owned, and best effort |
| Power: mode behavior | `decisions/power/power-mode-behavior.md` | Why 3-mode model, grace periods, EU clamping |
| Public API | `decisions/api/public-api-and-lifecycle.md` | Why instances use explicit environments, state flows, and constrained runtime mutation |
| Diagnostics: delivery | `decisions/diagnostics/flow-delivery.md` | Why diagnostics use a bounded hot flow |
| Discovery: advertisement | `decisions/discovery/connectable-advertisement.md` | Why discovery is connectable and uses two service UUIDs |
| Discovery: mesh hash | `decisions/discovery/mesh-hash-derivation.md` | Why FNV-1a of appId |
| Discovery: peer hint races | `decisions/discovery/peer-hint-and-identity-races.md` | Why rotating hints and platform handles never replace PeerIdentity |
| Settings: DSL | `decisions/model/settings-model.md` | Why lambda DSL, nested builders, BCV impact |
| Model: data model | `decisions/model/data-model.md` | Why PeerIdentity is stable, PeerHint rotates, and Scoreboard is immutable |
| Model: error hierarchy | `decisions/model/error-hierarchy.md` | Why sealed exception hierarchy |
| Model: mesh size limits | `decisions/model/mesh-size-limits.md` | Why max 256 destination routes, bounded alternate candidates, and 8 peers typical |
| Model: persistence | `decisions/storage/persistence-strategy.md` | Why trust state only, no plaintext |
| Transfer: identifier | `decisions/transfer/transfer-identifier.md` | Why transfers use an origin-scoped 32-bit counter |
| Transfer: protocol | `decisions/transfer/payload-transfer-protocol.md` | Why finite payloads use manifests, acceptance, windowed SACK, and adaptive RTO |
| Transfer: identity | `decisions/transfer/payload-identity-and-naming.md` | Why payloads use contextual id and stable origin, distinct from transport source |
| Transfer: source/sink | `decisions/transfer/transfer-source-sink-contract.md` | Source rereads, sink ordering, idempotence, and ownership rules |
| Explanation: module structure | `explanation/module-structure.md` | Why 4 Gradle modules, reference/proof separation |
| Explanation: peer lifecycle | `explanation/peer-lifecycle.md` | Why 3-state model (Connected/Disconnected/Gone) |
| Explanation: wire codec | `explanation/why-meshlink-wire-codec.md` | Why MeshLink owns a custom FlatBuffers-inspired KMP format |

## Quick Links

- **Full Specification**: [`SPEC.md`](../SPEC.md)
- **Machine-Readable Specs**: [`specs/`](../specs/)
- **Constitution (Binding Rules)**: [`CONSTITUTION.md`](../CONSTITUTION.md)
- **Agent Instructions**: [`AGENTS.md`](../AGENTS.md)
- **Crypto API**: [`reference/meshlink-crypto-api.md`](reference/meshlink-crypto-api.md) — meshlink-crypto v0.1.1 usage guide
