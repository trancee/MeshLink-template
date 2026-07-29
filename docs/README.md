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
| `specs/` | Machine-readable | **Generated** from code + SPEC.md (do not edit manually) |

## SPEC.md vs ADRs vs Reference Docs

**SPEC.md** (root) is the **single authoritative specification** — one document covering all layers (data model, discovery, trust, transport, security, routing, transfer, power, diagnostics, testing, configuration). It contains tables, state machines, parameter values, and wire formats.

**ADRs** (`docs/decisions/`) capture **design rationale only** — the *why* behind decisions, alternatives considered, trade-offs. They do NOT repeat specification content (tables, parameter values, wire formats).

**Reference docs** (`docs/reference/`) are **thin navigation facades** — one per layer, linking to the relevant SPEC.md section, ADR(s), and machine-readable spec file. They contain only platform-specific notes.

**Machine-readable specs** (`specs/`) are **generated** from source code and SPEC.md at build time. Do not edit manually.

### Conflict Resolution

When documents conflict:

1. **SPEC.md** wins for specification (what the system does)
2. **ADRs** win for design rationale (why it was decided)
3. **Code** wins for implementation (what actually runs)

If you find a discrepancy, file an issue.

## ADR Index (Rationale Only)

| Area | ADR | Key Rationale |
|---|---|---|
| Crypto: consolidated | `decisions/crypto/crypto-design.md` | Why Noise XX/IK/IX/NX, handshake state machine, key rotation, E2E routing |
| Crypto: constant-time | `decisions/crypto/constant-time-policy.md` | Why all crypto ops must be constant-time |
| Crypto: replay window | `decisions/crypto/replay-window.md` | Why sliding bitmap per RFC 9147 |
| Crypto: PQ-hybrid | `decisions/crypto/pq-hybrid-candidate-matrix.md` | Why conservative hybrid (C2) |
| Routing: consolidated | `decisions/routing/routing-design.md` | Why destination self-reports seqno, Hello/IHU removal, digest resync, always-encrypted metadata, composite metric |
| Transport: MTU | `decisions/transport/mtu-negotiation.md` | Why GATT control plane, L2CAP CoC data plane |
| Power: mode behavior | `decisions/power/power-mode-behavior.md` | Why 3-mode model, grace periods, EU clamping |
| Diagnostics: threading | `decisions/diagnostics/callback-threading.md` | Why coroutine dispatcher for callbacks |
| Discovery: mesh hash | `decisions/discovery/mesh-hash-derivation.md` | Why FNV-1a of appId |
| Settings: DSL | `decisions/model/settings-model.md` | Why lambda DSL, nested builders, BCV impact |
| Model: data model | `decisions/model/data-model.md` | Why PeerIdentity stable, PeerFingerprint truncated, Scoreboard immutable |
| Model: error hierarchy | `decisions/model/error-hierarchy.md` | Why sealed exception hierarchy |
| Model: mesh size limits | `decisions/model/mesh-size-limits.md` | Why max 256 route entries, 8 peers typical |
| Model: persistence | `decisions/storage/persistence-strategy.md` | Why trust state only, no plaintext |
| Explanation: module structure | `explanation/module-structure.md` | Why 4 Gradle modules, reference/proof separation |
| Explanation: peer lifecycle | `explanation/peer-lifecycle.md` | Why 3-state model (Connected/Disconnected/Gone) |
| Explanation: FlatBuffers | `explanation/why-pure-kotlin-flatbuffers.md` | Why pure-Kotlin FlatBuffers over CBOR |

## Quick Links

- **Full Specification**: [`SPEC.md`](../SPEC.md)
- **Machine-Readable Specs**: [`specs/`](../specs/)
- **Constitution (Binding Rules)**: [`CONSTITUTION.md`](../CONSTITUTION.md)
- **Agent Instructions**: [`AGENTS.md`](../AGENTS.md)
