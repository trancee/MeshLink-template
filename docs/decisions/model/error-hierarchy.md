# MeshLink Exception Hierarchy — Rationale

**Status:** Locked — 2026-07-31

> **Full hierarchy, codes, platform wrapping helpers, and test matrix** live in [SPEC.md §7.6](../../../SPEC.md#error-hierarchy). This ADR captures the *why*.

---

## Decision

**Single sealed interface `MeshLinkException` hierarchy in `commonMain` with stable explicit `ErrorCode` values. All platform exceptions (Android Bluetooth failures, iOS NSError) are wrapped at the boundary and never leak to consumers.**

---

## Why Sealed Hierarchy in commonMain?

| Alternative | Why Rejected |
|-------------|--------------|
| Platform-specific exceptions | Violates cross-platform parity (CONSTITUTION.md §III) |
| String-based error codes | No type safety; refactoring breaks consumers |
| Single generic exception + enum | Can't carry structured context (peer identity, transfer ID, chunk index) |
| Multiple root exceptions | Harder to catch "any MeshLink error" |

**Sealed hierarchy + `ErrorCode` enum** gives:

- Exhaustive `when` handling for consumers
- Structured context per error type (peer identity, transfer ID, etc.)
- Stable `ErrorCode` for programmatic handling
- Platform boundary wrapping is explicit and auditable

---

## Error hierarchy

```text
MeshLinkException
├── ConfigurationException
├── LifecycleException
├── PermissionException
├── BluetoothException
├── StorageException
├── CryptoException
├── TrustException
├── RoutingException
└── TransferException
```

Immediate public command failures throw typed subtypes. Untrusted parsing uses
sealed internal results; long-running payload failures use terminal status and
outcome. Coroutine `CancellationException` remains normal cancellation.

## ErrorCode Design

**Categories** use explicit UShort ranges (never enum ordinals): configuration 0x01xx,
permission 0x02xx, bluetooth 0x03xx, crypto 0x04xx, routing 0x05xx, transfer 0x06xx,
storage 0x07xx, lifecycle 0x08xx, transport 0x09xx, trust 0x0Axx, and internal 0x0Fxx.

**Categories** (reflected in enum grouping):

- **Configuration**: `INVALID_PARAMETER`, `INVALID_STATE`
- **Permission**: `PERMISSION_DENIED`
- **Bluetooth**: `BLUETOOTH_DISABLED`, `COC_NOT_SUPPORTED`, `CONNECTION_FAILED`, `GATT_OPERATION_FAILED`, `L2CAP_CHANNEL_FAILED`, `RADIO_IN_USE`
- **Storage**: `STORAGE_UNAVAILABLE`, `STORAGE_CORRUPTED`
- **Crypto**: `CRYPTO_OPERATION_FAILED`, `SIGNATURE_VERIFICATION_FAILED`, `REPLAY_DETECTED`
- **Routing**: `NO_ROUTE`, `ROUTE_ADVERTISEMENT_FAILED`, `ROUTE_LOOP_DETECTED`
- **Transfer**: `TRANSFER_TIMEOUT`, `TRANSFER_CANCELLED`, `TRANSFER_CORRUPTED`, `SESSION_NOT_FOUND`, `CHUNK_OUT_OF_BOUNDS`
- **Lifecycle**: *(reserved for future LifecycleException codes)*
- **Transport**: *(reserved for future transport-specific codes)*
- **Trust**: `PEER_NOT_FOUND`, `KEY_UNKNOWN`, `TRUST_VIOLATION`
- **Internal**: `INTERNAL_ERROR`, `SERIALIZATION_FAILED`

**Why `ErrorCode` not `errorCode: Int`?**

- Enum = stable, IDE-navigable, exhaustiveness-checkable
- Codes use explicit stable values and never enum ordinals
- Error context is redacted and does not reveal whether identity/key guesses were close
- Adding codes requires an intentional source/specification change

---

## Platform Wrapping Rules

| Platform | Boundary | Rule |
|----------|----------|------|
| Android | `androidMain` | Catch `SecurityException`, `IllegalArgumentException`, `BluetoothGatt` status codes → wrap in appropriate `MeshLinkException` subtype |
| iOS | `iosMain` | Catch `NSError` (CoreBluetooth `CBErrorDomain`) → wrap |

**Never**: Let platform exception propagate to `commonMain` or public API.

---

## Diagnostic Integration

Every caught `MeshLinkException` at public API boundary **MUST** emit corresponding `DiagnosticEvent` before re-throwing. Enables:

- Observability without catching
- Correlation of exceptions with protocol state
- Automated error budget tracking

---

## Testing Rationale

**Test matrix** ensures:

- Every `ErrorCode` has at least one platform trigger path
- Every exception subtype carries expected structured context
- Platform wrapping is tested per-platform (`androidHostTest`, iOS unit tests)

---

## Related

- [SPEC.md](../../../SPEC.md)
- [SPEC.md §7.6](../../../SPEC.md#error-hierarchy)
- [CONSTITUTION.md §III](../../../CONSTITUTION.md#iii-user-experience-consistency)
