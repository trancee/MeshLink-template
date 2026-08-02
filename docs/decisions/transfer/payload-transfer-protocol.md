# Payload transfer protocol

**Status:** Locked — 2026-07-31

> Normative frame layouts live in
> [specs/codecs/frames.yaml](../../../specs/codecs/frames.yaml). This record
> explains manifests, acceptance, chunking, selective acknowledgements, memory,
> lifetime, and retransmission timeout.

## Payload kinds and identifiers

MeshLink has two finite payload kinds:

```text
MESSAGE
TRANSFER
```

Every payload frame carries `kind`. MessageId and TransferId are independent
source-owned UInt domains. Complete identity is `(origin, kind, id)`. Unknown
kind or an ID whose kind conflicts with its accepted manifest fails closed.

## Manifest and acceptance

Every payload begins with an E2E-encrypted manifest:

```text
PayloadManifest {
    kind
    id
    origin
    destination
    priority
    timeToLive
    totalLength
    chunkSize
    chunkCount
}
```

Messages up to 64 KiB are automatically accepted when memory/capacity allows.
Large transfers appear as `IncomingTransfer` and require a host-provided
TransferSink before chunks may start.

```text
maximum pending offers per peer = 2
maximum pending offers globally = 8
acceptance timeout              = 30 seconds
```

`PayloadDecision` communicates ACCEPTED or REJECTED with a typed reason. The
sender transmits no chunks before acceptance. Duplicate identical manifests
join one pending operation; a conflicting manifest for the same identity fails
closed.

## Concurrency bounds

`TransferSettings.maxTransfersPerPeer` defaults to three and includes accepted or
outgoing MESSAGE and TRANSFER operations in AWAITING_DECISION, TRANSFERRING,
ROUTE_UNAVAILABLE, or RETRANSMITTING. Terminal operations do not count. Pending
incoming offers additionally obey two per peer and eight globally. Capacity
rejection uses RECEIVER_BUSY.

## Memory bounds

```text
maximumMessageSize             = 64 KiB
incomplete-message global budget = 2 MiB
large-transfer in-flight window  = 256 chunks
```

Messages use an internal bounded sink and are emitted only after complete E2E
authentication/reassembly. Larger payloads require TransferSource/TransferSink.
Incoming oversized manifests are rejected before payload allocation. Defensive
copies count against delivery-queue memory.

## Origin-owned lifetime

The origin starts a monotonic timer when send is accepted. The manifest carries
`timeToLive: UInt` in milliseconds, not a wall-clock timestamp. Priority defaults
are HIGH 10 minutes, NORMAL 5 minutes, and LOW 1 minute.

Destination acceptance/receive timers start on manifest receipt and never
exceed the advertised duration. Origin expiry is final: late acknowledgements
cannot resurrect work, and best-effort PayloadCancellation is sent.

Relays forward cut-through with bounded current-frame queues. They do not
persist payloads, restart lifetime, own retransmission state, or reassemble E2E
content. Route loss returns control to the origin, which enters ROUTE_UNAVAILABLE.

## Minimal chunks

```text
PayloadChunk {
    kind
    id
    index
    payload
}
```

Offset, length, and finality derive from manifest fields:

```text
offset = index × chunkSize
length = payload.size
isLast = index == chunkCount - 1
```

Non-final chunks have exact chunkSize; final length equals remaining totalLength.
Duplicate authenticated identical chunks are idempotent. Conflicting duplicate
content fails the payload. Chunks before accepted manifest are rejected without
allocation.

## Sliding selective acknowledgement

```text
PayloadAcknowledgement {
    kind
    id
    start
    bitmap: Byte[32]
}
```

All chunk indices below `start` are cumulatively acknowledged. Bit `n`
acknowledges `start + n`. Bits beyond chunkCount are zero. Sender keeps at most
256 chunks in flight and rereads missing chunks through TransferSource.

ACK emits after 32 newly received chunks or the power-aware maximum delay:

```text
HIGH    100 ms
MEDIUM  250 ms
LOW     500 ms
```

It emits immediately on gap detection, full receive window, final chunk, or
retransmission probe.

## Retransmission timeout

