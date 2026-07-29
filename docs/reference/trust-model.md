# Trust Model (TOFU)

> **Specification**: [SPEC.md §5](../../SPEC.md#trust-model-tofu)  
> **Machine-readable**: [specs/enums.yaml](../../specs/enums.yaml), [specs/state-machines.yaml](../../specs/state-machines.yaml)  
> **Design rationale**: [Crypto Design](../decisions/crypto/crypto-design.md)

## Handshake Patterns

| Layer | First Contact | Post-TOFU Reconnect | End-to-End |
|-------|---------------|---------------------|------------|
| Pattern | Noise XX | Noise IK | Noise IX |
| Protocol | `Noise_XX_25519_ChaChaPoly_SHA256` | `Noise_IK_25519_ChaChaPoly_SHA256` | `Noise_IX_25519_ChaChaPoly_SHA256` |

## Trust Flow

```text
Discovery → GATT Connection → Noise_XX Handshake → INITIATED
                                                    ↓
                                            TOFU Pin (first success)
                                                    ↓
                                                    TRUSTED → TrustRecord stored
```

## Key Distribution via Route Updates

- Each peer's public key included in `ROUTE_UPDATE` encrypted payload
- Direct neighbor (Noise XX) learns neighbor's public key, includes in route updates
- Route updates propagate hop-by-hop; each relay re-advertises destination's public key
- Enables E2E IX handshake where origin knows destination's static key
- NX fallback used only when destination key not in routing table

## NX Fallback (Unknown Destination Key)

**Trigger**: Cold start discovery, key rotation lag, network partition

**Mitigations**:

- Rate limit: 3 attempts/minute/destination
- Timeout: 10s (vs 30s for IX)
- Full 64-byte public key (Ed25519 \|\| X25519) in payload — verified byte-for-byte
- 32-bit nonce replay protection
- Diagnostic flag: `handshake.fallback_used = true`

## Key Rotation

**Triggers**: Periodic timer (3 days default), manual API, security event

**Wire format** (`KEY_ROTATION` frame, plaintext but signed):

```flatbuffers
KeyRotationAnnouncement {
    identityKey: IdentityKey (NEW Ed25519, 32B)
    handshakeKey: HandshakeKey (NEW X25519, 32B)
    seqNo: UInt (always 1 — new crypto era)
    signature: ByteArray (64B, Ed25519 with OLD private key)
    reason: KeyRotationReason (PERIODIC | MANUAL | SECURITY_EVENT)
}
```

**Grace periods**:

- PERIODIC/MANUAL: `rotationGracePeriod` (default 1h) — old key accepted for in-flight sessions
- SECURITY_EVENT: `compromiseGracePeriod` (default 0) — immediate revocation

## Revocation

- Explicit API action required
- No silent re-trust on identity mismatch
- Stored trust records persist until revoked

---

## Quick Links

- [SPEC.md §5 — Full trust model](../../SPEC.md#trust-model-tofu)
- [Crypto Design ADR](../decisions/crypto/crypto-design.md)
- [Key Rotation Propagation ADR](../decisions/crypto/key-rotation-propagation.md)
- [Enums Spec](../../specs/enums.yaml)
- [State Machines Spec](../../specs/state-machines.yaml)
