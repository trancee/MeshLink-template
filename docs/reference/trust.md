# Trust Model (TOFU)

> **Specification**: [SPEC.md §5](../../SPEC.md#trust-model-tofu)  
> **Design rationale**: [Crypto Design](../decisions/crypto/crypto-design.md), [Identity Binding and Fail-Closed](../decisions/crypto/identity-binding-and-fail-closed.md), [Key Rotation Propagation](../decisions/crypto/key-rotation-propagation.md), [Noise Session Renewal](../decisions/crypto/noise-session-renewal.md)  
> **Machine-readable**: [specs/codecs/enums.yaml](../../specs/codecs/enums.yaml), [specs/protocol/state-machines.yaml](../../specs/protocol/state-machines.yaml)

## Platform-Specific Notes

### Android

- Trust records persisted in `EncryptedSharedPreferences` with Android Keystore-backed master key
- Key rotation proofs stored in same encrypted prefs; migration on app update
- `resetTrust()` / `revokeTrust()` delete or mark records immediately
- KeyStore `StrongBox` preferred for key generation on supported devices (API 28+)

### iOS

- Trust records persisted in Keychain with `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`
- Key rotation proofs stored as separate Keychain items linked by peer identity
- `resetTrust()` / `revokeTrust()` use `SecItemDelete` / `SecItemUpdate`
- Secure Enclave used for Ed25519/X25519 key generation where available (iOS 13+)
- Keychain sync disabled (`kSecAttrSynchronizable: false`)

## Quick Links

- [SPEC.md §5 — Full trust model](../../SPEC.md#trust-model-tofu)
- [Crypto Design ADR](../decisions/crypto/crypto-design.md)
- [Identity Binding and Fail-Closed ADR](../decisions/crypto/identity-binding-and-fail-closed.md)
- [Crypto API: meshlink-crypto usage guide](meshlink-crypto-api.md)
- [Enums Spec](../../specs/codecs/enums.yaml)
- [Noise Session Renewal ADR](../decisions/crypto/noise-session-renewal.md)
- [State Machines Spec](../../specs/protocol/state-machines.yaml)
