# Route Metric Enhancement: Link Quality Signal

## Status: Proposed

## Context

Hello/IHU frames are removed because BLE connection state provides liveness. However, multi-hop routing decisions would benefit from link quality signals. The current RouteUpdate only carries hop count, which treats all links equally.

Consider:

- Link A: -60 dBm RSSI, stable, fast
- Link B: -85 dBm RSSI, intermittent, slow

Both cost "1 hop" but Link B should be deprioritized.

## Decision: Add Metric Field to RouteUpdate

### Metric Design Rationale

**Why RSSI is insufficient:**

- RSSI varies with antenna orientation
- RSSI affected by interference (microwave, Wi-Fi)
- RSSI doesn't capture packet loss or latency

**Alternative metrics considered:**

1. **Throughput-based**: Actual bytes/sec (requires measurement over time)
2. **Packet delivery ratio**: % of packets successfully delivered
3. **Latency measurement**: Round-trip time for probe packets
4. **RSSI + proxy**: Connection interval + power tier (available on all platforms)

**Chosen approach:** RSSI-based metric as primary, with platform-specific fallbacks, because:

- Available immediately at connection time
- Low overhead (no additional packets)
- Correlates reasonably well with link quality in practice

### RouteUpdate Enhancement

```flatbuffers
table RouteUpdate {
  // Destination peer ID (16 bytes)
  destination: uint8Vector(16);


  
  // Next hop toward destination (16 bytes)
  nextHop: uint8Vector(16);
  
  // Sequence number (unchanged)
  seqNo: uint32;
  
  // Metric: composite score (NEW)
  // Low byte: RSSI normalized 0-255
  // High bits: flags (CoC, interval, power tier)
  metric: uint32;
  
  // Flags: direct route indicator, stale bit, etc. (NEW)
  flags: uint8;
}
```

### Metric Calculation

Binary value where:

- **Low byte (8 bits):** RSSI normalized to 0-255 scale (0 = unusable, 255 = excellent)
- **High bits (24 bits):** Derived flags

**RSSI Normalization:**

Linear mapping is a simplification. The actual relationship is:

- RSSI varies with distance following log-distance path loss
- RSSI varies with environment (walls, interference)
- RSSI is measured in dBm (negative values)

For practical routing, we use linear normalization as a **proxy metric**:

```kotlin
/**
 * Normalizes RSSI to 0-255 range for routing metric.
 * 
 * This is a proxy metric that correlates with link quality but is not
 * a direct measure of throughput or reliability.
 * 
 * Physics note: RF propagation follows log-distance path loss:
 *   PL(d) = PL0 + 10*n*log10(d/d0)
 * where n is the path loss exponent (2-6 typically).
 * 
 * RSSI variation due to path loss is logarithmic, but we use linear
 * normalization for simplicity. Future versions may use logarithmic.
 */
fun normalizeRssi(rssiDbm: Int): UInt {
  return when {
    // Above -30 dBm: excellent signal
    rssiDbm >= -30 -> 255u
    // Below -100 dBm: no signal
    rssiDbm <= -100 -> 0u
    // Linear mapping between -100 and -30 dBm
    // This is a proxy; actual throughput may vary
    else -> ((rssiDbm + 100) * 255 / 70).toUInt()
  }
}
```

### Platform-Specific Behavior

| Platform | RSSI Available | Fallback Metric |
|----------|---------------|-----------------|
| Android | `BluetoothGatt.getRssi()` during connection | Connection interval + power tier |
| iOS | `CBPeripheral.rssi` during connection | Connection interval + power tier |
| macOS | Available in scans | N/A (not supported) |

**iOS/Android fallback metric:**

```kotlin
fun estimateLinkQualityFromConnection(
  connectionIntervalMs: Int,
  powerTier: PowerTier
): UInt {
  // Connection interval is a proxy for latency
  val intervalScore = when {
    connectionIntervalMs <= 15 -> 200u   // Fast
    connectionIntervalMs <= 30 -> 150u   // Medium
    else -> 100u                          // Slow
  }
  
  // Power tier indicates device capability
  val tierScore = if (powerTier == PowerTier.HIGH) 50u else 0u
  
  return intervalScore + tierScore
}
```

