# Research: Test Infrastructure and Coverage Configuration

**Ticket:** #21 — Verify test infrastructure and coverage configuration  
**Module:** `:meshlink` (the sole shipped artifact per CONSTITUTION.md)  
**Date:** 2026-08-19  
**Investigator:** TestInfra  

## Executive Summary

The test infrastructure for `:meshlink` is **largely compliant** with AGENTS.md and
CONSTITUTION.md. All five quality gates — Spotless, Detekt (zero suppressions),
Kover (100% line + branch), BCV apiCheck, and Wycheproof — are correctly configured.
However, **two gaps** were identified:

1. **Critical:** The Power-assert compiler plugin is **not configured** at all, despite
   AGENTS.md explicitly requiring it. The `kotlin("plugin.power-assert")` plugin is
   absent from `meshlink/build.gradle.kts` and from `gradle/libs.versions.toml`.
2. **Warning:** JUnit 5 is used implicitly (via Kotlin 2.4.10 KMP defaults) but is not
   explicitly configured — no `useJUnitPlatform()` or `kotlin("test.junit5")` dependency.
   This is acceptable for Kotlin 2.4.10 (KMP defaults to JUnit 5 for JVM since 2.1.0)
   but the AGENTS.md statement "JUnit 5 platform adapter" is not backed by explicit
   build configuration.

A third issue is **informational** regarding test file organization: 3 test files are
in the wrong package directory, and 5 source files lack dedicated unit tests.

---

## 1. Build File Configuration

**File:** `meshlink/build.gradle.kts` (146 lines)

### Plugins (lines 34–42)

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
    alias(libs.plugins.kover)
    alias(libs.plugins.dokka)
    alias(libs.plugins.skie)
}
```

All required plugins are present. Binary Compatibility Validator is applied at the
root level (`build.gradle.kts` line 25) and scoped to `:meshlink` via
`ignoredProjects` (line 32 of root build file). ✅

### Kotlin Configuration (lines 44–96)

- `explicitApi()` — enabled ✅ (CONSTITUTION.md requires explicit API)
- `jvmToolchain(21)` — JDK 21 ✅ (copilot-instructions.md requires JDK 21)
- `kotlin.code.style=official` — set in `gradle.properties` ✅
- `commonTest.dependencies { implementation(kotlin("test")) }` (line 94) ✅

### Android Target (lines 54–61)

- `namespace = "ch.trancee.meshlink"` ✅
- `compileSdk = 37`, `minSdk = 26` ✅
- Host test builder enabled ✅

### iOS Target (lines 69–76)

- `iosArm64()` only when `HostManager.hostIsMac` ✅ (matches CI job split)
- Static framework, baseName `"MeshLink"` ✅

---

## 2. Kover Coverage Configuration

**File:** `meshlink/build.gradle.kts` (lines 117–140)

```kotlin
kover {
    reports {
        verify {
            rule("line coverage") {
                bound {
                    aggregationForGroup = AggregationType.COVERED_PERCENTAGE
                    coverageUnits = CoverageUnit.LINE
                    minValue = 100
                }
            }
            rule("branch coverage") {
                bound {
                    aggregationForGroup = AggregationType.COVERED_PERCENTAGE
                    coverageUnits = CoverageUnit.BRANCH
                    minValue = 100
                }
            }
        }
    }
}

