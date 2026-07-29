# Mesh Hash Derivation (Application Isolation) — Rationale

**Status:** Locked — 2026-07-26

> **Specification content** (algorithm, wire format, settings) lives in [SPEC.md §4](../../../SPEC.md#discovery--identity). This ADR captures only the *why*.

---

## Decision

**Mesh Hash = FNV-1a 32-bit(appId) truncated to 16 bits** — used in discovery advertisement for application isolation.

---

## Why FNV-1a?

| Property | Why It Matters |
|----------|----------------|
| **Speed** | Single multiply + xor per byte — negligible CPU on mobile |
| **Collision resistance** | Sufficient for 16-bit truncation (birthday bound 256) |
| **Deterministic** | Same `appId` → same hash always |
| **No crypto dependency** | Pure Kotlin, no `SecureRandom` or provider |
| **Industry standard** | Used in DNS, hash tables, etc. |

**Why not SHA-256?** Overkill for 16-bit output; slower; requires crypto provider on older Android.

---

## AppId Format Rationale

**Recommended**: Reverse DNS (`com.example.myapp`) + optional suffix (`.dev`, `.test`)

**Rules**:

- Stable across app updates (not per-install)
- Unique per application
- ASCII printable recommended (UTF-8 encoded for hash)

---

## Collision Probability

| Active Meshes | Collision Probability (16-bit) |
|---------------|-------------------------------|
| 10 | ~0.02% |
| 50 | ~0.5% |
| 100 | ~2% |
| 256 | ~12% |
| 500 | ~39% |

**Mitigation**: Collisions only cause cross-discovery (peers see each other but fail handshake due to different identity keys). Not a security issue — just wasted radio time.

**If collision detected**: Handshake fails verification → `DiagnosticEvent.HandshakeEvent.verificationLevel = NONE` → peer ignored.

---

## Configuration Rationale

`appId` defaults to `BuildConfig.APPLICATION_ID` (package name) — zero-config for most apps. `meshHash` is derived, not settable, preventing misconfiguration.

---

## Wire Encoding Rationale

**16 bits, little-endian** — consistent with all other multi-byte fields in advertisement. Fits in 31-byte BLE ad packet alongside UUID, version, platform, power mode, PSM hint, PeerFingerprint.

---

## Related

- [SPEC.md §4](../../../SPEC.md#discovery--identity) — Full advertisement format
- [SPEC.md](../../../SPEC.md) — Implementation
- [SPEC.md](../../../SPEC.md) — Configuration
