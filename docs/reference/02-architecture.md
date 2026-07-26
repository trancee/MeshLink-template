# Architecture Overview

> Source: [SPEC.md §2](../../SPEC.md#2-architecture-overview)

## 2.1 Module Structure

```text
meshlink/          # Shipped library (JVM + Android + iOS)
meshlink-reference/ # Reference app consuming public API only
meshlink-proof/    # Real-device validation (android/ + ios/ subdirectories)
meshlink-benchmark/ # Performance benchmarking
```

`meshlink-proof/` contains `android/` and `ios/` subdirectories for platform-specific real-device validation. Both test the same proof scenarios on their respective platforms. [Decision: docs/explanation/module-structure.md]

## 2.2 Source Set Structure

- `commonMain` — Shared business logic (security, routing, transfer, diagnostics)
- `androidMain` — Platform-specific BLE glue, fallback crypto for older Android
- `iosMain` — Platform-specific BLE glue
- `commonTest` — Pure JVM tests (protocol logic, wire codecs, crypto)
- `androidHostTest` — Host-side Android tests (crypto fallback paths)
- `androidDeviceTest` — Instrumented device tests (reserved for future use; `:meshlink` currently has no Android-specific code requiring device tests)

## 2.3 Wire Protocol Reference Standards

- RFC 7748 (X25519/X448 ECDH)
- RFC 8032 (Ed25519 signatures)
- RFC 8439 (ChaCha20-Poly1305 AEAD)
- RFC 5869 (HKDF)
- RFC 2104 (HMAC)
- RFC 6234 (SHA-2 family)
- RFC 9147 (DTLS 1.3 for replay protection patterns)
- RFC 8966 (Babel routing for feasibility conditions and seqno)
- RFC 9420 (MLS — design reference for group security)
- RFC 7435 (Opportunistic security — design reference for best-effort encryption)
