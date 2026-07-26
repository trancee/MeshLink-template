# Route Metric Enhancement: Link Quality Signal

## Status: Locked — 2026-07-20

## Context

Hello/IHU frames are removed because BLE connection state provides liveness (see [`destination-sourced-seqno-ihu-removal-digest-resync-design.md`](destination-sourced-seqno-ihu-removal-digest-resync-design.md)). However, multi-hop routing decisions benefit from link quality signals beyond hop count.

Both Link A (-60 dBm) and Link B (-85 dBm) cost "1 hop" but Link B should be deprioritized.

## Decision

### Metric Structure

Composite `UInt32` where:

- **Low byte (8 bits):** RSSI normalized 0-255 (0 = unusable, 255 = excellent)
- **High bits (24 bits):** Flags for CoC support, interval, power tier

Normalization: `rssiNormalized = when { rssi >= -30 -> 255; rssi <= -100 -> 0; else -> ((rssi + 100) * 255 / 70).toUInt() }`

See SPEC.md §3.3 for complete `RouteMetric` and `LinkMetric` definitions.

### Why RSSI-Based

| Metric | Pros | Cons | Decision |
|--------|------|------|----------|
| RSSI | Immediate, no extra packets | Proxy only, environment-sensitive | **Primary** - baseline for routing |
| Throughput | Direct measure | Requires measurement overhead | Secondary - post-connection refinement |
| Packet Delivery | Reliability measure | Needs feedback loop | Future enhancement |

### Routing Integration

Path selection prefers:

1. Feasible routes only (RFC 8966 requirement)
2. Lower hop count
3. Higher metric score

See SPEC.md §8.4.1 for loop detection mechanisms.

## Testing

- `RouteMetricTest`: RSSI normalization
- `MetricForwardingTest`: Peer-to-peer metric propagation
- `PathSelectionTest`: Low-quality path deprioritization

## Related

- [SPEC.md Routing Model](../../../SPEC.md#3-core-data-models)
- [Root quality metric](link-quality-metric.md)
- [E2E Handshake Pattern](../crypto/e2e-handshake-pattern.md) (IX handshake for E2E)
