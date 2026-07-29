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

## Diagnostics Rationale

`MtuNegotiatedEvent` emits **effective** parameters (negotiated MTU, effective chunk size) so host apps observe actual behavior, not requested values.

---

## Related

- [SPEC.md §6 (Transport), §10 (Power)](../../../SPEC.md)
- [Power Mode Behavior](../power/power-mode-behavior.md)
