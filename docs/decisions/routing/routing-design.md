# MeshLink routing design

**Status:** Locked — 2026-07-31

> Normative fields, state transitions, and timing live in [SPEC.md
> §8](../../../SPEC.md#8-routing-layer) and [specs/protocol/state-machines.yaml](../../../specs/protocol/state-machines.yaml). This record explains the design rationale.

## Why Babel-inspired distance-vector

MeshLink uses a Babel-inspired (RFC 8966) loop-avoiding distance-vector protocol
adapted to authenticated BLE links. BLE connection state replaces Hello/IHU
neighbor liveness, but does not replace route feasibility, source-owned sequence
numbers, or starvation recovery.

**Rationale:** Babel's feasibility condition prevents routing loops without
requiring synchronized clocks or global topology knowledge. Its source-owned
sequence numbers provide freshness without a central authority. These properties
fit MeshLink's decentralized, offline-first mesh model.

## Why additive link cost with quadratic penalty

RSSI is normalized identically on every platform, then converted to link cost
via a quadratic penalty:

```text
qualityLoss   = 255 - normalizedRssi
qualityPenalty = ceil(qualityLoss² / 255)
linkCost      = 64 + qualityPenalty
```

Link cost ranges from 64 through 319. Path cost is the saturating sum of link
costs; `UInt.MAX_VALUE` means infinity.

**Rationale:** The quadratic penalty allows strong multi-hop paths to beat weak
direct links without making long excellent paths routinely beat acceptable direct
links. A linear penalty would over-penalize multi-hop paths; a step function
would create artificial cliffs. The 64 base cost ensures even perfect links have
non-zero cost, preventing zero-cost loops. Saturating sum prevents overflow from
being interpreted as a better route.

## Why RSSI smoothing and route hysteresis

Shared EWMA smoothing: `smoothedRssi = (3 × previous + new) / 4`. A metric
update is advertised only after smoothed RSSI changes by at least 3 dB. A
feasible candidate replaces the current route only when it remains best for two
consecutive observations spanning at least one second and improves by
`max(16, currentRouteCost / 10)`.

**Rationale:** Raw RSSI fluctuates significantly; EWMA with 3:1 weighting
smooths noise while tracking real changes. The 3 dB threshold avoids advertising
on noise. The two-observation + 1 second requirement plus `max(16, cost/10)`
switch margin prevents route flapping — a new route must be meaningfully better
and stable before switching. Loss, withdrawal, expiry, infeasibility, and trust
failure switch immediately because they represent real topology changes, not
measurement noise.

## Why source-owned sequence numbers

Each peer owns and persists the 32-bit sequence for its own destination route.
It increments and persists before advertising on cold start, a valid
route-sequence advancement, or explicit internal route reset. It does not
increment for reconnect, RPA/TransportHandle/peerHint change, RSSI change,
refresh, Noise renewal, long-term key rotation, or bearer migration.

**Rationale:** Sequence numbers are the freshness authority in Babel. If the
destination doesn't own its sequence, an attacker could inject stale routes.
Persisting before advertising ensures crash consistency. Not incrementing on
transport/crypto changes ensures sequence advances only on actual routing
topology changes, preserving feasibility comparison validity.

## Why feasibility-starvation recovery

A node with reachable advertisements but no feasible candidate sends
`RouteSequenceAdvancement` through the best authenticated next hop, then fans
out after 500 ms. One request per destination; relays deduplicate, exclude
incoming neighbor, enforce hop limit. Three-second deadline, 30-second dedup,
max three attempts/minute/peer.

**Rationale:** Feasibility prevents loops but can starve a node when all known
routes become infeasible (e.g., destination sequence advanced beyond local
knowledge). The advancement request forces the destination to increment its
sequence, creating a new feasible distance. The 500 ms unicast-then-fanout
minimizes traffic; the rate limits prevent amplification attacks.

## Why mandatory signed RouteStatement with hop-mutable advertisement

Destination-owned fields (identity, sequence, key binding) are in a signed
`RouteStatement`. Path fields (`routeCost`, `hopCount`) are hop-mutable in
`RouteAdvertisement`. Relays verify the statement, add local link cost with
saturation, increment hop count, and hop-encrypt for each neighbor.

**Rationale:** The destination must own its identity and sequence — these cannot
be relay-modified. Path cost and hop count are inherently local observations that
each relay must update. Separating them allows the destination to sign once per
sequence advancement, while relays dynamically update path metrics. Mandatory
signatures prevent unsigned route injection.

## Why encrypted routing control with explicit codes

Every routing-control frame is hop-encrypted and authenticated after the
adjacent Noise session exists. Explicit UByte codes: advertisement `0x01`,
withdrawal `0x02`, digest `0x03`, sequence advancement `0x04`, synchronization
`0x05`, snapshot `0x06`. Encoding never uses enum ordinal.

**Rationale:** Hop encryption ensures routing metadata cannot be observed or
modified by passive attackers. Explicit codes ensure wire stability — enum
ordinals change with source edits. The fixed code assignment allows protocol
evolution without breaking deployed implementations.

## Why per-neighbor split horizon with explicit withdrawal

Each `RouteExport` excludes candidates whose `nextHop` is the export neighbor.
A route previously exported is withdrawn immediately when the only remaining
candidate points back through that neighbor. Explicit withdrawal replaces poison
reverse.

**Rationale:** Split horizon prevents advertising a route back to the neighbor
it was learned from, reducing avoidable loops and traffic. Explicit withdrawal
is cleaner than poison reverse (which advertises infinite cost) because it
removes the route entirely rather than leaving a ghost entry. Feasibility
remains the primary loop-avoidance rule; split horizon is a traffic/loop
reduction optimization.

## Why per-neighbor synchronization with digests

Each adjacency tracks `RouteExport[neighbor]` and `RouteImport[neighbor]`.
`RouteDigest` is the first 64 bits of SHA-256 over canonical `RouteExport`
encoding. Mismatch triggers `RouteSynchronization` → `RouteSnapshot`. The
receiver validates and atomically replaces only that sender's import.

**Rationale:** Full routing table exchange is O(N) and unnecessary — only the
divergent neighbor's view matters. Per-neighbor digests localize the comparison.
The 64-bit digest is small enough for frequent periodic exchange; full snapshot
is only sent on mismatch. Atomic replacement of one neighbor's import avoids
cascading churn.

## Why capacity limits and eviction policy

`maxRoutes` defaults to 256 distinct remote destinations. Local self route
excluded. Alternate candidates from adjacent peers don't consume additional
slots. Eviction order: expired → unavailable without active transfers → least
recently refreshed → highest cost → highest identity. Never evict self, direct
peer, or active-transfer destination.

**Rationale:** 256 destinations bounds memory while supporting dense meshes.
Alternate candidates are per-neighbor ephemeral state, not public routes.
Eviction prioritizes keeping active/available routes. The tie-breakers
(highest cost, then highest identity) are deterministic. Protecting active
transfers prevents mid-transfer route loss.

## Why route-advertisement triggers and cooldown

Direct link up, RSSI threshold, expiry/withdrawal, digest mismatch, sequence
advancement trigger immediate or jittered advertisements. Internal one-second
cooldown coalesces ordinary changes; mandatory triggers bypass cooldown.

**Rationale:** Immediate triggers ensure fast convergence on real topology
changes. Cooldown prevents advertisement storms from RSSI noise. Jitter on
differential updates spreads load. Mandatory triggers (withdrawal, expiry,
digest mismatch) cannot be delayed because they represent correctness
requirements, not optimizations.

## Related

- [SPEC.md §8](../../../SPEC.md#8-routing-layer)
- [specs/protocol/state-machines.yaml](../../../specs/protocol/state-machines.yaml)
- [specs/codecs/frames.yaml](../../../specs/codecs/frames.yaml)
- [specs/codecs/enums.yaml](../../../specs/codecs/enums.yaml)
- [Identity Binding and Fail-Closed Behavior](../crypto/identity-binding-and-fail-closed.md)
- [Noise Session Renewal](../crypto/noise-session-renewal.md)
- [Peer Hints and Identity Races](../discovery/peer-hint-and-identity-races.md)
- [Transport Bearer and MTU](../transport/mtu-negotiation.md)
