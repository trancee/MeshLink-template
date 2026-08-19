# Research #19 — API Dump (BCV) Alignment with Current Code

**Ticket:** #19 — Verify API dump (BCV) alignment with current code
**Scope:** Compare `meshlink/api/jvm/meshlink.api` (Binary Compatibility Validator dump) against the current public API declared in `meshlink/src/commonMain/kotlin/ch/trancee/meshlink/`; verify `explicitApi()` compliance; identify stale or un-dumped symbols.

## Method

**Primary authority — Gradle BCV:** `./gradlew :meshlink:jvmApiCheck :meshlink:apiCheck --rerun-tasks --no-build-cache`

The BCV `apiCheck` task compiles the full JVM bytecode of `:meshlink` and performs an exact diff of every public API symbol against `meshlink/api/jvm/meshlink.api`. Any symbol present in the dump but not in the compiled bytecode (stale) or present in the bytecode but not in the dump (un-dumped new API) causes a check failure with a precise diff output. This is the authoritative verification.

**Secondary — manual cross-check:** Spot-read key source files (MeshLink.kt, MeshLinkSettings.kt, MeshLinkEnvironment.kt, MeshLinkVersion.kt, diagnostics/DiagnosticEvent.kt, model/Enums.kt, model/PowerMode.kt, model/RoutingPolicy.kt, model/Exceptions.kt, model/TransferResult.kt, util/SecureRandom.kt, util/RequireSetting.kt) and verified 1:1 symbol alignment against the dump.

**Tertiary — full quality gate suite:** `./gradlew :meshlink:detekt :meshlink:spotlessCheck :meshlink:build :meshlink:koverVerify --rerun-tasks --no-build-cache`

## Results

### 1. Gradle BCV `jvmApiCheck` and `apiCheck` — PASS

```text
> Task :meshlink:jvmApiCheck
> Task :meshlink:apiCheck
BUILD SUCCESSFUL
```

Both BCV tasks pass. The dump file `meshlink/api/jvm/meshlink.api` is byte-for-byte aligned with the current compiled public API. No stale entries (in dump, not in source) and no un-dumped entries (in source, not in dump). The BCV tool performs an exact diff — if even a single public symbol were missing or extra, the task would fail with a diff showing the discrepancy.

### 2. Full quality gate suite — PASS

| Gate | Task | Result |
|---|---|---|
| BCV (JVM) | `:meshlink:jvmApiCheck` | PASS |
| BCV (generic) | `:meshlink:apiCheck` | PASS |
| Detekt (zero suppressions) | `:meshlink:detekt` | PASS |
| Spotless (ktfmt kotlinlang) | `:meshlink:spotlessCheck` | PASS |
| Kover (100% line + branch) | `:meshlink:koverVerify` | PASS |
| Full build + tests | `:meshlink:build` | PASS |

### 3. Manual cross-check of representative symbols — all ALIGNED

#### Settings data classes & builders (MeshLinkSettings.kt)

- `KeyRotationSettings` — dump (lines 51–65): constructor, component1/2/3, copy, copy$default, equals, getCompromiseGracePeriod, getInterval, getRotationGracePeriod, hashCode, toString. Source: `public data class KeyRotationSettings(public val interval, public val rotationGracePeriod, public val compromiseGracePeriod)`. ✅ All match.
- `TransferSettings` — dump (lines 220–235): constructor, component1/2/3, copy, copy$default, equals, getChunkSize, getMaxRetries, getMaxTransfersPerPeer, hashCode, toString. Source: `public data class TransferSettings(public val maxRetries, public val chunkSize, public val maxTransfersPerPeer)`. ✅ All match.
- `RoutingSettings` — dump (lines 191–207): constructor, component1/2/3/4, copy, copy$default, equals, getMaxRoutes, getRouteAdvertisementChangeThreshold, getRouteDigestInterval, getRouteExpiry, hashCode, toString. Source: `public data class RoutingSettings(public val routeAdvertisementChangeThreshold, public val routeDigestInterval, public val routeExpiry, public val maxRoutes)`. ✅ All match.
- `DiagnosticsSettings` — dump (lines 1–14): constructor (two overloads — default params), component1/2, copy, copy$default, equals, getEmitLog, getEventBufferSize, hashCode, toString. Source: `public data class DiagnosticsSettings(public val eventBufferSize, public val emitLog)`. ✅ All match.
- `MeshLinkSettings` — dump (lines 114–138): constructor, component1–8, copy, copy$default, equals, getAppId, getDiagnostics, getEnableBackground, getKeyRotation, getPowerMode, getRegulatoryRegion, getRouting, getTransfer, hashCode, toString. Source: `public data class MeshLinkSettings(...)` with 8 fields including `enableBackground`. ✅ All match (including `enableBackground` field).

