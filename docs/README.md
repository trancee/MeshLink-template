# MeshLink Documentation

Documentation follows the [Diátaxis](https://diataxis.fr) framework. Each doc serves one of four needs.

## Structure

| Directory | Type | Serves |
|---|---|---|
| `docs/tutorials/` | Tutorial | Learning MeshLink hands-on (planned) |
| `docs/how-to/` | How-to guide | Accomplishing specific tasks |
| `docs/reference/` | Reference | API shape, config, error codes, device matrix |
| `docs/explanation/` | Explanation | Understanding design decisions |
| `docs/decisions/` | ADR | How decisions were reached (internal) |
| `docs/rfcs/` | Reference | Vendored IETF specifications |

## SPEC.md vs ADRs

**SPEC.md** (root) is the top-level specification — a single document covering all layers (data model, discovery, trust, transport, security, routing, transfer, power, diagnostics, testing, configuration). It is the primary reference for implementers.

**ADRs** (`docs/decisions/`) are design decision records for specific areas. Each ADR captures the *why* behind a decision. SPEC.md references the authoritative ADR for each decision via `[Decision: ...]` links.

When SPEC.md and an ADR conflict, the ADR is authoritative for the *decision*, but SPEC.md is authoritative for the *specification* (tables, state machines, parameter values). In practice, they should be consistent — if you find a discrepancy, file an issue.

## ADR Index

| Area | ADR | Key Decision |
|---|---|---|
| Data model | `decisions/model/data-model.md` | PeerIdentity stable/random, PeerFingerprint truncated hint, Scoreboard immutable dynamic bitfield |
| **Crypto: consolidated** | `decisions/crypto/crypto-design.md` | **IX handshake, NX fallback, session state machine, key rotation, E2E routing** |
| Crypto: vector policy | `decisions/crypto/vector-policy.md` | Wycheproof corpus classification, fail-closed rules |
| Crypto: Android fallback | `decisions/crypto/android-crypto-fallback-proof.md` | Ed25519 fallback exists, X25519/ChaCha20 need implementation |
| Crypto: PQ-hybrid | `decisions/crypto/pq-hybrid-candidate-matrix.md` | C2 (conservative + staged extension) recommended |
| **Routing: consolidated** | `decisions/routing/routing-design.md` | **Destination self-reports seqno, digest→full-table, always-encrypted metadata, composite metric (RSSI+flags)** |
| Transport: GATT/L2CAP | `decisions/transport/gatt-l2cap-transport-selection.md` | GATT=control plane, L2CAP CoC=data plane, fallback rules |
| Wire: format spec | `decisions/wire/wire-format-spec.md` | Frame type reference table |
| Power: mode behavior | `decisions/power/power-mode-behavior.md` | 3-mode model, grace periods, EU clamping |
| Explanation: module structure | `explanation/module-structure.md` | 4 Gradle modules, why reference/proof are separate |
| Explanation: peer lifecycle | `explanation/peer-lifecycle.md` | 3-state model (Connected/Disconnected/Gone), grace periods |
| Explanation: FlatBuffers | `explanation/why-pure-kotlin-flatbuffers.md` | Why pure-Kotlin FlatBuffers codec |
