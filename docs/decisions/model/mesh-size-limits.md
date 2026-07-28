# Mesh Size Limits & Practical Capacity

**Status:** Locked — 2026-07-26

## Decision

**Hard limit:** 256 route entries (enforced by `RouteTable.maxEntries = 256`).
**Practical limit:** 20-50 peers typical, 50-100 max in dense deployments.
**Documented expectations** for developers to plan deployments.

## Route Table Capacity

```kotlin
// meshlink/src/commonMain/kotlin/ch/trancee/meshlink/routing/RouteTable.kt

class RouteTable {
    companion object {
        const val MAX_ENTRIES = 256
        const val EVICTION_BATCH = 32  // Evict this many when full
    }
    
    private val routes = mutableMapOf<PeerIdentity, RouteEntry>()
    
    fun addOrUpdate(entry: RouteEntry): Boolean {
        if (routes.size >= MAX_ENTRIES && !routes.containsKey(entry.destination)) {
            evictOldest(EVICTION_BATCH)
        }
        routes[entry.destination] = entry
        return true
    }
    
    private fun evictOldest(count: Int) {
        routes.entries
            .sortedBy { it.value.lastUpdated }
            .take(count)
            .forEach { routes.remove(it.key) }
    }
}
```

**Eviction policy:** Least-recently-updated (not least-recently-used). Preserves active routes.

## Practical Peer Limits

| Scenario | Typical Peers | Max Observed | Notes |
|----------|---------------|--------------|-------|
| Casual (cafe, meetup) | 3-10 | 20 | Low density, intermittent |
| Conference/Event | 20-50 | 100 | High density, short duration |
| Dense urban (apartment) | 10-30 | 60 | Walls attenuate, natural partitioning |
| Outdoor festival | 50-100 | 150 | Line of sight, high churn |
| **Hard limit** | — | **256** | Route table full → eviction |

## Resource Consumption per Peer

| Resource | Per Peer | 50 Peers | 100 Peers | 256 Peers |
|----------|----------|----------|-----------|-----------|
| RouteEntry (RAM) | ~200 bytes | 10 KB | 20 KB | 51 KB |
| Noise Session (link) | ~1 KB | 50 KB | 100 KB | 256 KB |
| Noise Session (E2E) | ~1 KB | 50 KB | 100 KB | 256 KB |
| Transfer Buffers | ~chunkSize × 2 | 25 KB | 50 KB | 128 KB |
| **Total (est.)** | **~2.5 KB** | **~135 KB** | **~270 KB** | **~700 KB** |

**Well under** 8 MB steady-state budget (CONSTITUTION.md §IV).

## Bluetooth Controller Limits

| Platform | Max Concurrent Connections | Practical Limit |
|----------|---------------------------|-----------------|
| Android (typical) | 7-10 | 4-8 (power mode dependent) |
| iOS (typical) | 3-4 central, 3 peripheral | 3-4 |
| Android (high-end) | 15+ | 8-10 |
| iOS (recent) | 6-8 central | 4-6 |

**Power mode limits** (from `specs/enums.yaml`):

| Mode | Max Concurrent | Typical Achievable |
|------|---------------|-------------------|
| HIGH | 8 | 6-8 |
| MEDIUM | 4 | 3-4 |
| LOW | 2 | 2 |

## Mesh Diameter & Hop Count

| Mesh Size | Typical Diameter | Max Hops (TTL) | Convergence Time |
|-----------|------------------|----------------|------------------|
| < 10 peers | 1-2 hops | 5 | < 500ms |
| 10-50 peers | 2-4 hops | 10 | 1-2s |
| 50-100 peers | 3-5 hops | 15 | 2-3s |
| 100-256 peers | 4-7 hops | 20 | 3-5s |

**Routing TTL** from `RoutingPolicy.MAX_HOPS = 32` (plenty of headroom).

## Discovery & Advertisement Overhead

| Parameter | Value | Impact |
|-----------|-------|--------|
| Advertisement interval (MEDIUM) | 500ms | 2 ads/sec |
| Scan window (MEDIUM) | 10% duty cycle | ~100ms scan / sec |
| Discovery latency (avg) | 2-5s | Depends on mode, density |
| Advertisement size | 20 bytes | Fits in single BLE packet |

## Memory Budget Compliance

Per CONSTITUTION.md §IV: ≤8 MB heap for 8 peers steady state.

```text
8 peers × ~2.5 KB = 20 KB base
+ Coroutines, buffers, codec = ~500 KB
+ Android/iOS BLE stack = ~2-4 MB (external)
Total < 8 MB ✓
```

## Developer Guidance

```kotlin
// meshlink/src/commonMain/kotlin/ch/trancee/meshlink/MeshLinkSettings.kt

data class MeshLinkSettings(
    // ... existing settings ...
    
    /** Maximum peers to actively maintain connections to. 
        Higher = more mesh connectivity, more battery. 
        Default follows PowerMode.concurrentConnections. */
    val maxActivePeers: Int = 8,
    
    /** Maximum route table entries. Hard limit 256. */
    val maxRouteEntries: Int = 256,
    
    /** Peer eviction policy when limits reached. */
    val evictionPolicy: EvictionPolicy = EvictionPolicy.LEAST_RECENTLY_UPDATED,
)

enum class EvictionPolicy {
    LEAST_RECENTLY_UPDATED,  // Default: routes not refreshed longest
    LEAST_RECENTLY_USED,     // Routes not used for forwarding
    LOWEST_METRIC,           // Worst link quality first
}
```

## Monitoring & Diagnostics

```yaml
# specs/diagnostic-events.yaml
- name: MeshCapacityEvent
  fields:
    - currentPeerCount: Int
    - maxPeerCount: Int
    - routeTableSize: Int
    - evictionCount: Int
    - powerMode: PowerMode
    - timestamp: Instant
```

**Alert thresholds:**

- `routeTableSize > 200` → WARN (approaching limit)
- `evictionCount > 10/min` → WARN (high churn)
- `currentPeerCount > maxActivePeers` → INFO (throttling connections)

## Related

- [Power Mode Behavior](../../../docs/decisions/power/power-mode-behavior.md)
- [Routing Design: Route Table Capacity](../../../docs/decisions/routing/routing-design.md#84-route-table-capacity)
- [specs/enums.yaml PowerMode.concurrentConnections](../../../specs/enums.yaml)
- [CONSTITUTION.md §IV Performance Requirements](../../../CONSTITUTION.md)
