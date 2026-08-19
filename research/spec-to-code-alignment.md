# Research: Spec-to-Code Traceability Alignment

**Issue:** #17 — Verify spec-to-code traceability alignment
**Scope:** `specs/traceability/specification-map.yaml` vs `meshlink/src/commonMain/`
**Date:** 2026-08-19

---

## Executive Summary

The specification map **accurately tracks implementation status at the high level** — every
`not_implemented` cross-reference entry genuinely has no implementation, every implicit-`implemented`
entry has substantive code, and the `wire/` directory is correctly noted as empty.

However, **file-level traceability is incomplete**: 14 files with `SPEC-ANCHOR` comments are not
listed in any `code_files` list, the `spec_anchors_in_code` section is missing the
`peer-identity-model` anchor, several enums outside `Enums.kt` carry `SPEC-ANCHOR: enums` but
are unmentioned, and several existing files (TransferHandle, MessageHandle, TransferSource,
TransferSink, IncomingTransfer, TransferKind, etc.) appear nowhere in the spec map at all.

---

## 1. Verification of `implementation_status: not_implemented` Entries

All five entries explicitly marked `not_implemented` are genuinely unimplemented. Each has
`code_files: []` (empty), and the corresponding source code is absent.

| Spec ID | Status | Evidence |
|---------|--------|----------|
| `discovery-identity` | Correct | No `discovery/` directory exists. No BLE advertisement, scanning, or discovery code in commonMain. |
| `trust-model-tofu` | Correct | No Noise XX/IK implementation, no `CryptoProvider`, no `TrustStore`. Only `TrustRecord.kt` (a passive data holder) and `ReplayWindow.kt` (a 64-bit anti-replay utility) exist as crypto-adjacent infrastructure. |
| `transport-layer` | Correct | No transport implementation. `transport/L2capState.kt` contains only a 6-value enum (no GATT/L2CAP channel code, no bearer negotiation). |
| `security-layer` | Correct | Only `ReplayWindow.kt` and `ConstantTime.kt` exist as isolated crypto utilities. No Noise handshake state machine, no key rotation implementation, no `TrustStore`, no `KeyRotationProof` handling. |
| `routing-layer` | Correct | No `routing/` directory exists. No `RouteCoordinator`, `RouteStatement`, `RouteCandidate`, `RouteDigestTracker`, or `RouteAdvertisementPlanner` code. Only `RoutingPolicy.kt` constants exist in `model/`. |

**Conclusion:** All `not_implemented` entries are accurate.

---

## 2. Verification of Implicit-`implemented` Entries (no `implementation_status` field)

Every cross-reference entry without an explicit `implementation_status` has all its `code_files`
present and substantive:

| Spec ID | code_files | Status |
|---------|-----------|--------|
| `data-model` | PeerIdentity.kt, SeqNo.kt, Scoreboard.kt, TransferSession.kt, Enums.kt, IdentityKey.kt, HandshakeKey.kt, TransferResult.kt, MeshLinkVersion.kt, MeshLinkState.kt | All 10 files exist. All contain substantive implementations (value classes with real logic, data classes, enums). |
| `power-management` | PowerMode.kt | Exists. Substantive — enum with HIGH/MEDIUM/LOW modes, each with full `PowerModeSettings` including scan duty cycle, intervals, chunk sizes, retry budgets. |
| `diagnostics-events` | DiagnosticEvent.kt | Exists. Substantive — `DiagnosticCode` value class with 10 constants, `DiagnosticEvent` sealed interface with 10 data-class subtypes, each with real field mappings to `specs/catalogs/diagnostic-events.yaml`. |
| `build-quality` | meshlink/build.gradle.kts | Exists. |
| `configuration-model` | MeshLinkSettings.kt | Exists. Substantive — `MeshLinkSettings` data class plus `MeshLinkSettingsBuilder` with full validation in `build()` (appId, durations, routeExpiry > routeDigestInterval, maxRoutes range, etc.). |

**Conclusion:** All implicit-`implemented` entries are accurate. Their code files exist and are
substantive.

---

## 3. Verification of `implementation_status: partial` Entry

