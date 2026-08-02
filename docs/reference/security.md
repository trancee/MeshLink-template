# Security Layer

> **Specification**: [SPEC.md §7](../../SPEC.md#security-layer)  
> **Design rationale**: [Crypto Design](../decisions/crypto/crypto-design.md), [Identity Binding and Fail-Closed](../decisions/crypto/identity-binding-and-fail-closed.md), [Constant-Time Policy](../decisions/crypto/constant-time-policy.md), [Replay Window](../decisions/crypto/replay-window.md), [Key Rotation Propagation](../decisions/crypto/key-rotation-propagation.md), [Error Hierarchy](../decisions/model/error-hierarchy.md)

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

| Layer | No Trusted Pin | Trusted Current Pin |
|-------|----------------|---------------------|
| Direct hop | `Noise_XX_25519_ChaChaPoly_SHA256` | `Noise_IK_25519_ChaChaPoly_SHA256` |
| Routed E2E | Routed `Noise_XX_25519_ChaChaPoly_SHA256` | Routed `Noise_IK_25519_ChaChaPoly_SHA256` |

A pinned mismatch fails closed and never starts first-contact XX until explicit
trust reset. Route-learned candidate keys do not qualify as trusted pins.

## Identity Binding Terminology

- `meshHash`: 16-bit advertisement filter
- `appHash`: 128-bit domain-separated truncated SHA-256 hash of `appId`, bound into handshakes
- `handshakeHash`: Noise transcript hash `h`
- `peerHint`: random rotating 96-bit advertisement hint outside identity and trust

Direct first contact uses Noise XX with an encrypted, Ed25519-signed binding of
PeerIdentity, Ed25519/X25519 keys, key generation, protocol version, and appHash.
The rotating peerHint is not part of this binding. Automatic TOFU occurs only after the complete binding and
Noise transcript validate.

## Fail-Closed Rules

- Malformed or incompatible input is rejected before state mutation.
- Pinned identity/key mismatch never falls back to first-contact TOFU.
- Decrypt/sign/verify failures terminate the affected operation.
- No fallback to plaintext, stale keys, partially validated state, or silent security downgrade.
- A specified fallback must preserve security properties, be bounded and observable, and have dedicated tests.
- Failure is contained to the smallest safe scope and emits a typed, redacted reason.
- All cryptographic field operations and comparisons **MUST** be constant-time.

## Provider and Private-Key Boundary

Each primitive prefers the platform implementation only after once-per-process
known-answer and negative tests before advertising; a validated pure-Kotlin
implementation is the specific fallback. Storage also passes an ephemeral
private-key generate/load/use round trip. Fallback failure blocks startup.
Private keys remain opaque provider-owned handles and never enter public APIs,
strings, logs, exceptions, diagnostics, wire records, or reports.

Android wraps fallback/exportable keys with an Android Keystore AES-GCM key in
backup-excluded atomic storage. iOS uses non-synchronizable,
AfterFirstUnlockThisDeviceOnly Keychain items. Before first unlock after reboot,
MeshLink remains inactive rather than weakening key protection.

## Error Hierarchy (Sealed, commonMain)

```kotlin
sealed class MeshLinkException : Exception() {
    class ConfigurationException : MeshLinkException()
    class LifecycleException : MeshLinkException()
    class PermissionException : MeshLinkException()
    class BluetoothException : MeshLinkException()
    class StorageException : MeshLinkException()
    class CryptoException : MeshLinkException()
    class TrustException : MeshLinkException()
    class RoutingException : MeshLinkException()
    class TransferException : MeshLinkException()
}
```

All public immediate command failures use typed `MeshLinkException` subtypes with
explicit stable `ErrorCode` values (see SPEC §7.6). Platform exceptions are
wrapped at the boundary and never leak to consumers.

---

## Quick Links

- [SPEC.md §7 — Full security spec](../../SPEC.md#security-layer)
- [Crypto Design ADR](../decisions/crypto/crypto-design.md)
- [Identity Binding and Fail-Closed ADR](../decisions/crypto/identity-binding-and-fail-closed.md)
- [Constant-Time Policy ADR](../decisions/crypto/constant-time-policy.md)
- [Private-Key Handling ADR](../decisions/crypto/private-key-handling.md)
- [Replay Window ADR](../decisions/crypto/replay-window.md)
- [Noise Session Renewal ADR](../decisions/crypto/noise-session-renewal.md)
- [Key Rotation Propagation ADR](../decisions/crypto/key-rotation-propagation.md)
- [Error Hierarchy ADR](../decisions/model/error-hierarchy.md)
- [Wycheproof Skill](../../.agents/skills/wycheproof/SKILL.md)
