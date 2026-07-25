# GATT as the always-available control plane, L2CAP CoC as the preferred data plane

This is a design decision record, not an implementation status report. It
fixes the transport-selection policy MeshLink's BLE bearer layer must
implement, so the handshake, routing, and chunked-transfer layers can be
built against a stable contract instead of each feature choosing a bearer
ad hoc. [Destination-sourced route freshness, IHU cost signal removal, and
digest-triggered resync](../routing/destination-sourced-seqno-ihu-removal-digest-resync-design.md)
explicitly named "transport bearer selection" as out of scope for that
design; this record is where that choice is made.

## Scope

Covers which BLE bearer — GATT (ATT over the default fixed L2CAP channel)
or an L2CAP Connection-oriented Channel (CoC) opened against an advertised
PSM — carries which class of MeshLink traffic, and the negotiation/fallback
rules around that choice.

Does not cover:

- the wire encoding of control frames (already FlatBuffers, per
  [Why pure-Kotlin FlatBuffers](../../explanation/why-pure-kotlin-flatbuffers.md))
- the chunked-transfer SACK protocol internals (scoreboard, retransmit
  timing — [`PROJECT.md`](../../../PROJECT.md) pillar 4)
- the discovery advertisement format or the PSM hint field itself, which
  already exists — see
  [Discovery identity hash and privacy trade-offs](../../explanation/privacy-pseudonyms.md)
- routing algorithm behavior itself
- CoC MTU/credit tuning, which is an implementation-time performance
  concern, not a bearer-selection one

## Decision

**GATT is the always-available bearer and carries all control-plane
traffic, unconditionally.** Every MeshLink link MUST be fully operable over
GATT alone — that is the floor every peer supports regardless of platform
or L2CAP CoC availability. Control-plane traffic is:

- the Noise XX handshake and session establishment
- routing control frames (`RouteUpdate`, `RouteDigest`, etc. — see
  [Routing metadata privacy: always-encrypted design](../routing/routing-metadata-privacy.md))
- transfer control/acknowledgment signaling (chunk requests, SACK ranges) —
  the metadata that drives a transfer, as distinct from the payload bytes
  it carries

Control-plane traffic never moves to L2CAP CoC, even when a CoC channel is
open, because it is small, latency-sensitive, and must succeed before any
other bearer can be negotiated in the first place.

**L2CAP CoC is the preferred bearer for data-plane traffic only** — the
bulk chunked payload bytes of a transfer session — when both peers support
it and channel establishment succeeds. If CoC is unavailable, unsupported,
or fails at any point, data-plane traffic uses GATT instead, with no loss
of correctness — only throughput.

### Rationale

- [`CONSTITUTION.md`](../../../CONSTITUTION.md#iv-performance-requirements)'s
  own performance budget benchmarks "Throughput (1-hop **L2CAP**)," so the
  product is already expected to hit its throughput target via CoC on the
  data path, not via GATT notifications.
- GATT write/notify framing carries per-packet ATT overhead and is capped
  by the negotiated MTU; CoC's credit-based flow control avoids that
  overhead for bulk transfer, which is the standard reason mesh/file-transfer
  BLE stacks add it as an optional fast path rather than a replacement for
  GATT.
- Not all peer stacks or Android/iOS versions support CoC symmetrically,
  and it is real implementation complexity — so it must stay strictly
  additive to GATT, never a hard requirement for a link to function.

## Bearer detection and negotiation

The PSM hint already carried in the discovery advertisement (see
[Discovery identity hash and privacy trade-offs](../../explanation/privacy-pseudonyms.md))
is only a hint that a peer is currently offering an L2CAP CoC listener — it
is not a guarantee of a successful channel.

Sequencing per link:

1. GATT connection and the Noise XX handshake complete first, always, over
   GATT. No bearer negotiation happens before a trusted session exists.
2. Once the session is up, if the remote's advertised PSM hint was
   non-zero, the GATT-Central side of the link (which is also the natural
   L2CAP CoC client) attempts to open a CoC channel against that PSM on the
   GATT-Peripheral side (which hosts the CoC listener it advertised).
3. Only after that channel is confirmed open does new data-plane traffic
   for that link promote to CoC. Everything before that point, and
   everything if the attempt fails, stays on GATT.

## Fallback and downgrade rules

Fallback is valid only for explicit non-support or connection failure of
L2CAP CoC — never a silent, unexplained bearer switch.

Required fallback reasons:

- `fallback_no_psm_advertised`
- `fallback_coc_connect_failed`
- `fallback_coc_dropped_mid_transfer`
- `fallback_local_policy` (e.g. current power tier disables CoC)

| Local offers PSM | Remote offers PSM | CoC attempt outcome | Data-plane bearer | Reason |
|---|---|---|---|---|
| yes | yes | succeeds | `l2cap` | negotiated |
| yes | no | not attempted | `gatt` | `fallback_no_psm_advertised` |
| yes | yes | fails or times out | `gatt` | `fallback_coc_connect_failed` |
| yes | yes | succeeds, then channel drops mid-transfer | `gatt` for the rest of that session | `fallback_coc_dropped_mid_transfer` |

A CoC channel dropping mid-transfer falls back to GATT for the remainder of
that transfer session rather than aborting it — the chunked-transfer
scoreboard already tracks missing ranges regardless of which bearer
delivered them, so resuming on GATT is not a new failure mode.

Control-plane traffic is not part of this table: it is always `gatt`,
regardless of CoC state.

## Diagnostics contract

Per active link/transfer session, expose at minimum:

- `transfer.data_plane_bearer` (`gatt` | `l2cap`)
- `transfer.fallback_reason` (one of the reasons above, or absent when
  negotiated)

This follows the same machine-observable-outcome pattern already required
for routing-privacy negotiation.

## Non-goals

- Does not define new wire fields for PSM negotiation beyond the existing
  advertisement hint.
- Does not mandate CoC MTU, credit count, or chunk-size tuning — that is a
  benchmark-driven implementation detail once CoC is built, evaluated
  against the Principle IV performance budget.
- Does not change the chunked-transfer protocol's retransmission or
  scoreboard semantics — only which bearer carries its bytes.

## Related docs

- [`PROJECT.md`](../../../PROJECT.md) — Wire & Discovery Design (L2CAP PSM
  in the discovery advertisement) and pillar 4 (chunked transfer)
- [Discovery identity hash and privacy trade-offs](../../explanation/privacy-pseudonyms.md)
- [Routing metadata privacy envelope and negotiation contract](../routing/routing-metadata-privacy.md)
- [Destination-sourced route freshness, IHU cost signal removal, and digest-triggered resync](../routing/destination-sourced-seqno-ihu-removal-digest-resync-design.md)
- [`CONSTITUTION.md`](../../../CONSTITUTION.md#iv-performance-requirements) — Performance Requirements
