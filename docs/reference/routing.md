# Routing Layer

> **Specification**: [SPEC.md §8](../../SPEC.md#routing-layer)  
> **Design rationale**: [Routing Design](../decisions/routing/routing-design.md)

## Model

| Field | Semantics |
|-------|-----------|
| `routeCost` | Lower-is-better saturating additive path cost |
| `hopCount` | Independent topological distance and hard-limit input |
| `linkQuality` | Higher-is-better local observation of the next-hop link |
| `nextHop` | Local authenticated adjacent forwarding peer |

Selection order: feasible, lowest routeCost, lowest hopCount, highest local
linkQuality, lowest lexicographic nextHop.

## Link Cost

```text
qualityLoss    = 255 - normalizedRssi
qualityPenalty = ceil(qualityLoss² / 255)
linkCost       = 64 + qualityPenalty
```

RSSI is normalized over -100 through -30 dBm. Link cost ranges from 64 through
319. Path cost is a saturating sum; `UInt.MAX_VALUE` is infinity. Strong
multi-hop paths may beat weak direct paths.

RSSI uses a 3:1 EWMA. Changes below 3 dB do not advertise. Route switching
requires two observations over one second and improvement by
`max(16, currentRouteCost / 10)`, except immediate loss/invalidation cases.

## Feasibility and Sequence Ownership

Feasible distance is `(sequenceNumber, routeCost)`. A candidate is feasible when
its sequence is newer, or equal with lower cost.

Each destination persists and originates its own 32-bit sequence. Reconnect,
peerHint/RPA/TransportHandle change, RSSI update, Noise renewal, key rotation,
and bearer migration never increment it. `SeqNo` is internal and uses explicit
modular methods rather than Comparable.

## Sequence Recovery

`RouteSequenceAdvancement` uses unicast first, then bounded fan-out after 500 ms.
Attempts expire after three seconds; dedup entries after 30 seconds. At most
three destination-advancement attempts per peer per minute are accepted.

## Signed Route Data

```text
RouteStatement {
    version
    appHash
    destination
    sequenceNumber
    identityBinding
    signature
}

RouteAdvertisement {
    statement
    routeCost
    hopCount
}
```

The destination signs RouteStatement. Relays update cost/count under hop
authentication. Route signatures are mandatory and not configurable. v0.1 does
not claim protection from an authenticated Byzantine relay lying about mutable
path fields.

## Control Protection

All advertisements, withdrawals, digests, sequence advancements, synchronization,
and snapshots use hop AEAD after adjacent Noise authentication. There is no
plaintext or downgrade mode.

## Per-Neighbor Split Horizon and Synchronization

RouteExport excludes candidates whose nextHop is the export neighbor. An
alternate through another neighbor may be advertised; otherwise an existing
export is withdrawn immediately. Explicit withdrawal replaces poison reverse.

```text
RouteExport[neighbor]
RouteImport[neighbor]

RouteDigest
RouteSynchronization
RouteSnapshot
```

Digest compares the sender's RouteExport with the receiver's RouteImport for
that sender, not two complete local routing tables. Mismatch triggers
RouteSynchronization; the sender returns RouteSnapshot; the receiver atomically
replaces only that sender's import after validation.

The digest is the first 64 bits of SHA-256 over canonical fields. All three
records carry a per-adjacency UInt revision reset by a fresh hop Noise session.

## Time-to-Live and Hop Limit

`TransferOptions.timeToLive` is an elapsed delivery deadline, with priority
defaults of 10, 5, and 1 minutes. `maximumHopCount = 16` is a separate fixed
routing bound for all priorities. Relays decrement before forwarding and drop
zero; advertisements at or above 16 hops are rejected.

## Quick Links

- [SPEC.md §8 — Full routing spec](../../SPEC.md#routing-layer)
- [Routing Design ADR](../decisions/routing/routing-design.md)
- [State Machines Spec](../../specs/protocol/state-machines.yaml)
- [Wire Frames Spec](../../specs/codecs/frames.yaml)
- [Peer Hint and Identity Races](../decisions/discovery/peer-hint-and-identity-races.md)
