# Core Data Models

> **Specification**: [SPEC.md §3](../../SPEC.md#3-core-data-models)  
> **Design rationale**: [Data Model ADR](../decisions/model/data-model.md)  
> **Machine-readable**: [specs/codecs/models.yaml](../../specs/codecs/models.yaml), [specs/codecs/enums.yaml](../../specs/codecs/enums.yaml)

## Model Inventory

| Model | Stability | Description |
|-------|-----------|-------------|
| `PeerIdentity` | Public | 16-byte stable peer identifier; stable across key rotations |
| `IdentityKey` | Public | 32-byte Ed25519 static key |
| `HandshakeKey` | Public | 32-byte X25519 ephemeral/static handshake key |
| `MeshLinkVersion` | Public | Semantic version (major.minor.patch) |
| `TransferId` / `MessageId` | Public | 32-bit UInt origin-scoped identifiers |
| `AppHash` | Public | 128-bit application isolation hash |
| `KnownPeer` | Public | Snapshot of peer state, trust, and diagnostics |
| `Transfer` / `TransferHandle` | Public | Active transfer representation |
| `Message` / `MessageHandle` | Public | Incoming message representation |
| `SeqNo` | Internal | 32-bit sequence number with safe wrap-around |
| `Scoreboard` / `MutableScoreboard` | Internal | SACK bitfield for chunk acknowledgement |
| `TransferSession` | Internal | Internal transfer state tracking |
| `TransferStatus` | Internal | Internal status representation |
| `TransferOptions` | Public | Transfer configuration (priority, time-to-live) |
| `PowerModeSettings` | Public | Power mode parameters |

## Sealed Types

| Type | Subtypes | Description |
|------|----------|-------------|
| `TransferResult` | `Completed`, `Cancelled`, `Expired`, `UnrecoverableFailure`, `TrustFailure` | Terminal transfer outcomes |
| `MeshLinkException` | `ConfigurationException`, `LifecycleException`, `PermissionException`, `BluetoothException`, `StorageException`, `CryptoException`, `TrustException`, `RoutingException`, `TransferException` | Error hierarchy for command failures |
| `ErrorCode` | 20 stable UShort values | Stable error codes per category (see [§7.6](../../SPEC.md#error-hierarchy)) |
| `TransferState` | `AWAITING_DECISION`, `TRANSFERRING`, `ROUTE_UNAVAILABLE`, `RETRANSMITTING` | Non-terminal transfer lifecycle |
| `TransferKind` | `MESSAGE (0x00)`, `PAYLOAD (0x01)` | Wire discriminant for transfer payloads |
| `PeerTrust` | `UNVERIFIED`, `VERIFYING`, `TRUSTED`, `MISMATCHED`, `REVOKED` | Per-peer trust classification |
| `PeerState` | `CONNECTED`, `DISCONNECTED` | Public BLE link state |
| `MeshLinkState` | `UNINITIALIZED`, `CONFIGURED`, `RUNNING`, `PAUSED`, `STOPPED` | Instance lifecycle state |
| `PowerMode` | `HIGH`, `MEDIUM`, `LOW` | Power mode selection |
| `Priority` | `HIGH`, `NORMAL`, `LOW` | Transfer delivery priority |

## Quick Links

- [SPEC.md §3 — Full data model spec](../../SPEC.md#3-core-data-models)
- [Data Model ADR](../decisions/model/data-model.md)
- [Error Hierarchy Design ADR](../decisions/model/error-hierarchy.md)
- [Mesh Size Limits ADR](../decisions/model/mesh-size-limits.md)
- [Models YAML Spec](../../specs/codecs/models.yaml)
- [Enums YAML Spec](../../specs/codecs/enums.yaml)
