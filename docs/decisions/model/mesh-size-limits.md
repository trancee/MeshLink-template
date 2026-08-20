# Mesh Size Limits & Practical Capacity — Rationale

**Status:** Locked — 2026-07-31

> **Full specification** (route capacity, candidates, Bluetooth controller limits, mesh diameter, and diagnostics) lives in [SPEC.md §8](../../../SPEC.md#8-routing-layer). This ADR captures the *why*.

---

## Decision

**Default hard limit: 256 distinct remote destination routes** (`RoutingSettings.maxRoutes`).
**Practical limit: 20-50 peers typical, 50-100 max in dense deployments.**

---

## Why 256 Routes?

One route means one remote destination PeerIdentity. The local self route is not
counted. Each route may retain one candidate from every authenticated adjacent
peer, so alternate paths do not consume additional public route slots.

| Factor | Rationale |
|--------|-----------|
| **Power of 2** | Easy fixed capacity and diagnostics threshold |
| **Typical mesh < 50 peers** | 256 gives substantial churn/dense-event headroom |
| **Alternate candidates** | HIGH mode bounds candidates at 256 × 8 = 2,048 |
| **Memory budget** | Candidate state remains well below the 8 MB SDK target |
| **Controller limit** | Route capacity is independent of the smaller active-connection limit |

**Not**: 128 (too tight for dense churn), 512 (no demonstrated v0.1 benefit).

---

## Protected deterministic eviction

On capacity pressure, remove expired candidates and empty routes first. Among
remaining eligible routes, evict the least recently refreshed, with highest
selected routeCost and then highest destination identity as deterministic
tie-breaks.

Never evict the local self state, a direct authenticated peer, or an active-
transfer destination. If all routes are protected, reject the new route and emit
a capacity diagnostic. Disconnect removes only that neighbor's candidate so an
alternate route can take over.

---

## Practical Peer Limits by Scenario

| Scenario | Typical | Max Observed | Why |
|----------|---------|--------------|-----|
| Casual (cafe) | 3-10 | 20 | Low density, intermittent |
| Conference | 20-50 | 100 | High density, short duration |
| Dense urban | 10-30 | 60 | Walls attenuate → natural partitioning |
| Outdoor festival | 50-100 | 150 | Line of sight, high churn |

**Hard limit 256** counts remote destination routes, not alternate candidates or concurrent BLE connections.

---

## Power Mode vs Bluetooth Controller Limits

| Platform | Max Connections (theoretical) | Practical (MEDIUM mode) |
|----------|-------------------------------|-------------------------|
| Android typical | 7-10 | 3-4 |
| iOS typical | 3-4 central | 3-4 |
| Android high-end | 15+ | 8-10 |
| iOS recent | 6-8 | 4-6 |

**Power mode `concurrentConnections`** (HIGH=8, MEDIUM=4, LOW=2) is the *software* limit; platform hardware is the *hard* limit.

---

## Mesh Diameter & Convergence

| Mesh Size | Typical Diameter | Suggested Operational Hops | Convergence Time |
|-----------|------------------|----------------|------------------|
| < 10 peers | 1-2 hops | 5 | < 500ms |
| 10-50 peers | 2-4 hops | 10 | 1-2s |
| 50-100 peers | 3-5 hops | 15 | 2-3s |
| 100-256 peers | 4-7 hops | 20 | 3-5s |

**maximumHopCount** = 16, independent of delivery timeToLive and priority. This exceeds the documented practical diameter while tightly bounding loops and fan-out.

---

## Memory Budget Compliance

Per CONSTITUTION.md §IV: ≤8 MB heap for 8 peers steady state.

```text
8 peers × ~2.5 KB = 20 KB base
+ Coroutines, buffers, codec = ~500 KB
+ Android/iOS BLE stack = ~2-4 MB (external)
Total < 8 MB ✓
```

---

## Developer Guidance

`RoutingSettings.maxRoutes` defaults to 256. Active connections follow the
selected power mode and platform controller limit. Eviction policy is an
internal correctness rule rather than a configurable application strategy.

---

## Diagnostics Rationale

`MeshCapacityEvent` with alert thresholds:

- `routeCount > 200` → WARN (approaching maxRoutes)
- `evictionCount > 10/min` → WARN (high churn)
- `currentPeerCount > maxActivePeers` → INFO (throttling)

---

## Related

- [SPEC.md §8](../../../SPEC.md#8-routing-layer)
- [Power Mode Behavior](../power/power-mode-behavior.md)
- [Routing Design](../routing/routing-design.md)
- [CONSTITUTION.md §IV](../../../CONSTITUTION.md#iv-performance-requirements)
