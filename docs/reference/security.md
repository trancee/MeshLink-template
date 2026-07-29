# Security Layer

> **Specification**: [SPEC.md §7](../../SPEC.md#security-layer)  
> **Design rationale**: [Crypto Design](../decisions/crypto/crypto-design.md), [Constant-Time Policy](../decisions/crypto/constant-time-policy.md), [Replay Window](../decisions/crypto/replay-window.md), [Key Rotation Propagation](../decisions/crypto/key-rotation-propagation.md), [Error Hierarchy](../decisions/model/error-hierarchy.md)

## Crypto Primitives (All Validated Against Wycheproof)

| Primitive | Standard | Wycheproof Vectors |
|-----------|----------|-------------------|
| X25519 | RFC 7748 | 518 (264 valid + 254 acceptable) |
| Ed25519 | RFC 8032 | 150 (88 valid + 62 invalid) |
| ChaCha20-Poly1305 | RFC 8439 | 325 (256 valid + 69 invalid) |
| HKDF-SHA256 | RFC 5869 | 86 (83 valid + 3 invalid) |
| HMAC-SHA256 | RFC 2104 | 174 (66 valid + 108 invalid) |
| SHA-256 | RFC 6234 | Covered via other primitives |

## Handshake Patterns

| Layer | First Contact | Post-TOFU Reconnect | End-to-End |
|-------|---------------|---------------------|------------|
| Pattern | `Noise_XX` | `Noise_IK` | `Noise_IX` |
| Protocol | `Noise_XX_25519_ChaChaPoly_SHA256` | `Noise_IK_25519_ChaChaPoly_SHA256` | `Noise_IX_25519_ChaChaPoly_SHA256` |

**NX Fallback** (destination key unknown): `Noise_NX_25519_ChaChaPoly_SHA256` with full 64-byte public key in payload, rate-limited (3/min), 10s timeout, 32-bit nonce replay protection.

## Fail-Closed Rules

- Malformed/untrusted input **never** surfaces private keys in logs
- Invalid X25519 public keys fail before HKDF derivation
- Decrypt/sign/verify failures stop operation immediately
- No fallback to plaintext or cached secrets
- All cryptographic field operations and comparisons **MUST** be constant-time

## Android Crypto Constraints

- API 26-32: Runtime checks for X25519/XDH and ChaCha20-Poly1305 availability
- Pure-Kotlin fallback implementations for older devices
- Ed25519 fallback with constant-time arithmetic

## Error Hierarchy (Sealed, commonMain)

```kotlin
sealed class MeshLinkError : Exception()
sealed class SecurityError : MeshLinkError()
sealed class TrustError : SecurityError()
sealed class CryptoError : SecurityError()
sealed class TransportError : MeshLinkError()
sealed class RoutingError : MeshLinkError()
sealed class TransferError : MeshLinkError()
```

Platform exceptions wrapped at boundary — never leak to consumers.

---

## Quick Links

- [SPEC.md §7 — Full security spec](../../SPEC.md#security-layer)
- [Crypto Design ADR](../decisions/crypto/crypto-design.md)
- [Constant-Time Policy ADR](../decisions/crypto/constant-time-policy.md)
- [Replay Window ADR](../decisions/crypto/replay-window.md)
- [Key Rotation Propagation ADR](../decisions/crypto/key-rotation-propagation.md)
- [Error Hierarchy ADR](../decisions/model/error-hierarchy.md)
- [Wycheproof Skill](../../.agents/skills/wycheproof/SKILL.md)