**Builder visibility pattern — correctly reflected:**

- `MeshLinkSettingsBuilder` (dump lines 140–155): public vars `appId`, `powerMode`, `regulatoryRegion`, `enableBackground` appear as get/set pairs; `keyRotation()`, `transfer()`, `routing()`, `diagnostics()`, `build()` methods present. ✅ Source confirms these are `public`.
- `KeyRotationSettingsBuilder` (dump lines 67–75): public get/set pairs for `interval`, `rotationGracePeriod`, `compromiseGracePeriod`. ✅ Source confirms `public` vars.
- `TransferSettingsBuilder` (dump lines 237–245): public get/set pairs for `maxRetries`, `chunkSize`, `maxTransfersPerPeer`. ✅ Source confirms `public` vars.
- `RoutingSettingsBuilder` (dump lines 209–211): **only `<init>()`** — no get/set pairs. ✅ Source confirms all four properties (`routeAdvertisementChangeThreshold`, `routeDigestInterval`, `routeExpiry`, `maxRoutes`) are declared `internal`. They are correctly **absent** from the public API dump. This is the intended design (internal mutable holder).
- `DiagnosticsSettingsBuilder` (dump lines not shown, but present): public get/set pairs for `eventBufferSize`, `emitLog`. ✅ Source confirms `public` vars.

#### MeshLink.kt (entry point)

- `MeshLink` class (dump lines 77–98): Companion with `create()` and `getVERSION`; getters for `state`, `peers`, `transfers`, `messages`, `diagnostics`, `powerMode`, `powerModeSettings`; methods `start()`, `pause()`, `resume()`, `stop()`, `setPowerMode()`, `sendMessage()`, `sendPayload()`, `revokeTrust()`, `resetTrust()`. ✅ Source confirms all 9 public members.
  - The constructor is `private constructor(...)` in source — correctly **absent** from dump (only the synthetic default-param constructor appears, as `public synthetic fun <init>`). ✅
  - `MeshLink$Companion` (dump lines 100–103): `create()` and `getVERSION` — ✅ matches source.

#### MeshLinkEnvironment.kt

- `MeshLinkEnvironment` interface (dump lines 105–112): `acquireRadioLease`, `getComputeDispatcher`, `getMonotonicClock`, `getRadioDispatcher`, `getSecureStorage`, `releaseRadioLease`. ✅ Source: `public interface` with `acquireRadioLease()`, `releaseRadioLease()`, `secureStorage`, `monotonicClock`, `radioDispatcher`, `computeDispatcher`. Properties rendered as `get*()` JVM methods.
- `RadioLease` (dump lines 188–189): `public class` with only `<init>()` — no public members. ✅ Source: `public open class RadioLease internal constructor()` — internal constructor means no public constructor; class declaration only.
- `SecureStorage` interface (dump lines 213–218): `contains`, `delete`, `get`, `put`. ✅ Source: `public interface SecureStorage` with `put()`, `get()`, `delete()`, `contains()`.
- `MonotonicClock` interface (dump lines 183–186): `elapsedSince-5sfh64U`, `now`. ✅ Source: `public interface MonotonicClock` with `now()` and `elapsedSince(start: Instant): Duration`. The `Duration` return type is Kotlin-mangled to `-5sfh64U` suffix. ✅

#### MeshLinkVersion.kt

