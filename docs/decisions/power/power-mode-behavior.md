# Power Mode Behavior Specification

**Status:** Locked — 2026-07-20

> **Specification content** (parameter tables, grace periods, EU clamping) lives in [SPEC.md §10](../../../SPEC.md#power-management) and [specs/catalogs/settings.yaml](../../../specs/catalogs/settings.yaml). This ADR captures only the *rationale*.

## Context

MeshLink requires power-aware operation: discrete power modes governing scan duty cycle, advertisement interval, connection interval, concurrent connections, and transfer chunk size.

## Decision: Three-Mode Model with Fixed Grace Periods

### Why Three Modes?

| Alternatives Considered | Why Rejected |
|-------------------------|--------------|
| Continuous slider (0-100%) | No meaningful UX; developers can't reason about "73% power" |
| 5+ modes | Diminishing returns; 3 modes cover the practical range (max throughput / balanced / max battery) |
| Platform-specific modes | Violates cross-platform parity (CONSTITUTION.md §III) |

### Parameter Rationale

| Parameter | Rationale |
|-----------|-----------|
| **Scan duty cycle** | Linear relationship with BLE current draw (studies show ~1:1) |
| **Advertisement interval** | Shorter = faster discovery, but exponential power cost; 100ms/500ms/1000ms cover practical range |
| **Connection interval** | Quantized in 1.25 ms units (BLE Link Layer spec); 7.5 ms (6 units) = Android floor, 15 ms = iOS throughput/power sweet spot. See [Punchthrough: BLE Connection Interval & Throughput](https://punchthrough.com/ble-connection-interval-throughput/). Values: 7.5/15/30 ms (HIGH→LOW); idle intervals: 15/30/60 ms |
| **Chunk size** | Fits within BLE MTU (23–251B) after L2CAP/GATT headers (4B), ChaCha20-Poly1305 overhead (16B nonce+tag), framing |
| **Max retries / retry budget** | Balanced against battery drain and resource exhaustion; scales with mode's aggressiveness |

### Grace Period Rationale

Fixed grace period per mode — after expiry without reconnection, peer transitions to GONE (ephemeral cleanup; trust persists).

| Mode | Grace Period | Why |
|------|-------------|-----|
| HIGH | 15s | Aggressive mode expects fast reconnect; short grace avoids stale state |
| MEDIUM | 30s | Default; balances discovery reliability vs cleanup |
| LOW | 45s | Sparse connections; longer window for natural reappearance |

**Future**: Adaptive grace period based on peer stability tracked separately.

### EU Regulatory Clamping Rationale

- **Advertisement floor 300ms**: EU ETSI EN 300 328 limits duty cycle; intervals <300ms risk non-compliance
- **Scan ceiling 70%**: Same regulation; prevents excessive radio-on time

Clamping in shared policy code (not platform wrappers) ensures cross-platform consistency.

### Active and idle connection adaptation

The mode connection interval applies while handshake, urgent control, ACK, or
data work is queued. After five seconds without such work, MeshLink requests a
500–1000 ms idle interval for every mode. New work immediately requests the
active interval again.

The 5% scan-duty battery target applies to LOW/background idle operation. HIGH
and MEDIUM intentionally exceed it for discovery/performance. Platform APIs may
clamp or coarsely map requested intervals; diagnostics expose effective values
where observable.

### Platform Integration Rationale

| Platform | Mapping | Notes |
|----------|---------|-------|
| **Android** | HIGH→`LOW_LATENCY`, MEDIUM→`OPPORTUNISTIC`, LOW→`LOW_POWER` | Direct `ScanSettings` mode mapping |
| **iOS** | LOW uses background preservation | iOS scan modes less granular; background task for LOW mode |

### Diagnostics Contract Rationale

`PowerModeEffectiveEvent` emits **effective** parameters after clamping — so host apps observe actual behavior, not just requested values.

---

## Related

- [CONSTITUTION.md §IV](../../../CONSTITUTION.md#iv-performance-requirements) — Performance budgets
- [SPEC.md §10](../../../SPEC.md#power-management) — Full parameter tables
- [Peer Lifecycle](../../explanation/peer-lifecycle.md) — Grace period drives CONNECTED→DISCONNECTED→GONE
- [MTU Negotiation](../transport/mtu-negotiation.md) — Chunk size bounded by negotiated MTU