| Spec ID | Status | Evidence |
|---------|--------|----------|
| `transfer-layer` | Accurate | `TransferSession.kt`, `Scoreboard.kt`, `TransferResult.kt` exist with substantive implementations. However, `TransferCoordinator` is not implemented, `IncomingTransfer.accept()`/`reject()` are `TODO` stubs, and `MeshLink.sendPayload()`/`sendMessage()` are `TODO` stubs. The data models exist but the coordination logic does not. |

**Conclusion:** The `partial` status is accurate.

---

## 4. Cross-Reference: `code_files` Lists vs Actual File Listing

**All referenced files exist.** Every path listed in every `code_files` list under `cross_references`
is present on disk:

- `data-model`: 10/10 files present ✓
- `transfer-layer`: 3/3 files present ✓
- `power-management`: 1/1 file present ✓
- `diagnostics-events`: 1/1 file present ✓
- `build-quality`: `meshlink/build.gradle.kts` present ✓
- `configuration-model`: `MeshLinkSettings.kt` present ✓

**No missing referenced files.** No `code_files` entry points to a non-existent path.

---

## 5. The `wire/` Directory

The directory `meshlink/src/commonMain/kotlin/ch/trancee/meshlink/wire/` exists but is
**completely empty** — zero source files.

The spec map correctly tracks this: `implementation_status.not_implemented` lists
"Custom MeshLink Wire Codec (frames, enums, models, bounded reader/writer)".

**Design note (not a defect):** The codec spec files
(`specs/codecs/models.yaml`, `specs/codecs/frames.yaml`, `specs/codecs/enums.yaml`) define
wire-format contracts for frames, enums, and models. The enum and model *types* are implemented
directly as Kotlin types in the `model/` package (`Enums.kt`, `PeerIdentity.kt`, `TransferKind.kt`,
etc.) rather than in a dedicated `wire/` codec package. The actual wire codec implementation
(frame readers/writers, envelope construction/parsing, bounded binary codec) has not been written
anywhere in the source tree.

---

## 6. Discrepancies and Mismatches

### 6a. Contradiction: "MeshLinkSettings DSL integration" listed as not_implemented

The `configuration-model` cross-reference entry is implicitly `implemented` (no
`implementation_status` field), and `MeshLinkSettings.kt` exists with a fully working
`MeshLinkSettingsBuilder` (validation logic in `build()` is substantive). However, the
`implementation_status.not_implemented` list at the bottom of the spec map includes
"MeshLinkSettings DSL integration".

**Assessment:** The DSL code itself **is** implemented. The "integration" that is not implemented
is the wiring of settings into the `MeshLink` runtime lifecycle — but since `MeshLink.kt` is itself
a scaffold (`start()`, `pause()`, `resume()`, `stop()`, `setPowerMode()` all throw
`TODO("Not implemented — scaffold for BCV baseline")`), no settings integration can occur. The
not_implemented entry should more precisely be described as "MeshLinkSettings runtime integration
into MeshLink lifecycle" or moved into the `configuration-model` entry with a `partial` status.

### 6b. "Message and transfer handles, sources, and sinks" listed as not_implemented, but code exists

The `implementation_status.not_implemented` list includes "Message and transfer handles, sources,
and sinks". However, these files **do exist** with substantive implementations:

- `TransferHandle.kt` — public class with `id`, `status` (StateFlow), `outcome` (ReceiveChannel),
  `cancel` (suspend lambda), and `await()` method.
- `MessageHandle.kt` — same pattern, for MESSAGE operations.
- `TransferSource.kt` — public interface with `total` and `read(offset, length)`.
- `TransferSink.kt` — public interface with `write(offset, bytes)`, `complete()`, `abort(cause)`.

**Assessment:** These are type/interface definitions with real method signatures, not stubs. The
"not_implemented" status refers to the **functional capability** — `MeshLink.sendPayload()` and
`MeshLink.sendMessage()` (which return these handles) are `TODO` stubs, and there is no
`TransferCoordinator` to orchestrate actual transfers. The types exist as API surface for the BCV
baseline, but the functionality they serve is not wired up. This is a labeling precision issue —
the types are implemented, the behavior is not.

