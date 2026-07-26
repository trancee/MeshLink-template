# Discovery & Identity

> Source: [SPEC.md §4](../../SPEC.md#4-discovery--identity)

## 4.1 Advertisement Format

Single BLE advertisement packet containing:

| Field | Size | Description |
|-------|------|-------------|
| Fixed UUID | 4 bytes | `4d455348` ("MESH") |
| Protocol version | 3 bits | |
| Platform | 2 bits | |
| Power mode | 3 bits | Current power mode |
| Mesh hash | 16 bits | Application isolation filter |
| L2CAP PSM hint | 8 bits | Non-zero if CoC supported |
| PeerFingerprint | 12 bytes | SHA-256 truncated, discovery hint only |

## 4.2 Privacy Trade-offs

- **Stable PeerFingerprint**: Passive observers can correlate repeated sightings more easily than rotating pseudonyms
- **Protected**: Full public keys not advertised, plaintext never in ads, hop/e2e session keys established after discovery
- **Isolation**: Mesh hash derived from `appId` prevents cross-application discovery

[Decision: docs/explanation/privacy-pseudonyms.md]
