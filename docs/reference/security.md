# Security Layer

> **Specification**: [SPEC.md §7](../../SPEC.md#7-security-layer)  
> **Design rationale**: [Crypto Design](../decisions/crypto/crypto-design.md), [Identity Binding and Fail-Closed](../decisions/crypto/identity-binding-and-fail-closed.md), [Constant-Time Policy](../decisions/crypto/constant-time-policy.md), [Replay Window](../decisions/crypto/replay-window.md), [Key Rotation Propagation](../decisions/crypto/key-rotation-propagation.md), [Error Hierarchy](../decisions/model/error-hierarchy.md)  
> **Machine-readable**: [specs/codecs/enums.yaml](../../specs/codecs/enums.yaml), [specs/protocol/state-machines.yaml](../../specs/protocol/state-machines.yaml)

## Platform-Specific Notes

### Android

- Crypto primitives prefer Android Keystore / `Cipher` / `KeyAgreement` / `Signature` APIs (API 28+)
- Fallback: pure-Kotlin implementations for API 26-27, validated against Wycheproof
- Private keys: Android Keystore AES-256-GCM wrapped, backup-excluded, `AfterFirstUnlock` protection
- `KeyGenParameterSpec.Builder().setUserAuthenticationRequired(true)` for high-security keys
- SELinux policies may restrict key access; test on target API levels

### iOS

- Crypto primitives prefer CryptoKit (iOS 13+) / Security.framework
- Private keys: Keychain items with `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`, non-synchronizable
- Secure Enclave backed keys preferred for Ed25519/X25519 (iOS 13+)
- `kSecUseAuthenticationUI: kSecUseAuthenticationUIFail` for silent operations
- App Sandbox and Data Protection enforce key isolation

## Quick Links

- [SPEC.md §7 — Full security spec](../../SPEC.md#7-security-layer)
- [Crypto Design ADR](../decisions/crypto/crypto-design.md)
- [Identity Binding and Fail-Closed ADR](../decisions/crypto/identity-binding-and-fail-closed.md)
- [Constant-Time Policy ADR](../decisions/crypto/constant-time-policy.md)
- [Replay Window ADR](../decisions/crypto/replay-window.md)
- [Key Rotation Propagation ADR](../decisions/crypto/key-rotation-propagation.md)
- [Error Hierarchy ADR](../decisions/model/error-hierarchy.md)
- [Enums Spec](../../specs/codecs/enums.yaml)
- [State Machines Spec](../../specs/protocol/state-machines.yaml)
- [Wycheproof Skill](../../.agents/skills/wycheproof/SKILL.md)