### 6c. `spec_anchors_in_code` section is incomplete

The section lists 23 anchors, but two are missing or incomplete:

1. **`peer-identity-model` is missing.** `PeerIdentity.kt` contains `SPEC-ANCHOR: peer-identity-model`
   (line 12), but this anchor is **not listed** in the `spec_anchors_in_code` section. All other
   peers (TransferId, MessageId, SeqNo, Scoreboard, etc.) are listed.

2. **`enums` anchor only references Enums.kt, but spans 3 files.** The `SPEC-ANCHOR: enums` comment
   appears in:
   - `model/Enums.kt` (listed in spec_anchors_in_code ✓)
   - `transfer/PayloadDecision.kt` (NOT listed ✗)
   - `transport/L2capState.kt` (NOT listed ✗)

### 6d. 14 files with SPEC-ANCHOR comments are not in any `code_files` list

The following files carry `SPEC-ANCHOR` comments but do not appear in any `cross_references` entry's
`code_files` list:

| File | SPEC-ANCHOR |
|------|-------------|
| `MeshLink.kt` | `meshlink-public-api` |
| `MeshLinkEnvironment.kt` | `meshlink-environment` |
| `MeshLinkState.kt` | `meshlink-state` |
| `TransferId.kt` | `transfer-id-model` |
| `MessageId.kt` | `message-id-model` |
| `TrustRecord.kt` | `trust-record` |
| `RoutingPolicy.kt` | `ttl-by-priority` |
| `ErrorCode.kt` | `error-code` |
| `Exceptions.kt` | `error-hierarchy` |
| `ReplayWindow.kt` | `replay-window` |
| `AppHash.kt` | `app-hash` |
| `KnownPeer.kt` | `known-peer-model` |
| `ConstantTime.kt` | `constant-time` |
| `MeshHash.kt` | `mesh-hash` |

These are substantive implementations (verified by reading each file) that are anchored to spec
sections but **orphaned from the `code_files` traceability** — they exist in the code with anchors
but are never linked to a spec section via `code_files`.

### 6e. Existing files completely absent from the spec map

The following files exist in `commonMain` but are referenced **nowhere** in
`specification-map.yaml` — not in any `code_files` list, not in
`spec_anchors_in_code`, and they carry no `SPEC-ANCHOR` comment:

| File | Content | Traced? |
|------|---------|---------|
| `model/IncomingTransfer.kt` | Internal class; `accept()`/`reject()` are `TODO("Not implemented")` stubs | No |
| `model/TransferKind.kt` | Public enum: MESSAGE(0x00), PAYLOAD(0x01). Matches `enums.yaml` exactly | No |
| `model/TransferHandle.kt` | Public class with `internal constructor`; substantive | No |
| `model/MessageHandle.kt` | Public class with `internal constructor`; substantive | No |
| `model/Transfer.kt` | Public data class snapshot | No |
| `model/TransferOptions.kt` | Options for transfers | No |
| `model/TransferStatus.kt` | Transfer status type | No |
| `model/TransferSource.kt` | Public interface for data source | No |
| `model/TransferSink.kt` | Public interface for data sink | No |
| `model/CryptoKeyConstants.kt` | Key size constants | No |
| `util/SecureRandom.kt` | Secure random ULong generator | No |
| `util/RequireSetting.kt` | Settings validation helper | No |
| `util/BigEndianConversions.kt` | Big-endian byte conversion utilities | No |

### 6f. `Enums.kt` does not contain `TransferKind`

The `enums.yaml` spec defines `TransferKind` as `package: ch.trancee.meshlink.model`. The
implementation lives in `model/TransferKind.kt` (not `Enums.kt`). The spec map's
`spec_anchors_in_code` lists only `Enums.kt` for the `enums` anchor. `TransferKind.kt` is not
traced at all, though it is listed in the bottom `implementation_status.implemented` summary
section.

### 6g. Settings catalog header imprecision

`specs/catalogs/settings.yaml` line 3 states:
> "Source of truth: SPEC.md §14 and meshlink/src/commonMain/kotlin/ch/trancee/meshlink/model/PowerMode.kt"