### Composite Metric Structure

```kotlin
data class RouteMetric(
  val rssiNormalized: UInt = 0u,          // 0-255
  val supportsCoc: Boolean = false,
  val fastInterval: Boolean = false,
  val highPowerTier: Boolean = false
) {
  val composite: UInt = 
    ((supportsCoc.bit(8) or fastInterval.bit(9) or highPowerTier.bit(10)) shl 8) or
    rssiNormalized
}
```

### Routing Algorithm Integration

Destination-self-reported metric:

```kotlin
// In RouteCoordinator.onPeerConnected()
fun announceSelfRoute() {
  val metric = RouteMetric(
    rssiNormalized = normalizeRssi(ble.getConnectedRssi()),
    supportsCoc = ble.supportsL2capCoC(),
    fastInterval = ble.getConnectionInterval() <= 15,
    highPowerTier = config.powerTier == PowerTier.HIGH
  )
  
  routingTable.installDirectRoute(
    destination = localPeerId,
    seqNo = localSeqNo,
    metric = metric.composite
  )
}
```

### Path Selection

Route selection considers both metric and hop count:

```kotlin
data class RouteCandidate(
  val nextHop: PeerIdentity,
  val totalMetric: UInt,
  val hopCount: UInt,
  val feasibility: FeasibilityCondition
)

fun selectBestRoute(candidates: List<RouteCandidate>): RouteCandidate {
  // Preference order:
  // 1. Feasible routes only (RFC 8966)
  // 2. Lower hop count
  // 3. Higher metric score
  return candidates
    .filter { it.feasibility.isValid() }
    .sortedWith(compareBy<RouteCandidate> { it.hopCount }
                  .thenByDescending { it.totalMetric })
    .first()
}
```

### Diagnostics Contract

Per route entry, expose:

```yaml
route:
  destination: "<peer-hash>"
  next_hop: "<hop-peer-hash>"
  metric:
    rssi_normalized: 187
    supports_coc: true
    connection_interval_ms: 15
    path_quality: "good" # derived field
```

### Testing Requirements

- `RouteMetricTest`: verify metric calculation from BLE parameters
- `MetricForwardingTest`: verify metric preserved through mesh
- `PathSelectionTest`: verify low-quality paths deprioritized
- `MetricChangeTest`: verify route updates when link quality changes
- `RssiNormalizationTest`: verify RSSI mapping is correct

### Metric Comparison: RSSI Proxy vs Throughput-Based

| Metric | Pros | Cons | Recommendation |
|--------|------|------|----------------|
| RSSI | Immediate, low overhead, available on connect | Proxy only, varies with environment | **Primary metric** - use as baseline |
| Throughput | Direct measure of capacity | Requires measurement time, overhead | **Secondary metric** - use after connection established |
| Packet Delivery Ratio | Direct reliability measure | Requires feedback loop, overhead | **Future enhancement** - add to diagnostics |
| Latency | Direct performance measure | Requires ping mechanism | **Future enhancement** - use for path selection |

**Hybrid approach recommended:**

1. Use RSSI for initial path selection
2. Refine with throughput measurements after 100ms
3. Fall back to RSSI if throughput measurement fails

### Trade-offs Documented

| Trade-off | Analysis |
|-----------|----------|
| RSSI is proxy, not perfect | Acceptable for routing decisions; hybrid with throughput recommended |
| Linear vs logarithmic | Linear is simpler; can refine based on data |
| Metric adds 4 bytes | Acceptable overhead for better routing |
| Platform differences | Fallback metric ensures cross-platform behavior |
| Throughput measurement overhead | Adds 100ms measurement window; hybrid approach mitigates |

## Related

- `docs/decisions/routing/destination-sourced-seqno-ihu-removal-digest-resync-design.md`
- `docs/decisions/transport/gatt-l2cap-transport-selection.md` (CoC indicator)
- `docs/explanation/understanding-babel-routing.md` (metric field)
- RFC 8966 §3.2.2 (route metric considerations)
