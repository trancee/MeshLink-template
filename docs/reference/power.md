# Power Management

> **Specification**: [SPEC.md §10](../../SPEC.md#power-management)  
> **Design rationale**: [Power Mode Behavior](../decisions/power/power-mode-behavior.md)

## Power Modes

| Parameter | HIGH | MEDIUM | LOW |
|-----------|------|--------|-----|
| Scan duty cycle | 20% | 10% | 5% |
| Advertisement interval | 100 ms | 500 ms | 1000 ms |
| Connection interval | 7.5–15 ms | 15–30 ms | 30–60 ms |
| Max concurrent connections | 8 | 4 | 2 |
| Chunk size | 512 B | 256 B | 128 B |
| Max retries | 10 | 5 | 3 |
| Retry budget | 60 s | 30 s | 15 s |
| Grace period (disconnect→GONE) | 15 s | 30 s | 45 s |

## EU Regulatory Clamping

When `regulatoryRegion = RegulatoryRegion.EU`:

- Advertisement interval **clamped to ≥ 300 ms**
- Scan duty cycle **clamped to ≤ 70%**

Applied in shared policy code (not platform wrappers).

## Grace Periods

Per-mode grace period controls `PeerLifecycleState` transition `DISCONNECTED → GONE`:

- During grace: routes can degrade before full retraction; transfers can pause instead of abandon
- Host app sees `PeerEvent.StateChanged(..., DISCONNECTED)`

---

## Quick Links

- [SPEC.md §10 — Full power spec](../../SPEC.md#power-management)
- [Power Mode Behavior ADR](../decisions/power/power-mode-behavior.md)
- [SPEC.md §11 — Diagnostics (PowerModeEffectiveEvent)](../../SPEC.md#diagnostics--events)
- [MTU Negotiation ADR](../decisions/transport/mtu-negotiation.md)
