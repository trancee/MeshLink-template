# Public API and lifecycle

**Status:** Locked — 2026-07-31

> The normative API shape lives in
> [SPEC.md §2.3](../../../SPEC.md#public-api-surface). This decision record
> explains the instance, lifecycle, observation, and runtime-configuration
> model.

## Environment boundary

`MeshLinkEnvironment` is an opaque public capability supplied to the instance.
Platform factories create it; shared callers never pass Android Context, Core
Bluetooth objects, TransportHandle, private keys, or provider objects through
the protocol API. Internally it owns BLE central/peripheral access, GATT/L2CAP,
secure storage, crypto selection, secure randomness, monotonic time, dispatchers,
radio lease, and background restoration hooks.

## Context

MeshLink must expose one behaviorally identical API on Android and iOS while
keeping platform BLE, storage, and crypto inputs outside shared protocol logic.
The API also needs deterministic ownership for coroutines and radio resources,
support for virtual multi-node tests, and natural Swift interoperation.

## Decision

### Instance and environment

`MeshLink` is a final instance-based class, not a process-wide singleton. Its
constructor accepts immutable settings and an opaque `MeshLinkEnvironment`.
Platform factory functions create the environment without exposing Android or
iOS types to shared protocol code.

Multiple instances may coexist. Virtual environments may run concurrently, but
one physical environment grants its BLE radio lease to only one running
instance. A conflicting start fails with `RadioInUseException`. Stopping or
rolling back a failed start releases the lease.

### Lifecycle

The public states are `UNINITIALIZED`, `CONFIGURED`, `RUNNING`, `PAUSED`, and `STOPPED`.
Lifecycle commands are suspending, serialized, restartable after stop, and
idempotent when already at their target state. The constructor transitions
`UNINITIALIZED` to `CONFIGURED`; `start()` transitions `CONFIGURED` to `RUNNING`.
Internal transitional states do not expand the public state machine.

Pause retains the environment lease and in-memory protocol state while stopping
new discovery and transfer admission. Stop releases radio resources, clears
ephemeral routes and transfers, and retains only required persisted identity and
trust state.

### Observation

The public observable surface is state-oriented where a current value exists
and event-oriented where values represent occurrences:

`messages` is a hot, non-replaying flow. It emits only complete authenticated
messages, at most once per `(origin, MESSAGE, id)` while receiver tombstones
remain. Late collectors receive future messages only. Delivery uses bounded
buffers and never emits partial payloads; collector cancellation does not stop
MeshLink or other collectors.

```kotlin
val state: StateFlow<MeshLinkState>
val knownPeers: StateFlow<List<KnownPeer>>
val transfers: StateFlow<List<Transfer>>
val messages: Flow<Message>
val diagnostics: Flow<DiagnosticEvent>
```

`KnownPeer` exposes `PeerState` and `PeerTrust` snapshots; it never exposes keys, generations, hints, handles, routes, or platform state.

`knownPeers` emits complete immutable snapshots sorted by PeerIdentity. One
serialized per-peer transition produces one atomic snapshot; hint/handle changes,
key rotation, and reconnect do not remove/re-add or duplicate entries.

`knownPeers` includes peers whose canonical identity is known across unverified,
verifying, trusted, mismatched, and revoked states. Advertisement-only candidates
are not canonical peers. Trusted, mismatched, and revoked records remain visible as disconnected;
transient unverified/verifying observations are removed when their work ends.

The application sees one stable PeerIdentity per remote installation. Rotating
peerHint, TransportHandle, cryptographic keys, key generations, proof chains,
Noise epochs, and route next hops remain internal. Valid reconnects, key
rotations, route changes, and session renewals never require the application to
replace an identity or supply key material.

For peer times, `seenAt` is immutable and means the first observation of the
full canonical identity. `verifiedAt` is nullable and means the latest
successful authentication. Event instants use a past-participle verb ending in
`At`; future boundaries retain future semantics such as `expiresAt`.

Diagnostics remain events rather than a retained health history. Completed
messages and incoming transfer offers are never silently dropped; bounded queue
exhaustion rejects new incoming work explicitly. Diagnostic delivery may shed
low-severity events only with a summarized overflow event.

### Messages and transfers

`transfers` exposes one immutable public `Transfer` snapshot type for both finite
MESSAGE and TRANSFER operations:

```kotlin
class Transfer {
    val kind: PayloadKind
    val id: UInt
    val status: StateFlow<TransferStatus>
}
```

The enclosing `kind` supplies the ID namespace. `sendMessage` accepts an
in-memory payload and returns `MessageHandle`.
Delivered `Message` values expose contextual `id: MessageId`, stable
`origin: PeerIdentity`, and local `completedAt: Instant`; relay/source transport
identity and sender-provided time are not exposed.
`sendTransfer` accepts a random-access `TransferSource` and returns
`TransferHandle`. Incoming finite transfers appear as `IncomingTransfer` while
awaiting a host-provided `TransferSink`; `accept(sink)` and `reject()` are
idempotent. Both handles expose one atomic `StateFlow<TransferStatus>`, an
awaitable terminal outcome, and explicit idempotent cancellation. `TransferStatus`
contains `state`, `offset`, `total`, `retryCount`, and nullable
`transferResult`. `offset` is the highest contiguous payload boundary credited
as acknowledged outgoing or sink-accepted incoming data; out-of-order progress
remains represented by SACK state.

The v0.1 payload kinds are `MESSAGE` and `TRANSFER`; open-ended streams are out
of scope. `MessageId` and `TransferId` are distinct semantic types that share
the same four-byte wire slot according to payload kind. See the
[transfer identifier decision](../transfer/transfer-identifier.md).

Both send methods accept optional `TransferOptions.DEFAULT`. Initial options are
priority and an optional positive time-to-live override. Chunk sizing, bearer
selection, retry algorithms, compression, and cryptographic modes are not
per-transfer options.

### Error delivery

Immediate command failures use stable `MeshLinkException` subtypes with an
`ErrorCode`. Platform exceptions are wrapped at their boundary. Long-running
transfer failures are terminal transfer outcomes, not exceptions emitted later
by a send call. Coroutine cancellation remains coroutine cancellation.

### Configuration validation

Static settings are validated during construction and invalid values throw a typed
`ConfigurationException` before an instance is returned. Validation covers
appId normalization/length, positive durations, route expiry versus digest
interval, route and transfer limits, maxRoutes, maxTransfersPerPeer, chunk
bounds, grace periods, and diagnostic buffer size.

Runtime prerequisites are validated by `start()`: permissions, Bluetooth
availability, protected key storage, provider self-tests, background integration,
and the physical-radio lease. Security invariants are not disableable through
production settings.

### Time, randomness, and testability

Production environments enforce platform monotonic time and CSPRNG sources for
deadlines, renewal, identities, keys, peerHint, IDs, and nonces. Applications
cannot replace them through the public API. The internal virtual environment may
inject deterministic clock, test-only random source, scheduler, virtual BLE
delivery/faults, process restart, storage faults, and provider failures for
repeatable tests.

## Trust commands

`resetTrust(peerIdentity)` deletes the peer's current binding and rotation
position, cancels active work, and permits future XX/automatic TOFU. It never
changes local identity or keys.

`revokeTrust(peerIdentity)` cancels active work, persists a blocking REVOKED
record, and rejects future XX/IK/rotation recovery. Only explicit reset permits
trust again. Neither command accepts or exposes keys.

### Runtime configuration

Settings are immutable for an instance except for power mode. Applications may
call `setPowerMode`; all routing, security, regulatory, persistence, backgroundOperation,
diagnostics capacity, and transfer defaults remain fixed.

`PowerMode.settings` contains nominal mode values. `powerMode` exposes the
successfully selected mode, while `effectivePowerSettings` exposes values after
regulatory and platform clamping. A failed update leaves both at their previous
successful values. Existing transfers retain their established chunk framing;
new transfers and connections use the updated settings.

Diagnostics are consumed through `diagnostics`; settings do not expose an event
callback. Optional platform logging remains part of `DiagnosticsSettings`.

## Why not a singleton

A singleton hides process-wide mutable state, prevents isolated tests, makes
resource teardown ambiguous, and cannot represent multiple virtual nodes. An
instance with an explicit environment makes ownership visible and testable.

## Why state snapshots and event flows differ

Peers and active transfers have meaningful current state, so `StateFlow`
prevents callers from reconstructing it from potentially missed events.
Messages and diagnostics are delivered occurrences, so retaining an ever-growing
state list would waste memory and misrepresent their semantics.

## Related

- [Diagnostic flow delivery](../diagnostics/flow-delivery.md)
- [Peer hints and identity races](../discovery/peer-hint-and-identity-races.md)
- [Payload identity and naming](../transfer/payload-identity-and-naming.md)
- [Background operation](../transport/background-operation.md)
- [Transfer identifier](../transfer/transfer-identifier.md)
- [Settings DSL](../model/settings-model.md)
- [Exception hierarchy](../model/error-hierarchy.md)
- [SPEC.md §14 — Settings](../../../SPEC.md#settings-model)
