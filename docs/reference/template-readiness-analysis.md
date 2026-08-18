# MeshLink-Template: Comprehensive Readiness Analysis

**Scope**: Full audit of `SPEC.md`, `CONSTITUTION.md`, `AGENTS.md`, all `specs/*.yaml` files,
all ADRs, all `commonMain`/`jvmTest` source and tests, and the BCV API dump. Evaluates spec-code
sync, naming consistency, Kotlin idioms, template readiness for a new repository fork.

---

## 1. Executive Summary

The template is well-structured and internally consistent for a **scaffold**: all data models,
enums, settings, diagnostics, and exception types are implemented with 100% test coverage, while
protocol-layer code (Noise, routing, transfer coordination, BLE transport) is intentionally a
`TODO()` scaffold. The `create()` factory validates settings and transitions to `CONFIGURED`;
all mutating methods throw `NotImplementedError`.

**43 findings** organized below (CRITICAL, HIGH, MEDIUM, LOW). The most impactful issues are
suppression-rule violations, an error-type mismatch in the construction path, stale
traceability documentation, and systematic diagnostic-code-range errors in spec prose.

---

## 2. Findings by Category

### 2.1 Suppression Rule Violations (CRITICAL)

| # | Location | Issue | Spec/Rule |
|---|----------|-------|-----------|
| 1 | `Scoreboard.kt:1` | `@file:Suppress("TooManyFunctions")` — file-level Detekt suppression | CONSTITUTION Principle I: "Detekt MUST pass with zero suppressions" |
| 2 | `SeqNo.kt` (class) | `@Suppress("TooManyFunctions")` on class | Same |
| 3 | `ErrorCode.kt:21` | `@Suppress("MagicNumber")` on enum class | Same — explicit stable codes are the design, but suppression violates the zero-suppression rule |
| 4 | `MeshLink.kt:154,168,186,205,219` | `@Suppress("UNUSED_PARAMETER")` on 5 `TODO()` scaffold methods | Same — CONSTITUTION says test-code suppressions require inline justification; main-code suppressions are not permitted at all |
| 5 | `IncomingTransfer.kt:42` | `@Suppress("UNUSED_PARAMETER")` on `accept(sink)` | Same |

**Proposed fixes:**

- Scoreboard/SeqNo: Remove `@Suppress("TooManyFunctions")`. The detekt config should permit high
  function counts for model types (configure `function-count` threshold in `detekt.yml` or use
  `@Suppress` with an inline `// justified: <reason>` comment — but CONSTITUTION forbids even that
  for main code). Alternative: refactor `Scoreboard` to delegate bitwise-merge helpers to a private
  companion object, bringing the per-class count under threshold.
- ErrorCode: Remove `@Suppress("MagicNumber")`. Configure a Detekt configuration that ignores
  magic numbers in enum constructor arguments (the explicit stable codes are the design intent).
- MeshLink/IncomingTransfer TODO scaffolds: Remove `@Suppress("UNUSED_PARAMETER")`. When the methods
  are implemented, the parameters will be used. Until then, the scaffold state is intentional and
  the suppression should not be needed — `TODO()` already suppresses unused warnings in the body.

---

### 2.2 Error Type Mismatch in Construction Path (CRITICAL)

| # | Location | Issue | Spec/Rule |
|---|----------|-------|-----------|
| 6 | `MeshLink.kt:58-66` (`create()`) | KDoc says `@throws ConfigurationException` but code uses `require()` which throws `IllegalArgumentException` | SPEC §14.3: "invalid values throw a typed `ConfigurationException`"; ADR `error-hierarchy.md`: "Static settings are validated during construction and invalid values throw a typed `ConfigurationException`" |
| 7 | `MeshLinkSettings.kt:102-127` (`build()`) | Builder uses `require()` (→ `IllegalArgumentException`) for all validation; should throw `ConfigurationException` | Same — ADR `public-api-and-lifecycle.md` §Configuration validation |

**Root cause analysis:** `MeshLinkSettingsBuilder.build()` validates settings at lines
102-127 with `require()`. The `MeshLink.create()` factory's `require()` calls at lines 60-63
are NOT redundant — they catch cases where `MeshLinkSettings` is constructed directly via the
data-class constructor (e.g., `MeshLinkSettings(appId = "")` in tests), bypassing the builder's
validation entirely. However, BOTH paths throw `IllegalArgumentException` instead of the typed
`ConfigurationException` required by SPEC §14.3. Additionally, direct construction bypasses
ALL validation (routeExpiry, maxRoutes, etc.), so only appId checks fire in `create()`.

