# GATT MTU Negotiation & L2CAP Credit Flow Control — Rationale

**Status:** Locked — 2026-07-26

**Specification content** (MTU tables, chunk size bounds, code examples) lives in [SPEC.md §6, §10](../../../SPEC.md). This ADR captures only the *why*.

---

## Decision

**Request MTU 517 on every GATT connection** (maximum for BLE 4.2+). L2CAP CoC uses credit-based flow control with default MTU 251 (BLE 4.0) or negotiated MTU.

---

## Rationale

### Why MTU 517 as Target?

- **BLE 4.2+ supports up to 517** — requesting max gives headroom for all power modes
- **HIGH mode needs 512-byte chunks + 3-byte ATT header = 515 minimum** — 517 leaves 2 bytes margin
- **Android `requestMtu(517)` is a hint**; peer may grant less — code must handle any negotiated MTU ≥ 23

### Chunk Size ↔ MTU Binding

| Power Mode | Chunk Size | Min MTU Required | Why |
|------------|------------|------------------|-----|
| HIGH | 512 | 515 | 512 + 3 ATT header |
| MEDIUM | 256 | 259 | 256 + 3 ATT header |
| LOW | 128 | 131 | 128 + 3 ATT header |

**Rule**: If negotiated MTU < `chunkSize + 3`, reduce chunk size for that session. This is a **per-session adaptation**, not a global settings change.

### Why GATT Control Plane, L2CAP CoC Data Plane?

| Requirement | GATT | L2CAP CoC |
|-------------|------|-----------|
| Always available | ✅ Yes (BLE 4.0+) | ❌ Optional (BLE 4.2+, peer must support) |
| MTU negotiation | Standard `requestMtu()` | Credit-based, negotiated at open |
| Reliability | ATT handle-value notifications | Credit flow control (explicit ACK) |
| Throughput | Limited by ATT overhead | Higher (larger MTU, no ATT overhead) |
| Fallback needed | N/A | ✅ Yes — GATT always works |

**Decision**: Control plane (handshakes, routing, transfer control) MUST work over GATT alone for reliability. Data plane (bulk chunks) promotes to L2CAP CoC when available for throughput.

### Android vs iOS MTU Behavior

| Aspect | Android | iOS |
|--------|---------|-----|
| MTU Request API | `BluetoothGatt.requestMtu(int)` | None — auto-negotiated on first write |
| Callback | `onMtuChanged(gatt, mtu, status)` | Check `peripheral.maximumWriteValueLength` |
| Reliability | Explicit, controllable | Transparent, post-facto |

**Rationale**: Abstract behind `GattMtuManager` (expect/actual). Android actively requests; iOS passively observes. Both expose `negotiatedMtu` to shared transport logic.

### L2CAP Credit Flow Control

- **Initial credits: 10** — balances latency (sender can burst) vs memory (receiver buffer)
- **Credit threshold: 3** — request replenishment early to avoid pipeline stall
- **Max credits: 50** — bounds receiver memory
- **Monitoring loop**: Periodic check (100ms) prevents deadlock if credits not returned

**Why not larger initial credits?**

- BLE 4.2 default initial credits = 10; some stacks enforce this
- Larger credits → more receiver buffer memory → higher RAM pressure on mobile
- 10 × 517B ≈ 5KB per channel — acceptable for 8 concurrent connections

---

## L2CAP capability and health

Advertised support does not imply runtime reliability. L2CAP capability remains
transport state and never enters routing LinkQuality or routeCost.

Each authenticated adjacent peer tracks process-local `L2capHealth` with
`state`, `failureCount`, `failedAt`, and `retriesAt`. Open failure/timeout,
unexpected EOF, stream error, stall, partial-frame timeout, or channel drop
immediately stops assigning new chunks to L2CAP, discards partial frames, and
moves data to GATT without changing route, trust, E2E keys, or transfer IDs.
SACK retransmits missing chunks.

Circuit-breaker schedule:

```text
failure 1 → retry in 15–30 seconds
failure 2 → retry in 1–2 minutes
failure 3 → retry in 5–10 minutes
failure 4 → disable L2CAP for the process lifetime
```

Failure history resets only after ten continuous healthy minutes or one
error-free transfer of at least 1 MiB. Health is not persisted; process restart
permits a clean capability probe. During bearer drain, only one bearer assigns
new chunks and late duplicate chunks remain idempotent.

## Diagnostics Rationale

`MtuNegotiatedEvent` emits **effective** parameters (negotiated MTU, effective chunk size) so host apps observe actual behavior, not requested values. Typed fallback diagnostics distinguish open failure, timeout, stream error, stall, mid-transfer drop, and local policy.

---

## Related

- [SPEC.md §6 (Transport), §10 (Power)](../../../SPEC.md)
- [Power Mode Behavior](../power/power-mode-behavior.md)
