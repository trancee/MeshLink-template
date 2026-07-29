# MeshLink Exception Hierarchy — Rationale

**Status:** Locked — 2026-07-28

> **Full hierarchy, codes, platform wrapping helpers, and test matrix** live in [SPEC.md §7.6](../../../SPEC.md#error-hierarchy-sealed). This ADR captures the *why*.

---

## Decision

**Single sealed `MeshLinkException` hierarchy in `commonMain` with stable `ErrorCode` enum. All platform exceptions (Android `BluetoothException`, iOS `NSError`) wrapped at boundary — never leak to consumers.**

---

## Why Sealed Hierarchy in commonMain?

| Alternative | Why Rejected |
|-------------|--------------|
| Platform-specific exceptions | Violates cross-platform parity (CONSTITUTION.md §III) |
| String-based error codes | No type safety; refactoring breaks consumers |
| Single generic exception + enum | Can't carry structured context (peerId, sessionId, chunkIndex) |
| Multiple root exceptions | Harder to catch "any MeshLink error" |

**Sealed hierarchy + `ErrorCode` enum** gives:

- Exhaustive `when` handling for consumers
- Structured context per error type (peerId, sessionId, etc.)
- Stable `ErrorCode` for programmatic handling
- Platform boundary wrapping is explicit and auditable

---

## ErrorCode Design

**Categories** (reflected in enum grouping):

- **Trust/Security**: `PEER_NOT_FOUND`, `KEY_UNKNOWN`, `TRUST_VIOLATION`, `SIGNATURE_VERIFICATION_FAILED`, `REPLAY_DETECTED`
- **Routing**: `NO_ROUTE`, `ROUTE_UPDATE_FAILED`, `ROUTE_LOOP_DETECTED`
- **Transfer**: `TRANSFER_TIMEOUT`, `TRANSFER_CANCELLED`, `TRANSFER_CORRUPTED`, `SESSION_NOT_FOUND`, `CHUNK_OUT_OF_BOUNDS`
- **Transport**: `BLUETOOTH_DISABLED`, `CONNECTION_FAILED`, `COC_NOT_SUPPORTED`, `GATT_OPERATION_FAILED`, `L2CAP_CHANNEL_FAILED`
- **Configuration**: `INVALID_PARAMETER`, `INVALID_STATE`, `PERMISSION_DENIED`
- **Internal**: `INTERNAL_ERROR`, `CRYPTO_OPERATION_FAILED`, `SERIALIZATION_FAILED`

**Why `ErrorCode` not `errorCode: Int`?**

- Enum = stable, IDE-navigable, exhaustiveness-checkable
- No magic numbers; adding codes requires source change (intentional)

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
- [SPEC.md §7.6](../../../SPEC.md#error-hierarchy-sealed)
- [CONSTITUTION.md §III](../../../CONSTITUTION.md#iii-user-experience-consistency)
