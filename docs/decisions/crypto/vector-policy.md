# Crypto Vector Policy

Explains how MeshLink classifies tracked crypto vectors and what the runtime does with each class.

## Canonical Evidence

The machine-readable policy and unmodified Wycheproof vector files live under
`meshlink/src/commonTest/resources/wycheproof/` (`policy.json`,
`ed25519_test.json`, `x25519_test.json`, `chacha20_poly1305_test.json`,
`hkdf_sha256_test.json`, `hmac_sha256_test.json`), alongside a smaller RFC-style
regression corpus (`*.jsonl`) that `WycheproofRegressionTest` runs.

The authoritative automated evidence comes from test classes in the provider
coverage matrix below, run as part of `:meshlink:jvmTest` and
`:meshlink:testAndroidHostTest`.

## Policy Verdicts

| Policy | Provider expectation | Runtime expectation |
|---|---|---|
| `accept` | Must produce the expected result. | Runtime may proceed normally. |
| `reject` | Must not accept the vector. Returning `false` or throwing is acceptable. | Runtime fails closed. No permissive fallback. |
| `fail_closed_or_match` | May reject or throw, but any returned value must match the tracked shared secret exactly. | Runtime still fails closed on rejection. |

`fail_closed_or_match` is currently used only for X25519 edge cases where providers may reject malformed public keys at different layers.

## Coverage

The tracked manifest classifies the **entire** upstream Wycheproof corpus for all
required primitives. Every `tcId` is accounted for, and `CryptoPolicyCorpusTest`
fails the build if that ever drifts.

| Algorithm | valid | invalid | acceptable | Total |
|---|---:|---:|---:|---:|
| `ed25519` | 88 | 62 | 0 | 150 |
| `x25519` | 264 | 0 | 254 | 518 |
| `chacha20_poly1305` | 256 | 69 | 0 | 325 |
| `hkdf_sha256` | 83 | 3 | 0 | 86 |
| `hmac_sha256` | 66 | 108 | 0 | 174 |
| **Total** | **757** | **242** | **254** | **1253** |

If `policy.json` changes, update this table in the same change.

## Provider Coverage Matrix

| Surface | Evidence path |
|---|---|
| Corpus integrity | `CryptoPolicyCorpusTest` + `policy.json` |
| JVM provider conformance | `JvmCryptoPolicyConformanceTest` (`meshlink/src/jvmTest/`) |
| Android provider conformance | `AndroidCryptoPolicyConformanceTest` (`meshlink/src/androidHostTest/`); fallback covered by `WycheproofRegressionTest` against `AndroidFallbackCryptoProvider` |
| Runtime fail-closed | `CryptoProviderRuntimeContractTest` (`meshlink/src/commonTest/`) and `MeshRuntimeAndroidCryptoTest` (`meshlink/src/androidHostTest/`) |
| iOS bridge boundary | Compile/link only for `iosArm64` |

## Fail-Closed Runtime Expectations

- Malformed advertisements fail before a peer becomes reachable
- Rejected or all-zero X25519 shared secrets fail before HKDF derivation or transport dispatch
- Decrypt, sign, verify, or derivation failure stops the operation immediately
- No fallback to plaintext, cached shared secrets, or a permissive alternate provider path
- All cryptographic field operations and comparisons MUST implement constant-time algorithms

## Redaction Rules

Use structured identifiers (algorithm, tcId, bucket id, provider label, runtime stage). Do **not** print: private keys, shared-secret bytes, session keys, HKDF output, or full raw vector payloads.

## Maintenance Workflow

1. Update the Wycheproof corpus under `meshlink/src/commonTest/resources/wycheproof/`.
2. Classify every new `tcId` in `policy.json`.
3. Update this document so the coverage table matches.
4. Run `:meshlink:jvmTest` and `:meshlink:testAndroidHostTest`.
