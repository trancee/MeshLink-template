# Discovery & Identity

> **Specification**: [SPEC.md §4](../../SPEC.md#discovery--identity)  
> **Design rationale**: [Mesh Hash Derivation](../decisions/discovery/mesh-hash-derivation.md)

## Advertisement Format

Single BLE advertisement packet:

| Field | Size | Description |
|-------|------|-------------|
| Fixed UUID | 4 bytes | `4d455348` ("MESH" ASCII) |
| Protocol version | 3 bits | Current protocol version |
| Platform | 2 bits | `0=Android`, `1=iOS`, `2=Desktop`, `3=Reserved` |
| Power mode | 3 bits | `0=HIGH`, `1=MEDIUM`, `2=LOW`, `3-7=Reserved` |
| Mesh hash | 16 bits | Application isolation filter (FNV-1a of appId) |
| L2CAP PSM hint | 8 bits | Assigned PSM 0x0080–0x00FF; `0` = CoC not supported |
| PeerFingerprint | 12 bytes | SHA-256(Ed25519Pub \|\| X25519Pub) truncated to 96 bits — discovery hint only |

## Privacy Trade-offs

| Aspect | Trade-off |
|--------|-----------|
| Stable PeerFingerprint | Passive observers can correlate repeated sightings more easily than rotating pseudonyms |
| Protected | Full public keys not advertised; plaintext never in ads; hop/E2E session keys post-discovery |
| Isolation | Mesh hash from `appId` prevents cross-application discovery |

---

## Quick Links

- [SPEC.md §4 — Full discovery spec](../../SPEC.md#discovery--identity)
- [Mesh Hash Derivation ADR](../decisions/discovery/mesh-hash-derivation.md)
- [Wire Frames Spec](../../specs/wire-frames.yaml)
- [Data Model ADR](../decisions/model/data-model.md#peer-fingerprint-is-truncated-discovery-hint)
