# Power Management

> Source: [SPEC.md §10](../../SPEC.md#10-power-management)

## 10.1 Power Modes

```text
enum class PowerMode {
  HIGH,     // Performance prioritized (20% scan, 100ms adv, 7.5ms conn, 8 concurrent, 512B chunks)
  MEDIUM,   // Balanced (10% scan, 500ms adv, 15ms conn, 4 concurrent, 256B chunks) - DEFAULT
  LOW       // Battery conserved (5% scan, 1000ms adv, 30ms conn, 2 concurrent, 128B chunks)
}
```

## 10.2 Regulatory Region

```text
enum class RegulatoryRegion {
  DEFAULT,  // Rely on platform's normal behavior
  EU        // Apply EU clamping (adv interval floor 300ms, scan duty cycle ceiling 70%)
}
```

When region = EU:

- Advertisement interval floor: 300ms (below spec values clamped)
- Scan duty cycle ceiling: 70%

[Decision: docs/decisions/power/power-mode-behavior.md, docs/explanation/regulatory-compliance.md]

## 10.3 Grace Period

Fixed grace period per power mode:

| Mode | Grace Period |
|------|-------------|
| HIGH | 15 seconds |
| MEDIUM (default) | 30 seconds |
| LOW | 45 seconds |

After the grace period expires without reconnection, the peer transitions to GONE and ephemeral state (presence, routes, pending transfers) is cleaned up. Pinned trust state persists.

**Future work:** An adaptive grace period that adjusts based on peer stability (disconnect history) and session uptime is tracked in a separate design note and can be introduced as a future enhancement.

[Decision: docs/decisions/power/power-mode-behavior.md]

## 10.4 Mode-Driven Parameters

| Mode | Scan Duty Cycle | Adv Interval | Conn Interval | Concurrent | Chunk Size | Max Retries | Retry Budget |
|------|-----------------|--------------|---------------|------------|------------|-------------|--------------|
| HIGH | 20% | 100ms | 7.5-15ms | 8 | 512B | 10 | 60s |
| MEDIUM | 10% | 500ms | 15-30ms | 4 | 256B | 5 | 30s |
| LOW | 5% | 1000ms | 30-60ms | 2 | 128B | 3 | 15s |

*Parameter rationale:*

- **Scan duty cycle**: Based on BLE power consumption studies showing linear relationship with current draw
- **Advertisement interval**: Shorter intervals improve discovery latency but increase power consumption
- **Connection interval**: BLE connection intervals are quantized in 1.25ms units; 7.5ms (=6 units) is the minimum valid interval and the Android BLE stack floor. 15ms (=12 units) is the iOS sweet spot for throughput/power balance. The code stores these as `Double` milliseconds to preserve the exact BLE-valid values without rounding artifacts.
- **Concurrent connections**: Limited by controller resources and connection management overhead
- **Chunk sizes**: Sized to fit within BLE MTU (23-251 bytes) after accounting for L2CAP/GATT headers (4 bytes), security overhead (nonce+tag=16 bytes for ChaCha20-Poly1305), and protocol framing, while minimizing packetization overhead
- **Max retries & retry budget**: Tuned to balance reliability against resource exhaustion and battery drain

*Note: Connection intervals are shown as min-max ranges supported by the controller stack. Values that are multiples of 1.25ms are guaranteed to be valid across BLE controllers; non-multiples (e.g. 7ms) may be rejected or silently rounded by the stack.*