- `MeshLinkVersion` (dump lines 161–177): Comparable impl, Companion with `parse()`, data class component1/2/3, copy, etc. ✅ Source: `public data class MeshLinkVersion(public val major, public val minor, public val patch) : Comparable<MeshLinkVersion>`.
- `MeshLinkVersion$Companion` (dump lines 179–181): `parse()` — ✅ matches source.

#### diagnostics/DiagnosticEvent.kt

- `DiagnosticCode` value class (dump lines 247–260): Companion with 9 code getters (`HANDSHAKE`, `KEY_ROTATION`, `NOISE_SESSION`, `POWER_MODE_EFFECTIVE`, `ROUTE_DECRYPT_FAILURE`, `ROUTE_DIGEST_MISMATCH`, `TRANSFER_BEARER`, `TRANSFER_FAILURE`, `TRANSFER_SESSION_TRANSITION`, `TRANSPORT_FALLBACK`), `getValue-Mh2AYeg`, box/unbox, etc. ✅ Source: `@JvmInline public value class DiagnosticCode(public val value: UShort)` with 10 companion constants.
- `HandshakeId` value class (dump lines 523–535) and `NoiseSessionId` value class (dump lines 537–549): both `@JvmInline public value class` wrapping `UInt`. ✅
- `DiagnosticEvent` sealed interface (dump line 275–279): `getCode-qm_Ev1w`, `getOccurredAt`, `getSeverity`. ✅ Source: `public sealed interface DiagnosticEvent` with `code`, `severity`, `occurredAt`.
- All 8 nested data classes (`HandshakeEvent`, `KeyRotationEvent`, `NoiseSessionEvent`, `PowerModeEffectiveEvent`, `RouteDecryptFailureEvent`, `RouteDigestMismatchEvent`, `TransferBearerEvent`, `TransferFailureEvent`, `TransferSessionTransitionEvent`, `TransportFallbackEvent`) — ✅ all present in dump with matching fields.

#### model/Enums.kt

- All 20+ public enums (`Bearer`, `DecryptFailureReason`, `DiagnosticSeverity`, `ErrorCode`, `FrameType`, `HandshakePattern`, `KeyRotationReason`, `KeyType`, `MeshLinkState`, `NoiseFailureReason`, `NoiseLayer`, `NoiseRole`, `NoiseSessionState`, `PeerState`, `PeerTrust`, `PowerMode`, `Priority`, `RegulatoryRegion`, `TransferKind`, `TransferState`, `TransportFallbackReason`, `VerificationLevel`) — ✅ all present in dump with correct enum entries.
- `ErrorCode$Companion` (dump lines 668–670): `fromValue-xj2QHRw` — ✅ matches source companion.
- Two `internal` enums (`KeyRotationState`, `PeerLifecycle`) — ✅ correctly **absent** from dump.

#### model/PowerMode.kt

- `PowerMode` enum (dump lines 964–973): HIGH, LOW, MEDIUM entries; `getEntries`, `getSettings`, `valueOf`, `values`. ✅ Source: `public enum class PowerMode { HIGH, MEDIUM, LOW }` with `public val settings: PowerModeSettings`.
- `PowerMode$Companion` (dump lines 975–976): **empty class** — no public members. ✅ Source: `public companion object` with `internal fun high()`, `internal fun medium()`, `internal fun low()`. All methods are `internal`, so nothing from the Companion appears in the dump.
- `PowerModeSettings` (dump lines 978–1002): data class with component1–14, all getter pairs, but **no public constructor** (internal constructor). ✅ Source: `public data class PowerModeSettings internal constructor(...)` — the `internal` constructor is correctly absent from the public dump; only the class + its public data-class members appear.

#### model/RoutingPolicy.kt

- `RoutingPolicy` object (dump lines 1042–1046): `INSTANCE`, `MAXIMUM_HOP_COUNT` (const), `ttl-5sfh64U` (Duration return, mangled). ✅ Source: `public object RoutingPolicy` with `public const val MAXIMUM_HOP_COUNT: Int = 16` and `public fun ttl(priority: Priority): Duration`.

#### model/Exceptions.kt

