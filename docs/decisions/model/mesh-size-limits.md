# Mesh Size Limits & Practical Capacity — Rationale

**Status:** Locked — 2026-07-26

> **Full specification** (route table eviction, resource tables, Bluetooth controller limits, mesh diameter, developer guidance, diagnostics) lives in [SPEC.md §8.4, §10, §13.7](../../../SPEC.md). This ADR captures the *why*.

---

## Decision

**Hard limit: 256 route entries** (enforced by `RouteTable.maxEntries`).
**Practical limit: 20-50 peers typical, 50-100 max in dense deployments.**

---

## Why 256 Route Entries?

| Factor | Rationale |
|--------|-----------|
| **Power of 2** | Clean memory alignment; easy to reason about |
| **Typical mesh < 50 peers** | 256 gives 5× headroom for churn/transient entries |
| **Memory budget** | 256 × ~200B entry = 51 KB — negligible vs 8 MB limit |
| **Bluetooth controller limit** | Android/iOS typically 7-10 concurrent connections — 256 is purely routing table, not active connections |
| **Eviction simplicity** | LRU batch eviction (32 entries) is O(1) amortized |

**Not**: 128 (too tight for dense + churn), 512 (no practical benefit, wastes RAM)

---

## Why Least-Recently-Updated Eviction?

| Policy | Why Rejected |
|--------|--------------|
| Least-Recently-Used | Requires tracking "use" (forwarding) — adds complexity |
| Lowest-Metric | Would evict weak-but-only links, partitioning mesh |
| Random | Unpredictable; hard to debug |

**Least-recently-updated** preserves:

- Active routes (refreshed by periodic sync)
- Direct neighbors (updated on every connect)
- High-quality paths (metric changes trigger updates)

---

## Practical Peer Limits by Scenario

| Scenario | Typical | Max Observed | Why |
|----------|---------|--------------|-----|
| Casual (cafe) | 3-10 | 20 | Low density, intermittent |
| Conference | 20-50 | 100 | High density, short duration |
| Dense urban | 10-30 | 60 | Walls attenuate → natural partitioning |
| Outdoor festival | 50-100 | 150 | Line of sight, high churn |

**Hard limit 256** is for route table entries only — not concurrent BLE connections (limited by power mode + platform).

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

| Mesh Size | Typical Diameter | Max Hops (TTL) | Convergence Time |
|-----------|------------------|----------------|------------------|
| < 10 peers | 1-2 hops | 5 | < 500ms |
| 10-50 peers | 2-4 hops | 10 | 1-2s |
| 50-100 peers | 3-5 hops | 15 | 2-3s |
| 100-256 peers | 4-7 hops | 20 | 3-5s |

**Routing TTL** = 32 (plenty of headroom).

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

Configurable limits in `MeshLinkSettings`:

- `maxActivePeers` — default follows PowerMode.concurrentConnections
- `maxRouteEntries` — hard limit 256
- `evictionPolicy` — default LEAST_RECENTLY_UPDATED

---

## Diagnostics Rationale

`MeshCapacityEvent` with alert thresholds:

- `routeTableSize > 200` → WARN (approaching limit)
- `evictionCount > 10/min` → WARN (high churn)
- `currentPeerCount > maxActivePeers` → INFO (throttling)

---

## Related

- [SPEC.md §8.4, §10, §13.7](../../../SPEC.md)
- [Power Mode Behavior](../power/power-mode-behavior.md)
- [Routing Design](../routing/routing-design.md)
- [CONSTITUTION.md §IV](../../../CONSTITUTION.md#iv-performance-requirements)