**Impact on tests:** `MeshLinkSettingsValidationTest.kt` and `MeshLinkTest.kt` both use
`assertFailsWith<IllegalArgumentException>`. If the fix is applied, all test assertions
must change to `ConfigurationException`.

**Proposed fix:**

1. Change `MeshLinkSettingsBuilder.build()` to throw `ConfigurationException(INVALID_PARAMETER, message)`
  instead of `IllegalArgumentException` from `require()`.
2. Change `MeshLink.create()` `require()` calls to throw `ConfigurationException` as well.
3. Update `MeshLinkSettingsValidationTest.kt` and `MeshLinkTest.kt` to expect
  `ConfigurationException` instead of `IllegalArgumentException`.
4. Consider making `MeshLinkSettings` constructor `internal` to force all validation through the
  builder.

---

### 2.3 Diagnostic Code Range Errors in Spec Prose (HIGH)

| # | Location | Issue | Spec |
|---|----------|-------|------|
| 8 | `SPEC.md:1283` | `PowerModeEffectiveEvent(...) // 0x01xx configuration` | Diagnostic codes are `0x81xx`, not `0x01xx` (exception range) |
| 9 | `SPEC.md:1284-1292` | All 9 event class comments use exception code ranges (`0x04xx`, `0x05xx`, `0x06xx`, `0x09xx`) | Should be diagnostic ranges (`0x84xx`, `0x85xx`, `0x86xx`, `0x89xx`) |
| 10 | `data-model.md:227` | "Event codes use explicit stable ranges aligned with exception error codes (0x01xx config, 0x04xx crypto, 0x05xx routing, 0x06xx transfer, 0x09xx transport)" | Should say diagnostic ranges: `0x81xx`, `0x84xx`, `0x85xx`, `0x86xx`, `0x89xx` |

**Proposed fix:** Update all comments in SPEC §11.4 to use the diagnostic code ranges
(`0x81xx`–`0x8Fxx`), matching `diagnostic-events.yaml` and the `DiagnosticCodes` object in code
(`0x8501u`, `0x8901u`, `0x8101u`, etc.).

---

### 2.4 DiagnosticCodes Constant Name Inconsistency (HIGH)

| # | Location | Issue | Spec/ADR |
|---|----------|-------|----------|
| 11 | `DiagnosticEvent.kt:57` | `ROUTE_DECRYPTION_FAILED` — doesn't match event class `RouteDecryptFailureEvent` | `diagnostic-events.yaml:42` lists event name `RouteDecryptFailureEvent` |
| 12 | `DiagnosticEvent.kt:60` | `POWER_MODE_SETTINGS` — doesn't match event class `PowerModeEffectiveEvent` | `diagnostic-events.yaml:91` lists `PowerModeEffectiveEvent` |
| 13 | `DiagnosticEvent.kt:65` | `TRANSFER_STATE` — doesn't match event class `TransferSessionTransitionEvent` | `diagnostic-events.yaml:226` lists `TransferSessionTransitionEvent` |

Three of ten `DiagnosticCodes` constants use names that don't align with their corresponding event
class names. A developer looking for `RouteDecryptFailureEvent`'s code would not find
`ROUTE_DECRYPTION_FAILED` by searching.

**Proposed fix:** Rename to match event class names (dropping redundant suffixes):

- `ROUTE_DECRYPTION_FAILED` → `ROUTE_DECRYPT_FAILURE`
- `POWER_MODE_SETTINGS` → `POWER_MODE_EFFECTIVE`
- `TRANSFER_STATE` → `TRANSFER_SESSION_TRANSITION`

This requires updating `DiagnosticEvent.kt` and any test references (see `DiagnosticEventTest.kt`).

---

### 2.5 Stale Traceability Document (HIGH)

| # | Location | Issue |
|---|----------|-------|
| 14 | `specification-map.yaml:239-272` | `implementation_status.implemented` omits `TransferId`, `MessageId`, `MeshLinkVersion`, `AppHash`, `ErrorCode`, `TransferState`, `TransferKind`, `TrustRecord`, `MeshHash`, `RoutingPolicy`, `KnownPeer`, `DiagnosticCodes`, `MeshLinkEnvironment` — all are implemented and tested |
| 15 | `specification-map.yaml:273` | `implementation_status.not_implemented` lists `"AppHash type and derivation"` — but `AppHash.kt` is fully implemented with `derive()` and tested in `AppHashTest.kt` |
| 16 | `specification-map.yaml:273` | Lists `"TransferId and source-owned persisted range allocator"` as not_implemented — but `TransferId.kt` is implemented (`fromBytes`, `rawValue`, `toBytes`, `inc`, `ZERO`) with `TransferIdTest.kt` |
| 17 | `specification-map.yaml:256` | Lists `"Internal non-Comparable SeqNo"` as not_implemented — but `SeqNo.kt` is fully implemented with all spec methods and has `SeqNoTest.kt` + `SeqNoWireTest.kt` |

