# Diagnostics & Events

> **Specification**: [SPEC.md §11](../../SPEC.md#diagnostics--events)  
> **Design rationale**: [Callback Threading](../decisions/diagnostics/callback-threading.md)

## Peer Events (Public API)

```kotlin
sealed interface PeerEvent {
    data class Found(val peerId: PeerIdentity, val state: PeerConnectionState) : PeerEvent
    data class StateChanged(val peerId: PeerIdentity, val state: PeerConnectionState) : PeerEvent
    data class Lost(val peerId: PeerIdentity) : PeerEvent
}
```

## Peer Lifecycle (Internal)

```text
CONNECTED (active BLE link)
    └── BLE link lost → DISCONNECTED (grace period active)
            ├── BLE reconnects → CONNECTED
            └── Grace period expires → GONE (ephemeral cleanup; trust retained)
```

Grace periods: HIGH=15s, MEDIUM=30s, LOW=45s.

## Diagnostic Event Hierarchy

Sealed interface `DiagnosticEvent` with subtypes per layer:

| Layer | Event Types |
|-------|-------------|
| Route | `RouteDecryptFailureEvent`, `RouteDigestMismatchEvent` |
| Transport | `TransportFallbackEvent` |
| Transfer | `TransferDataPlaneBearerEvent`, `TransferSessionTransitionEvent`, `TransferFailureEvent` |
| Power | `PowerModeEffectiveEvent` |
| Handshake | `HandshakeEvent` |
| Key Rotation | `KeyRotationEvent` |
| Noise | `NoiseSessionTransitionEvent` |

## Severity Levels

`DEBUG`, `INFO`, `WARN`, `ERROR` — mapped to platform logging (Logcat, OSLog).

## Callback Threading

All callbacks execute on dedicated `MeshLink` coroutine dispatcher (IO-limited, 2 threads, not Main thread).

**Contract**:

- **DO** forward to your own dispatcher
- **DO** update UI via `mainDispatcher.launch { }`
- **DON'T** block, do I/O, or update UI directly

## emitToLog

Opt-in (`emitToLog = false` default). Platform-native: Logcat (Android), `os_log` (iOS).

---

## Quick Links

- [SPEC.md §11 — Full diagnostics spec](../../SPEC.md#diagnostics--events)
- [Callback Threading ADR](../decisions/diagnostics/callback-threading.md)
- [Diagnostic Events Spec](../../specs/diagnostic-events.yaml)
- [SPEC.md §5.7 — Peer lifecycle](../../SPEC.md#e2e-handshake-routing-over-mesh)
