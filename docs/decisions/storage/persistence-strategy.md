# PeerIdentity & TrustStore Persistence Strategy — Rationale

**Status:** Locked — 2026-07-26

> **Full implementation** (platform-specific stores, encryption, migration, diagnostics) lives in platform `*Main` sources and [SPEC.md §1.3, §5.4](../../../SPEC.md). This ADR captures the *why*.

---

## Decision

**`expect`/`actual` platform-specific storage with hardware-backed encryption:**

| Platform | Storage | Encryption |
|----------|---------|------------|
| Android | Jetpack DataStore | Android Keystore (AES-256-GCM, hardware-backed if available) |
| iOS | Keychain | Secure Enclave (AES-256-GCM) |
| JVM (test) | File | Software AES-256-GCM (test only) |

**Persisted (minimal):**

1. `PeerIdentity` (16 bytes) — generated once at install
2. `TrustStore` — `PeerIdentity → TrustRecord` (public keys, timestamps, state)
3. `LocalIdentityKeys` — Ed25519 + X25519 private keys (encrypted)

**NOT persisted:** Diagnostics, route tables, transfer sessions, ephemeral keys, scan results.

---

## Why Minimal Persistence?

| Not Persisted | Reason |
|---------------|--------|
| Diagnostics | Volatile; replayable via eventCallback |
| Route tables | Rebuilt from advertisements on startup |
| Transfer sessions | In-memory only; doesn't survive process restart (CONSTITUTION.md §1.3) |
| Ephemeral keys | Regenerated per session |
| Scan results | Stale on restart |

**Principle**: Only persist what's required to re-verify pinned trust (identity material + first-seen/last-verified timestamps).

---

## Why `expect`/`actual` with Hardware-Backed Encryption?

| Alternative | Why Rejected |
|-------------|--------------|
| Plaintext SharedPreferences/UserDefaults | Private keys in plaintext — extractable from backup/root |
| SQLDelight/Room | Overhead for simple key-value; no hardware encryption |
| Custom file + software crypto | No hardware protection; keys in process memory |
| `MultiPlatformSettings` lib | No control over encryption backend |

**Platform-native + hardware-backed** gives:

- Android: Keystore (TEE/StrongBox) — keys never leave hardware
- iOS: Secure Enclave — keys never leave hardware
- Test: Software fallback (explicitly not for production)

---

## Why DataStore (Android) / Keychain (iOS)?

| Platform | Choice | Rationale |
|----------|--------|-----------|
| Android | DataStore | Coroutine-first, transactional, migration support, replaces SharedPreferences |
| iOS | Keychain | Hardware-backed, persists across app deletes (if `ThisDeviceOnly`), encrypted at rest |
| Both | Encrypted payload | AEAD (AES-256-GCM) — integrity + confidentiality; master key in hardware |

---

## TrustRecord Design Rationale

```kotlin
data class TrustRecord(
    val peerIdentity: PeerIdentity,
    val identityKey: ByteArray,      // Ed25519 public (32B)
    val handshakeKey: ByteArray,     // X25519 public (32B)
    var state: TrustState,           // INITIATED | TRUSTED | REVOKED
    var generation: Int,             // Key rotation count
    val seenAt: Instant,             // First handshake (immutable)
    var verifiedAt: Instant,         // Last successful verify
)
```

| Field | Why |
|-------|-----|
| `generation` | Tracks key rotation count; enables "new crypto era" detection |
| `seenAt` | Immutable first contact — for forensic/audit |
| `verifiedAt` | Updates on each successful handshake — staleness detection |
| `state` | TOFU lifecycle: `INITIATED` → `TRUSTED` → `REVOKED` |

---

## Uninstall/Reinstall Behavior

| Platform | Behavior | Rationale |
|----------|----------|-----------|
| iOS | Keychain persists (`ThisDeviceOnly`) → identity survives reinstall | User expectation: "my device" identity persists |
| Android | Keystore clears on uninstall → identity regenerated | Acceptable per TOFU model; first handshake re-pins |

**Not a bug** — TOFU model assumes first handshake establishes trust. Reinstall = new device from network perspective.

---

## Migration Strategy

| Version | Change | Migration |
|---------|--------|-----------|
| 1 | Initial | — |
| 2 | Add `keyRotationCount` to TrustRecord | Default 0 |
| 3 | Add `revokedAt` timestamp | Nullable, default null |

**Android**: DataStore `PreferenceMigration` (automatic).
**iOS**: Manual on startup (check schema version, migrate).

---

## Security Considerations

| Threat | Mitigation |
|--------|------------|
| App backup exposes keys | `android:allowBackup="false"` / iOS Keychain `ThisDeviceOnly` |
| Root/jailbreak extracts keys | Hardware-backed keystore (TEE/StrongBox/Secure Enclave) |
| TrustStore tampering | AEAD encryption + integrity check on decrypt |

---

## Diagnostics Rationale

Events for observability without persisting diagnostics:

- `PeerIdentityGenerated` — identity created
- `TrustRecordUpdated` — state/key rotation
- `StorageError` — failure (never silent)

---

## Related

- [SPEC.md §1.3, §5.4](../../../SPEC.md)
- [Data Model ADR](../model/data-model.md)
- [Trust Model (TOFU)](../../reference/trust-model.md)
- [Crypto Design](../crypto/crypto-design.md)