// Ensure XML report runs whenever check executes
tasks.check { dependsOn(tasks.koverXmlReport) }
```

**Assessment:** ✅ Fully compliant. Both line coverage and branch coverage have
`minValue = 100` with `AggregationType.COVERED_PERCENTAGE`. The `tasks.check`
dependency on `koverXmlReport` ensures coverage reports run on every `check`
execution, even without `--no-build-cache`.

---

## 3. Test Framework

**File:** `meshlink/build.gradle.kts` (line 94)

```kotlin
commonTest.dependencies { implementation(kotlin("test")) }
```

**Assessment:** ✅ `kotlin("test")` is the test framework. Tests use
`kotlin.test.Test`, `kotlin.test.assertEquals`, `kotlin.test.assertTrue`,
`kotlin.test.assertFailsWith`, etc. — the multiplatform `kotlin.test` API.

### JUnit 5 Platform Adapter

AGENTS.md states: "Test framework: JUnit 5 platform adapter (Kotlin Multiplatform)".

- No `useJUnitPlatform()` configuration exists in any build file.
- No explicit `kotlin("test.junit5")` dependency in `gradle/libs.versions.toml`
  or any `build.gradle.kts`.
- No JUnit 5 dependencies (`junit-jupiter`, `jupiter-api`, etc.) in the version catalog.

With Kotlin 2.4.10, the KMP plugin automatically resolves `kotlin("test")` for JVM
targets to JUnit 5 (Jupiter) — this default changed in Kotlin 2.1.0. The JUnit 5
platform adapter is therefore **used implicitly** without explicit configuration.

**Assessment:** ⚠️ Functionally correct (JUnit 5 is used via KMP defaults since Kotlin 2.1+),
but the configuration is implicit. No explicit `useJUnitPlatform()` or `kotlin("test.junit5")`
dependency is present. This should be documented explicitly or upgraded to explicit
configuration for clarity and auditability.

---

## 4. Power-Assert Compiler Plugin

AGENTS.md requires: "Power-assert: Use power-assert for assertions; assertEquals only
for pure structural comparisons." The `kotlin-power-assert` plugin is the mechanism
for this — it transforms `kotlin.assert()` and other assertion calls to produce
detailed diagnostic output showing intermediate expression values on failure.

**Configuration search results:**

- `meshlink/build.gradle.kts` plugins block (lines 34–42): No `kotlin("plugin.power-assert")` ❌
- `gradle/libs.versions.toml` [plugins] section: No power-assert plugin entry ❌
- `gradle/libs.versions.toml` [libraries] section: No power-assert runtime dependency ❌
- All other build files (`build.gradle.kts`, `meshlink-proof/build.gradle.kts`,
  `meshlink-reference/build.gradle.kts`, `meshlink-benchmark/build.gradle.kts`):
  No power-assert configuration ❌

**Assessment:** ❌ **Critical non-compliance.** The power-assert compiler plugin is not
configured anywhere in the project. The plugin ID should be
`kotlin("plugin.power-assert")` with version matching the Kotlin version (2.4.10).
The version catalog needs a corresponding entry:

```toml
kotlin-power-assert = { id = "org.jetbrains.kotlin.plugin.power-assert", version.ref = "kotlin" }
```

And in the build file:

```kotlin
plugins {
    // ...
    alias(libs.plugins.kotlin.power.assert)  // or kotlin("plugin.power-assert")
}
```

All test files currently use `kotlin.test.assertEquals`, `kotlin.test.assertTrue`, etc.
without power-assert transformation. This means test failure messages lack the
detailed intermediate-value diagnostics that power-assert provides.

---

## 5. Detekt Configuration

**File:** `meshlink/detekt.yml` (27 lines)

```yaml
complexity:
  TooManyFunctions:
    active: true
    allowedFunctionsPerClass: 40
    allowedFunctionsPerFile: 50

style:
  MagicNumber:
    active: true
    ignoreEnums: true
    ignoreNumbers:
      - '-1'
      - '0'
      - '1'
      - '2'
      - '255'
      - '256'
      - '1024'
      - '4096'
  ThrowsCount:
    active: true
    max: 15
  MaxLineLength:
    active: true
    maxLineLength: 140
  UnusedParameter:
    active: false
```

**Build configuration** (`meshlink/build.gradle.kts` lines 98–106):

```kotlin
detekt {
    buildUponDefaultConfig = true
    config = files("detekt.yml")
}

