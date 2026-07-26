# Diagnostics & Events

> Source: [SPEC.md §11](../../SPEC.md#11-diagnostics--events)

## 11.1 Peer Events

```text
sealed interface PeerEvent {
  data class Found(val peerIdentity: PeerIdentity, val connectionState: PeerConnectionState)
  data class StateChanged(val peerIdentity: PeerIdentity, val state: PeerConnectionState)
  data class Lost(val peerIdentity: PeerIdentity)
}
```

## 11.2 Connection States

```text
enum class PeerConnectionState {
  CONNECTED,
  DISCONNECTED
  // GONE is internal only, triggers PeerEvent.Lost
}
```

### 11.2.1 Internal Connection State Tracking

`PeerLifecycleState` is the internal runtime tracking type that drives the peer lifecycle (CONNECTED → DISCONNECTED → GONE). It is not exposed publicly — only `PeerConnectionState` (the enum above) is visible to the host app.

```text
PeerLifecycleState {
  peerIdentity: PeerIdentity
  connectionState: PeerConnectionState
  expiresAt: Instant?        // Non-null while grace window is active; null when GONE
  rssi: Int?                 // For metric calculation
  supportsCoc: Boolean       // L2CAP CoC capability
  connectionInterval: Int    // ms
  handshakeAt: Instant?      // For timeout calculations
}
```

`PeerLifecycleState` tracks grace periods and coordinates cleanup across routing, transfer, and presence state. The host app only sees `PeerEvent.Found`, `PeerEvent.StateChanged`, and `PeerEvent.Lost`.

## 11.3 Diagnostic Events (Machine Observable)

All diagnostic events are defined in `meshlink/src/commonMain/kotlin/ch/trancee/meshlink/diagnostics/DiagnosticEvent.kt` as a sealed interface hierarchy.

| Event Category | Fields | Code Type |
|----------------|--------|-----------|
| `route.*` | `peerIdentity`, `frameType`, `failureReason` | `RouteDecryptFailureEvent` |
| `transport.*` | `peerIdentity`, `reason` | `TransportFallbackEvent` |
| `transfer.*` | `sessionId`, `bearer` | `TransferDataPlaneBearerEvent` |
| `power.*` | `requestedTier`, `effectiveTier`, `regulatoryRegion`, `scanDutyCyclePercent`, `advertisementIntervalMs`, `connectionIntervalMs` | `PowerTierEffectiveEvent` |
| `handshake.*` | `sessionId`, `pattern`, `fallbackUsed`, `verificationLevel`, `rateLimitAttempts`, `nonceReplayDetected` | `HandshakeEvent` |
| `key_rotation.*` | `peerIdentity`, `oldKeyVerified`, `sequenceNumberReset`, `propagationDeadlineMet`, `reason` | `KeyRotationEvent` |
| `route.*` | `peerIdentity`, `localDigest`, `remoteDigest` | `RouteDigestMismatchEvent` |
| `transfer.*` | `sessionId`, `peerIdentity`, `fromState`, `toState`, `bytesTransferred`, `totalBytes` | `TransferSessionTransitionEvent` |
| `transfer.*` | `sessionId`, `peerIdentity`, `reason` | `TransferFailureEvent` |
| `noise.*` | `peerIdentity`, `layer`, `fromState`, `toState`, `role`, `handshakePattern`, `failureReason` | `NoiseSessionTransitionEvent` |

**Diagnostic Field Descriptions:**

- `transfer.priority`: Reflects the Priority (HIGH/NORMAL/LOW) of the transfer, inherited from the originating RoutingMessage. Enables QoS monitoring and resource allocation decisions. This field is surfaced on `TransferSessionTransitionEvent` via the transfer session's `priority` field.
- `handshake.fallbackUsed`: `true` when the NX fallback handshake pattern is used instead of IX; set when the destination's public key is unknown.
- `handshake.fullPublicKeyVerified`: `true` when the NX fallback verified the full 64-byte concatenated public key (Ed25519 || X25519) byte-for-byte in Msg1.
- `key_rotation.sequenceNumberReset`: `true` when the neighbor accepted the new key and reset its seqno to 1.
- `key_rotation.propagationDeadlineMet`: `true` when the key rotation announcement reached all direct neighbors within the deadline.

## 11.4 Error Model

Errors use a sealed `MeshLinkException` hierarchy in `commonMain`, with platform exceptions wrapped and never leaking to consumers:

- Trust/Security errors (PeerNotFoundError, TrustError, KeyUnknownError)
- Routing errors (NoRouteError, RouteUpdateError)
- Transfer errors (TransferTimeoutError, TransferCancelledError, TransferCorruptedError)
- Transport errors (BluetoothStateError, ConnectionTimeoutError, CocNotSupportedError)

**ErrorCode enum:** `PEER_NOT_FOUND`, `KEY_UNKNOWN`, `TRUST_VIOLATION`, `TRANSFER_TIMEOUT`, `BLUETOOTH_DISABLED`, `CONNECTION_FAILED`, `INVALID_PARAMETER`, `INTERNAL_ERROR`

**TransferFailureReason:**

```text
sealed interface TransferFailureReason {
  data class Unrecoverable(val message: String) : TransferFailureReason
  data class TrustFailure(val peerIdentity: PeerIdentity) : TransferFailureReason
}
```

This type is carried by `TransferSession.failureReason` and distinguishes the two terminal failure modes that map to the `FAILED` delivery outcome:

- `Unrecoverable` → delivery outcome `unrecoverable-failure`
- `TrustFailure` → delivery outcome `trust-failure`