RTO means **retransmission timeout**: how long the sender waits without adequate
acknowledgement before deciding that one or more in-flight chunks may be lost.
It is not the transfer timeToLive, route expiry, acceptance timeout, or GATT
fragment timeout.

Initial value:

```text
initialRto = clamp(
    1 second + hopCount × 250 ms + powerModeAckDelay,
    1 second,
    10 seconds,
)
```

For the first valid non-retransmitted RTT sample `R`:

```text
smoothedRtt = R
rttVariation = R / 2
```

For later samples (integer-duration equivalents of α=1/8 and β=1/4):

```text
rttVariation = 3/4 × rttVariation + 1/4 × abs(smoothedRtt - R)
smoothedRtt  = 7/8 × smoothedRtt  + 1/8 × R
rto = smoothedRtt + max(4 × rttVariation, 250 ms)
```

Clamp RTO to 1–30 seconds. Do not sample retransmitted chunks (Karn's rule).
Each unsuccessful timeout doubles the current RTO up to 30 seconds. A valid
non-retransmitted ACK updates estimates.

Gap ACK may retransmit selectively before RTO. The transfer coordinator
serializes gap and timeout events so one chunk is not redundantly scheduled by
both. Route/next-hop change discards or heavily penalizes the old estimator;
L2CAP-to-GATT fallback resets bearer-specific estimates. RTO never extends
remaining timeToLive.

## Status and retry semantics

Public `TransferStatus` contains immutable `state`, `offset`, `total`,
`retryCount`, and nullable `deliveryOutcome`. `offset` is the highest
contiguous payload boundary credited by acknowledgement or sink acceptance;
out-of-order progress remains in SACK state.
`retryCount` counts payload-level retransmission rounds, where one round may
send multiple missing chunks. It excludes GATT operation retries, Noise
handshake retries, route-sequence advancement, L2CAP circuit-breaker attempts,
and platform retries. It is monotonic for one payload operation and remains
readable through its handle after terminal collection removal.

## Delivery semantics

Receiver emits/completes at most once per `(origin, kind, id)`. Completed-ID
tombstones suppress duplicate delivery while duplicate manifests/chunks remain
idempotent. Sender reports SUCCESS only after an acknowledgement proves every
chunk received.

TIMEOUT means confirmation was not obtained before timeToLive; it does not prove
the receiver never completed delivery. Lost final ACK causes probe/retransmit
and repeated final ACK without redelivery. Durable business-level exactly-once
processing requires an application identifier inside the payload.

## Transfer size

Protocol representation permits totalLength 1..Long.MAX_VALUE, chunkSize
1..UShort.MAX_VALUE, and chunkCount 1..UInt.MAX_VALUE. Checked arithmetic must
prove `ceil(totalLength / chunkSize) == chunkCount`; overflow or inconsistency
rejects before allocation.

No buffer scales with totalLength. The host TransferSink may reject for
insufficient storage or policy, while the SDK retains only bounded metadata and
the 256-chunk window.

## Cancellation and process behavior

PayloadCancellation applies to messages and transfers and is idempotent. Process
death discards active payloads, handles, timers, windows, and scoreboards. Trust
persists, but payload transfer does not resume after restoration.

## Required tests

- Message 0/1/64 KiB boundaries and oversized rejection
- Offer per-peer/global limits, timeout, absent handler, and sink failure
- Manifest duplicate/conflict and kind mismatch
- Every chunk length/index/final-boundary case
- 256-bit ACK window shifts, cumulative start, tail bits, and duplicate ACKs
- ACK emission by count, power delay, gap, full window, final, and probe
- Initial RTO by hop/power boundaries
- Smoothed RTT/variation exact integer vectors
- Karn behavior, exponential cap, route/bearer reset, and TTL bound
- Simultaneous gap/timeout race
- Route loss, L2CAP fallback, process death, cancellation, and late ACK
- Bounded memory under maximum peers/transfers

## Related

- [Transfer identifier](transfer-identifier.md)
- [GATT channel and framing](../transport/gatt-channel-and-framing.md)
- [Routing design](../routing/routing-design.md)
- [Public API and lifecycle](../api/public-api-and-lifecycle.md)