tasks.withType<Detekt>().configureEach {
    setSource(files("src/commonMain/kotlin", "src/commonTest/kotlin"))
    include("**/*.kt")
}
```

### Suppression Audit

**Detekt YAML config:** No `exclusions`, `suppressions`, or `build` sections.
No rules are explicitly suppressed via configuration. ✅

**`@Suppress` annotation search** (in `meshlink/src/commonMain` and
`meshlink/src/commonTest`):

- Only `@SuppressSkieWarning.NameCollision(suppress = true)` found (line 48 of
  `MeshLink.kt`) — this is a **SKIE-specific** annotation for suppressing SKIE
  framework generation warnings, not a Detekt suppression.
- No `@Suppress("Detekt...")` or `@Suppress("...")` annotations anywhere in
  source or test code. ✅

**Assessment:** ✅ Zero suppressions configured. Detekt uses `buildUponDefaultConfig = true`
which applies all default Detekt rules on top of the custom config. No rules are
suppressed via YAML or code annotations.

> **Note:** Detekt 2.0.0-alpha.6 is used (from version catalog). The `detekt.yml`
> only customizes rules under `complexity` and `style` sections. With
> `buildUponDefaultConfig = true`, all default Detekt rules remain active.

---

## 6. Spotless Configuration

**File:** `meshlink/build.gradle.kts` (lines 108–115)

```kotlin
spotless {
    kotlin {
        ktfmt().kotlinlangStyle()
    }
    kotlinGradle {
        ktfmt().kotlinlangStyle()
    }
}
```

**Assessment:** ✅ Fully compliant. Spotless uses `ktfmt().kotlinlangStyle()` for both
`kotlin` and `kotlinGradle` source sets, matching the AGENTS.md requirement.
Pre-commit hook (`.githooks/pre-commit`) runs `spotlessApply` before commits.
All other modules (`meshlink-proof`, `meshlink-reference`, `meshlink-benchmark`)
use the same configuration.

---

## 7. Test File Organization (1:1 Mapping)

AGENTS.md requires: "1:1 mapping: `Foo.kt` → `FooTest.kt` in the same package under
`commonTest/`."

### Overall Coverage

- **38 of 43** source files have a corresponding `*Test.kt` file (88%).
- **5 source files lack dedicated tests:**

| Source File | Package | Coverage Status |
|---|---|---|
| `CryptoKeyConstants.kt` | `model` | No test at all |
| `MeshLink.kt` | root | No dedicated test (high-level orchestrator; `MeshLinkEnvironment` is injected) |
| `MeshLinkEnvironment.kt` | root | No test at all |
| `RequireSetting.kt` | `util` | No test at all |
| `SecureRandom.kt` | `util` | No test at all |

Two of the 5 (L2capState, PayloadDecision) are not in this list — they ARE covered:

- `L2capState.kt` is exercised by `EnumCoverageTest.kt` which imports
  `ch.trancee.meshlink.transport.L2capState` and asserts its `.entries`.
- `PayloadDecision.kt` is similarly covered by `EnumCoverageTest.kt`
  (`ch.trancee.meshlink.transfer.PayloadDecision`).

### Package Location Deviations

**3 test files are declared in the wrong package** — they live in the root
`ch.trancee.meshlink` package but their source counterparts are in subpackages:

| Test File | Declared Package | Source File | Source Package | Correct Test Location |
|---|---|---|---|---|
| `ConstantTimeTest.kt` | `ch.trancee.meshlink` | `ConstantTime.kt` | `ch.trancee.meshlink.util` | `ch/trancee.meshlink/util/ConstantTimeTest.kt` |
| `MeshHashTest.kt` | `ch.trancee.meshlink` | `MeshHash.kt` | `ch.trancee.meshlink.util` | `ch/trancee.meshlink/util/MeshHashTest.kt` |
| `DiagnosticEventTest.kt` | `ch.trancee.meshlink` | `DiagnosticEvent.kt` | `ch.trancee.meshlink.diagnostics` | `ch/trancee.meshlink/diagnostics/DiagnosticEventTest.kt` |

These tests work correctly because Kotlin allows importing classes from any package
— the test compiles and runs. However, this violates the 1:1 package mapping
requirement in AGENTS.md.

**Assessment:** ⚠️ The 1:1 mapping is followed for the majority of files, but:

- Package location is wrong for 3 test files (ConstantTimeTest, MeshHashTest,
  DiagnosticEventTest).
- 5 source files lack dedicated unit tests. Three of these (MeshLink.kt,
  MeshLinkEnvironment.kt, CryptoKeyConstants.kt) may be acceptable as they are
  high-level orchestration/constants. RequireSetting.kt and SecureRandom.kt should
  have tests for completeness.

---

## 8. Wycheproof Test Vectors

**File:** `meshlink/src/commonTest/resources/wycheproof/`

```text
chacha20_poly1305_test.json   ✅
ed25519_test.json             ✅
hkdf_sha256_test.json         ✅
hmac_sha256_test.json         ✅
x25519_test.json              ✅
```

All 5 required Wycheproof vector sets are present, matching the AGENTS.md
requirement: "Crypto tested against ch.trancee.meshlink:wycheproof test vectors
in meshlink/src/commonTest/resources/wycheproof/".

**Assessment:** ✅ All required Wycheproof test vector files are present.

---

## 9. Summary of Findings

| # | Check | Status | Details |
|---|---|---|---|
| 1 | Build plugins | ✅ Pass | All 7 required plugins + BCV at root |
| 2 | Kover 100% line+branch | ✅ Pass | minValue=100 for both, COVERED_PERCENTAGE |
| 3 | kotlin("test") framework | ✅ Pass | `commonTest.dependencies { implementation(kotlin("test")) }` |
| 4 | JUnit 5 platform adapter | ⚠️ Warning | Used implicitly via Kotlin 2.4.10 KMP defaults; no explicit config |
| 5 | Detekt zero suppressions | ✅ Pass | No YAML exclusions; no @Suppress annotations (only @SuppressSkieWarning) |
| 6 | Power-assert plugin | ❌ **FAIL** | Not configured anywhere; `kotlin("plugin.power-assert")` absent from build + version catalog |
| 7 | Spotless ktfmt kotlinlangStyle | ✅ Pass | Both kotlin and kotlinGradle source sets |
| 8 | Test file 1:1 mapping | ⚠️ Warning | 3 test files in wrong package; 5 source files lack dedicated tests |
| 9 | Wycheproof vectors | ✅ Pass | All 5 present (chacha20_poly1305, ed25519, hkdf_sha256, hmac_sha256, x25519) |
| 10 | Explicit API | ✅ Pass | `explicitApi()` enabled |
| 11 | JVM toolchain | ✅ Pass | `jvmToolchain(21)` |
| 12 | BCV scoping | ✅ Pass | Applied at root, scoped to :meshlink via ignoredProjects |

### Recommended Actions

1. **Add power-assert plugin** (Critical): Add `kotlin-power-assert` plugin entry to
   `gradle/libs.versions.toml` and apply it in `meshlink/build.gradle.kts`.
   Configure `powerAssert { functions = listOf("kotlin.assert", "kotlin.test.assertTrue",
   "kotlin.test.assertEquals", "kotlin.test.assertFalse", "kotlin.test.assertNull",
   "kotlin.test.assertNotNull", "kotlin.test.assertFailsWith", "kotlin.test.assertNotEquals") }`.

2. **Explicitly configure JUnit 5** (Warning): Add `kotlin("test.junit5")` or explicit
   `useJUnitPlatform()` to make the JUnit 5 platform adapter explicit and auditable.

3. **Fix test file package locations** (Warning): Move `ConstantTimeTest.kt` and
   `MeshHashTest.kt` to `ch/trancee/meshlink/util/`, and `DiagnosticEventTest.kt` to
   `ch/trancee/meshlink/diagnostics/`, updating their package declarations accordingly.

4. **Add missing unit tests** (Informational): Add dedicated tests for
   `RequireSetting.kt` and `SecureRandom.kt` to complete the 1:1 mapping.
