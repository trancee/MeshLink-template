# Android Crypto Fallback Proof Plan

Records the shipped fallback plus the remaining validation posture.

## Current Status

- Android app floor is API 26+, but Android's official crypto API guarantees arrive later:
  - `KeyAgreement` XDH: API 33+
  - `Cipher` ChaCha20-Poly1305: API 28+
- **Ed25519** has an in-repo fallback implementation.
- **X25519/XDH and ChaCha20-Poly1305** are runtime-capability features on API 26-32.
- If either primitive is missing, `JcaCryptoProviderFactory.create()` fails fast rather than weakening the crypto contract.

## Validation Evidence (2026-06-13)

- Attached Android 9 / SDK 28 hardware (SM-G390F) passed `connectedAndroidDeviceTest` against `CryptoRuntimeValidationDeviceTest`.
- Runtime probe reported `x25519=false`, `ed25519=false`, `chacha=false`, `meshRuntime=false`, selected `AndroidFallbackCryptoProvider` — confirms the fallback path on real API 28-class hardware.
- API 26/28 AVDs crashed before `sys.boot_completed` — emulator proof blocked by local emulator stability, not by the MeshLink runtime.

## Performance Optimizations (2026-07-07)

Fallback paths were the slowest entries in the fleet. Optimizations made to pure-Kotlin fallback implementations without changing external behavior:

- **PureEd25519.kt**: Dedicated `square()`/`double()` field-arithmetic routines; precomputed radix-16 fixed-base comb table (`baseCombTable`); windowed scalar multiplication for verification; addition-chain exponentiation for `invert()`/`power2523()` (254→11 multiplications for inversion, 250→9 for sqrt).
- **PureX25519.kt**: Fast `squareInto()` alongside generic multiply; addition-chain `invert()`.
- **AndroidFallbackCryptoProvider.kt**: Poly1305 replaced `BigInteger` arithmetic with constant-time 5×26-bit-limb implementation (poly1305-donna style, RFC 8439 §2.5).

Real-device fleet re-benchmarks confirmed speedups on fallback devices with no change on native `JcaCryptoProvider` devices (e.g. Samsung Galaxy XCover 4 `ed25519KeyGen` 15.4ms → 11.9ms).

## Implementation Plan

### Task 1 — Make the runtime boundary explicit in tests

Files: `CryptoRuntimeCapabilityTest.kt`, `CryptoProviderFactoryTest.kt`, `PureEd25519Test.kt` (`meshlink/src/androidHostTest/`)

Goals: verify `supportsMeshLinkRuntime` requires both X25519/XDH and ChaCha20-Poly1305; verify missing ChaCha20-Poly1305 fails explicitly; keep Ed25519 fallback behavior unchanged.

### Task 2 — Add the fallback or adapter path

Files: `JcaCryptoProviderFactory.kt`, `JcaCapabilityProbe.kt` (`meshlink/src/androidMain/`)

Goals: deterministic path for X25519/XDH when platform provider is missing; deterministic path for ChaCha20-Poly1305 when platform provider is missing; explicit failure if either can't be satisfied; no silent primitive substitution.

### Task 3 — Validate on real hardware and emulator targets

Update `docs/reference/device-test-matrix.md`:

- API 26 emulator: lowest supported transport floor + fallback path
- API 28 emulator: first official ChaCha20-Poly1305 floor
- API 30 attached device: runtime-capability path coverage
- API 33+ attached devices: official XDH + ChaCha20-Poly1305 support floor

### Task 4 — Keep docs aligned with proven state

Update only after the runtime story is proven: `docs/reference/release-status.md`, `docs/how-to/add-meshlink-to-your-app.md`, `docs/how-to/evaluate-meshlink-with-the-reference-app.md`, `README.md`.

## Acceptance Criteria

- Code has explicit tests for the missing-primitive failure path
- Code either implements a fallback or documents that the lower floor depends on runtime capability on API 26-32
- Device matrix records at least one device/emulator target for each relevant crypto tier
- Release-status docs can state the Android crypto story without ambiguity

## Non-Goals

Lowering the Android app floor below API 26, changing the iOS floor, changing the wire format or transport semantics, claiming hardware-backed crypto support where the platform only offers runtime-capability.
