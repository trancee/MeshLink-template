# MeshLink routing design

**Status:** Locked — 2026-07-31

> Normative fields, state transitions, and timing live in [SPEC.md
> §8](../../../SPEC.md#routing-layer). This record explains destination-owned
> freshness, additive cost, feasibility, hysteresis, authenticated control, and
> per-neighbor synchronization.

## Model

MeshLink uses a Babel-inspired, loop-avoiding distance-vector protocol adapted
to authenticated BLE links. BLE connection state replaces Hello/IHU neighbor
liveness, but does not replace route feasibility, source-owned sequence numbers,
or starvation recovery.

A route candidate separates cost, topology, and local observation:

```text
RouteCandidate {
    destination
    nextHop
    sequenceNumber
    routeCost
    hopCount
    linkQuality
    expiresAt
}
```

- `routeCost` is lower-is-better, additive, and used by feasibility/selection.
- `hopCount` is an independent topological distance and hard-limit input.
- `linkQuality` describes the local link to `nextHop` and is higher-is-better.
- `nextHop` is local state inferred from the authenticated adjacent sender; it
  is not a destination-signed wire claim.

## Additive link cost

RSSI is normalized identically on every platform:

```text
rssi <= -100 dBm → quality = 0
rssi >=  -30 dBm → quality = 255
otherwise        → quality = ((rssi + 100) × 255) / 70
```

The shared integer cost formula is:

```text
qualityLoss   = 255 - quality
qualityPenalty = ceil(qualityLoss² / 255)
linkCost      = 64 + qualityPenalty
```

Equivalent integer arithmetic:

```kotlin
val qualityLoss = 255u - normalizedRssi.toUInt()
val qualityPenalty = (qualityLoss * qualityLoss + 254u) / 255u
val linkCost = 64u + qualityPenalty
```

Link cost ranges from 64 through 319. Path cost is the saturating sum of link
costs; `UInt.MAX_VALUE` means infinity. The quadratic penalty allows strong
multi-hop paths to beat weak direct links without making long excellent paths
routinely beat acceptable direct links.

L2CAP capability/health and negotiated latency remain transport state. They do
not enter LinkQuality, routeCost, or the feasibility tuple; bearer failure falls
back to GATT without route churn.

## RSSI smoothing and route hysteresis

Shared EWMA smoothing uses:

```text
smoothedRssi = (3 × previousSmoothedRssi + newRssi) / 4
```

The first valid sample initializes directly. Invalid platform sentinels are
ignored. A metric update is advertised only after smoothed RSSI changes by at
least 3 dB. Link loss/restoration remains immediate.

A feasible candidate replaces the current route only when it remains best for
two consecutive observations spanning at least one second and improves by:

```text
switchMargin = max(16, currentRouteCost / 10)
```

Loss, withdrawal, expiry, infeasibility, hop-limit violation, or trust failure
switches immediately without hysteresis.

## Feasible distance

For each destination, retain:

```text
feasibleDistance = (sequenceNumber, routeCost)
```

A candidate is feasible when its sequence is newer, or its sequence is equal
and its cost is lower than the feasible cost. Route selection then uses:

1. feasible candidates;
2. lowest routeCost;
3. lowest hopCount;
4. highest local linkQuality; and
5. lowest lexicographic nextHop as deterministic tie-break.

## Source-owned sequence numbers

Each peer owns and persists the 32-bit sequence for its own destination route.
It increments and persists before advertising on cold start, a valid
route-sequence advancement, or explicit internal route reset.

It does not increment for reconnect, RPA/TransportHandle/peerHint change, RSSI
change, refresh, Noise renewal, long-term key rotation, or bearer migration.
Routing sequence and key generation are independent.

`SeqNo` is internal and deliberately not `Comparable`. Modular serial ordering
is not a global total order. It exposes only explicit operations such as
`isNewerThan`, `isOlderThan`, `isNewerThanOrEqualTo`, `distanceFrom`, and `inc`.
An exact half-range difference is ambiguous and cannot authorize an ordering
decision.

## Feasibility-starvation recovery

A node with reachable advertisements but no feasible candidate creates:

```text
RouteSequenceAdvancement {
    requester
    destination
    sequenceNumber
    requestId
    hopLimit
}
```

`sequenceNumber` is the value the destination must surpass. The requester sends
immediately through the best known authenticated next hop. After 500 ms without
a sufficiently new route, it fans the same request out to other authenticated
neighbors except the incoming/failed hop.

One request per destination remains active. Relays deduplicate
`(requester, requestId)`, never forward back through the incoming neighbor, and
enforce hopLimit. `requestId` is a random non-zero origin-scoped UInt and is not
an authorization token.

An attempt has a three-second hard deadline and 30-second dedup retention. A
peer may trigger at most three destination sequence-advancement attempts per
minute. The destination coalesces older/equal requirements, persists a newer
sequence, and advertises immediately without normal update jitter. Failed
attempts retry with exponential backoff and full jitter while application
traffic remains in ROUTE_UNAVAILABLE.

## Route statement and mutable advertisement

Destination-owned fields use a mandatory signed statement:

```text
RouteStatement {
    version
    appHash
    destination
    sequenceNumber
    identityBinding
    signature
}
```

Path fields remain hop-mutable:

```text
RouteAdvertisement {
    statement
    routeCost
    hopCount
}
```

Relays verify the statement, add local link cost with saturation, increment hop
count, apply feasibility/policy, and hop-encrypt separately for each outgoing
neighbor. An unpinned statement is a self-consistent candidate only; it cannot
change trust. Route signatures are mandatory and not configurable.

An authenticated malicious relay can still lie about mutable cost/count.
Preventing Byzantine metric manipulation requires a path-proof protocol and is
not a v0.1 guarantee.

## Routing-control protection

Every routing-control frame is hop-encrypted and authenticated after the
adjacent Noise session exists:

- advertisements;
- withdrawals;
- digests;
- sequence advancements;
- synchronization triggers; and
- full snapshots.

AEAD associated data binds frame type, protocol version, and direction.
Authentication failure drops the frame before routing-field parsing. No
plaintext retry or downgrade exists.

Routing uses explicit UByte codes: advertisement `0x01`, withdrawal `0x02`,
digest `0x03`, sequence advancement `0x04`, synchronization `0x05`, and
snapshot `0x06`. Encoding never uses enum ordinal.

## Per-neighbor split horizon

Each RouteExport selects the best feasible candidate excluding candidates whose
nextHop is the export neighbor. A self-origin route is exported to every
neighbor. If an alternate candidate through another neighbor exists, it may be
exported.

A route previously exported to a neighbor is withdrawn immediately when the
only remaining candidate points back through that neighbor. Explicit withdrawal
replaces poison reverse; no infinite-cost advertisement is needed. Feasibility
remains the primary loop-avoidance rule, while split horizon reduces avoidable
loops and traffic.

## Per-neighbor synchronization

Each adjacency tracks canonical wire-level sets:

```text
RouteExport[neighbor] = advertisements last sent to that neighbor
RouteImport[neighbor] = advertisements last accepted from that neighbor
```

`RouteImport` is stored before adding receiver-local link cost or policy.

Synchronization uses:

```text
RouteDigest   // summary of sender RouteExport
RouteSynchronization // receiver asks sender to synchronize
RouteSnapshot // complete sender RouteExport response
```

The receiver compares an incoming digest with `RouteImport[sender]`, never with
its complete local routing table. On mismatch it sends `RouteSynchronization`;
the sender
returns `RouteSnapshot`. The receiver validates the complete snapshot and
atomically replaces only that sender's import.

Digest input uses destination-sorted canonical RouteStatements plus advertised
routeCost and hopCount. It excludes nextHop, local linkQuality, expiry/arrival
times, feasible distance, and diagnostics.

`RouteDigest`, `RouteSynchronization`, and `RouteSnapshot` carry a per-adjacency UInt
revision. It starts at zero for each fresh authenticated hop session and
increments whenever that neighbor's RouteExport changes. Stale delayed values
are rejected with modular UInt comparison.

The digest is the first 64 bits of SHA-256 over the canonical RouteExport
encoding. It is a hop-AEAD-protected synchronization checksum, not an
authorization proof. Raw implementation buffers are not hashed; the MeshLink Wire Codec defines a
separate deterministic canonical field encoding for digests/signatures.

A matching digest renews every unchanged candidate in that sender's RouteImport.
`routeDigestInterval` defaults to five minutes and `routeExpiry` to 15 minutes,
so three missed digest leases expire candidates. Neighbor disconnect invalidates
its candidates immediately. Unchanged routes are not periodically
re-advertised.

## Capacity and eviction

`RoutingSettings.maxRoutes` defaults to 256 and counts distinct remote
destination PeerIdentity values. The local self route is excluded. A route may
retain at most one candidate from each authenticated adjacent peer, so alternate
paths do not consume additional public route slots.

When capacity is reached:

1. Remove expired candidates and empty destinations.
2. Consider only unavailable destinations without active transfers.
3. Evict the least recently refreshed eligible route.
4. Break ties by highest selected routeCost, then highest destination identity.
5. Never evict self state, a direct authenticated peer, or an active-transfer
   destination.
6. If every route is protected, reject the new destination and emit a capacity
   diagnostic.

Disconnect removes or degrades only that neighbor's candidate; another feasible
candidate for the destination may take over immediately.

## Route-advertisement triggers

- Direct authenticated link up: immediate self-origin statement/advertisement.
- Smoothed RSSI threshold: jittered differential update.
- Every routeDigestInterval (default five minutes): send per-neighbor RouteDigest; full RouteSnapshot only after RouteSynchronization.
- Route expiry/withdrawal: immediate.
- RouteImport digest mismatch: immediate RouteSynchronization.
- Successful sequence advancement: immediate destination advertisement.

An internal one-second `routeAdvertisementCooldown` coalesces ordinary changes
but never delays withdrawal, disconnect, trust invalidation, sequence recovery,
or requested synchronization. Matching digests renew unchanged RouteImport
leases, so no periodic unchanged advertisements are sent. Feasibility and the
cooldown are mandatory and have no public disable/tuning settings.

## Time-to-live and hop limit

Delivery time-to-live and routing hop limit are independent:

- `TransferOptions.timeToLive` is elapsed time during which a message/transfer
  may remain active or in ROUTE_UNAVAILABLE. Priority supplies defaults of 10, 5, and 1
  minutes for HIGH, NORMAL, and LOW.
- `maximumHopCount` is the fixed number of relays a routed envelope/control
  operation may traverse. It is 16 for every priority and is not configurable.

New routed envelopes start with `hopLimit = 16`; each relay decrements before
forwarding and drops zero. Route advertisements with `hopCount >= 16` are
rejected. RouteSequenceAdvancement uses the same bound.

A higher priority may receive scheduler preference and longer elapsed delivery
time, but it never receives permission to traverse more hops. Keeping the
limits separate avoids treating minutes as topology and bounds loops/fan-out
regardless of application priority.

## Testing requirements

The virtual harness proves:

- exact RSSI normalization/cost table;
- saturating path addition;
- quality-sensitive direct versus multi-hop selection;
- EWMA and hysteresis boundaries;
- feasibility across wrap-around and half-range ambiguity;
- source-only sequence ownership and crash-safe persistence;
- unicast-first/fan-out sequence recovery, dedup, rate limit, and timeout;
- signed statement mutation rejection;
- hop-mutable path handling;
- encrypted-control downgrade rejection;
- per-neighbor digest mismatch and atomic snapshot replacement;
- convergence within three seconds; and
- route/trust/transport/key rotations without identity or sequence conflation.

## Related

- [RFC 8966](../../rfcs/routing/rfc8966.txt)
- [Data model](../model/data-model.md)
- [Peer hints and identity races](../discovery/peer-hint-and-identity-races.md)
- [Identity binding and fail-closed behavior](../crypto/identity-binding-and-fail-closed.md)
- [SPEC.md §8](../../../SPEC.md#routing-layer)
