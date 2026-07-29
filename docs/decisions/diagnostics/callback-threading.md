# Diagnostic Callback Threading Model — Rationale

**Status:** Locked — 2026-07-26

> **Specification content** (dispatcher settings, callback signature, platform emitters, testing) lives in [SPEC.md §11](../../../SPEC.md#diagnostics--events). This ADR captures only the *why*.

---

## Decision

**All diagnostic callbacks execute on a dedicated `MeshLink` coroutine dispatcher** (IO-limited, not Main thread). Applies to both `eventCallback` and `emitToLog`.

---

## Why Dedicated Dispatcher?

| Alternative | Why Rejected |
|-------------|--------------|
| Main thread | Blocks UI; crashes on iOS if callback does I/O |
| Default dispatcher | Competes with CPU-intensive crypto/routing work |
| Caller's thread | Unpredictable (BLE callback, timer, etc.) — breaks encapsulation |
| New thread per event | Thread explosion under high event rate |

**Design**: Fixed thread pool (2 threads) — isolates blocking callbacks, bounds memory, prevents starvation.

---

## Why Not Main Thread?

- **Android**: `Log.d()` is fast but user callbacks may do I/O or UI ops
- **iOS**: `os_log` is fast but any Swift callback may capture `@MainActor` context
- **Cross-platform parity**: Same threading model on both platforms

---

## Callback Contract (Rationale)

| Rule | Rationale |
|------|-----------|
| **Execute on diagnostic dispatcher** | Predictable, isolated, testable |
| **Don't block** | 2-thread pool → blocking starves other events |
| **Don't update UI directly** | Not Main thread; use `mainDispatcher.launch { }` |
| **Do forward to your dispatcher** | Decouples MeshLink from host app architecture |

---

## emitToLog Rationale

- **Opt-in** (`emitToLog = false` default) — production apps may not want logcat/os_log spam
- **Platform-native** — logcat on Android, `os_log` on iOS (structured, filterable)
- **Severity mapping** — DEBUG→verbose, INFO→debug, WARN→warn, ERROR→error

---

## Testing Rationale

- **Test override** (`DiagnosticDispatcher.testOverride`) enables deterministic testing with `TestCoroutineDispatcher`
- **Blocking resilience test** — verifies 2-thread pool handles one blocked callback without stalling others

---

## Performance Targets

| Metric | Target | Mechanism |
|--------|--------|-----------|
| Callback latency | < 1 ms | Lock-free channel, minimal emitter work |
| Throughput | 10,000 events/sec | Batched dispatch, bounded queue |
| Memory overhead | < 100 KB | Object pooling for frequent events |
| Blocking tolerance | 2 concurrent blocked | Fixed thread pool (2) isolates blocking |

---

## Related

- [SPEC.md §11](../../../SPEC.md#diagnostics--events) — Full diagnostic event hierarchy
- [SPEC.md](../../../SPEC.md)
- [CONSTITUTION.md §IV](../../../CONSTITUTION.md#iv-performance-requirements)
