# Connectable discovery advertisement

**Status:** Locked — 2026-07-31

> The normative field layout lives in
> [SPEC.md §4.1](../../../SPEC.md#41-advertisement-format). This decision record
> explains why MeshLink uses a connectable advertisement and how the
> two-service-UUID design degrades safely in the background.

## Context

MeshLink must discover a nearby peer before it can establish GATT, authenticate
that peer with Noise, exchange control traffic, and optionally open an LE L2CAP
credit-oriented channel. A non-connectable advertisement can announce data but
cannot accept the connection request required by those later steps.

Core Bluetooth also restricts background advertising. Service UUIDs may move to
an overflow area and are only discoverable by an iOS scanner that explicitly
requests them. A dynamic metadata UUID therefore cannot be the only way to
obtain connection metadata.

## Decision

MeshLink emits a **connectable, undirected legacy BLE advertisement** while its
runtime is available for peer connections.

The advertisement carries two service UUIDs:

1. `0x4D455348` (`"MESH"`) is the fixed 32-bit protocol marker and the known
   scan-filter value.
2. A dynamic 128-bit UUID carries the 16-byte discovery metadata defined in
   SPEC.md.

`0x4D455348` is intentionally a private, unassigned 32-bit UUID. MeshLink does
not represent it as Bluetooth SIG-assigned. A future SIG assignment or a
confirmed collision requires an advertised protocol-version migration rather
than silent reinterpretation.

The fixed service contains metadata characteristic
`4D455348-0001-1000-8000-00805F9B34FB` and bidirectional channel characteristic
`4D455348-0002-1000-8000-00805F9B34FB`. Component `0000` is the service;
`0003`–`00FF` are reserved and assigned values are never reused.

The dynamic UUID is a discovery fast path, not an authentication credential. If
a platform does not surface it, the fixed MeshLink GATT service exposes the full
PeerIdentity, version, key generation, 16-bit PSM, and nonce after connection. The
rotating peerHint remains advertisement-only. The security handshake
authenticates identity and keys; neither advertisement nor initial GATT metadata
establishes trust by itself.

## Why connectable

A connectable advertisement permits the central to establish the BLE ACL link
needed for:

- the GATT metadata fallback;
- the hop-by-hop Noise handshake;
- routing and transfer control traffic over GATT; and
- obtaining the authoritative 16-bit L2CAP PSM from transcript-bound GATT metadata and opening the channel when both peers support it.

Connectability grants transport access only. It does not grant MeshLink trust.
Unauthenticated connections remain subject to bounded connection counts,
handshake timeouts, rate limits, and immediate closure on malformed traffic.

## Alternatives rejected

### Non-connectable discovery followed by a mode switch

The scanning peer has no portable signal with which to ask the advertiser to
switch modes. Both peers would need another scan cycle, increasing latency and
introducing races during background suspension.

### Simultaneous connectable and non-connectable advertising sets

Some Android controllers support multiple advertising sets, but Core Bluetooth
does not expose a matching portable contract. Depending on this would violate
cross-platform parity.

### Dynamic UUID as the only metadata source

iOS background advertising may place service UUIDs in an overflow area. A
scanner can explicitly request the fixed marker but cannot know the dynamic UUID
in advance. The fixed GATT metadata characteristic is therefore the required
fallback.

## Duplicate-connection arbitration

Before identity resolution, retain the first viable connection provisionally
and coalesce callbacks by TransportHandle and peerHint. After GATT claims full
identities, compare PeerIdentity values lexicographically: the lower identity is
the preferred central and Noise initiator, and the duplicate link not matching
that assignment closes before concurrent authentication can start.

Equal full identities across two installations fail closed. If only one viable
connection exists, retain it regardless of preferred role; ordering resolves
duplicates rather than forcing avoidable reconnects. Role selection grants no
trust and canonical merging occurs only after Noise succeeds.

## Operational consequences

- The advertisement contains no local name or optional fields that would crowd
  out the two service UUIDs.
- The advertisement carries only an L2CAP-available capability bit. GATT carries
  a 16-bit PSM; v0.1 accepts `0x0000` or the dynamic range `0x0080` through
  `0x00FF` without truncation.
- Advertisement metadata is validated as untrusted input.
- Inconsistent protocol, app, or L2CAP-availability metadata emits a diagnostic
  and cannot create or update trust. Rotating peerHint is not expected in GATT.
- Android↔Android, Android↔iOS, and iOS↔iOS device tests must cover foreground,
  background, and screen-locked discovery.

## Related

- [Mesh hash derivation](mesh-hash-derivation.md)
- [Peer hints and identity races](peer-hint-and-identity-races.md)
- [Transport bearer and MTU decision](../transport/mtu-negotiation.md)
- [SPEC.md §4 — Discovery and identity](../../../SPEC.md#4-discovery--identity)
- [Wire-frame machine-readable specification](../../../specs/codecs/frames.yaml)