The actual source of truth for `MeshLinkSettings` is `meshlink/src/commonMain/kotlin/ch/trancee/meshlink/MeshLinkSettings.kt`
(package `ch.trancee.meshlink`). `PowerMode.kt` is the source for `PowerMode`/`PowerModeSettings`
only. The settings catalog content itself (fields, defaults, validation rules) aligns correctly
with the code — this is a documentation cross-reference imrecision, not a spec/code mismatch.

---

## 7. Spec-to-Spec Alignment Notes

### Diagnostic events catalog matches implementation

`specs/catalogs/diagnostic-events.yaml` defines 10 diagnostic event codes (0x8101–0x8603) with
field schemas. The implementation in `DiagnosticEvent.kt` defines exactly these 10 event types with
matching `DiagnosticCode` constants and field sets. The sealed interface hierarchy is substantive.
This alignment is correct. ✓

### Enums spec matches implementations

All wire enums defined in `specs/codecs/enums.yaml` have matching Kotlin implementations:

- `FrameType` → `model/Enums.kt` (14 frame types with UByte codes 0x00–0x42) ✓
- `TransferKind` → `model/TransferKind.kt` (MESSAGE=0x00, PAYLOAD=0x01) ✓
- `PayloadDecision` → `transfer/PayloadDecision.kt` (ACCEPTED=0x00, REJECTED=0x01) ✓
- `TransferResultCode` → not yet implemented (not in code) — correctly tracked as not_implemented ✓

### State machines spec matches code

`specs/protocol/state-machines.yaml` defines `TransferState`, `NoiseSessionState`,
`DiscoveryAttemptState`, `L2capState`, `PeerLifecycle`, `PeerTrust`. The implemented ones
(`TransferState` → `TransferSession.kt`, `L2capState` → `L2capState.kt`, `PeerTrust` →
`Enums.kt`, `PeerLifecycle` → `Enums.kt`) match. The not-yet-implemented ones (`NoiseSessionState`,
`DiscoveryAttemptState`) are correctly tracked as not_implemented. ✓

---

## 8. Overall Assessment

| Criterion | Result |
|-----------|--------|
| `not_implemented` entries are genuinely unimplemented | ✅ All 5 accurate |
| `partial` entry is genuinely partial | ✅ Accurate |
| Implicit `implemented` entries have substantive code | ✅ All accurate |
| All `code_files` paths exist on disk | ✅ No missing files |
| `wire/` directory is empty and tracked as such | ✅ Correct |
| `spec_anchors_in_code` is complete | ❌ Missing `peer-identity-model`; `enums` spans 3 files but only lists 1 |
| All commonMain files are traced in the spec map | ❌ 14 files with anchors not in any `code_files`; 15 files completely untraced |
| Bottom `implementation_status` list is precise | ⚠️ "MeshLinkSettings DSL integration" contradicts implicitly-implemented `configuration-model`; "Message and transfer handles" exist as types |

**The specification map is accurate for its primary purpose — tracking high-level implementation
status of major protocol layers.** The discrepancies are in file-level granularity and cross-referencing
completeness, not in status claims for major entries. The main gaps are:

1. **Orphaned anchored files**: 14 files have `SPEC-ANCHOR` comments but aren't in any `code_files`
   list, meaning the spec sections they implement aren't traced to them via the cross-reference
   mechanism.
2. **Missing anchor**: `peer-identity-model` in `PeerIdentity.kt` is absent from
   `spec_anchors_in_code`.
3. **Incomplete `enums` anchor**: `PayloadDecision.kt` and `L2capState.kt` both carry
   `SPEC-ANCHOR: enums` but aren't listed.
4. **Untraced files**: 15 files exist in commonMain with no specification map footprint at all
   (though most are type definitions for the scaffold API surface, not functional implementations).
5. **Labeling precision**: 2 entries in the bottom `implementation_status.not_implemented` list
   ("MeshLinkSettings DSL integration", "Message and transfer handles, sources, and sinks") describe
   *functional wiring* gaps, not *code existence* gaps — the types exist but aren't integrated into
   the MeshLink runtime since `MeshLink.kt` is a scaffold.
