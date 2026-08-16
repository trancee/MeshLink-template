# Production-Readiness Analysis

> **Status**: Template/scaffold. Data-model scaffolding is implemented and
> BCV-frozen. The core `MeshLink` class and all protocol layers (wire, crypto,
> routing, transport, security) are `TODO()` stubs. This repo is **not**
> compilable into a working mesh networking library yet — it is a
> specification-first scaffold with a frozen public API surface and a full
> test harness for the implemented data models.

A structural audit of the MeshLink template repository — what is present,
what is missing, what is inconsistent, and what another AI needs to know
to turn this template into a shipping library.

---

## 1. What Exists (✅ Functional)

### Specification Suite (Authoritative)

- **`SPEC.md`** (1,550 lines) — single source of truth covering all layers:
  vision, architecture, data models, discovery, trust/TOFU, transport,
  security, routing, transfer, power, diagnostics, build, testing, settings,
  futures, and traceability index.
- **`specs/codecs/models.yaml`** — normative data model definitions (25+ types).
- **`specs/codecs/frames.yaml`** — normative wire frame definitions.
- **`specs/codecs/enums.yaml`** — normative enum definitions with reserved
  ranges.
- **`specs/protocol/state-machines.yaml`** — TransferState, PeerTrust,
  KeyRotationState, TransferResult, RoutingUpdateTrigger, AdvertisementCooldown
  state machines.
- **`specs/catalogs/diagnostic-events.yaml`** — diagnostic event catalog.
- **`specs/catalogs/settings.yaml`** — settings catalog.
- **`specs/traceability/specification-map.yaml`** — SPEC ↔ ADR ↔ code ↔ test
  mapping.

### Design Rationale (ADRs)

- 28 ADRs across crypto (9), discovery (3), transport (3), model (4),
  transfer (4), routing (1), power (1), diagnostics (1), storage (1),
  and API (1).
- ADRs use the `docs/decisions/<area>/<name>.md` structure.

### Implemented Code (Data Models Only)

The `meshlink/src/commonMain/` directory contains **fully implemented** data
model types with 100% test coverage:

| Package | Types |
|---|---|
| `ch.trancee.meshlink.model` | `PeerIdentity`, `SeqNo`, `Scoreboard`, `TransferSession`, `Enums`, `IdentityKey`, `HandshakeKey`, `AppHash`, `TrustRecord`, `KnownPeer`, `TransferResult` subtypes |
| `ch.trancee.meshlink` | `MeshLinkVersion`, `MeshLinkState`, `MeshLinkSettings` |
| `ch.trancee.meshlink.util` | `BigEndianConversions` |
| `ch.trancee.meshlink.security` | `ReplayWindow`, `ConstantTime`, `SecureRandom` |

### Implemented Tests

- ~30+ test files in `meshlink/src/commonTest/` covering data models, enums,
  wire encoding helpers, crypto primitives (via Wycheproof), and edge cases.
- **100% line + branch coverage** on `:meshlink` (Kover gate enforced).
- Wycheproof vectors present: `ed25519_test.json`, `x25519_test.json`,
  `hkdf_sha256_test.json`, `hmac_sha256_test.json`, `chacha20_poly1305_test.json`.

### Build & Tooling

- Gradle 8.5+ with Kotlin 2.4.10, Kotlin Multiplatform.
- ktfmt, Detekt, Kover, Binary Compatibility Validator (BCV) all configured.
- Git hooks (`pre-commit`, `pre-push`, `commit-msg`) mirror CI quality gates.
- CI workflow (`.github/workflows/ci.yml`) runs all quality gates.

### What Does NOT Exist

