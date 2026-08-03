# Diagnostic flow delivery

**Status:** Locked — 2026-07-31

> The event catalog and public stream live in
> [SPEC.md §11](../../../SPEC.md#diagnostics--events). This decision record
> explains delivery, buffering, collector context, and platform logging.

## Event metadata

Every event has an explicit `code`, `severity`, and `occurredAt: Instant`.
`occurredAt` is the event instant; generic `timestamp` is not used. Codes are
stable UShort values grouped by lifecycle/configuration, discovery/peer,
transport/BLE, crypto/trust, routing, transfer, storage, and internal ranges.
Payloads may contain redacted identity or operation identifiers but never raw
handles, addresses, keys, ciphertext, payloads, or platform exception text.

## Context

Diagnostics originate from BLE callbacks, protocol coroutines, timers, and
storage boundaries. Running application code directly from those producers
would make threading unpredictable and could block security, routing, or radio
work. Retaining an unbounded history would violate the memory budget.

## Decision

MeshLink exposes diagnostics as a hot, read-only flow:

```kotlin
val diagnostics: Flow<DiagnosticEvent>
```

An internal bounded channel serializes producers before publishing events. The
configured `DiagnosticsSettings.eventBufferSize` bounds memory. Application
collector code runs in the collector's coroutine context; MeshLink does not
invoke a callback on a hidden application-facing thread.

No `eventCallback` setting exists. Applications select their own collection
scope and dispatcher using standard coroutine operators.

## Severity policy

Severity is fixed by event consequence and cannot be downgraded by settings:

- DEBUG: routine transitions and successful expected operations
- INFO: discovery, connections, bearer selection, and expected retries
- WARN: fallback, capacity pressure, diagnostic shedding, delayed propagation,
  and L2CAP circuit-breaker activation
- ERROR: trust/signature/replay failures, malformed authenticated control,
  storage corruption, provider self-test failure, and invariant violation

Repeated routine events may be coalesced. Security failures retain counters.
ERROR does not imply process termination; fail-closed scope determines the
affected frame, operation, peer, or instance.

## Backpressure and overflow

Protocol and BLE callback threads never block on a slow diagnostic consumer.
When the bounded buffer is full:

1. Security and error events are retained ahead of debug and informational
   events.
2. Low-severity events may be coalesced or dropped.
3. MeshLink emits one summarized overflow event when capacity becomes available.
4. Overflow handling never includes private keys, shared secrets, session keys,
   KDF output, or payload plaintext.

Diagnostics are observability data, not a correctness channel. Peer, transfer,
and message behavior is exposed through their dedicated public state and event
flows.

## Platform logging

`DiagnosticsSettings.emitLog` is opt-in and defaults to `false`. When enabled,
an internal consumer maps events to Logcat on Android and unified logging on
iOS. Logging uses structured identifiers and the common severity catalog.

Platform logging obeys the same redaction policy as the public flow and cannot
block protocol producers.

## Collector guidance

Applications may choose their own context:

```kotlin
meshLink.diagnostics
    .flowOn(applicationDiagnosticsDispatcher)
    .collect { event -> record(event) }
```

UI updates must be moved to the platform's main context by the application.
Stopping one collector does not stop MeshLink or other collectors.

## Testing

Tests use injected dispatchers and virtual time to verify:

- event ordering from concurrent producers;
- bounded memory and severity-aware overflow;
- one overflow summary per saturation interval;
- producer progress when a collector is blocked;
- identical event shapes and severity on Android and iOS; and
- secret and payload redaction.

## Related

- [Public API and lifecycle](../api/public-api-and-lifecycle.md)
- [SPEC.md §11](../../../SPEC.md#diagnostics--events)
- [CONSTITUTION.md §I](../../../CONSTITUTION.md#i-rigorous-code-quality)
