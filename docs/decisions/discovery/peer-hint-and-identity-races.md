# Peer hints and identity race coordination

**Status:** Locked — 2026-07-31

> Normative discovery and trust behavior lives in [SPEC.md §§4 and
> 5](../../../SPEC.md#discovery--identity). This decision record defines the
> rotating advertisement hint, platform-handle behavior, concurrent discovery
> coordination, key-rotation recovery, and the application-facing identity
> invariant.

## Context

Android and iOS may change the platform representation of a BLE peer while
MeshLink independently rotates advertisement metadata, renews Noise sessions,
rotates long-term keys, changes power settings, and recovers routes. Callbacks
for those operations can arrive concurrently and out of order.

A stable identifier in plaintext advertising defeats much of BLE private-address
randomization. A rotating identifier improves opportunistic privacy but cannot
be synchronized portably with controller-managed address rotation. The design
must therefore keep discovery hints operational and unauthenticated while
making `PeerIdentity` the only durable peer key.

## Application-facing invariant

An application deals with one stable `PeerIdentity` for a remote installation.
It never receives or manages:

- `peerHint`;
- `TransportHandle`;
- Ed25519 or X25519 keys;
- key generations or rotation chains;
- Noise epochs, nonces, or handshake identifiers; or
- route-specific next-hop identity.

Key rotation, BLE address changes, hint rotation, reconnects, route migration,
and Noise renewal do not change the public `PeerIdentity`. `peers`, message
sources, destinations, transfer ownership, trust commands, and application
state all use that identity. The library may emit redacted diagnostics, but no
application action is required for a valid rotation or reconnect.

A reinstall creates a new installation and therefore a new `PeerIdentity`.
Explicit trust reset/revoke remains an application command but still requires
no key handling.

## Peer hint

`peerHint` is a 12-byte CSPRNG value carried only in the dynamic advertisement
UUID. It is not persisted, not derived from identity or keys, not sent through
GATT, and not included in the signed identity binding.

A new hint is generated whenever advertising starts and at a uniformly random
best-effort interval from 10 through 20 minutes. A suspended application is not
awakened only to rotate. An overdue hint rotates before advertising resumes when
the platform permits.

The hint provides short-lived candidate deduplication and reduces
installation-lifetime static identification. It does not authenticate a peer,
authorize a connection, index durable state, or guarantee unlinkability against
a continuous observer.

Android 15–17 require controller RPA rotation at randomized intervals from 5
through 15 minutes. Public app APIs do not provide a portable callback or atomic
coupling between that rotation and advertisement-data changes. Independent
address and hint changes can therefore be correlated by a continuous observer.
This limitation is explicit and is not represented as full passive anonymity.

## Transport handle

`TransportHandle` is an internal opaque reference supplied by the platform
transport adapter:

- Android wraps the `BluetoothDevice` from `ScanResult` or GATT callbacks.
- iOS wraps the retained `CBPeripheral`/`CBCentral` and app-scoped identifier.
- The virtual harness supplies a deterministic virtual handle.

A handle may change because of RPA rotation, restoration, disconnection, or
platform behavior. It is never persisted, sent on the wire, exposed publicly,
or treated as identity.

## Discovery attempt states

The pre-identity coordinator owns this verb-only state machine:

```text
DISCOVERED
CONNECTING
RESOLVING
AUTHENTICATING
SUCCEEDED
BACKING_OFF
FAILED
```

`RESOLVING` reads and validates untrusted GATT metadata, extracts a claimed
`PeerIdentity`, looks up trust, and selects the authentication path.
`AUTHENTICATING` runs Noise and validates the signed identity binding.

One single-owner coordinator serializes attempt registration and transitions.
Scan callbacks cannot independently start duplicate GATT work.

## Attempt indexes and deduplication

The coordinator maintains bounded ephemeral indexes by transport handle, peer
hint, and claimed identity.

- Same handle and same hint updates observation data only.
- Same handle and new hint attaches the observation to the active attempt.
- New handle and mapped hint skips full identity resolution provisionally and
  attempts IK with the mapped pin; successful IK is still mandatory.
- Different handles advertising the same unauthenticated hint are not merged as
  trusted identity. Their bounded attempts serialize once they claim the same
  PeerIdentity.
- The same claimed identity never runs concurrent Noise authentication.
- Canonical merging occurs only after authentication succeeds.
- Copied or replayed hints can cause at most bounded connection work; they never
  reuse trust or resume state without IK.

A changed `TransportHandle` alone does not change trust or create a new
`KnownPeer`. A new physical connection always requires Noise authentication.

## Per-peer serialization

After GATT claims an identity, one per-peer coordinator serializes:

- duplicate-link arbitration;
- IK/XX and rotation recovery;
- trust revoke/reset;
- long-term key rotation;
- Noise session renewal;
- disconnect/reconnect;
- route replacement; and
- transfer pause/resume.

Coordinators communicate with events rather than nested locks. Trust-state
changes cancel stale queued events through an operation generation so a late
callback cannot revive revoked or reset state.

## Authentication selection

```text
No trusted pin:
    first-contact XX

Trusted pin and current generation:
    IK

Trusted pin and a valid newer rotation chain:
    rotation-recovery XX

Trusted pin with no valid continuity proof:
    fail closed
```

Rotation-recovery XX is not TOFU. It creates an encrypted channel for delivering
proofs, and the current binding becomes trusted only if every proof validates
back to the existing pin.

## Signed rotation chain

Each long-term rotation creates a proof containing protocol version, `appHash`,
stable PeerIdentity, `oldGeneration`, `newGeneration`, new Ed25519/X25519 keys, and
reason. The old Ed25519 key signs continuity and the new Ed25519 key signs
possession. Routing sequence numbers are independent and never reset.

Proofs are retained for the installation lifetime. A peer that missed multiple
rotations receives the chain from its pinned generation to current over the
rotation-recovery channel. Proof transfer is bounded, chunked, rate-limited, and
never mutates trust until the complete chain validates.

Validation requires contiguous generations, unchanged identity/application
context, valid old/new signatures, no repeated generation with different keys,
and current X25519 possession through the completed handshake. Exact duplicate
proofs are idempotent. A fork with two valid different bindings for one
generation fails closed and requires explicit trust resolution; MeshLink never
chooses first-seen or route majority.

## Race matrix

| Concurrent events | Required behavior |
|-------------------|-------------------|
| Handle changes; hint unchanged | Reuse tentative hint mapping, establish new link if needed, require IK, preserve public identity |
| Hint changes; active link remains | Update advertising only; do not touch trust, routes, transfers, or sessions |
| Handle and hint both change | Create bounded candidate, resolve claimed identity, serialize under peer coordinator, authenticate |
| Hint rotates during connection attempt | Existing handle attempt continues; new observations coalesce where possible |
| Power mode or advertised capability changes | Atomically update advertisement metadata with the same hint; no identity resolution. Actual 16-bit PSM remains GATT-only |
| Advertisement update fails | Keep previous complete advertisement/hint; never publish partial local state |
| Key rotates during IK | Use a handshake-start binding snapshot; planned old key may finish within grace, otherwise recover by proof chain |
| Hint, RPA, and key rotate while disconnected | Resolve claimed identity, run rotation-recovery XX, validate chain, retain same public PeerIdentity |
| Noise renewal and identity rotation overlap | Identity rotation wins; abort uncommitted renewal and establish fresh session with accepted binding |
| Trust revoke/reset races with callbacks | Cancel peer operations; late callbacks cannot mutate the newer trust generation |
| Route changes during authentication | Continue the same transcript over a valid replacement path or suspend within deadline; never start a second peer authentication |
| Transfer waits during reconnect | Keep transfer keyed by PeerIdentity; fresh hop IK restores path; renew expired E2E session before retransmission |
| Process suspends | Do not wake only for hint/renewal; reconcile restored callbacks before new work |
| Process dies | Discard hints, handles, attempts, routes, transfers, and traffic keys; retain identity/trust/rotation chain/counters |
| App is reinstalled | Create a new installation identity, keys, and hint; never attach old remote trust to the new identity |

## Rotation atomicity

Local long-term rotation is transactional:

1. Generate new keys and both signatures.
2. Persist new binding, proof, generation, and retained old material required by
   the approved grace policy in one recoverable transaction.
3. Only after persistence succeeds, expose the new generation to handshakes and
   route announcements.
4. A crash before commit leaves the old generation current.
5. A crash after commit always has the proof required for recovery.

A security-event rotation closes old-key sessions immediately. Planned rotation
may let already-established sessions drain within the approved grace period;
new sessions use the current generation.

## Required tests

The virtual harness must deterministically permute event order for:

- hint rotation before/during/after scan, GATT, XX, and IK;
- handle rotation with same and changed hints;
- duplicate handles and copied hints;
- power/capability advertisement updates and GATT PSM changes during hint rotation;
- one and multiple missed key generations;
- key rotation during IK and Noise renewal;
- valid duplicate proofs, missing proof, rollback, and forked generation;
- route loss and replacement during each authentication phase;
- revoke/reset racing every discovery callback;
- suspension, restoration, process death, and reinstall; and
- transfer resumption without changing public PeerIdentity.

Real-device proof covers Android RPA changes, iOS restoration, foreground and
background hint updates, screen lock, simultaneous connections, and cross-
platform recovery after key rotation.

## External constraints

- [Android 16 CDD — randomized RPA rotation](https://source.android.com/docs/compatibility/16/android-16-cdd)
- [Bluetooth Core — advertising-data fingerprinting](https://www.bluetooth.com/wp-content/uploads/Files/Specification/HTML/Core-54/out/en/architecture,-mixing,-and-conventions/architecture.html)
- [Apple Core Bluetooth background behavior](https://developer.apple.com/library/archive/documentation/NetworkingInternetWeb/Conceptual/CoreBluetooth_concepts/CoreBluetoothBackgroundProcessingForIOSApps/PerformingTasksWhileYourAppIsInTheBackground.html)

## Related

- [Connectable advertisement](connectable-advertisement.md)
- [Identity binding and fail-closed behavior](../crypto/identity-binding-and-fail-closed.md)
- [Noise session renewal](../crypto/noise-session-renewal.md)
- [Crypto design](../crypto/crypto-design.md)
- [Public API and lifecycle](../api/public-api-and-lifecycle.md)
