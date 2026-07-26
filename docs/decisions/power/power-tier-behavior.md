# Power Tier Behavior Specification

**Status:** Locked — 2026-07-20

See [SPEC.md §10](../../../SPEC.md#10-power-management) for complete parameter tables and [specs/settings.yaml](../../../specs/settings.yaml) for machine-readable config.

## Context

MeshLink requires power-aware operation: discrete power tiers governing scan duty cycle, advertisement interval, connection interval, concurrent connections, and transfer chunk size.

## Decision: Three-Tier Model with Fixed Grace Periods

### PowerTier Enum

```kotlin
enum class PowerTier { HIGH, MEDIUM, LOW }
```

### Tier Parameters

**Complete parameter tables in [SPEC.md §10.4](../../../SPEC.md#104-tier-driven-parameters) and [specs/enums.yaml](../../../specs/enums.yaml).**

| Tier | Scan Duty | Adv Interval | Conn Interval | Concurrent | Chunk Size | Max Retries | Retry Budget | Grace Period |
|------|-----------|--------------|---------------|------------|------------|-------------|--------------|--------------|
| HIGH | 20% | 100ms | 7.5–15ms | 8 | 512B | 10 | 60s | 15s |
| MEDIUM | 10% | 500ms | 15–30ms | 4 | 256B | 5 | 30s | 30s |
| LOW | 5% | 1000ms | 30–60ms | 2 | 128B | 3 | 15s | 45s |

**Rationale:**

- Scan duty cycle: Based on BLE power consumption studies showing linear relationship with current draw
- Advertisement interval: Shorter intervals improve discovery latency but increase power consumption
- Connection interval: Quantized in 1.25ms units; 7.5ms (=6 units) is the Android BLE stack floor; 15ms is the iOS sweet spot for throughput/power balance
- Chunk sizes: Sized to fit within BLE MTU (23–251 bytes) after accounting for L2CAP/GATT headers (4 bytes), security overhead (nonce+tag=16 bytes for ChaCha20-Poly1305), and protocol framing
- Max retries & retry budget: Tuned to balance reliability against resource exhaustion and battery drain

### Grace Period

Fixed grace period per power tier. After the grace period expires without reconnection, the peer transitions to GONE and ephemeral state (presence, routes, pending transfers) is cleaned up. Pinned trust state persists.

| Tier | Grace Period |
|------|-------------|
| HIGH | 15 seconds |
| MEDIUM (default) | 30 seconds |
| LOW | 45 seconds |

**Future work:** Adaptive grace period based on peer stability is tracked separately.

### Regulatory Clamping (EU)

When `RegulatoryRegion = EU` (see [SPEC.md §10.2](../../../SPEC.md#102-regulatory-region)):

- Advertisement interval floor: 300ms (values below clamped to 300ms)
- Scan duty cycle ceiling: 70% (values above clamped to 70%)

Clamping happens in shared policy code, not platform-specific wrappers.

### Platform Integration

#### Android

Power tier maps to Android `ScanSettings`:

- HIGH → `SCAN_MODE_LOW_LATENCY`
- MEDIUM → `SCAN_MODE_OPPORTUNISTIC`
- LOW → `SCAN_MODE_LOW_POWER`

#### iOS

iOS scan modes are less granular than Android. The LOW tier uses background preservation rather than scan duty cycle.

### Diagnostics Contract

`PowerTierEffectiveEvent` emits observed effective parameters after regulatory clamping:

```yaml
power:
  requestedTier: "medium"
  effectiveTier: "medium"
  regulatoryRegion: "DEFAULT"
  scanDutyCyclePercent: 10
  advertisementIntervalMs: 500
  connectionIntervalMs: 15.0
```

### Testing

- `PowerTierTest`: verify each tier produces correct platform settings
- `BatteryConsumptionBenchmark`: verify LOW tier consumes ≤1% battery/hour
- `CrossPlatformComparisonTest`: verify similar behavior on Android/iOS

## Related

- [CONSTITUTION.md §IV Performance Requirements](../../../CONSTITUTION.md#iv-performance-requirements)
- [SPEC.md §10 Power Management](../../../SPEC.md#10-power-management)
- [peer-lifecycle.md](../../explanation/peer-lifecycle.md)
- [gatt-l2cap-transport-selection.md](../transport/gatt-l2cap-transport-selection.md)
- [optimize-ble-throughput skill references](../../../.agents/skills/optimize-ble-throughput/references/mobile-platforms.md)
