# Identity and trust persistence

**Status:** Locked — 2026-07-31

> Private-key encryption and memory rules live in
> [Private-key handling](../crypto/private-key-handling.md). This record defines
> what persists, platform ownership, reinstall behavior, and corruption policy.

## Persisted state

MeshLink persists only continuity and crash-safety state:

- local PeerIdentity;
- Ed25519/X25519 provider aliases or encrypted private records;
- corresponding public keys and current key generation;
- installation-lifetime public rotation proof chain;
- trust records keyed by remote PeerIdentity;
- local route-sequence value;
- MessageId/TransferId allocation high-water marks;
- storage schema/version; and
- installation marker metadata.

## Ephemeral state

MeshLink never persists:

- peerHint or TransportHandle;
- advertisements or scan candidates;
- BLE/GATT/L2CAP connections and health;
- Noise traffic/ephemeral keys, counters, replay windows, or pending renewal;
- routes, RouteImport/RouteExport, or feasible distance;
- active messages/transfers, chunks, scoreboards, retries, or payload data; or
- diagnostics.

Process death therefore reconstructs identity/trust but starts fresh radio,
Noise, routing, and transfer state.

## Android

Android uses an app-private atomic encrypted record rather than a new runtime
storage dependency. Exportable private keys are AES-256-GCM wrapped by an
Android Keystore key; non-exportable provider keys persist by alias. Records are
excluded from backup.

Ordinary uninstall or app-data clear destroys the app container and Keystore
association. Backup/restore must not migrate installation identity to another
device.

## iOS

Identity/private representations use non-synchronizable,
`AfterFirstUnlockThisDeviceOnly` Keychain items. Trust/public continuity records
use app-private storage or Keychain according to sensitivity, without iCloud
synchronization.

Because Keychain may survive app deletion, an app-container installation marker
is authoritative. Missing marker causes stale MeshLink Keychain items to be
deleted before creating a new installation identity. App offload and deletion
are tested separately.

## Installation identity

PeerIdentity is random and stable across process restart, device reboot, app
updates, key rotation, RPA/TransportHandle change, peerHint rotation, route
change, and Noise renewal.

Reinstall creates a new PeerIdentity and key set. Remote applications continue
to address the old and new installations as different peers; MeshLink never
silently attaches old trust to the new identity.

## Trust record

Internal trust state contains stable peer identity, current public binding,
trust state, key generation, immutable `seenAt`, latest successful `verifiedAt`,
and rotation-chain position. Applications observe only the stable PeerIdentity,
public trust/presence state, and approved timestamps; they never manage keys,
generation, or proof chains.

Trusted and revoked records persist. Transient unverified discovery candidates
do not.

## Atomicity and corruption

Writes use temporary-record, flush, atomic-replace, and committed-marker
semantics. Counter ranges are durably reserved before use, so crashes create
gaps rather than identifier reuse.

Authentication failure, partial record, missing key under an existing
installation marker, schema rollback, or mixed key generation fails closed.
MeshLink never repairs unexplained corruption by silently generating replacement
keys under the same PeerIdentity.

## Migration

Every persisted record has an explicit schema version. Migrations are
transactional, deterministic, covered from every supported prior version, and
preserve identity/key/trust invariants. A migration cannot weaken key
accessibility, enable backup, or convert private material to a textual form.

## Background and lock state

Before first unlock after reboot, protected key material is unavailable and
MeshLink remains inactive. After first unlock, accepted platform background
integration may use the keys under OS policy. Force-stop/force-quit does not
change persisted trust but prevents guaranteed execution.

## Diagnostics

Storage diagnostics expose operation, schema version, provider label, and typed
failure category only. They never include private bytes, ciphertext plaintext,
raw stored records, or payload data.

## Related

- [Private-key handling](../crypto/private-key-handling.md)
- [Peer hints and identity races](../discovery/peer-hint-and-identity-races.md)
- [Background operation](../transport/background-operation.md)
- [Data model](../model/data-model.md)
- [Trust model](../../reference/trust-model.md)