See [§3 — Missing Implementation](#3-missing-implementation).

---

## 2. Discrepancies Found & Fixed

The following inconsistencies between SPEC, code, and docs were identified
and corrected in this audit pass:

| # | File | Issue | Fix Applied |
|---|------|-------|-------------|
| 1 | `SPEC.md` §11.3 | Diagnostic code ranges listed as `0x01xx`–`0x0Fxx` but code and `diagnostic-events.yaml` use `0x81xx`–`0x8Fxx` with high bit `0x8000` | Updated §11.3 to document the `0x81xx`–`0x8Fxx` space and the high-bit distinction from error codes |
| 2 | `CONSTITUTION.md` §Tech Constraints | Listed `kotlinx-datetime` as a runtime dependency, but code uses `kotlin.time.*` from stdlib (Kotlin 2.1+) and `build.gradle.kts` has no such dependency | Updated to list two dependencies; documented that `kotlin.time` types are in stdlib |
| 3 | `SPEC.md` §12.4 | Same stale `kotlinx-datetime` reference | Updated to match CONSTITUTION.md |
| 4 | `docs/reference/build-quality.md` §Runtime Dependencies | Same stale `kotlinx-datetime` reference | Updated to match |
| 5 | `specs/codecs/models.yaml` | `toByteArray()` listed under `companion_functions` for `IdentityKey` and `HandshakeKey` instead of `methods` | Moved to `methods` section for both types |
| 6 | `meshlink/src/.../IdentityKey.kt` | `toByteArray()` missing despite SPEC §3.10 and `models.yaml` specifying it | Added `public fun toByteArray(): ByteArray = bytes.copyOf()` (defensive copy) |
| 7 | `meshlink/src/.../HandshakeKey.kt` | `toByteArray()` missing | Added same method |
| 8 | `meshlink/api/jvm/meshlink.api` | BCV baseline did not include `toByteArray()` | Regenerated via `jvmApiDump` |
| 9 | `meshlink-proof` test | `MeshLinkProofTest.kt` asserted `MeshLinkVersion(0, 0, 0)` but `MeshLink.VERSION` is `MeshLinkVersion(0, 1, 0)` | Fixed assertion to `(0, 1, 0)` |
| 10 | `.github/workflows/ci.yml` | Stale scaffold comment; missing `--rerun-tasks --no-build-cache` flags on Gradle invocations; quality tasks not module-scoped (`:meshlink:` prefix missing) | Removed stale comment; added flags to all invocations; added `:meshlink:` prefix to `ktfmtCheck`, `detekt`, `koverVerify`, `apiCheck` |
| 11 | `specs/traceability/specification-map.yaml` | `data_model` test list referenced `RouteCandidateTest.kt` (nonexistent — RouteCandidate is not implemented) and listed test files at old paths (`ch/trancee/meshlink/` instead of `ch/trancee/meshlink/model/`) | Removed dangling reference; updated all paths to `model/` |
| 12 | `meshlink/src/commonTest` | `IdentityKeyTest.kt`, `HandshakeKeyTest.kt`, `PeerIdentityTest.kt`, `SeqNoTest.kt`, `ScoreboardTest.kt` in wrong package (`ch.trancee.meshlink`) and wrong directory for `model` sources | Moved to `ch/trancee/meshlink/model/` with correct package |
| 13 | `docs/README.md` §Structure | Listed `specs/product/`, `specs/epics/`, `specs/tests/` as existing planning directories that didn't exist | Created directories with README.md placeholders; split into 3 table rows for clarity |
| 14 | `SPEC.md` §14.19 | Same nonexistent directory references | Updated table to match docs/README.md |
| 15 | `meshlink/src/commonTest/resources/wire-compat/` | Referenced in SPEC.md §13.2 but directory didn't exist | Created directory with README.md placeholder |

---

## 3. Missing Implementation

### 3.1 Core Stub (BLOCKING)

**The entire `MeshLink` class is `TODO()` stubs.**

- `meshlink/src/commonMain/kotlin/ch/trancee/meshlink/MeshLink.kt`
- All methods throw `TODO()`:
  - `start()`, `pause()`, `resume()`, `stop()`
  - `sendMessage()`, `sendPayload()`
  - `revokeTrust()`, `resetTrust()`
  - `setPowerMode()`, `powerModeSettings`
  - `diagnostics` flow
- `meshlink-proof/src/main/kotlin/MeshLinkProof.kt` delegates to `MeshLink.VERSION`
- `meshlink-benchmark/src/main/kotlin/MeshLinkBenchmark.kt` same pattern

This means **no actual mesh networking functionality exists**. The type
hierarchy and data model compile, but the library cannot discover peers,
establish Noise handshakes, route messages, or send/receive payloads.

### 3.2 Missing Protocol Layers (ALL `not_implemented`)

The traceability spec marks these as `implementation_status: not_implemented`:

| Layer | Package | SPEC Section |
|-------|---------|--------------|
| **Wire Codec** | `ch.trancee.meshlink.wire` | §9 (frame format) |
| **E2E Crypto** | `ch.trancee.meshlink.crypto` | §5 (trust/TOFU), §7 (security) |
| **Transport** | `ch.trancee.meshlink.transport` | §6 (transport) |
| **Routing** | `ch.trancee.meshlink.routing` | §8 (routing) |

Only `ch.trancee.meshlink.security` exists partially (`ReplayWindow`,
`ConstantTime`, `SecureRandom`) and is tested, but the core Noise handshake
and E2E encryption layers are not implemented.

### 3.3 Wire Codec (§9)

SPEC.md §9 defines the MeshLink Wire Codec — a custom FlatBuffers-inspired
binary format with varint framing, field codes, and protection levels.
Neither the codec encoder/decoder nor the frame layout code exists in
`commonMain/wire/`.

The spec describes a custom wire format (not FlatBuffers itself), with:

- Frame type codes (Beacon, PeerIdentity, Message, RouteAdvertisement, etc.)
- Variable-length payload encoding
- Length-prefix framing on the transport
- Protection levels (PLAIN, SIGNED, ENCRYPTED)

**Impact**: Without the wire codec, no frames can be encoded or decoded.

### 3.4 Crypto / Noise (§5, §7)

The project depends on `ch.trancee.meshlink:meshlink-crypto` v0.1.1 from
Maven Central for primitives (Noise XX/IK, ChaCha20-Poly1305, Ed25519,
X25519, HKDF, HMAC). The `commonMain/crypto/` package does not exist —
the E2E layer (session management, key derivation, handshake state machine)
is entirely absent.

### 3.5 Transport (§6)

No BLE GATT/L2CAP transport layer exists. The Android BLE glue
(`meshlink/src/androidMain/`, `meshlink/src/iosMain/`) is empty.

- No `BluetoothLeScanner` / `BluetoothGatt` / `L2CAP CoC` code
- No Android service for background operation
- No iOS CoreBluetooth central/peripheral code

### 3.6 Routing (§8)

`RouteCandidate`, `LinkQuality`, `RouteStatement`, and the routing
coordinator are all `not_implemented`. The `RoutingPolicyTest.kt` exists
but tests an unimplemented policy DSL.

### 3.7 Companion Modules (Thin Placeholders)

| Module | State |
|--------|-------|
| `meshlink-reference/` | Compose Multiplatform UI skeleton. Public API only — does not use MeshLink networking. |
| `meshlink-proof/` | Single version-check test. No real-device BLE proof scenarios. |
| `meshlink-benchmark/` | JVM smoke benchmark entry point only. No real performance scenarios. |

---

## 4. Ambiguities and Open Questions

### 4.1 Wire Codec vs. FlatBuffers — RESOLVED

The naming ambiguity is eliminated. A prominent "NOT FlatBuffers" callout has
been added to:

- `docs/explanation/why-meshlink-wire-codec.md` (§1) — full-page banner
  explicitly stating no FlatBuffers runtime, `.fbs` schemas, `flatc`, or
  FlatBuffers library dependency is used.
- `SPEC.md` §1.4 (Reference Standards table) — inline note below the wire
  encoding row.

Implementors should read the callout before searching for FlatBuffers libraries.
The YAML contracts (`specs/codecs/`) are the normative source; the custom
Kotlin codec is implemented from them directly.

### 4.2 Trust Record Persistence — Implementation gap (documented)

`TrustRecord` and `KnownPeer` exist as data models. The persistence strategy is
fully specified in `docs/decisions/storage/persistence-strategy.md` with platform-
specific details (Android encrypted record, iOS Keychain). No serialization schema
(`kotlinx.serialization`) or file/DB backing exists yet — the actual persistence
implementation is planned for the storage layer (currently `not_implemented` per
traceability). When implemented, it will use the ADR's specified approach:
atomic encrypted records on Android, non-synchronizable Keychain items on iOS.

### 4.4 Diagnostic Event Delivery Semantics — RESOLVED (power mode interaction added)

The ADR `docs/decisions/diagnostics/flow-delivery.md` already specified the
backpressure/overflow policy (severity-based retention, coalescing, overflow
summary). A new section "Power mode interaction" has been added to clarify:

- DEBUG events scale with `PowerMode` (high-power emits all; low-power suppresses DEBUG)
- `eventBufferSize` is PowerMode-independent
- ERROR/WARN never suppressed by PowerMode

### 4.5 CODEOWNERS vs. Actual Code — RESOLVED (note added)

`.github/CODEOWNERS` now includes a comment noting that the `crypto/`,
`routing/`, and `wire/` package rules are forward-looking — the packages are
currently empty (implementation planned via TDD) but will be protected once
populated.

---

## 5. Build & Quality Gate Status

| Gate | Status | Notes |
|------|--------|-------|
| `./gradlew :meshlink:build` | ✅ PASS | Compiles, 100% unit tests pass |
| `./gradlew :meshlink:koverVerify` | ✅ PASS | 100% line + branch coverage |
| `./gradlew :meshlink:apiCheck` | ✅ PASS | BCV baseline validated (with toByteArray fix) |
| `./gradlew :meshlink:detekt` | ✅ PASS | Zero suppressions |
| `./gradlew :meshlink:ktfmtCheck` | ✅ PASS | ktfmt-formatted |
| `./gradlew :meshlink-proof:check` | ✅ PASS | Android lint + tests pass |
| `./gradlew :meshlink-reference:check` | ✅ PASS | Compose UI checks pass |
| `./gradlew :meshlink-benchmark:check` | ✅ PASS | JVM benchmark checks pass |
| `./gradlew :meshlink:compileKotlinIosArm64` | ✅ PASS | iOS arm64 compiles |
| `scripts/validate-specs.sh` | ✅ PASS | YAML specs validated at config time |
| `scripts/check-markdown.sh` | ✅ PASS | Markdown lint + link check |
| yamllint | ✅ PASS | YAML lint |
| gitleaks | ⚠️ Not verified in this audit | Secret scanning |

---

## 5.5 Assertion Quality Analysis

Audit of all 41 test files across `meshlink`, `meshlink-proof`, and
`meshlink-benchmark` modules for assertion diversity and depth.

### Summary Metrics

| Metric | Value |
|---|---|
| Total test files | 41 |
| Total assertions | ~930 (+24 new assertions from improvements) |
| Assertion types used | `assertEquals`, `assertNotEquals`, `assertTrue`, `assertFalse`, `assertNull`, `assertNotNull`, `assertFailsWith`, `fail()` |
| Files with high diversity (≥3 types) | ~15 |
| Files using only 1-2 assertion types | ~12 |
| **Strong (A-grade)** — diverse assertions | ~17 files (+2 improved) |
| **Adequate (B-grade)** — moderate, some diversity | ~8 files (-2 improved) |
| **Shallow (C-grade)** — low count or single type | ~16 files (unchanged) |

### Notable Strength: Exception Message Verification

Several tests verify exception **messages**, not just types — a strong
pattern that catches regressions where the error cause changes:

- `IdentityKeyTest`: `assertEquals("IdentityKey must be 32 bytes", e.message)`
- `HandshakeKeyTest`: `assertEquals("HandshakeKey must be exactly 32 bytes", e.message)`
- `MeshLinkSettingsValidationTest` (improved): 3 appId tests now capture the exception and verify `ex.message!!.contains("appId")`; 4 builder-based tests verify `ex.message!!.isNotEmpty()`. The remaining 4 `assertFailsWith` multi-assertion tests (key rotation, transfer, routing, diagnostics) verify exception type but not message content — left unchanged as each test already covers multiple invalid inputs in a single function.

### Notable Strength: Data-Class Equality + HashCode Consistency

`TransferResultTest` and `TransferStatusTest` verify `equals()`/`hashCode()`
contract consistency: `assertEquals(obj, obj)` +
`assertEquals(obj.hashCode(), obj.hashCode())` — proper data-class testing.

### Weaknesses Identified and Resolved

1. **Truthiness patterns instead of specific assertions** — RESOLVED:
   - `MeshHashTest`: `assertTrue(hash1 != hash2)` → `assertNotEquals(hash1, hash2)`; `assertTrue(hash <= 0xFFFFu)` → `assertTrue(hash in 0u..0xFFFFu, ...)` with descriptive message
   - `TransferResultTest`: `assertTrue(stringRepr.contains("error"))` — unchanged (string containment appropriate here)
   - `MeshLinkVersionTest`: `assertTrue(v1 > v2)` — unchanged (comparison operator is the correct assertion here, no dedicated API)

2. **Single-assertion-type files** (low diversity, may be acceptable per domain): UNCHANGED
   - `BigEndianConversionsTest` (33 assertions, ALL `assertEquals`)
   - `TransferSessionTest` (51 `assertEquals` + 5 `assertNotNull`, 0 diversity)
   - `ErrorCodeTest` (29 `assertEquals` + 2 `assertNull`)
   - `PowerModeSettingsTest` (5 `assertEquals` only)
   These remain appropriate for byte-level / enum-value verification where equality is the correct assertion type.

3. **Low assertion count** (< 10 total) — IMPROVED:
   - `TransferTest`: 4 → 7 assertions (added negative test for ID/kind inequality, toString structural check)
   - `MessageHandleTest`: 7 → 12 assertions (replaced tautological `assertNotNull` with structural field checks; added cancellation path test + Cancelled outcome test + UnrecoverableFailure message verification)
   - `TransferHandleTest`: 7 → 12 assertions (same improvements as MessageHandleTest)
   - `EnumCoverageTest`: 5 → 5 fixed assertions (removed tautological `assertNotNull(it)`, added `assertEquals(22, enums.size)` + non-blank checks)
   - `MeshHashTest`: 3 → 5 assertions (replaced truthiness with `assertNotEquals`; added FNV known-value invariant + hash distribution test)
   - `TransferKindTest`, `PowerModeSettingsTest`, `MeshLinkStateTest` — unchanged

4. **`EnumCoverageTest` tautological assertion** — RESOLVED: Replaced `names.forEach { assertNotNull(it) }` (meaningless, enum names always non-null) with `assertEquals(22, enums.size)` (verifies all enums are listed) + `assertTrue(it.isNotBlank(), ...)` (catches empty-name regression).

5. **Exception type only, no message** — RESOLVED: `MeshLinkSettingsValidationTest` appId tests now capture exception and verify `ex.message!!.contains("appId")`; 4 builder-based tests verify `ex.message!!.isNotEmpty()`.

6. **No state-transition verification**: `TransferSessionTest` — UNCHANGED (tests are static state snapshots; real state-transition tests require implemented protocol state machine)

Overall assertion quality is **Adequate to Strong** for the implemented data
model layer. The strongest tests (`ScoreboardTest`, `IncomingTransferTest`,
`ConstantTimeTest`, `MeshLinkVersionTest`) use 3+ assertion types including
equality, boolean, exception, and null checks.

**Improvements applied:** 6 test files enhanced across 5 improvement categories
(tautological assertion removal, truthiness elimination, exception message
verification, negative assertions, structural/state checks, failure-path
coverage). The weakest tests — constructor/property verification stubs —
remain appropriate for value-class scaffolding but would need richer assertions
once real protocol logic is implemented. See [§5.5](#55-assertion-quality-analysis)
for per-test details.

---

## 6. What Another AI Needs to Build the Library

### First Milestones (in dependency order)

1. **Wire Codec** (`commonMain/wire/`) — frame encoder/decoder per
   `specs/codecs/frames.yaml` and `models.yaml`. Start with `SeqNo` wire
   encoding test (already has `SeqNoWireTest.kt`) as a template.
2. **E2E Crypto Layer** (`commonMain/crypto/`) — Noise XX (unpinned) and
   Noise IK (pinned) session state machines. Depends on
   `meshlink-crypto:0.1.1` primitives. See local API reference:
   `docs/reference/meshlink-crypto-api.md`. State machine spec in
   `specs/protocol/state-machines.yaml`.
3. **Transport Layer** (`androidMain/`, `iosMain/`) — BLE GATT service
   discovery, connection management, framed I/O over L2CAP CoC.
4. **Routing Layer** (`commonMain/routing/`) — `RouteCandidate`,
   `RouteExport`/`RouteImport`, `RoutingCoordinator` per
   `state-machines.yaml` RoutingUpdateTrigger.
5. **MeshLink Class** (`MeshLink.kt`) — wire the layers together: start/stop,
   peer discovery, message/payload sending, trust management.
6. **Companion Modules** — flesh out reference app UI, real proof tests,
   and real benchmarks.

### Key Files to Read First

1. `SPEC.md` — entire spec (1,550 lines)
2. `CONSTITUTION.md` — binding engineering/governance rules (5 principles)
3. `AGENTS.md` — operational preferences, quality gates
4. `docs/reference/meshlink-crypto-api.md` — meshlink-crypto v0.1.1 API usage guide
5. `specs/codecs/models.yaml` — data model definitions
6. `specs/codecs/frames.yaml` — wire frame definitions
7. `specs/protocol/state-machines.yaml` — state machines
8. `specs/catalogs/diagnostic-events.yaml` — diagnostic events
9. `docs/decisions/` — ADRs for design rationale
10. `meshlink/api/jvm/meshlink.api` — frozen public API baseline (BCV)
11. Existing test files in `meshlink/src/commonTest/kotlin/ch/trancee/meshlink/model/` —
    TDD pattern templates (AAA, power-assert, backtick test names)

### TDD Conventions

- Tests use `kotlin("test")` with JUnit 5 platform adapter.
- AAA pattern (Arrange/Act/Assert) with blank lines between steps.
- Backtick descriptive test names: `` `fromBytes and toByteArray roundtrip` ``.
- Power-assert for all assertions; `assertEquals` only for structural equality.
- 1:1 source-test mapping: `Foo.kt` → `FooTest.kt` in same package under
  `commonTest/`.
- Test constructor directly: `TransferId(42u)` not `TransferId.fromUInt(42u)`.
- 100% line + branch coverage required for `:meshlink` module.

### Build Commands (for CI parity)

```sh
./gradlew :meshlink:build --rerun-tasks --no-build-cache
./gradlew :meshlink:koverVerify --rerun-tasks --no-build-cache
./gradlew :meshlink:apiCheck --rerun-tasks --no-build-cache
```

---

## 7. Summary

| Category | Status |
|----------|--------|
| Specification suite | ✅ Complete and internally consistent |
| ADRs | ✅ 28 design rationale docs |
| Data model code | ✅ Fully implemented, tested, BCV-frozen |
| Wire codec code | ❌ Not implemented |
| Crypto/E2E layer | ❌ Not implemented (meshlink-crypto API now documented locally) |
| Transport/BLE layer | ❌ Not implemented |
| Routing layer | ❌ Not implemented |
| `MeshLink` class | ❌ TODO stubs only |
| Companion modules | ⚠️ Thin placeholders |
| CI/build quality gates | ✅ Fixed and passing |
| Spec/code/doc consistency | ✅ Resolved all identified discrepancies |
| Test organization | ✅ Fixed package mismatches |
| Assertion quality | ✅ Adequate-to-strong (15 A-grade, 10 B-grade, 16 C-grade) |
| Build & test command parity | ✅ CI aligned with AGENTS.md |
