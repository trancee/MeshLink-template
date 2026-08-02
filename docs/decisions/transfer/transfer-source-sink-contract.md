# Transfer source and sink contract

**Status:** Locked — 2026-07-31

## Source

```kotlin
public interface TransferSource {
    public val size: Long

    public suspend fun read(
        offset: Long,
        length: Int,
    ): ByteArray
}
```

`size` is fixed and positive. MeshLink requests valid ranges within the source;
the final range may be shorter than the configured chunk size. A source may be
read again for arbitrary missing chunks after selective acknowledgement.

One source is not read concurrently by MeshLink. The application must return
exactly the requested bytes for each non-final range and must not block
indefinitely. Coroutine cancellation propagates through the read.

## Sink

```kotlin
public interface TransferSink {
    public suspend fun write(
        offset: Long,
        bytes: ByteArray,
    )

    public suspend fun complete()
    public suspend fun abort(cause: MeshLinkException?)
}
```

Writes may arrive out of order because route paths and chunks may reorder.
MeshLink serializes calls for one sink. An identical duplicate write is
idempotent; conflicting bytes for the same range fail the transfer.

`complete()` occurs exactly once after every payload range is accepted.
`abort()` occurs at most once and may follow partial writes. Application
exceptions are wrapped into typed transfer failures and never leak platform
exception text.

MeshLink does not close, own, or persist application resources. The host owns
file/database lifecycle and must keep the source/sink valid until the handle
reaches a terminal state or abort completes.

## Progress semantics

Public transfer status uses contextual names:

```kotlin
val offset: Long
val total: Long
```

`offset` is the highest contiguous payload boundary acknowledged by the remote
for outgoing data or accepted by the sink for incoming data. Out-of-order
chunks may exist beyond offset without advancing it; SACK state represents that
separate progress. `offset` starts at zero, never exceeds `total`, and equals
`total` at successful completion.

## Security and bounds

The manifest validates total size, chunk size, chunk count, and timeToLive before
source/sink calls. No source/sink callback receives keys, peer hints, transport
handles, route state, or unencrypted data from a different authenticated
payload identity. SDK memory remains bounded by the active chunk window.

## Related

- [Payload transfer protocol](payload-transfer-protocol.md)
- [Public API and lifecycle](../api/public-api-and-lifecycle.md)
- [GATT channel and framing](../transport/gatt-channel-and-framing.md)