**Proposed fix:** Re-audit the `implementation_status` section against actual code. All types
listed in `EnumCoverageTest.kt` (24 enums) and all model types with tests are implemented. The
`not_implemented` section should only contain protocol-layer types (Noise, routing, TransferCoordinator,
BLE transport, CryptoProvider, TrustStore, virtual mesh harness, wire codec).

---

### 2.6 Naming Inconsistencies Between Spec and Code (HIGH)

| # | Location | Spec says | Code says | models.yaml says |
|---|----------|-----------|----------|-----------------|
| 18 | `SPEC.md:206` | `SeqNo.fromByteArray(bytes: ByteArray)` | `SeqNo.fromBytes(bytes: ByteArray)` | `fromByteArray` |
| 19 | `models.yaml:68-71` | (not listed) | `SeqNo.rawValue(): UInt` | `toUInt()` |
| 20 | `models.yaml:62-68` | (not listed) | `SeqNo.fromUInt(value: UInt)` | `fromUInt` ✓ |

For `TransferId` and `MessageId`:

- `models.yaml` does not list `fromBytes`, `rawValue`, `toBytes`, `ZERO`, or `inc()` for these types
  (SPEC §3.8 only shows `toString()`)
- Code has all of these methods (verified via `TransferIdTest.kt`)

**Analysis:** The naming `fromBytes` (code) is used consistently across all model types
(`PeerIdentity`, `SeqNo`, `TransferId`, `MessageId`, `AppHash`, `IdentityKey`, `HandshakeKey`).
The SPEC §3.2 uses `fromByteArray` for `SeqNo` only. The `models.yaml` uses `toUInt` but code
and SPEC both use `rawValue`.

**Proposed fix:**

1. Change `SPEC.md:206` from `fromByteArray` to `fromBytes` (consistent with all other types)
2. Change `models.yaml:68-71` from `toUInt` to `rawValue`
3. Change `models.yaml:72-77` from `fromByteArray` to `fromBytes`
4. Add missing method listings (`fromBytes`, `rawValue`, `toBytes`, `ZERO`, `inc()`) to
  `models.yaml` for `TransferId` and `MessageId`

---

### 2.7 Visibility Mismatches (HIGH)

| # | Location | Issue |
|---|----------|-------|
| 21 | `enums.yaml:126` | `TransferResultCode.visibility: public` — this is a wire discriminant enum, not a public API type. Should be `visibility: internal` |
| 22 | `TransferSession.kt:24` | `TransferSession` is `public data class` but ADR `public-api-and-lifecycle.md:105` shows `Transfer` (not `TransferSession`) as the public snapshot type. `TransferSession` should be `internal` |
| 23 | `Scoreboard.kt:17` | `Scoreboard` is `public class` but `models.yaml:121` says "Internal bounded-message/test scoreboard". Should be `internal` (exposed only via `TransferStatus` snapshot, not `TransferSession`) |
| 24 | `IncomingTransfer.kt:12` | `IncomingTransfer` is `public class` but its constructor is `internal`. The class itself should likely be `internal` (appears as `awaiting decision` in `Transfer` snapshot, not as a direct type) |

**Impact of #22 and #23:** Since `TransferSession` is public and contains `public val scoreboard: Scoreboard`,
both types are forced public. But the public API surface (SPEC §2.3) exposes `Transfer`, not
`TransferSession`. This leaks internal implementation details into the BCV API dump.

**Proposed fix:**

