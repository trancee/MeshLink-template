# Private-key handling and storage

**Status:** Locked — 2026-07-31

> This record defines the mandatory boundary for private-key ownership,
> persistence, memory handling, diagnostics, rotation, and device-lock state.
> It applies to platform and pure-Kotlin providers.

## Invariant

Private keys never leave the crypto/storage subsystem through public APIs, wire
messages, strings, diagnostics, exceptions, logs, crash breadcrumbs, analytics,
or test reports.

Shared protocol code uses opaque internal handles:

```kotlin
internal sealed interface IdentityPrivateKey
internal sealed interface HandshakePrivateKey
internal sealed interface EphemeralPrivateKey
```

Public keys remain serializable `IdentityKey` and `HandshakeKey` values. Each
private handle records its owning provider internally; a wrong-provider
operation fails closed.

## Runtime self-tests

Before advertising once per process, each platform candidate runs known-answer
and negative tests for SHA-256, HMAC-SHA256, HKDF-SHA256,
ChaCha20-Poly1305, X25519, and Ed25519, plus secure-random availability and a
private-key generate/store/load/use round trip.

A failing platform primitive selects only that primitive's validated pure-Kotlin
fallback. Fallback failure blocks startup. Provider choice is not persisted and
self-test keys never enter installation storage. Diagnostics expose only
primitive, provider label, and stage. The complete gate must remain inside the
500 ms cold-start budget.

## Provider ownership

Non-exportable platform keys remain provider-owned and persist by opaque alias.
Fallback/exportable keys may be serialized only inside the provider/storage
bridge and only directly into authenticated encryption or Keychain storage.

Handles render redacted metadata only, for example provider label and a
non-secret key identifier. No private handle exposes `toByteArray`, encoding,
copy, reflection-friendly data properties, or meaningful raw equality output.

## Android persistence

Fallback or exportable private keys are encrypted with AES-256-GCM using a
wrapping key generated in Android Keystore. Hardware backing is preferred when
available and verified, but is not claimed solely from API level or provider
name.

Every record write uses a fresh 96-bit nonce. AAD binds schema version,
`appHash`, PeerIdentity, key type, and key generation. Only ciphertext, nonce,
tag, and non-secret metadata enter an app-private atomic file. The record is
excluded from Android backup.

A non-exportable native key record stores only provider alias and public key.
Plaintext preferences, generic object serialization, external storage, and
backup-restorable private records are prohibited.

## iOS persistence

CryptoKit/fallback representations use Keychain with:

```text
kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
kSecAttrSynchronizable = false
```

An app-container installation marker detects deletion/reinstall because Keychain
items may outlive the deleted app. Missing marker plus existing MeshLink items
means stale installation state: delete it before creating a new PeerIdentity and
keys.

MeshLink does not claim Secure Enclave execution for Ed25519 or X25519.

## First unlock

After reboot and before the user's first device unlock, MeshLink does not:

- load or recreate identity material;
- advertise as an operational peer;
- accept or initiate handshakes; or
- process protected routing/transfer work.

It reports typed protected-storage unavailability and resumes only when the
platform makes device-only key material available. This rule is identical in
observable behavior on Android and iOS.

## Atomic updates

Identity and key-generation updates use a recoverable transaction:

```text
write encrypted temporary record
→ flush
→ atomically replace committed record
→ update commit marker
```

A crash exposes either a complete old generation or complete new generation,
never a mixed binding. Authentication-tag/AAD failure, missing keys under an
existing installation marker, or rollback ambiguity fails closed. MeshLink does
not silently regenerate keys under the same PeerIdentity.

## Memory handling

- Private data uses mutable buffers only.
- No String, hex, Base64, list, or immutable-collection conversion is allowed.
- Copies are minimized and temporary arrays are overwritten in `finally`.
- Ephemeral handles are released when handshake state closes.
- Fallback scalar operations avoid secret-dependent control/data access.
- Garbage-collected JVM/Native runtimes prevent an absolute zeroization
  guarantee; documentation states best effort and prefers genuinely
  non-exportable keys when supported.

## Rotation and cryptographic erasure

Planned old keys remain encrypted only through the approved grace period.
Security-event rotation disables old handles immediately. At grace expiry,
provider aliases and wrapped records are removed.

Rotation proof chains contain public keys and signatures only. Physical flash
secure erasure is not assumed; deletion of the wrapping key/alias provides
cryptographic erasure of remaining ciphertext.

## Redaction

Diagnostics identify only algorithm, provider label, operation stage, public
key generation, and redacted key ID. Error text never interpolates input key
bytes. Test vectors use dedicated non-production fixtures and reports never
capture runtime key records.

## Required tests

- Platform and fallback known-answer/vector tests
- Wrong-provider handle rejection
- Private marker scan across logs, exceptions, reports, and persisted files
- Corrupted nonce/tag/AAD and rollback failure
- Process kill at every atomic-persistence step
- Before-first-unlock startup
- Android backup exclusion
- iOS non-synchronizable and reinstall-marker behavior
- Planned/security rotation deletion
- Public API/reflection audit proving no private-byte accessor
- Best-effort temporary-buffer overwrite tests

## Related

- [Identity binding and fail-closed behavior](identity-binding-and-fail-closed.md)
- [Peer identity persistence](../storage/persistence-strategy.md)
- [Crypto design](crypto-design.md)
- [CONSTITUTION.md Principle I](../../../CONSTITUTION.md#i-rigorous-code-quality)
