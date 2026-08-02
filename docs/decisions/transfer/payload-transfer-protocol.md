# Payload transfer protocol

**Status:** Locked — 2026-07-31

> Normative frame layouts live in
> [specs/codecs/frames.yaml](../../../specs/codecs/frames.yaml) and
> [specs/protocol/state-machines.yaml](../../../specs/protocol/state-machines.yaml).
> This record explains manifests, acceptance, chunking, selective acknowledgements,
> memory, lifetime, and retransmission timeout.

## Why two payload kinds

MeshLink has two finite payload kinds: `MESSAGE` (≤64 KiB, in-memory) and
`TRANSFER` (unbounded, random-access `TransferSource`/`TransferSink`).

**Rationale:** Small messages benefit from automatic acceptance and bounded
memory. Large transfers require explicit host cooperation (source/sink) because
they exceed automatic budgets and need application-defined storage. The kind
field in every frame allows unambiguous demultiplexing.

## Why E2E-encrypted manifest with acceptance

Every payload begins with an E2E-encrypted `PayloadManifest` carrying all
immutable contract fields. Messages up to 64 KiB auto-accept under a 2 MiB
global incomplete-message budget. Large transfers wait in `AWAITING_DECISION`
for a host `TransferSink`.

**Rationale:** The manifest establishes the contract before any data moves —
chunk size, total length, chunk count, priority, and lifetime. Auto-accept for
small messages optimizes the common case (notifications, commands). Explicit
acceptance for large transfers prevents unbounded memory allocation and gives
the host control over storage.

## Why concurrency bounds

`maxTransfersPerPeer` defaults to three (accepted or outgoing in
`AWAITING_DECISION`, `TRANSFERRING`, `ROUTE_UNAVAILABLE`, `RETRANSMITTING`).
Pending incoming offers additionally capped at two per peer, eight globally.
`RECEIVER_BUSY` rejection on overflow.

**Rationale:** Per-peer limit prevents a single malicious/broken peer from
consuming all transfer slots. Global limit bounds total concurrent operations.
The 30-second acceptance timeout ensures stalled offers don't block slots
indefinitely. Terminal operations don't count because they're being cleaned up.

## Why memory bounds

```text
maximumMessageSize             = 64 KiB
incomplete-message global budget = 2 MiB
large-transfer in-flight window  = 256 chunks
```

Messages use an internal bounded sink; large transfers require host
`TransferSource`/`TransferSink`. Oversized manifests rejected before allocation.

**Rationale:** 64 KiB covers typical application messages (JSON, protobuf, CBOR).
2 MiB global budget bounds peak memory from inbound messages. 256-chunk window
bounds sender-side retransmission state. These are hard caps, not soft limits.

## Why origin-owned monotonic lifetime

The origin starts a monotonic timer on send acceptance. `timeToLive: UInt`
milliseconds (not wall-clock). Priority defaults: HIGH 10 min, NORMAL 5 min,
LOW 1 min. Relays forward cut-through with bounded queues; they do not persist
payloads, restart lifetime, or own retransmission state.

**Rationale:** Monotonic lifetime avoids clock sync issues across offline
devices. Priority defaults trade latency for battery. Cut-through relaying
minimizes latency and relay memory — relays forward frames without reassembly.
Origin expiry is final because late acknowledgements cannot resurrect work
after the sender has released resources.

## Why minimal chunks with derived offset/length/finality

```text
PayloadChunk { kind, id, index, payload }
```

Offset = `index × chunkSize`, length = `payload.size`, `isLast = index == chunkCount - 1`. Non-final chunks have exact `chunkSize`; final length equals remaining `totalLength`.

**Rationale:** Carrying only `index` minimizes per-chunk overhead (4 bytes vs
12+ for offset/length/finality). Derivation from manifest eliminates
inconsistency. Exact chunk size enables the fixed 256-chunk ACK window
structure.

## Why sliding selective acknowledgement with fixed 256-bit window

```text
PayloadAcknowledgement { kind, id, start, bitmap: Byte[32] }
```

All indices below `start` are cumulatively acknowledged. Bit `n` acknowledges
`start + n`. Sender keeps at most 256 chunks in flight.

**Rationale:** SACK (RFC 2018) enables selective retransmission of only missing
chunks. Fixed 32-byte bitmap bounds wire size and processing. Cumulative `start`
plus bitmap allows both cumulative and selective ACK in one structure. 256-chunk
window matches the in-flight limit.

## Why adaptive RTO with Karn's rule

Initial RTO: `clamp(1s + hopCount×250ms + powerAckDelay, 1s, 10s)`. Updates use
α=1/8, β=1/4 for smoothed RTT and variation: `RTO = smoothedRtt + max(4×rttVariation, 250ms)`, clamped 1–30 s. Karn's rule excludes retransmitted samples.
Unsuccessful timeout doubles RTO to cap.

**Rationale:** Initial RTO accounts for hop count and power-mode ACK delay.
Adaptive RTO converges to actual path RTT. Karn's rule prevents retransmission
ambiguity from corrupting estimates. Exponential backoff on persistent timeout
prevents congestion collapse. Clamping bounds RTO between reasonable minimum
and maximum.

## Why at-least-once delivery with tombstones

Receiver emits/completes at most once per `(origin, kind, id)`. Completed-ID
tombstones suppress duplicate delivery. Sender reports SUCCESS only after full
acknowledgement. TIMEOUT means confirmation unknown, not proof of non-delivery.

**Rationale:** Exactly-once at the transport layer requires consensus, which is
impractical for offline mesh. At-least-once with idempotent application
processing is the standard model. Tombstones bound duplicate suppression
memory. TIMEOUT ≠ non-delivery because the final ACK could be lost; the
receiver may have completed but the ACK didn't reach the sender.

## Why checked arithmetic for transfer size

`totalLength` 1..`Long.MAX_VALUE`, `chunkSize` 1..`UShort.MAX_VALUE`,
`chunkCount` 1..`UInt.MAX_VALUE`. Checked arithmetic must prove
`ceil(totalLength / chunkSize) == chunkCount`; overflow/inconsistency rejects
before allocation.

**Rationale:** Protocol permits large transfers but implementation must reject
impossible combinations. Checked arithmetic prevents allocation attacks and
integer overflow bugs. The host `TransferSink` may apply additional policy
limits; the SDK only enforces wire representability.

## Why process death discards payloads but preserves trust

Active payloads, handles, timers, windows, and scoreboards are discarded on
process death. Trust persists. Payload transfer does not resume after
restoration.

**Rationale:** Payload state (scoreboards, retransmission timers, in-flight
windows) is complex and tied to specific Noise sessions and transport bearers.
Restoring it correctly across process death is extremely difficult and
error-prone. Trust is simple (identity + keys + timestamps) and essential for
reconnection. New transfers can start immediately after restart; resumption
would add significant complexity for marginal benefit.

## Related

- [Transfer identifier](transfer-identifier.md)
- [GATT channel and framing](../transport/gatt-channel-and-framing.md)
- [Routing design](../routing/routing-design.md)
- [Public API and lifecycle](../api/public-api-and-lifecycle.md)
- [specs/codecs/frames.yaml](../../../specs/codecs/frames.yaml)
- [specs/protocol/state-machines.yaml](../../../specs/protocol/state-machines.yaml)
