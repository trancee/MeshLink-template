# GATT channel and bearer framing

**Status:** Locked — 2026-07-31

> Normative UUIDs and binary layouts live in
> [specs/codecs/frames.yaml](../../../specs/codecs/frames.yaml). This record
> explains GATT roles, metadata, operation ordering, fragmentation, concurrent
> peers, and L2CAP stream framing.

## Service and characteristics

MeshLink uses its private unassigned service namespace:

```text
Service:  4D455348-0000-1000-8000-00805F9B34FB
Metadata: 4D455348-0001-1000-8000-00805F9B34FB
Channel:  4D455348-0002-1000-8000-00805F9B34FB
```

Component `0000` is the service, `0001` metadata, `0002` channel, and
`0003`–`00FF` reserved. Assigned components are never reused.

Metadata is read-only. Channel supports write, write without response, notify,
and indicate. BLE central maps to GATT client; peripheral maps to GATT server.

## Bootstrap metadata

```text
GattMetadata {
    version: UByte
    appHash: Byte[16]
    identity: Byte[16]
    keyGeneration: UInt
    psm: UShort
    nonce: Byte[16]
}
```

The record is untrusted until its canonical bytes and nonce are mixed into and
validated by the Noise transcript. PeerIdentity/keyGeneration are claims used
only to select IK, XX, or rotation recovery; keyGeneration never changes trust
alone. `psm` is authoritative only after
Noise; v0.1 accepts `0x0000` or `0x0080`–`0x00FF` while retaining a 16-bit width.

### PSM allocation policy

The 16-bit `psm` field is **never a static, application-chosen value**. L2CAP CoC
PSMs in the dynamic range are assigned by the operating system when the server
registers an L2CAP CoC service. On Android, `BluetoothServerSocket` with an
`L2CAP` socket type receives a PSM from the Bluetooth stack. On iOS,
`CBL2CAPChannel` exposes a PSM after the channel is published.

`0x0000` indicates "L2CAP not available on this peer/installation" — the central
falls back to GATT for all traffic. `0x0080`–`0x00FF` is the v0.1 dynamic range
accepted without truncation when both peers support L2CAP.

The advertisement carries only an L2CAP-availability capability bit (see ADR
`connectable-advertisement.md`). The actual PSM is obtainable only from trusted
GATT metadata after Noise authentication, which prevents an attacker from
redirecting L2CAP traffic to an arbitrary PSM.

## Subscription gate

Before Noise:

1. Connect GATT.
2. Discover service and both characteristics.
3. Read/validate bounded metadata structure.
4. Enable Channel notifications/indications through CCCD.
5. Confirm subscription.
6. Begin Noise.

Server-originated work remains in a bounded queue until subscription succeeds.
Overflow fails the attempt; handshake/control records are never silently
dropped.

## Direction and reliability

```text
client → server:
    write with response    = control
    write without response = fallback data

server → client:
    indication             = control
    notification           = fallback data
```

Transfer SACK supplies application reliability for fallback data. One
connection-local scheduler prioritizes handshake/renewal, trust/rotation,
routing, transfer control, then transfer data. Only one response-requiring GATT
operation and one indication may be outstanding per connection. Platform
backpressure pauses producers.

## Connection context

Every physical connection owns:

```text
ConnectionContext {
    transportHandle
    generation
    negotiatedMtu
    subscriptionState
    operationQueue
    inboundReassembly
    outboundQueue
    authenticationState
}
```

`generation` is a monotonically increasing process-local value scoped by
ConnectionContext. It distinguishes a new connection when the platform reuses a
TransportHandle. The internal key is `(TransportHandle, generation)`.
Generation is not keyGeneration, a wire value, persisted state, or public API.
Closing a connection invalidates its complete context and late callbacks.

## Concurrent peers

Reassembly and scheduling are per connection and direction, so each peer may
send `index == 0` simultaneously without collision. Android callbacks map the
specific BluetoothDevice to a context; iOS maps CBCentral/CBPeripheral.
Outbound notifications/indications always target one peer and never broadcast a
peer-specific frame to all subscribers.

Duplicate physical links start separate contexts. After claimed identity
resolution, the per-peer coordinator retains one deterministic link and closes
the other. Fragment state never migrates between contexts.

## GATT fragment codec

```text
GattFragment {
    index: UShort

    if index == 0:
        totalLength: UShort

    payload: remaining bytes
}
```

There are no flags or record identifiers. Start is `index == 0`; completion is
`accumulatedLength == totalLength`. Maximum encoded frame length is 65,535 bytes.
First-fragment overhead is four bytes; continuation overhead is two.

Rules:

- Start length is 1–65,535 and validates before allocation.
- Continuations require active state and exactly previous index + 1.
- Empty continuation, index wrap, gap, overflow, timeout, or conflicting
  duplicate discards the record.
- An identical latest-fragment duplicate may be idempotent.
- A new start aborts an incomplete record and begins a new one.
- After failure, non-start fragments are ignored until the next valid start.
- Completion emits exactly one MeshLink Wire Codec frame; partial bytes never
  reach protocol logic.
- Fragments for one record are contiguous; scheduler preemption occurs between
  records.
- Large RouteSnapshot results paginate into bounded MeshLink records.
- Transfer chunks are sized to fit one GATT fragment when possible.

## Reassembly resource limits

```text
pre-auth maximum frame:   4 KiB
post-auth maximum frame: 65,535 bytes
active inbound record:    one per connection
active outbound record:   one per connection
```

Global memory remains bounded by the active-connection limit. An unauthenticated
peer cannot allocate a full post-auth frame. Timeouts and disconnects release
buffers.

## L2CAP framing

L2CAP uses the same MeshLink frame bytes with a two-byte little-endian length:

```text
frameLength: UShort
frameBytes: Byte[frameLength]
```

Partial stream reads accumulate until exact length. EOF, timeout, zero/invalid
length, or overflow discards the partial frame and enters the accepted L2CAP
health/fallback path.

Bearer framing is outside signatures; canonical MeshLink frame bytes are signed
or digested where required.

## Required tests

- MTU 23 and larger boundary vectors
- One-fragment and maximum-length records
- Every truncation/index/gap/duplicate/overflow condition
- New start during incomplete record
- Pre-auth 4 KiB rejection and post-auth acceptance
- Concurrent index-zero fragments from maximum active peers
- Reused TransportHandle with stale generation callbacks
- Targeted Android/iOS notifications with no cross-peer broadcast
- Operation priority and round-robin peer fairness
- L2CAP partial read/EOF/stall and GATT fallback
- Byte-identical reassembled MeshLink frames across bearers/platforms

## Related

- [Connectable advertisement](../discovery/connectable-advertisement.md)
- [Background operation](background-operation.md)
- [MTU and L2CAP health](mtu-negotiation.md)
- [MeshLink Wire Codec](../../explanation/why-meshlink-wire-codec.md)
