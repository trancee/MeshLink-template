# Diagnostics & Events

> **Specification**: [SPEC.md §11](../../SPEC.md#diagnostics--events)  
> **Design rationale**: [Diagnostic Flow Delivery](../decisions/diagnostics/flow-delivery.md), [Public API and Lifecycle](../decisions/api/public-api-and-lifecycle.md)

## Public Observation

```kotlin
val knownPeers: StateFlow<List<KnownPeer>>
val transfers: StateFlow<List<Transfer>>
val messages: Flow<Message>
val diagnostics: Flow<DiagnosticEvent>
```

Peers and transfers expose current snapshots. Messages and diagnostics are
occurrences and do not accumulate as an unbounded public history.

## Known Peers

`knownPeers` contains peers whose full canonical identity has been learned,
including unverified, verifying, trusted, mismatched, and revoked peers.
Advertisement-only candidates are not canonical peers.

- `seenAt` is immutable and records when the full identity was first learned.
- `verifiedAt` is nullable and records the latest successful authentication.
- Trusted, mismatched, and revoked records persist as disconnected.
- Transient unverified/verifying observations do not persist after work ends.

## Peer Lifecycle (Internal)

```text
CONNECTED (active BLE link)
    └── BLE link lost → DISCONNECTED (grace period active)
            ├── BLE reconnects → CONNECTED
            └── Grace period expires → GONE (ephemeral cleanup; trust retained)
```

Grace periods: HIGH=15s, MEDIUM=30s, LOW=45s. Internal `GONE` removes transient
presence; a persisted trusted or revoked identity remains in `knownPeers` as
unavailable.

## Diagnostic Event Hierarchy

Sealed interface `DiagnosticEvent` has subtypes per layer:

| Layer | Event Types |
|-------|-------------|
| Route | `RouteDecryptFailureEvent`, `RouteDigestMismatchEvent` |
| Transport | `TransportFallbackEvent` |
| Transfer | `TransferDataPlaneBearerEvent`, `TransferSessionTransitionEvent`, `TransferFailureEvent` |
| Power | `PowerModeEffectiveEvent` |
| Handshake | `HandshakeEvent` |
| Key Rotation | `KeyRotationEvent` |
| Noise | `NoiseSessionEvent` |

## Severity Levels

`DEBUG`, `INFO`, `WARN`, `ERROR` — mapped to platform logging when enabled.

## Flow Delivery

An internal bounded channel serializes diagnostic producers. Application code
runs in the collector's coroutine context, never directly on a BLE callback
thread. A slow collector cannot block protocol work.

On saturation, lower-severity events may be coalesced or dropped, but MeshLink
retains security/error events ahead of them and emits a summarized overflow
event when capacity returns.

## Platform Logging

`DiagnosticsSettings.emitToLog` is opt-in and defaults to `false`. Platform
logging uses Logcat on Android and unified logging on iOS. It follows the same
secret and payload-redaction rules as the public diagnostic flow.

## Quick Links

- [SPEC.md §11 — Full diagnostics spec](../../SPEC.md#diagnostics--events)
- [Diagnostic Flow Delivery ADR](../decisions/diagnostics/flow-delivery.md)
- [Public API and Lifecycle ADR](../decisions/api/public-api-and-lifecycle.md)
- [Diagnostic Events Spec](../../specs/catalogs/diagnostic-events.yaml)
