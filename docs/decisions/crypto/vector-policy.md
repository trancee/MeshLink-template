# Crypto vector policy

This note explains how MeshLink classifies tracked crypto vectors and what the
runtime is expected to do with each class.

Use it when you are extending the local crypto corpus, updating
`policy.json`, or debugging the crypto/runtime contract gate.

## Canonical evidence

- The machine-readable policy and the unmodified upstream Wycheproof vector
  files it classifies live under
  `meshlink/src/commonTest/resources/wycheproof/`
  (`policy.json`, `ed25519_test.json`, `x25519_test.json`,
  `chacha20_poly1305_test.json`, `hkdf_sha256_test.json`,
  `hmac_sha256_test.json`), alongside the
  separate, much smaller RFC-style regression corpus
  (`chacha20_poly1305.jsonl`, `ed25519.jsonl`, `hkdf_sha256.jsonl`,
  `hmac_sha256.jsonl`, `x25519.jsonl`) that `WycheproofRegressionTest` runs
  against every algorithm the constitution requires.
- The authoritative automated evidence comes from the test classes in the
  [provider coverage matrix](#provider-coverage-matrix) below, run as part of
  the repository's current Gradle/CI crypto verification bundle
  (`:meshlink:jvmTest`, `:meshlink:testAndroidHostTest`).
- The iOS bridge boundary and install contract will be documented in the
  planned "MeshLink SDK API reference" (`docs/reference/`) and "How to use
  MeshLink from Swift" (`docs/how-to/`) pages — not yet written.
- Higher-level peer-trust behavior will be documented in the planned "The
  trust model" page (`docs/explanation/`) — not yet written.
- Physical Android ↔ iOS proof evidence will live with the planned
  benchmark and validation baselines (`meshlink-benchmark/README.md` — the
  `:meshlink-benchmark` Gradle module doesn't exist yet).

## Policy verdicts

| Policy | Provider expectation | Runtime expectation |
|---|---|---|
| `accept` | Provider must produce the expected result. | Runtime may proceed normally once the accepted result is available. |
| `reject` | Provider must not accept the vector. Returning `false` or throwing is acceptable. | Runtime fails closed. There is no permissive fallback. |
| `fail_closed_or_match` | Provider may reject or throw, but any returned value must match the tracked shared secret exactly. | Runtime still fails closed on rejection. Accepting with the wrong value is a regression. |

`fail_closed_or_match` is currently used only for X25519 edge cases where
providers may reject malformed public keys at different layers; the tracked
manifest has no `ed25519` `fail_closed_or_match` buckets and no `x25519`
`reject` buckets today.

## Current manifest coverage

The tracked manifest classifies the **entire** upstream Wycheproof corpus for
all required primitives -- Ed25519, X25519, ChaCha20-Poly1305, HKDF, HMAC, and
SHA-256. Every `tcId` in the corresponding Wycheproof JSON files is accounted
for, and `CryptoPolicyCorpusTest` fails the build if that ever drifts.

| Algorithm | valid | invalid | acceptable | Total |
|---|---:|---:|---:|---:|
| `ed25519` | 88 | 62 | 0 | 150 |
| `x25519` | 264 | 0 | 254 | 518 |
| `chacha20_poly1305` | 256 | 69 | 0 | 325 |
| `hkdf_sha256` | 83 | 3 | 0 | 86 |
| `hmac_sha256` | 66 | 108 | 0 | 174 |
| `sha256` | — | — | — | — (RFC-style regression corpus) |
| **Total** | **757** | **242** | **254** | **1253** |

If `policy.json` changes, update this table in the same change.

## Provider coverage matrix

| Surface | What it proves now | Evidence path |
|---|---|---|
| Corpus integrity | Every tracked `tcId` is classified explicitly, exactly once, with a `wycheproofResult` that agrees with the vector file, and stale/missing `tcId`s fail the build. | `CryptoPolicyCorpusTest` (`meshlink/src/commonTest/kotlin/ch/trancee/meshlink/crypto/`) plus `policy.json` |
| JVM provider conformance | `JvmCryptoProvider` honors every tracked policy bucket across the full 668-vector manifest. | `JvmCryptoPolicyConformanceTest` (`meshlink/src/jvmTest/...`) |
| Android provider conformance | `JcaCryptoProvider` -- the JCA-backed provider Android uses whenever the device supports X25519/XDH and Ed25519 -- honors the same manifest on host execution. The pure-Kotlin fallback used on devices without that platform support is covered separately by `WycheproofRegressionTest` against `AndroidFallbackCryptoProvider`. | `AndroidCryptoPolicyConformanceTest` (`meshlink/src/androidHostTest/...`) |
| Runtime fail-closed behavior | A real Wycheproof low-order/non-canonical X25519 public key fails the Noise XX (link) / IX (end-to-end) handshake at the X25519/HKDF step, before any session key is derived, for whichever `CryptoProvider` a source set wires up (`CryptoProviderRuntimeContractTest`), and for whichever provider `JcaCryptoProviderFactory`'s capability probe actually selects at runtime on Android (`MeshRuntimeAndroidCryptoTest`). | `CryptoProviderRuntimeContractTest` (`meshlink/src/commonTest/...`) and `MeshRuntimeAndroidCryptoTest` (`meshlink/src/androidHostTest/...`) |
| iOS bridge boundary | The iOS bridge contract remains compile/link only for `iosArm64`. | Current Gradle/CI verification bundle |

## Fail-closed runtime expectations

The current runtime proof boundary is deliberately narrow and deterministic:

- malformed advertisements fail before a peer becomes reachable
- rejected or all-zero X25519 shared secrets fail before HKDF derivation or
  transport dispatch
- decrypt, sign, verify, or derivation failure stops the operation immediately
- MeshLink never falls back to plaintext, cached shared secrets, or a permissive
  alternate provider path

## Redaction rules

Use structured identifiers such as algorithm, tcId, bucket id, provider label,
and runtime stage. Do **not** print:

- private keys
- shared-secret bytes
- session keys
- HKDF output
- full raw vector payloads

Human-readable failure context is good. Secret material is not.

## Maintenance workflow

1. Update the tracked local RFC or Wycheproof corpus under
   `meshlink/src/commonTest/resources/wycheproof/`.
2. Classify every new `tcId` in `policy.json`.
3. Update this document so the coverage table still matches the manifest.
4. Run the current Gradle/CI verification bundle for the crypto/runtime
   contract (`:meshlink:jvmTest`, `:meshlink:testAndroidHostTest`).
5. If later work broadens live-radio or cross-platform proof, extend the current
   verification bundle instead of reviving slice-specific shell gates.
