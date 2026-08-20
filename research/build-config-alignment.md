# Research #22 — Build Configuration & Version Catalog Alignment

**Ticket:** #22 — Verify build configuration and version catalog alignment
**Scope:** Audit `gradle/libs.versions.toml`, `settings.gradle.kts`, `build.gradle.kts`, `meshlink/build.gradle.kts`, `gradle.properties`, the Gradle wrapper, CI (`.github/workflows/ci.yml`), and the documented claims in `AGENTS.md`, `CONSTITUTION.md`, `copilot-instructions.md`, `SPEC.md`, `README.md`, `docs/explanation/module-structure.md`, `docs/how-to/bootstrap-project-tooling.md`, and `docs/decisions/crypto/meshlink-crypto-dependency.md`.

## Method

Static verification only (no Gradle invocation — the host is Linux/arm64 without JDK 21/Android SDK, and `meshlink-proof`/`meshlink-reference` require an Android SDK to configure). Every claim below is grounded in the file contents read at audit time (2026-08-19).

## 1. Version catalog (`gradle/libs.versions.toml`)

**Result: PASS — all versions are pinned to exact releases.**

- `[versions]`: `kotlin = "2.4.10"`, `agp = "9.3.1"`, `detekt = "2.0.0-alpha.6"`, `spotless = "8.9.0"`, `kover = "0.9.9"`, `binary-compatibility-validator = "0.18.1"`, `dokka = "2.2.0"`, `skie = "0.10.14"`, `compose-multiplatform = "1.11.1"`, `compose-material3 = "1.11.0-alpha07"`, `kotlinx-coroutines = "1.10.2"`, `kotlinx-benchmark = "0.4.17"`.
- `[libraries]`: every library uses `version.ref = "<x>"` (catalog-resolved) or an explicit `version = "<x>"`. No `+`, `latest`, `unspecified`, or `camel` markers — confirmed by a regex scan of the whole file.
- `[plugins]`: every plugin aliases `version.ref = "<x>"`.

The catalog is the single source of truth; all module build files reference it via `libs.*` (no hardcoded coordinates), matching CONSTITUTION.md Principle I ("Dependencies MUST pin exact versions").

## 2. Toolchain versions (Kotlin / AGP / JDK)

**Result: PASS — consistent across build config, CI, and docs.**

| Component | Declared in build | CI / docs claim | Match |
|---|---|---|---|
| Kotlin | `libs.versions.toml` `kotlin = "2.4.10"` (plugins use `version.ref = "kotlin"`) | `CONSTITUTION.md` §Tech Constraints ("targets Kotlin 2.4.10"); `copilot-instructions.md` ("Kotlin 2.4.10 per `gradle/libs.versions.toml`"); ADR §4 ("Kotlin 2.4.10"); `SPEC.md` §12.4 | ✅ |
| AGP | `libs.versions.toml` `agp = "9.3.1"` (`android-library` + `android-kotlin-multiplatform-library` use `version.ref = "agp"`) | `copilot-instructions.md` ("AGP 9.3.1") | ✅ |
| JDK | `jvmToolchain(21)` in `meshlink` + every consumer module | CI uses `actions/setup-java@v5`, `distribution: temurin`, `java-version: "21"`; `README.md` ("JDK — Temurin 21 (per CI)"); `bootstrap-project-tooling.md` ("Temurin 21") | ✅ |
| Gradle | Wrapper `gradle/wrapper/gradle-wrapper.properties`: `gradle-9.6.1-bin.zip` | Not pinned elsewhere in docs (no conflicting claim); compatible with KGP for Kotlin 2.4.10 | ✅ |

## 3. `explicitApi()` and quality-tool scoping

**Result: PASS.**

- `meshlink/build.gradle.kts` line 45: `explicitApi()` inside `kotlin { … }` — enabled.
- Quality plugins (Dokka, SKIE, Kover, BCV apiCheck) are declared in `meshlink/build.gradle.kts` only; the root `build.gradle.kts` comment explicitly explains this is by CONSTITUTION.md's Technical Constraints (§273–281). Detekt/Spotless are applied per-module where they make sense (the consumer modules still run them, as `AGENTS.md` Tier table shows).

## 4. KMP target set

**Result: PASS — JVM + Android API 21/37 + iOS arm64 (device) only, no simulator.**

`meshlink/build.gradle.kts`:

- `jvm()` — JVM host tests ✅
- `android { compileSdk = 37; minSdk = 21; withHostTestBuilder {}.configure {} }` ✅
- `listOf(iosArm64())` guarded by `if (HostManager.hostIsMac)` — iOS arm64 **device** only, no `iosSimulatorArm64`, no `iosX64` ✅

The iOS target is macOS-gated (matches the `ios-build` CI job on `macos-latest` compiling `compileKotlinIosArm64`). A grep for `Simulator|simulator|iosSimulator|iosX64` across the repo finds matches **only** in skill reference docs and the `CHANGELOG.md` removal note — never in any `build.gradle.kts`. The `CHANGELOG.md` entry ("Removed `iosSimulatorArm64` KMP target") and CI comment ("The iOS simulator target was removed") confirm the intended state matches the build files.

