# Power Tier Behavior Specification

## Status: Proposed

## Context

MeshLink requires power-aware operation to prevent battery drain during extended mesh participation. The current spec says "discrete power tiers governing scan duty cycle, advertisement interval, connection interval, concurrent-connection budget, and transfer chunk size" but doesn't define what those tiers are or their quantified behavior.

## Decision: Four Tier Model with Adaptive Grace Periods

### PowerTier Enum

```kotlin
enum class PowerTier {
    HIGH,    // Performance prioritized
    MEDIUM,  // Balanced (default)
    LOW,     // Battery conserved
    IDLE     // No background activity
}
```

### Tier Parameters with Rationale

| Parameter | HIGH | MEDIUM | LOW | IDLE |
|-----------|------|--------|-----|-----|
| Scan duty cycle | 20% | 10% | 5% | 0% (no scans) |
| Advertisement interval | 100ms | 500ms | 1000ms | Never |
| Connection interval min | 7.5ms | 15ms | 30ms | N/A |
| Connection interval max | 15ms | 30ms | 60ms | N/A |
| Concurrent connections | 8 | 4 | 2 | 0 |
| Transfer chunk size | 512 bytes | 256 bytes | 128 bytes | N/A |

**Rationale for values:**

- **Scan duty cycle:** Based on BLE advertising power consumption studies
- **Connection intervals:** 7.5ms is lowest supported on most Android; 15ms is iOS sweet spot
- **Chunk size:** Larger chunks reduce overhead; smaller chunks reduce memory/battery

### Grace Period Design

The original spec used fixed sweeps (30-60s) but this doesn't adapt to peer behavior.

**New approach:** Adaptive grace period based on peer's observed reliability:

```kotlin
data class PeerGraceState(
  val disconnects: Int,
  val recentDisconnects: Int,  // Last 5 minutes
  val averageUptime: Duration,  // Average connected time
  val powerTier: PowerTier
)

fun calculateGracePeriod(state: PeerGraceState): Duration {
  // Base period from power tier
  val base = when (state.powerTier) {
    HIGH -> Duration.seconds(15)
    MEDIUM -> Duration.seconds(30)
    LOW -> Duration.seconds(45)
    IDLE -> Duration.ZERO
  }
  
  // Adjust based on peer history
  val stabilityFactor = when {
    state.recentDisconnects == 0 -> 1.0  // Stable peer
    state.recentDisconnects <= 2 -> 0.7  // Occasional disconnect
    else -> 0.5 // Frequent disconnect - shorter grace
  }
  
  // Adjust based on average uptime
  val uptimeFactor = when {
    state.averageUptime > Duration.minutes(5) -> 1.2  // Long sessions - be patient
    state.averageUptime < Duration.seconds(30) -> 0.8 // Very short - be quick
    else -> 1.0
  }
  
  return (base * stabilityFactor * uptimeFactor).coerceAtLeast(Duration.seconds(10))
}
```

### Platform Integration

#### Android Implementation

Power tier integrates with Android PowerManager:

```kotlin
suspend fun PowerTier.to AndroidScanSettings(): ScanSettings {
  return when(this) {
    HIGH -> ScanSettings.Builder()
      .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
      .setReportDelay(0L)
      .build()
    MEDIUM -> ScanSettings.Builder()
      .setScanMode(ScanSettings.SCAN_MODE_OPPORTUNISTIC)
      .build()
    LOW -> ScanSettings.Builder()
      .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
      .build()
    IDLE -> null // No scanning
  }
}
```

#### iOS Implementation

Power tier integrates with CoreBluetooth background modes:

```swift
func applyPowerTier(_ tier: PowerTier) {
  switch tier {
  case .HIGH:
    self.scanOption = CBCentralManagerScanOptionAllowDuplicatesKey
  case .LOW:
    // iOS doesn't have granular scan modes
    // Use background preservation instead
    self.delegate = self // Allow background scanning
  case .IDLE:
    self.centralManager.stopScan()
  default:
    break
  }
}
```

**iOS Limitation:** iOS scan modes are less granular than Android. The `LOW` tier on iOS uses background preservation rather than scan duty cycle.

### Diagnostics Contract

Per active peer, expose in telemetry:

```yaml
power:
  tier: "medium"
  scan_duty_cycle_observed: 0.12 # actual measured
  advertisement_interval_ms: 500
  connection_interval_ms: [15, 25]
  coc_fallback_count: 3
  grace_period_seconds: 32
  peer_stability: "stable" # derived from disconnect history
```

### Testing Requirements

- `PowerTierTest`: verify each tier produces correct platform settings
- `GracePeriodAdaptiveTest`: verify adaptive calculation based on peer history
- `BatteryConsumptionBenchmark`: verify LOW tier consumes ≤1% battery/hour
- `SweepTimerPrecisionTest`: verify grace period timing within ±2s tolerance
- `CrossPlatformComparisonTest`: verify similar behavior on Android/iOS

### Trade-offs

| Trade-off | Analysis |
|-----------|----------|
| Fixed vs adaptive grace | Adaptive better handles real-world peer behavior |
| Android vs iOS power control | iOS has fewer knobs; use background modes |
| Granular tiers vs simplicity | 4 tiers balance flexibility with complexity |
| No automatic tier switching | App controls tier; no hidden behavior |

## Related

- `CONSTITUTION.md` §IV Performance Requirements
- `docs/explanation/peer-lifecycle.md`
- `docs/decisions/transport/gatt-l2cap-transport-selection.md`
- `.agents/skills/optimize-ble-throughput/references/mobile-platforms.md`
