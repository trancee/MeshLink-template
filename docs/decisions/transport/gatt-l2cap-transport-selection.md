# GATT as Control Plane, L2CAP CoC as Data Plane

Fixes the transport-selection policy so handshake, routing, and chunked-transfer layers can be built against a stable contract.

## Decision

**GATT is the always-available bearer and carries all control-plane traffic, unconditionally.** Every MeshLink link MUST be fully operable over GATT alone — that is the floor every peer supports regardless of platform or L2CAP CoC availability. Control-plane traffic is:

- the Noise XX handshake and session establishment
- routing control frames (`RouteUpdate`, `RouteDigest`, etc.)
- transfer control/acknowledgment signaling (chunk requests, SACK ranges)

Control-plane traffic never moves to L2CAP CoC, even when a CoC channel is open, because it is small, latency-sensitive, and must succeed before any other bearer can be negotiated.

**L2CAP CoC is the preferred bearer for data-plane traffic only** — the bulk chunked payload bytes of a transfer session — when both peers support it and channel establishment succeeds. If CoC is unavailable, unsupported, or fails, data-plane traffic uses GATT instead, with no loss of correctness — only throughput.

### Rationale

- CONSTITUTION.md §IV benchmarks "Throughput (1-hop L2CAP)" — the product is expected to hit its throughput target via CoC on the data path, not GATT.
- GATT write/notify framing carries per-packet ATT overhead and is capped by the negotiated MTU; CoC's credit-based flow control avoids that overhead for bulk transfer.
- Not all peer stacks or Android/iOS versions support CoC symmetrically — it must stay strictly additive to GATT, never a hard requirement.

## Bearer Detection and Negotiation

The PSM hint in the discovery advertisement is only a hint that a peer is currently offering an L2CAP CoC listener — not a guarantee.

Sequencing per link:

1. GATT connection and the Noise XX handshake complete first, always, over GATT.
2. Once the session is up, if the remote's advertised PSM hint was non-zero, the GATT-Central side attempts to open a CoC channel against that PSM.
3. Only after that channel is confirmed open does new data-plane traffic promote to CoC. Everything before that point, and everything if the attempt fails, stays on GATT.

## Fallback and Downgrade Rules

Fallback is valid only for explicit non-support or connection failure of L2CAP CoC — never a silent bearer switch.

| Local offers PSM | Remote offers PSM | CoC outcome | Data-plane bearer | Reason |
|---|---|---|---|---|
| yes | yes | succeeds | `l2cap` | negotiated |
| yes | no | not attempted | `gatt` | `fallback_no_psm_advertised` |
| yes | yes | fails or times out | `gatt` | `fallback_coc_connect_failed` |
| yes | yes | succeeds, then drops mid-transfer | `gatt` for rest of session | `fallback_coc_dropped_mid_transfer` |

A CoC channel dropping mid-transfer falls back to GATT for the remainder of that transfer session — the chunked-transfer scoreboard already tracks missing ranges regardless of bearer, so resuming on GATT is not a new failure mode.

Control-plane traffic is always `gatt`, regardless of CoC state.

## Diagnostics Contract

Per active link/transfer session, expose at minimum:

- `transfer.data_plane_bearer` (`gatt` | `l2cap`)
- `transfer.fallback_reason` (one of the reasons above, or absent when negotiated)

## Non-Goals

- Does not define new wire fields for PSM negotiation beyond the existing advertisement hint.
- Does not mandate CoC MTU, credit count, or chunk-size tuning — benchmark-driven implementation detail.
- Does not change the chunked-transfer protocol's retransmission or scoreboard semantics — only which bearer carries its bytes.

## Related Docs

- [PROJECT.md](../../../PROJECT.md) — Wire & Discovery Design and pillar 4 (chunked transfer)
- [CONSTITUTION.md](../../../CONSTITUTION.md#iv-performance-requirements) — Performance Requirements