## 5. Binary Compatibility Validator at root

**Result: PASS.**

`build.gradle.kts` (root):

- line 25: `alias(libs.plugins.binary.compatibility.validator)` — applied at root (no `apply false`), so BCV auto-configures subprojects.
- lines 28–32:

  ```kotlin
  apiValidation {
      ignoredProjects.addAll(listOf("meshlink-reference", "meshlink-proof", "meshlink-benchmark"))
  }
  ```

  Only `:meshlink` is validated — exactly the shipped artifact per CONSTITUTION.md §Technical Constraints. ✅

## 6. Consumer-module build configs align with intent

**Result: PASS.**

| Module | Plugins | Targets | Why |
|---|---|---|---|
| `meshlink-reference` | KMP + AGP + Compose + Detekt + Spotless | `android` (21/37), `iosArm64` (macOS) | Public-API demo only; no Kover/SKIE/BCV/Dokka (correctly excluded) ✅ |
| `meshlink-proof` | AGP library + Detekt + Spotless | Android only (21/37), instrumented tests | Real-device BLE validation; Android-only by design ✅ |
| `meshlink-benchmark` | Kotlin JVM + kotlinx-benchmark + Detekt + Spotless | JVM only | JMH benchmarks; JVM smoke tests ✅ |

All consumer modules use `jvmToolchain(21)` and the kotlinlang/Spotless/ktfmt style, consistent with `:meshlink`.

## 7. Spec / docs vs. build config

**Result: PASS with one finding (see §8).**

- `docs/explanation/module-structure.md` module table ("`meshlink` runs on JVM + Android + iOS arm64 (device)") matches the build config ✅.
- `README.md` project-structure table ("JVM + Android + iOS device targets") matches ✅.
- `SPEC.md` §2.1 module block ("JVM + Android + iOS"), §12.3 minimums (Android API 21, iOS 14), §12.4 runtime deps (`kotlinx-coroutines-core` + `meshlink-crypto` v0.1.1) all match ✅.
- `bootstrap-project-tooling.md` JDK/tooling guidance matches CI ✅.
- `CONSTITUTION.md` Technical Constraints (Android API 21, iOS 14, two runtime deps, `libs.meshlink.crypto`) all match ✅.

## 8. Findings — doc/build inconsistency (RECOMMENDATION)

**Finding 8.1 — minSdk mismatch between ADR and build files.**

`docs/decisions/crypto/meshlink-crypto-dependency.md` §Context (line 41) describes the shared target set as:

> Both repositories share the same organization, toolchain (Kotlin 2.4.10, AGP 9.x), **target set (JVM, Android API 21+, iOS arm64)**, and package namespace (`ch.trancee.meshlink`).

and §Decision point 5 (line 89) states:

> All template modules (`meshlink`, `meshlink-reference`, `meshlink-proof`) are bumped from 36 to 37. `minSdk` is unchanged (21).

**The ADR's "Android API 21+" correctly records `MeshLink-crypto`'s minSdk (21).** The three template modules previously declared `minSdk = 26` in their `build.gradle.kts`, while the ADR distinguished MeshLink (26) from MeshLink-crypto (21). The mismatch was resolved by standardising on minSdk 21 across all MeshLink modules to match the crypto dependency's floor.

**Resolution:** [#34 — Closed] Changed `minSdk = 26` → `minSdk = 21` in all three module `build.gradle.kts` files (`meshlink`, `meshlink-reference`, `meshlink-proof`). Updated doc references across `CONSTITUTION.md`, `SPEC.md`, `README.md`, `.github/copilot-instructions.md`, and all reference/decision docs to reflect Android API 21 as the project minimum. ADR line 89 updated from `(26/21 respectively)` to `(21)`.

## 9. Conclusion

The Gradle build configuration and version catalog are **aligned** with the conventions documented in `AGENTS.md`, `CONSTITUTION.md`, and `copilot-instructions.md`:

- ✅ Version catalog: all versions pinned exactly, no floating markers.
- ✅ Toolchain: Kotlin 2.4.10, AGP 9.3.1, JDK 21 — consistent across build files, CI, and docs.
- ✅ `explicitApi()` enabled in `meshlink/build.gradle.kts`.
- ✅ KMP targets correct: JVM + Android (minSdk 21, compileSdk 37) + iOS arm64 device only.
- ✅ BCV applied at root with `ignoredProjects` excluding the three non-shipped modules.
- ✅ Docs (`module-structure.md`, `SPEC.md`, `README.md`, `bootstrap-project-tooling.md`) match the build config.

**Resolution:** The minSdk mismatch between the ADR (21, from MeshLink-crypto) and the build files (26) has been resolved by standardising on 21 across all template modules — see §8.1 and follow-up ticket [#34](https://github.com/trancee/MeshLink-template/issues/34).
