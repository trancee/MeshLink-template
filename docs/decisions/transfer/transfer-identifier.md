# Transfer identifier scope and width

**Status:** Locked — 2026-07-31

> The normative model and frame fields live in
> [SPEC.md §§3.5 and 3.8](../../../SPEC.md#transfer-session-model). This
> decision record explains why transfer identifiers are 32-bit and scoped by
> the authenticated origin rather than globally unique session tokens.

## Context

A logical transfer can survive chunk retries, route changes, temporary link
loss, and GATT-to-L2CAP migration. Its identifier must therefore remain
separate from shorter-lived BLE, Noise, and handshake sessions.

The identifier appears in every transfer chunk, acknowledgement, cancellation,
and retry frame. Its repeated wire cost therefore matters on BLE. It is not an
authorization token: possession or prediction of an identifier must never
permit a peer to inject, acknowledge, or cancel transfer data.

## Decision

Rename the transfer-domain identifier to `TransferId` and encode it as an
unsigned 32-bit value.

A transfer is uniquely identified by the tuple:

```text
(authenticated origin PeerIdentity, TransferId)
```

The origin owns one monotonically increasing counter. `0` is reserved as an
invalid placeholder, so allocation begins at `1`. The counter is scoped to the
stable per-install `PeerIdentity`; reinstalling creates a new identity and may
restart the counter.

## Allocation and persistence

The allocator reserves counter ranges durably before using them. For example,
it may persist a high-water mark of `1025` and allocate values `1` through
`1024` in memory. Before using `1025`, it persists the next range. A crash can
therefore create harmless gaps but cannot reuse an allocated identifier.

The range size is an implementation and benchmark decision, not part of the
wire contract. Storage corruption fails closed; it must not silently reset the
counter under an unchanged `PeerIdentity`.

## Replay and wrap-around

Receivers key active state and completed-transfer tombstones by the full
`(origin, transferId)` tuple. A duplicate transfer-open frame with an
inconsistent authenticated manifest is rejected.

Tombstones are retained for at least the maximum transfer lifetime and replay
retention period. Old encrypted frames cannot cross a newly established E2E
key epoch. At `UInt.MAX_VALUE`, allocation wraps past reserved zero and may use
a candidate only when it is absent from active state and retained tombstones.
Four billion transfers within one retention window are outside the supported
capacity.

## Security properties

- `TransferId` is correlation data, not a capability or secret.
- Every operation using it also requires an authenticated origin and valid
  transfer state.
- Predictability does not weaken confidentiality, integrity, cancellation, or
  acknowledgement authorization.
- The counter reveals ordering to peers already able to observe transfer
  traffic; hiding transfer volume is not a v0.1 requirement.

## Why not random 32-bit identifiers

Random 32-bit values accumulate birthday-collision risk. A source-owned counter
is collision-free before wrap-around and needs no collision-retry protocol.

## Why not 64 or 128 bits

The authenticated `PeerIdentity` already supplies the namespace. A globally
unique identifier would duplicate that context. Four bytes are saved versus a
64-bit value in every repeated transfer frame, while no relevant identity or
security information is lost.

## Naming boundaries

`TransferId` is used only for logical payload transfers. Noise sessions and
individual handshake attempts use domain-specific internal identifiers if they
need diagnostic correlation; they do not reuse `TransferId`.

## Related

- [Transfer reference](../../reference/transfer.md)
- [Data model decisions](../model/data-model.md)
- [SPEC.md §9 — Transfer layer](../../../SPEC.md#9-transfer-layer)
- [Wire-frame machine-readable specification](../../../specs/codecs/frames.yaml)
