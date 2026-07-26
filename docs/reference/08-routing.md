# Routing Layer

> Source: [SPEC.md §8](../../SPEC.md#8-routing-layer)

## 8.1 Protocol Basis

Babel-style distance-vector (RFC 8966) adapted for BLE mesh:

- **Feasibility condition**: Loop avoidance by requiring candidate routes to look strictly better
- **SeqNo freshness**: Destination self-reports sequence number, prevents stale route propagation
- **Differential updates**: Only route changes advertised, not full table dumps

[Decision: docs/decisions/routing/routing-design.md]

## 8.2 Sequence Number Semantics

- **Destination-owned**: Each node owns one seqno counter, incremented only on cold start
- **Self-origin announcements**: After connection, each node sends RouteUpdate about itself
- **No Hello/IHU frames**: BLE transport already provides liveness signals

[Decision: docs/decisions/routing/routing-design.md]

## 8.3 Route Digest & Resync

- 32-bit FNV-1a hash of route table included in advertisements
- On mismatch, receiver pushes full table (no request/response round-trip)
- Simple correct behavior, bandwidth optimization deferred

## 8.4 Route Table Capacity

- Route tables are bounded by `maxRouteEntries` (default: 256)
- When the table exceeds this limit, least-recently-updated entries are evicted
- This prevents unbounded memory growth and ensures predictable convergence behavior

**Rationale:** 256 entries balance mesh size expectations (~10-20 peers common in typical deployment) with memory bounds. Evicting least-recently-updated entries ensures stale routes are removed first while active routes persist. The eviction happens atomically during route table updates.

### 8.4.1 Loop Detection

MeshLink uses two complementary mechanisms to prevent routing loops, following RFC 8966:

#### 1. Source-peer tracing (primary defense)

Each `RouteEntry` records the `source` peer — the immediate neighbor from whom the route was received. When evaluating a route update for a destination, the receiving peer checks whether the `source` is the same as itself. If it is, the update is silently discarded — it is a loop back to the origin.

This is the Babel-style "split horizon with poisoned reverse" principle: a node never advertises a route back to the peer from which it learned that route.

#### 2. Feasibility condition (loop avoidance)

The Babel feasibility condition (`route.metric < feasibleDistance(destination)`) is the second defense. Even if a route update passes the source check, it is only accepted if its metric is strictly better than any feasible route already in the table for the same destination. This prevents a two-node ping-pong where each node keeps accepting the other's slightly-better metric.

#### 3. SeqNo freshness (stale-route prevention)

Each `RouteEntry` carries a destination-self-reported `seqNo`. A route update whose `seqNo` is not newer than the currently accepted route for the same destination is rejected. This is handled by `SeqNo.isNewerThan()` using signed 32-bit comparison per RFC 8966 §3.7.

Together, these three mechanisms — source tracing, feasibility filtering, and seqno freshness — provide robust loop prevention for the Babel-style distance-vector routing plane.

## 8.5 TTL by Priority

| Priority | TTL |
|----------|-----|
| HIGH | 10 minutes |
| NORMAL | 5 minutes |
| LOW | 1 minute |

## 8.6 Route Update Trigger Conditions

Route updates are triggered by the following events. All non-immediate updates include random jitter (0–500 ms) to avoid synchronization storms.

| Trigger | Condition | Frame Type | Jitter |
|---------|-----------|------------|--------|
| **Direct link up** | New GATT/L2CAP connection established | `RouteUpdate` (self-origin) | None (immediate) |
| **Metric change** | `abs(newRssi - oldRssi) > routeUpdateChangeThreshold` (default 3 dB) | `RouteUpdate` | 0–500 ms |
| **Periodic full sync** | Every `fullTableSyncInterval` (default 5 min) | `RouteUpdate` (all routes) | 0–500 ms |
| **Route expiry** | Route entry not refreshed before `routeEntryExpiry` (default 15 min) | `RouteWithdrawal` | None (immediate) |
| **Digest mismatch** | Received `RouteDigest` differs from local table hash | `RouteUpdate` (all routes) | None (immediate) |

**Minimum interval enforcement**: No more than one update to the same peer within `routeUpdateMinInterval` (default 1 s), regardless of triggers.

**Maximum interval enforcement**: If no triggers fire, a keep-alive `RouteUpdate` is sent at `routeUpdateMaxInterval` (default 30 s) to refresh route freshness.

[Decision: docs/decisions/routing/routing-design.md]
