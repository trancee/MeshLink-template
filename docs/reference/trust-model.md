# Trust Model (TOFU)

> **Specification**: [SPEC.md §5](../../SPEC.md#trust-model-tofu)  
> **Machine-readable**: [specs/codecs/enums.yaml](../../specs/codecs/enums.yaml), [specs/protocol/state-machines.yaml](../../specs/protocol/state-machines.yaml)
> **Design rationale**: [Crypto Design](../decisions/crypto/crypto-design.md)

## Handshake Patterns

| Layer | No Trusted Pin | Trusted Current Pin |
|-------|----------------|---------------------|
| Direct hop | `Noise_XX_25519_ChaChaPoly_SHA256` | `Noise_IK_25519_ChaChaPoly_SHA256` |
| Routed E2E | Routed `Noise_XX_25519_ChaChaPoly_SHA256` | Routed `Noise_IK_25519_ChaChaPoly_SHA256` |

A pinned mismatch fails closed. No application early data is sent during IK.

## First-Contact Identity Binding and Trust Flow

Noise XX carries an encrypted Ed25519-signed binding of protocol version,
`appHash`, stable PeerIdentity, current Ed25519/X25519 keys, and key generation.
The X25519 key must equal the Noise static key. The rotating advertisement
peerHint remains outside identity and trust.

```text
Discovery
    → GATT connection
    → Noise XX completes
    → signed identity binding validates
    → automatic TOFU pin
    → TRUSTED record stored
```

TOFU proves continuity after first contact, not prior real-world identity. Any
later identity or pinned-key mismatch fails closed until explicit reset.

## Key Distribution via Route Updates

- Route updates may carry signed candidate identity bindings and keys.
- Route-learned identity data is a hint until the destination has a trusted pin.
- Unpinned E2E first contact uses routed XX regardless of route hints.
- A trusted current pin permits routed IK.
- Route metadata never mutates trust by itself.

## Key Rotation

**Triggers**: Periodic timer (3 days default), manual API, security event

Each rotation creates a hop-encrypted `KeyRotationProof` containing stable
PeerIdentity/appHash, contiguous generations, new Ed25519/X25519 keys, reason,
an old-key continuity signature, and a new-key possession signature. Routing
SeqNo remains independent.

Proofs are retained for the installation lifetime. A peer that missed rotations
uses rotation-recovery XX to receive and validate the chain from its pin to the
current generation. Missing, rolled-back, or forked chains fail closed. The
application continues using the same PeerIdentity and never manages keys or
proofs.

**Grace periods**:

- PERIODIC/MANUAL: `rotationGracePeriod` (default 1h) — old key accepted for in-flight sessions
- SECURITY_EVENT: `compromiseGracePeriod` (default 0) — immediate revocation

## Trust Reset and Revocation

`resetTrust(peerIdentity)` forgets the current remote binding and permits a
future XX/automatic TOFU flow. `revokeTrust(peerIdentity)` persists a blocking
REVOKED record and rejects future XX/IK/rotation recovery. Only explicit reset
removes the block. Neither operation exposes or accepts keys.

---

## Quick Links

- [SPEC.md §5 — Full trust model](../../SPEC.md#trust-model-tofu)
- [Crypto Design ADR](../decisions/crypto/crypto-design.md)
- [Identity Binding and Fail-Closed ADR](../decisions/crypto/identity-binding-and-fail-closed.md)
- [Key Rotation Propagation ADR](../decisions/crypto/key-rotation-propagation.md)
- [Noise Session Renewal ADR](../decisions/crypto/noise-session-renewal.md)
- [Enums Spec](../../specs/codecs/enums.yaml)
- [State Machines Spec](../../specs/protocol/state-machines.yaml)
