# Transfer Layer

> **Specification**: [SPEC.md §9](../../SPEC.md#transfer-layer)  
> **Design rationale**: [Payload Transfer Protocol](../decisions/transfer/payload-transfer-protocol.md), [Transfer Identifier](../decisions/transfer/transfer-identifier.md)

## Source and Sink

A `TransferSource` supplies outgoing data to MeshLink:

```text
application storage → TransferSource → MeshLink → network
```

MeshLink calls `source.read(offset, length)`. The source has a fixed `size` and
must support random-access rereads because SACK may identify an arbitrary
missing chunk for retransmission. MeshLink does not close or own the source.

A `TransferSink` receives incoming data from MeshLink:

```text
network → MeshLink → TransferSink → application storage
```

MeshLink calls `sink.write(offset, bytes)`. Writes may arrive out of order, so a
sink must support random-access writes or provide its own ordering/storage
mechanism. Identical duplicate writes are idempotent; conflicting bytes fail the
transfer. MeshLink calls `complete()` exactly once on success or `abort()` at
most once after failure/cancellation. The application owns the sink resource.

The source is read by MeshLink; the sink is written by MeshLink. Neither is
persisted or closed by the library.

See the [Transfer Source and Sink Contract](../decisions/transfer/transfer-source-sink-contract.md).

## Payload Kinds

- `MESSAGE`: bounded in-memory payload, maximum 64 KiB
- `TRANSFER`: finite random-access TransferSource/TransferSink payload

Every frame carries kind. Identity is `(origin, kind, id)`, where id is
MessageId or TransferId.

## Manifest and Acceptance

Every payload begins with E2E-encrypted PayloadManifest. Messages auto-accept
when the 2 MiB incomplete-message budget permits. Transfers wait in
AWAITING_DECISION for a host sink.

Limits:

```text
pending offers per peer = 2
pending offers globally = 8
acceptance timeout       = 30 seconds
```

No chunk is sent before PayloadDecision ACCEPTED.

## Chunks and SACK

PayloadChunk contains only kind, id, index, and payload. Offset, length, and
finality derive from manifest totalLength/chunkSize/chunkCount.

PayloadAcknowledgment uses `start` plus a fixed 32-byte bitmap. All lower chunk
indices are cumulatively acknowledged; bit n represents start+n. Sender keeps at
most 256 chunks in flight.

ACK emits after 32 chunks, after the power-aware 100/250/500 ms maximum delay,
or immediately for gap, full window, final chunk, or retransmission probe.

## Progress

Transfer status uses `offset` and `total`. `offset` is the highest contiguous
payload boundary acknowledged by the remote or accepted by the sink. Out-of-
order chunks do not advance it until gaps are filled; `offset == total` at
successful completion.

## Delivery Lifetime

`TransferOptions.timeToLive` is a monotonic elapsed duration encoded as UInt
milliseconds. HIGH, NORMAL, and LOW default to 10, 5, and 1 minutes. It is
independent of the fixed 16-hop routing limit.

Relays forward cut-through with bounded queues and do not persist/reassemble E2E
payloads or own retry state. Origin expiry is final.

## Retransmission Timeout

RTO is the sender's retransmission timeout—not transfer timeToLive. Initial RTO
uses hop count and ACK delay. Valid non-retransmitted ACKs update smoothed RTT
and RTT variation; RTO is clamped to 1–30 seconds. Karn's rule excludes
retransmitted samples, and repeated timeout doubles RTO to the cap. Route/bearer
change resets the relevant estimator.

## Lifecycle

```text
AWAITING_DECISION → IN_PROGRESS
IN_PROGRESS ↔ WAITING_FOR_ROUTE / RETRYING
IN_PROGRESS → COMPLETED / CANCELLED / FAILED / TIMED_OUT
```

Process death discards active payloads. Persisted trust allows fresh sessions,
not transfer resumption.

## Wire Frames

| Frame | Code |
|-------|------|
| `PAYLOAD_MANIFEST` | `0x20` |
| `PAYLOAD_DECISION` | `0x21` |
| `PAYLOAD_CHUNK` | `0x22` |
| `PAYLOAD_ACKNOWLEDGMENT` | `0x23` |
| `PAYLOAD_CANCELLATION` | `0x24` |

All are E2E Noise records inside hop-encrypted MESH_ENVELOPE.

## Quick Links

- [SPEC.md §9](../../SPEC.md#transfer-layer)
- [Payload Transfer Protocol ADR](../decisions/transfer/payload-transfer-protocol.md)
- [Transfer Identifier ADR](../decisions/transfer/transfer-identifier.md)
- [State Machines](../../specs/protocol/state-machines.yaml)
- [Codec Frames](../../specs/codecs/frames.yaml)
