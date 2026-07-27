# Understanding Babel Routing in MeshLink

 MeshLink adapts the Babel routing protocol (RFC 8966) for BLE mesh networking.
 This document explains the key Babel concepts as they apply to MeshLink.

## What is Babel?

Babel is a distance-vector routing protocol designed for wireless mesh networks.
It was designed to be robust in environments with:

- Intermittent connectivity (nodes come and go)
- Variable link quality (signal strength fluctuates)
- Multi-hop forwarding (messages may pass through several peers)

 MeshLink uses three Babel mechanisms and omits two that don't fit BLE.

## What MeshLink Uses from Babel

### 1. Destination-Sourced SeqNo

Each node owns **one** sequence number counter, incremented only on cold start
(`MeshLink.start()`). After connecting to a neighbor, the node sends a self-origin
`RouteUpdate` advertising its current seqno. Neighbors adopt this seqno as the
authoritative value for that destination.

This avoids the need for round-trip seqno requests (which would be broken by
BLE disconnections).

### 2. Feasibility Condition

When a node receives a route update, it checks whether the route looks
**strictly better** than any existing route to the same destination:

```text
route.metric < feasibleDistance(destination)
```

This prevents a two-node ping-pong where each node keeps accepting the other's
slightly-better metric, creating an infinite loop.

### 3. Differential Updates

Nodes don't advertise the full route table on every update. Instead, they advertise
only the changes (new routes, metric changes, withdrawn routes). This reduces
control-plane traffic significantly.

A 32-bit FNV-1a hash of the route table (RouteDigest) is included in nearly every
advertisement. If the digest doesn't match, the receiver pulls the full table.

## What MeshLink Omits from Babel

## Key Differences from IPv4 Babel

| Aspect | IPv4 Babel | MeshLink Babel |
|--------|------------|----------------|
| Metrics | Composite (rtt + bandwidth + loss) | RSSI + capability flags |
| Liveness | Periodic packets per second | BLE connect/disconnect events |
| Delivery | IP packets, best-effort | GATT/L2CAP, with SACK |
| Topology | Full mesh possible | Small mesh (10-20 peers typical) |
| Update frequency | Periodic + event-triggered | Event-triggered with 1s minimum |

## Route Selection Priority

When multiple routes exist for the same destination, MeshLink selects by:

1. **Feasible routes only** — infeasible routes are not used (Babel requirement)
2. **Lower hop count** — prefer fewer hops
3. **Higher metric score** — prefer better RSSI + capability combination

## Why This Works for BLE

BLE mesh nodes disconnect constantly. Babel's design is inherently resistant to
this because:

- Routes expire (15 min default), so stale routes are cleaned up automatically
- The digest resync mechanism corrects inconsistencies when nodes reconnect
- Feasibility condition prevents count-to-infinity problems during churn
- Small mesh sizes (10-20 peers) mean route tables stay manageable
