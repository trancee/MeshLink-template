# Privacy and Pseudonyms in MeshLink

## The Privacy Trade-Off

 MeshLink's discovery advertisement includes a **stable PeerFingerprint**:
 a 12-byte truncated SHA-256 hash of the concatenated Ed25519 and X25519
 public keys. This fingerprint is used solely to identify devices during
 BLE scan — it acts as a discovery hint, NOT as an authentication credential.

The trade-off:

- **Passive correlation**: An observer who sees the same PeerFingerprint
  across multiple BLE advertisements can link those sightings to the same
  physical device. This is more possible than with rotating pseudonyms.
- **Security**: The fingerprint is not the canonical trust identity. It
  is 96 bits (truncated from 256-bit hash). The public keys themselves are
  never advertised. Plaintext is never in advertisements. Session keys are
  established only after the Noise handshake completes.
- **Isolation**: The 16-bit mesh hash in the advertisement distinguishes
  different applications running on the same device, preventing cross-app
  discovery even if they share the same PeerIdentity.

## Why Stable Fingerprints (Not Rotating Pseudonyms)

MeshLink chose stable fingerprints over rotating pseudonyms for three reasons:

### 1. TOFU Trust Requires Persistent Identity

Trust On First Use pins the peer's identity keys on first successful
handshake. If fingerprints rotated, each new fingerprint would appear as a
"new" peer, requiring a fresh handshake and TOFU pinning every time the
pseudonym changed. This defeats the purpose of TOFU.

### 2. Key Rotation Is Already Managed

When a peer rotates its keys, the `KeyRotationAnnouncement` mechanism
propagates the new public key through the mesh. The stable PeerIdentity
ensures the TrustStore lookup succeeds during key rotation announcement.
If PeerIdentity changed with each key rotation, the TrustStore indexed by
PeerIdentity would become stale, breaking key rotation.

### 3. Small Mesh Size Makes Correlation Low-Risk

Practical mesh sizes are 10-20 peers. The 96-bit birthday bound (2^48)
makes accidental collision negligible for any mesh this size. Active
tracking by a determined adversary is a theoretical concern, not a practical
one for the intended use cases (neighborhood networks, IoT clusters,
offline collaboration).

## Mitigations

While stable fingerprints are the chosen approach, several mitigations
reduce the correlation risk:

| Mitigation | How |
|------------|-----|
| No public keys in ads | Only truncated 12-byte fingerprint is exposed |
| No plaintext in ads | All control-plane frames are AEAD-encrypted |
| Mesh hash isolation | Cross-app discovery is impossible |
| Session key establishment | After Noise handshake, all further communication is encrypted |
| Key rotation | Periodic rotation limits the window during which a compromised key can be used |
| TOFU pinning | First successful authenticates; subsequent mismatches are rejected |

## What Would Require a Change

The stable fingerprint approach would be revisited if:

- MeshLink is deployed in high-threat environments where passive correlation
  is a real concern (e.g., activists, journalists in hostile regions)
- Hardware-based pseudonym rotation becomes available on both Android and iOS
  BLE stacks
- A formal privacy threat model is created that shows active fingerprint
  correlation is a realistic attack for MeshLink's target use cases

At that point, a pseudonym rotation mechanism could be added that preserves
TOFU trust by binding the pseudonym to the PeerIdentity (which stays stable)
rather than to the keys.

## Related

- [Trust Model (TOFU)](../../SPEC.md#5-trust-model-tofu) — TOFU trust flow
- [Discovery & Identity](../../SPEC.md#4-discovery--identity) — advertisement format
- [Crypto Design ADR](../decisions/crypto/crypto-design.md) — key rotation protocol
- [Data Model ADR](../decisions/model/data-model.md) — PeerFingerprint design
