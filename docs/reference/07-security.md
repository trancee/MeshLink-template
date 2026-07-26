# Security Layer

> Source: [SPEC.md §7](../../SPEC.md#7-security-layer)

## 7.1 Crypto Primitives (Required)

All validated against Wycheproof test vectors:

| Primitive | Standard | Test Vector Source | Coverage |
|-----------|----------|-------------------|----------|
| X25519 | RFC 7748 | Wycheproof | 518 vectors (264 valid + 254 acceptable) |
| Ed25519 | RFC 8032 | Wycheproof | 150 vectors (88 valid + 62 invalid) |
| ChaCha20-Poly1305 | RFC 8439 | Wycheproof | 325 vectors (256 valid + 69 invalid) |
| HKDF-SHA256 | RFC 5869 | Wycheproof | 86 vectors (83 valid + 3 invalid) |
| HMAC-SHA256 | RFC 2104 | Wycheproof | 174 vectors (66 valid + 108 invalid) |
| SHA-256 | RFC 6234 | RFC-style regression corpus | Covered via other primitives' Wycheproof vectors |

[Decision: docs/decisions/crypto/vector-policy.md]

## 7.2 Handshake Patterns

- **Link layer (first contact):** `Noise_XX_25519_ChaChaPoly_SHA256` - mutual authentication for initial TOFU
- **Link layer (post-TOFU reconnect):** `Noise_IK_25519_ChaChaPoly_SHA256` - proactive mutual auth + 0-RTT when both peers hold pinned keys (1 round trip vs XX's 1.5)
- **E2E layer:** `Noise_IX_25519_ChaChaPoly_SHA256` - origin knows destination key
- **E2E fallback:** `Noise_NX_25519_ChaChaPoly_SHA256` with full public key verification when destination key unknown

## 7.3 Fail-Closed Rules

- Malformed/untrusted input never surfaces private keys in logs
- Invalid X25519 public keys fail before HKDF derivation
- Decrypt/sign/verify failures stop operation immediately
- No fallback to plaintext or cached secrets
- All cryptographic field operations and comparisons MUST implement constant-time algorithms to prevent timing side-channel attacks

## 7.4 Android Crypto Constraints

- API 26-32 runtime checks for X25519/XDH and ChaCha20-Poly1305
- Pure-Kotlin fallback implementations for older devices
- Ed25519 fallback with constant-time arithmetic (optimized for performance)

[Decision: docs/decisions/crypto/crypto-design.md]