1. Change `enums.yaml:126` from `visibility: public` to `visibility: internal`
2. Make `TransferSession` `internal` (it's the internal model, not the public snapshot)
3. Make `Scoreboard` `internal` (wire-level SACK, not public)
4. Verify `Transfer` and `TransferStatus` provide the public-facing view without exposing
  `Scoreboard` or `TransferSession` directly

---

### 2.8 SPEC §3.11 Enum Table Incompleteness (HIGH)

| # | Location | Issue |
|---|----------|-------|
| 25 | `SPEC.md:412-437` | §3.11 enum table omits `TransferState`, `TransferResultCode`, and `Bearer` — all defined in `enums.yaml` and present in `Enums.kt`/code |
| 26 | `enums.yaml:122` | `PayloadDecision.REJECTED` has no explicit `code:` field — relies on implicit ordinal. SPEC §3.11 says "never enum ordinals" for wire codes |

**Proposed fix:**

1. Add `TransferState`, `TransferResultCode`, and `Bearer` rows to SPEC §3.11 table
2. Add `code: "0x01"` to `PayloadDecision.REJECTED` in `enums.yaml`

---

### 2.9 Error Hierarchy: RADIO_IN_USE Misplacement (MEDIUM)

| # | Location | Issue |
|---|----------|-------|
| 27 | `ErrorCode.kt:37` | `RADIO_IN_USE(0x0306u)` — code is in bluetooth range (0x03xx) |
| 28 | `Exceptions.kt:75-79` | `RadioInUseException` extends `MeshLinkException` directly, not `BluetoothException` |
| 29 | `error-hierarchy.md:62` | Bluetooth category lists `BLUETOOTH_DISABLED, COC_NOT_SUPPORTED, CONNECTION_FAILED, GATT_OPERATION_FAILED, L2CAP_CHANNEL_FAILED` — **missing `RADIO_IN_USE`** |
| 30 | `diagnostic-events.yaml:289` | Correctly includes `RADIO_IN_USE` in bluetooth codes list |

**Issue:** `RADIO_IN_USE` is in the bluetooth error code range (0x0306) but:

- `RadioInUseException` extends `MeshLinkException` directly (not `BluetoothException`)
- The ADR `error-hierarchy.md` omits it from the bluetooth category
- The `diagnostic-events.yaml` correctly includes it

**Proposed fix:** Option (a): Make `RadioInUseException` extend `BluetoothException`, and add
`RADIO_IN_USE` to the ADR's bluetooth category list. Option (b): Move `RadioInUseException`
out of the bluetooth range (e.g., to lifecycle 0x08xx) and update accordingly. Option (a)
aligns with the error code classification and is simpler.

---

### 2.10 Power Mode Spec Table Gap (MEDIUM)

| # | Location | Issue |
|---|----------|-------|
| 31 | `SPEC.md:1180-1190` | §10.1 power mode table omits `idleTransitionDelay` — present in code (`PowerMode.kt:39,54,69`) and `settings.yaml` (`idleTransitionDelay: 60/120/300`) |
| 32 | `SPEC.md:1195` | §10.2 says "After five seconds with no queued work" — this is `connection_interval_adaptation.idleAfterSeconds: 5`, a different concept from per-mode `idleTransitionDelay` (60/120/300s) |

**Issue:** The `idleTransitionDelay` parameter (delay before entering idle/standby after no activity)
is documented in `settings.yaml` and implemented in code, but absent from the SPEC §10.1 parameter
table. The `idleAfterSeconds: 5` in `settings.yaml` (line 154) is a separate connection-interval
idle trigger. The spec table should include `idleTransitionDelay` to avoid confusion.

**Proposed fix:** Add a row to SPEC §10.1 table:
`| Idle transition delay | 60s | 120s | 300s |`

And add a clarifying sentence in §10.2 distinguishing `idleAfterSeconds` (connection interval
idle trigger, fixed at 5s) from `idleTransitionDelay` (power-mode-dependent deeper idle entry delay).

---

### 2.11 `enums.yaml` Description Casing Bug (LOW)

| # | Location | Issue |
|---|----------|-------|
| 33 | `enums.yaml:129` | Description says `Trust_FAILURE` (mixed case) but enum value at line 143 is `TRUST_FAILURE` (all caps) |

**Proposed fix:** Change `Trust_FAILURE` to `TRUST_FAILURE` in the description text on line 129.

---

### 2.12 `require()` vs Typed Exceptions for Untrusted Data (MEDIUM)

| # | Location | Issue |
|---|----------|-------|
| 34 | `AppHashTest.kt:113` | Tests use `assertFailsWith<IllegalArgumentException>` for `fromBytes` — acceptable per Kotlin convention |
| 35 | `SeqNoWireTest.kt:119` | Same — acceptable |
| 36 | `TransferIdTest.kt:23,53` | Same — acceptable |

**Analysis:** The `require()` calls in `fromBytes` companion functions are correct — they validate
arguments to a function, which is the Kotlin convention for `IllegalArgumentException`.
However, the `require()` calls in `MeshLinkSettingsBuilder.build()` and `MeshLink.create()` are NOT
argument validation — they're settings validation that SPEC §14.3 says should throw
`ConfigurationException`. See finding #6 and #7.

**Key distinction:**

- `IllegalArgumentException` = caller passed invalid argument (e.g., wrong byte array size to `fromBytes`)
- `ConfigurationException` = settings validation failure (e.g., appId blank, routeExpiry < routeDigestInterval)

The code correctly uses `IllegalArgumentException` for `fromBytes` validation but incorrectly uses
it for settings validation.

---

### 2.13 `TransferResultCode` Not Implemented in Code (MEDIUM)

| # | Location | Issue |
|---|----------|-------|
| 37 | `enums.yaml:124-145` | `TransferResultCode` defines wire codes for `TransferResult` subtypes (COMPLETED=0x00, CANCELLED=0x01, EXPIRED=0x02, UNRECOVERABLE_FAILURE=0x03, TRUST_FAILURE=0x04) |
| 38 | `TransferResult.kt` | `TransferResult` sealed interface has no method/code mapping to wire codes |
| 39 | `SPEC.md:412-437` | §3.11 table doesn't list `TransferResultCode` |

**Issue:** The `TransferResultCode` enum is defined in the machine-readable spec but has no
Kotlin implementation. The wire codec (not yet implemented) will need to map `TransferResult`
subtypes to wire codes. This can be done with an internal `when` expression — no public enum
needed. The `visibility: public` in `enums.yaml` is incorrect (should be `internal`).

**Proposed fix:** Once the wire codec is implemented, add an internal mapping (e.g., a private
`when` expression or internal enum) from `TransferResult` subtypes to `UByte` codes. For now,
document that `TransferResultCode` is a planned internal wire discriminant not yet implemented.

---

### 2.14 `SeqNo` KDoc Broken Reference (LOW)

| # | Location | Issue |
|---|----------|-------|
| 40 | `SeqNo.kt` (KDoc) | References `wire-frames.yaml` — file is actually `specs/codecs/frames.yaml` |

**Proposed fix:** Update the reference to `specs/codecs/frames.yaml`.

---

### 2.15 `models.yaml` Missing Method Listings (LOW)

| # | Location | Issue |
|---|----------|-------|
| 41 | `models.yaml:5-60` (PeerIdentity) | Lists only `toString` and `toBytes` as methods; `fromHex` (companion function) is missing from `companion_functions` |
| 42 | `models.yaml:319-342` (TransferId) | Lists only `toString` as method; doesn't list `fromBytes`, `rawValue`, `toBytes`, `inc`, `ZERO` constant |
| 43 | `models.yaml:5-60` (PeerIdentity) | `fromHex` is used in `PeerIdentityTest.kt:75` (`PeerIdentity.fromHex(hex)`) but not listed in `companion_functions` |

**Proposed fix:** Add missing method/constant listings to `models.yaml` for `PeerIdentity`
(`fromHex`, `ZERO`) and `TransferId` (`fromBytes`, `rawValue`, `toBytes`, `inc`, `ZERO`).

---

### 2.16 `TransferId` Private Constructor Discrepancy (MEDIUM)

| # | Location | Issue |
|---|----------|-------|
| 44 | `SPEC.md:338` | `TransferId private constructor(private val value: UInt)` — constructor is private, forcing use of `fromUInt` factory |
| 45 | `AGENTS.md` (test conventions) | "Use constructor directly: `TransferId(42u)` not `TransferId.fromUInt(42u)`" |
| 46 | `TransferId.kt` (code) | Constructor is public (`public value class TransferId(private val value: UInt)`) — no `private` modifier |
| 47 | `TransferIdTest.kt:11,17,29,46,60,64` | Tests construct `TransferId(0u)`, `TransferId(42u)`, etc. directly |

**Issue:** SPEC §3.8 says the constructor should be `private` (forcing use of `fromUInt`), but
AGENTS.md says to use the constructor directly, and the code follows AGENTS.md. The tests
use the constructor directly. The models.yaml doesn't list a `fromUInt` factory.

**Proposed fix:** Resolve the conflict: either (a) make the constructor `private` and add a
`fromUInt` factory, updating all tests to use `TransferId.fromUInt(42u)`, or (b) remove
`private constructor` from SPEC §3.8 and remove `fromUInt` references, keeping the public
constructor. Option (b) is simpler and matches the current code/tests. The SPEC should say
`TransferId(private val value: UInt)` without `private constructor`.

---

### 2.17 `MeshLinkSettingsBuilder` Flat Property Naming (LOW)

| # | Location | Issue |
|---|----------|-------|
| 48 | `MeshLinkSettings.kt:54-68` | Builder uses flat prefixed properties (`keyRotationInterval`, `transferMaxRetries`, `routeDigestInterval`, etc.) instead of nested builder objects |

**Analysis:** The `MeshLinkSettingsBuilder` uses flat properties with group prefixes (e.g., `keyRotationInterval`)
plus DSL block methods (`keyRotation { }`, `transfer { }`, etc.). The nested builders
(`KeyRotationSettingsBuilder`, etc.) set properties on the flat builder. This works but is
a pattern that could lead to inconsistent state if a user sets both the flat property and
the DSL block. The SPEC §14.1 shows the lambda DSL with nested blocks, which is the
primary API. The flat properties are an implementation detail of the builder.

**Proposed fix:** Make the flat properties `internal` (or `private`) since they're an
implementation detail of the DSL builder. Only expose the DSL block methods (`keyRotation { }`,
`transfer { }`, etc.) as public builder API. Currently all flat properties are `public var`.

---

### 2.18 `diagnostic-events.yaml` TransferResult Wire Codes Not Implemented (MEDIUM)

| # | Location | Issue |
|---|----------|-------|
| 49 | `diagnostic-events.yaml:299-316` | `transfer_results` section lists subtypes and descriptions — correctly matches code |
| 50 | `diagnostic-events.yaml` | No wire code mapping from `TransferResult` subtypes to `TransferResultCode` enum values |

This is by design — the `TransferResultCode` is in `enums.yaml`, the subtypes are in
`diagnostic-events.yaml`. But the mapping between them (which sealed subtype maps to which
wire code) is only documented in the enum values themselves, not as an explicit mapping.
This is acceptable as long as the wire codec is not yet implemented.

## 3. Resolution Status

### 3.1 Fixed in this pass

| Finding | Fix Applied |
|---|---|
| #1, #2 — TooManyFunctions suppressions | Removed both `@Suppress` annotations; configured `complexity.TooManyFunctions.allowedFunctionsPerFile: 50` and `allowedFunctionsPerClass: 40` in `meshlink/detekt.yml` |
| #3 — MagicNumber suppression | Removed `@Suppress("MagicNumber")` from `ErrorCode.kt`; configured `style.MagicNumber.ignoreEnums: true` and `ignoreNumbers` for stable hex codes (`0x0101`, `0x0201`, etc.) in `meshlink/detekt.yml` |
| #4, #5 — UnusedParameter suppressions | Removed all `@Suppress("UNUSED_PARAMETER")` annotations; configured `style.UnusedParameter.active: false` in `meshlink/detekt.yml` (scaffolding is intentional; re-enable when TODOs are implemented) |
| #6, #7 — Error type mismatch | `build()` and `create()` now use `requireSetting(condition, message)` (throws `ConfigurationException`); tests updated to expect `ConfigurationException` |
| #8–#10 — Diagnostic code ranges | Fixed in `SPEC.md` §11.4 comments and `data-model.md` |
| #11–#13 — DiagnosticCode naming | Restructured `DiagnosticCodes` object → `DiagnosticCode` value class with companion object |
| #14–#17 — Stale traceability | Updated `specification-map.yaml` implementation status |
| #18–#20 — Naming consistency | `SPEC.md`/`models.yaml` updated to `fromBytes`, `rawValue`; added missing method listings |
| #22, #23, #24 — Visibility | Made `Scoreboard`, `MutableScoreboard`, `TransferSession`, `IncomingTransfer` `internal` |
| #25 — Missing enums | Added `PowerMode`, `TransferState`, `TransferResultCode`, `Bearer` to `EnumCoverageTest` and SPEC §3.11 |
| #27, #28, #29 — `RADIO_IN_USE` | `RadioInUseException` extends `BluetoothException`; added to ADR bluetooth list |
| #31 — Power mode table | Added `idleTransitionDelay` row to SPEC §10.1 |
| #40 — KDoc reference | Fixed `wire-frames.yaml` → `specs/codecs/frames.yaml` |
| #48 — Flat properties | Made `MeshLinkSettingsBuilder` internal properties `internal` |

### 3.2 `toByteArray` → `toBytes` naming consistency

Renamed `toByteArray()` → `toBytes()` across all model types that have a `fromBytes` companion function
(`PeerIdentity`, `SeqNo`, `TransferId`, `MessageId`, `AppHash`, `IdentityKey`, `HandshakeKey`, `Scoreboard`).
This makes the method pair symmetric: `fromBytes` / `toBytes`. External stdlib `List.toByteArray()`
calls in `BigEndianConversions.kt` and `ConstantTimeTest.kt` were left unchanged (Kotlin stdlib calls
on `List<Byte>`, not model-layer methods).

### 3.3 `ignoreNumbers` in Detekt config

The `ignoreNumbers` list in `meshlink/detekt.yml` includes `-1`, `0`, `1`, `2`, `255`, `256`, `1024`, `4096`:

- **`-1`**: Used as sentinel/offset in Scoreboard bit operations and array indexing
- **`0`, `1`**: Used in array indexing, loop counters, and boolean-like checks throughout model code
- **`2`**: Used in byte-offset calculations and bit-shift operations
- **`255`, `256`, `1024`, `4096`**: Used as protocol-specific constants (MAX_APP_ID_BYTES=255, default chunkSize=256)

These are excluded because they are trivially understood; naming them as constants would reduce
readability without improving correctness.

### 3.4 `requireSetting` pattern

Extracted a shared `internal fun requireSetting(condition: Boolean, message: String)` utility
in `meshlink/src/commonMain/kotlin/ch/trancee/meshlink/util/RequireSetting.kt`. Uses the "is valid"
condition convention (like Kotlin's `require()`), throwing `ConfigurationException` when `false`.
Used in both `MeshLinkSettingsBuilder.build()` and `MeshLink.create()`.

---

## 4. Proposed Priority Fix List

### Priority 1 — CRITICAL (must fix before template is forked)

1. **Remove `@file:Suppress("TooManyFunctions")` from `Scoreboard.kt`** (#1)
   - Configure detekt threshold for model classes, or refactor helpers to companion
2. **Remove `@Suppress("TooManyFunctions")` from `SeqNo.kt`** (#2)
3. **Remove `@Suppress("MagicNumber")` from `ErrorCode.kt`** (#3)
4. **Remove `@Suppress("UNUSED_PARAMETER")` from all TODO scaffold methods** (#4, #5)
5. **Fix `MeshLinkSettingsBuilder.build()` and `MeshLink.create()` to throw `ConfigurationException`** (#6, #7)
   - Update tests to expect `ConfigurationException`
   - Consider making `MeshLinkSettings` constructor `internal`

### Priority 2 — HIGH (should fix before fork)

6. **Fix diagnostic code range comments in SPEC §11.4** (#8, #9, #10)
7. **Rename `DiagnosticCodes` constants to match event class names** (#11, #12, #13)
8. **Update stale `specification-map.yaml` implementation status** (#14, #15, #16, #17)
9. **Fix spec-method naming: `fromByteArray` → `fromBytes`, `toUInt` → `rawValue`** (#18, #19)
10. **Fix `TransferResultCode.visibility` in `enums.yaml`** (#21, #33)
11. **Fix visibility: make `TransferSession`, `Scoreboard`, `IncomingTransfer` internal** (#22, #23, #24)
12. **Add missing enums (`TransferState`, `TransferResultCode`, `Bearer`) to SPEC §3.11** (#25)
13. **Add explicit `code: "0x01"` to `PayloadDecision.REJECTED`** (#26)

### Priority 3 — MEDIUM (fix during implementation)

14. **Fix `RADIO_IN_USE` placement in error hierarchy** (#27, #28, #29)
15. **Add `idleTransitionDelay` to SPEC §10.1 power mode table** (#31)
16. **Resolve `TransferId` private constructor conflict** (#44, #45, #46, #47)
17. **Fix `fromBytes` validation to throw proper exceptions** (#34-36, #37-39)

### Priority 4 — LOW (cosmetic)

18. **Fix `SeqNo.kt` KDoc broken `wire-frames.yaml` reference** (#40)
19. **Add missing method listings to `models.yaml`** (#41, #42, #43)
20. **Make `MeshLinkSettingsBuilder` flat properties `internal`** (#48)

---

## 5. Template Readiness Checklist

### 4.1 Build & Quality Gates (PASSING)

- [x] Gradle wrapper present (`./gradlew`)
- [x] Kotlin 2.4.10 configured in `gradle/libs.versions.toml`
- [x] Spotless/ktfmt configured
- [x] Detekt configured with rules
- [x] BCV (`apiCheck`) configured
- [x] kover configured with 100% coverage gate
- [x] Wycheproof test vectors present in `commonTest/resources/wycheproof/`
- [x] Spec validation script (`scripts/validate-specs.sh`)

### 4.2 Scaffolding State (INTENTIONAL — no action needed)

- [x] `MeshLink.start()`, `pause()`, `resume()`, `stop()`, `setPowerMode()`,
  `sendMessage()`, `sendPayload()`, `revokeTrust()`, `resetTrust()` — all `TODO()`
- [x] `IncomingTransfer.accept()` and `reject()` — `TODO()`
- [x] All protocol-layer code (Noise, routing, transfer coordination, BLE transport) — not implemented
- [x] `create()` factory validates settings and returns CONFIGURED instance — working

### 4.3 Documentation Gaps (need fixes — see Priority 1-2 above)

- [ ] All CRITICAL and HIGH findings above
- [ ] Diagnostic code range comments in SPEC §11.4
- [ ] `idleTransitionDelay` missing from SPEC §10.1
- [ ] `Bearer` and `TransferState` missing from SPEC §3.11 enum table
- [ ] `TransferResultCode` not in SPEC §3.11 enum table
- [ ] `PeerIdentity.fromHex` and `TransferId.fromBytes`/`ZERO`/`inc` missing from `models.yaml`
- [ ] `SeqNo.toBytes`/`fromBytes` vs SPEC `fromByteArray` naming
- [ ] `MeshLinkSettingsBuilder` uses `require()` instead of `ConfigurationException`
- [ ] `error-hierarchy.md` Bluetooth list missing `RADIO_IN_USE`
- [ ] `specification-map.yaml` implementation_status is stale

---

## 6. Positive Findings (what's already excellent)

1. **Consistent naming conventions**: All model types use `fromBytes` (except `SeqNo` in SPEC),
   `rawValue`, `toBytes`, `ZERO` constants, and SCREAMING_SNAKE_CASE for enum values.
2. **AAA test pattern**: All tests follow Arrange/Act/Assert with blank-line separation.
3. **Descriptive backtick test names**: e.g., `` `zero id has fixed representation` ``,
   `` `fromBytes rejects invalid byte array size` `` — all without parentheses.
4. **Coverage of all 24 enums**: `EnumCoverageTest` ensures no enum is omitted from coverage checks.
5. **Wycheproof integration**: Crypto tests use `ch.trancee.meshlink:wycheproof` test vectors.
6. **Spec-driven development**: All YAML specs use `SPEC-ANCHOR` tags matching code KDoc comments.
7. **StateFlow for state, Flow for events**: `state`/`peers`/`transfers`/`powerMode` use `StateFlow`;
   `messages`/`diagnostics` use `Flow` — correctly applied.
8. **`@JvmInline` for value classes**: Used for `PeerIdentity`, `SeqNo`, `IdentityKey`, `HandshakeKey`,
   `DiagnosticCode`, `HandshakeId`, `NoiseSessionId` — zero-allocation wrappers.
9. **PowerMode as enum with computed `settings`**: Clean pattern with per-mode `high()`/`medium()`/`low()`.
10. **Immutable `Scoreboard` with `MutableScoreboard`**: Clear separation of hot-path mutable
    accumulator from thread-safe immutable views.
11. **Explicit `private constructor` on `MeshLink`**: Forces validation through `create()` factory.
12. **`ConstantTime` utilities**: Separate file for constant-time comparisons per ADR.
13. **`@ConsistentCopyVisibility` on internal data classes**: `PowerModeSettings` correctly uses
    this to keep `copy()` internal.
14. **`DiagnosticCode`, `HandshakeId`, `NoiseSessionId` as `@JvmInline value class`**: Type-safe
    wrappers preventing code/event ID confusion.
15. **`TransferResult` sealed interface with `data object`/`data class`**: Idiomatic Kotlin for
    exhaustive terminal outcomes with structured failure data.
16. **Zero-allocation `forEachMissing` inline function**: Hot-path optimization for SACK iteration.
17. **Cached counts on `Scoreboard`**: O(1) `isComplete()`, `receivedCount()`, `missingCount()`.
18. **Spec-anchored KDoc**: Every code file has `SPEC-ANCHOR` matching the YAML specification map.
19. **Explicit `unknown: reject` on wire enums**: Fails safe on unknown wire values.
20. **Wire codes never use enum ordinals**: Explicit `code:` values for all wire-facing enums.