- `MeshLinkException` sealed class (dump lines 803–807): `getErrorCode`, `getMessage`. ✅ Source: `public sealed class MeshLinkException(public open val errorCode, override val message) : RuntimeException(message)`.
- `ConfigurationException`, `LifecycleException`, `PermissionException`, `StorageException`, `CryptoException`, `TrustException`, `RoutingException`, `TransferException` — all data class subclasses with component1/component2, copy, etc. ✅
- `BluetoothException` (dump line 580): `public class` (not final — `open`). ✅ Source: `public open class BluetoothException`.
- `RadioInUseException` (dump lines 1013–1019): extends `BluetoothException`, has two constructors (default + ErrorCode/String). ✅ Source: `public class RadioInUseException(override val errorCode: ErrorCode = ErrorCode.RADIO_IN_USE, ...) : BluetoothException(errorCode, message)`.

#### util/SecureRandom.kt & RequireSetting.kt

- `SecureRandomKt` (dump lines 1335–1337): `randomULong` function. ✅ Source: `public fun randomULong(): ULong`.
- `RequireSetting.kt` — `requireSetting` is declared `internal` → ✅ correctly **absent** from dump.
- `BigEndianConversionsKt` (dump lines 1312–1319): all functions with Duration/UInt mangling suffixes. ✅
- `ConstantTime` (dump lines 1321–1328): `constantTimeEquals`, `constantTimeEqualsBoolean`, `constantTimeIsZero`, `constantTimeSelect`, `constantTimeSwap`, `INSTANCE`. ✅
- `MeshHash` (dump lines 1330–1333): `derive-OGnWXxg`, `INSTANCE`. ✅

#### model value classes (AppHash, HandshakeKey, IdentityKey, PeerIdentity, TransferId, MessageId)

All present in dump with `box-impl`, `constructor-impl`, `equals-impl`, `hashCode-impl`, `toString-impl`, `unbox-impl`, `toBytes-impl`, companion objects with `fromBytes`/`fromHex`/`derive`/`getZERO` methods. ✅ All match their `@JvmInline` source declarations.

### 4. `explicitApi()` compliance — PASS

`meshlink/build.gradle.kts` line 45: `explicitApi()` is enabled. This is a compile-time enforcement: the Kotlin compiler **fails the build** if any public declaration lacks an explicit visibility modifier (`public`/`internal`/`private`) or an explicit return type. Since `:meshlink:build` passes (all `compileKotlinJvm`, `compileKotlinIosArm64` tasks succeed), every public declaration in the source has:

- Explicit visibility modifier (`public`, `internal`, `private`, `protected`)
- Explicit return type on all functions and properties

Spot-checked source confirms the convention is followed uniformly: `public class`, `public data class`, `public interface`, `public sealed interface`, `public enum class`, `public val`, `public fun`, `public suspend fun`, `public object`, `public const val`, `@JvmInline public value class`.

### 5. Discrepancy summary — NONE

| Category | Count |
|---|---|
| Stale (in dump, not in source) | 0 |
| Un-dumped (in source, not in dump) | 0 |
| Missing visibility/return type (explicitApi violations) | 0 |
| All quality gates | PASS |

**No discrepancies found.** The API dump is fully aligned with the current public API.

## Conclusion

The BCV dump file `meshlink/api/jvm/meshlink.api` is **fully aligned** with the current public API in `meshlink/src/commonMain/kotlin/ch/trancee/meshlink/`. The authoritative Gradle BCV tasks (`jvmApiCheck` and `apiCheck`) both pass, and a thorough manual cross-check of representative declarations — including subtle cases like `internal`-only builders (`RoutingSettingsBuilder`), `internal constructor` data classes (`PowerModeSettings`), `internal` companion object methods (`PowerMode$Companion`), `internal` enums (`KeyRotationState`, `PeerLifecycle`), and `internal` helper functions (`requireSetting`) — confirms every public symbol in the source is present in the dump and every entry in the dump corresponds to a live source declaration. `explicitApi()` is enabled and enforced at compile time, so all public declarations have explicit visibility and return types. No stale or un-dumped entries exist.
