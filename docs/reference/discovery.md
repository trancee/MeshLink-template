# Discovery & Identity

> **Specification**: [SPEC.md §4](../../SPEC.md#discovery--identity)  
> **Design rationale**: [Connectable Advertisement](../decisions/discovery/connectable-advertisement.md), [Mesh Hash Derivation](../decisions/discovery/mesh-hash-derivation.md)

## Advertisement Format

MeshLink emits a connectable, undirected legacy BLE advertisement containing
two service UUIDs:

| Service UUID | Size | Description |
|--------------|------|-------------|
| Protocol marker | 32 bits | Private, unassigned `0x4D455348` (`"MESH"`); known scan filter |
| Discovery metadata | 128 bits | Dynamic UUID containing the 16-byte packed metadata |

The dynamic UUID contains:

| Field | Size | Description |
|-------|------|-------------|
| Protocol version | 3 bits | Current protocol version |
| Platform | 3 bits | `0=Android`, `1=iOS`, `2=Desktop`, `3-7=Reserved` |
| Power mode | 2 bits | `0=HIGH`, `1=MEDIUM`, `2=LOW`, `3=Reserved` |
| Mesh hash | 16 bits | Application isolation filter (FNV-1a of appId) |
| Capability flags | 8 bits | Bit 0 = L2CAP available; bits 1-7 reserved and zero |
| Peer hint | 12 bytes | Random rotating `peerHint`; short-lived unauthenticated discovery value |

The dynamic UUID is a fast path. If it is unavailable under platform background
restrictions, the fixed MeshLink GATT service exposes full PeerIdentity,
version, key generation, 16-bit PSM, and a fresh nonce. `peerHint` remains advertisement-only.
Trust is established only by the security handshake.

## Peer-Hint Lifecycle and Deduplication

A new CSPRNG peerHint is generated when advertising starts and at a uniformly
random best-effort interval from 10 through 20 minutes. It is not persisted or
bound to identity. Platform RPA rotation is independent, so no hard passive-
unlinkability guarantee is made.

Discovery attempts use `TransportHandle` and peerHint only as ephemeral indexes.
The same handle/hint never starts duplicate work. A changed handle with a mapped
hint may skip full identity resolution provisionally, but every new physical
connection still requires IK. Changed hints resolve through GATT and merge into
existing state only after authentication yields the same PeerIdentity.

## Privacy Trade-offs

| Aspect | Trade-off |
|--------|-----------|
| Rotating peer hint | Reduces installation-lifetime static identification but cannot guarantee continuous unlinkability because platform RPA rotation is independent |
| Protected | Full PeerIdentity and public keys are learned only after connection; Noise establishes trust |
| Isolation | `meshHash` filters discovery; 128-bit `appHash` enforces the handshake boundary |

---

## Quick Links

- [SPEC.md §4 — Full discovery spec](../../SPEC.md#discovery--identity)
- [Connectable Advertisement ADR](../decisions/discovery/connectable-advertisement.md)
- [Mesh Hash Derivation ADR](../decisions/discovery/mesh-hash-derivation.md)
- [Wire Frames Spec](../../specs/codecs/frames.yaml)
- [Peer Hint and Identity Races ADR](../decisions/discovery/peer-hint-and-identity-races.md)
- [Data Model ADR](../decisions/model/data-model.md#peerhint-is-a-rotating-advertisement-hint)
