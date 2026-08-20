# Private-key handling and storage

**Status:** Locked — 2026-07-31

> Normative boundary, persistence, memory handling, rotation, and test requirements
> live in [SPEC.md §7.3](../../../SPEC.md#7-security-layer) and implementation in
> crypto/storage modules. This record explains the design rationale.

## Why private keys never leave the crypto/storage subsystem

**Decision:** Private keys never enter public APIs, wire messages, strings,
diagnostics, exceptions, logs, crash breadcrumbs, analytics, or test reports.
Public code uses opaque internal handles (`IdentityPrivateKey`,
`HandshakePrivateKey`, `EphemeralPrivateKey`). Public keys remain serializable
`IdentityKey`/`HandshakeKey` values.

**Rationale:** The fundamental security invariant is that private key material is
never observable outside the crypto subsystem. This prevents accidental logging,
serialization in crash reports, exposure in analytics, and reflection-based
extraction. Opaque handles with provider ownership ensure operations fail closed
when a handle is used with the wrong provider.

## Why runtime self-tests with pure-Kotlin fallback

**Decision:** Each platform candidate runs known-answer and negative tests for
all primitives before advertising. Failing platform primitive selects only that
primitive's validated pure-Kotlin fallback. Fallback failure blocks startup.
Self-test keys are ephemeral and never enter installation storage.

**Rationale:** Platform crypto implementations can have bugs, be unavailable, or
be disabled by policy. Self-tests catch these before any protocol traffic.
Per-primitive fallback means a broken X25519 doesn't force pure-Kotlin Ed25519.
Ephemeral test keys ensure the tests don't pollute the trust store. The 500 ms
cold-start budget forces efficient implementation.

## Why provider ownership with opaque handles

**Decision:** Non-exportable platform keys remain provider-owned by opaque
alias. Fallback/exportable keys serialized only inside provider/storage bridge
directly into authenticated encryption or Keychain storage. Handles render only
redacted metadata (provider label, non-secret key ID). No `toBytes`,
encoding, copy, or raw equality.

**Rationale:** Provider ownership ensures the platform's key isolation (StrongBox,
Secure Enclave) is respected. Opaque handles prevent accidental leakage through
serialization, reflection, or debug output. The provider/storage bridge is the
only boundary where exportable keys cross, and it uses authenticated encryption
with bound AAD.

## Why Android persistence uses AES-256-GCM with Keystore wrapping key

**Decision:** Fallback/exportable keys encrypted with AES-256-GCM, wrapping key
in Android Keystore. Fresh 96-bit nonce per write. AAD binds schema version,
`appHash`, PeerIdentity, key type, key generation. App-private atomic file,
excluded from backup. Non-exportable native keys store only provider alias +
public key.

**Rationale:** AES-256-GCM provides authenticated encryption. Keystore-backed
wrapping key leverages hardware isolation when available. Fresh nonce per write
prevents nonce reuse. AAD binding ensures ciphertext cannot be replayed across
different keys, versions, or applications. Atomic file + backup exclusion
prevents partial writes and backup leakage. Non-exportable keys don't need
wrapping — they stay in Keystore by alias.

## Why iOS persistence uses Keychain with AfterFirstUnlockThisDeviceOnly

**Decision:** Keychain items with `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`
and `kSecAttrSynchronizable = false`. Installation marker detects
deletion/reinstall.

**Rationale:** `AfterFirstUnlockThisDeviceOnly` ensures keys are only accessible
after user unlock and never leave the device. Non-synchronizable prevents iCloud
Keychain sync (which would break device-only security). Installation marker
handles the case where Keychain items outlive app deletion — a fresh install
must not inherit stale identity/keys.

## Why MeshLink is inactive before first unlock

**Decision:** After reboot, before first user unlock: no identity material
load/recreation, no advertisement, no handshake acceptance/initiation, no
protected routing/transfer work. Reports typed protected-storage unavailability.

**Rationale:** Before first unlock, device-only key material is encrypted and
unavailable. Attempting to use it would either fail or (worse) silently use
insecure fallback. Inactivity with typed unavailability is honest and fail-closed.
Behavior is identical on Android and iOS for cross-platform consistency.

## Why atomic updates with recoverable transactions

**Decision:** Identity/key-generation updates use temporary record → flush →
atomic replace → commit marker. Crash exposes either complete old or complete
new generation, never mixed. AAD failure, missing keys under existing marker,
or rollback ambiguity fails closed. No silent key regeneration under same
PeerIdentity.

**Rationale:** Atomic replace ensures the storage is never in a partially
updated state. The commit marker makes the transition observable. Fail-closed
on any ambiguity prevents key confusion attacks. No silent regeneration under
same PeerIdentity preserves the trust model — a corrupted storage means the
identity is unrecoverable, not silently reset.

## Why memory handling constraints

**Decision:** Private data uses mutable buffers only. No String, hex, Base64,
list, or immutable-collection conversion. Copies minimized, temporary arrays
overwritten in `finally`. Ephemeral handles released when handshake state
closes. Fallback scalar operations avoid secret-dependent control/data access.
GC runtimes prevent absolute zeroization guarantee; documentation states best
effort and prefers non-exportable keys.

**Rationale:** Immutable collections and Strings can leave copies in GC memory.
Mutable buffers allow explicit overwrite. Minimizing copies reduces exposure
surface. `finally` block overwrite ensures cleanup even on exception.
Non-exportable hardware keys provide genuine isolation; software-only keys are
best-effort due to GC.

## Why cryptographic erasure on rotation

**Decision:** Planned old keys remain encrypted through grace period.
Security-event rotation disables old handles immediately. At grace expiry,
provider aliases and wrapped records removed. Rotation proofs contain only
public keys/signatures. Wrapping key/alias deletion provides cryptographic
erasure; physical flash secure erasure not assumed.

**Rationale:** Grace period allows in-flight sessions to complete. Security-event
immediate disable limits exposure window. Deleting the wrapping key renders
ciphertext undecryptable (cryptographic erasure), which is stronger than file
deletion and doesn't depend on flash controller behavior. Proofs need only
public material; private keys never enter proofs.

## Why redaction in diagnostics and tests

**Decision:** Diagnostics show only algorithm, provider label, stage, public
generation, redacted key ID. Error text never interpolates input key bytes.
Test vectors use dedicated non-production fixtures; reports never capture
runtime key records.

**Rationale:** Even in failure, private key material must not leak. Redaction
rules are enforced at emission point, not by convention. Dedicated test
fixtures ensure production keys never enter test infrastructure.

## Related

- [Identity binding and fail-closed behavior](identity-binding-and-fail-closed.md)
- [Peer identity persistence](../storage/persistence-strategy.md)
- [Crypto design](crypto-design.md)
- [CONSTITUTION.md Principle I](../../../CONSTITUTION.md#i-rigorous-code-quality)
- [SPEC.md §7.3](../../../SPEC.md#7-security-layer)
