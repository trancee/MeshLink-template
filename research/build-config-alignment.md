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

**Result: PASS — JVM + Android API 26/37 + iOS arm64 (device) only, no simulator.**

`meshlink/build.gradle.kts`:

- `jvm()` — JVM host tests ✅
- `android { compileSdk = 37; minSdk = 26; withHostTestBuilder {}.configure {} }` ✅
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
| `meshlink-reference` | KMP + AGP + Compose + Detekt + Spotless | `android` (26/37), `iosArm64` (macOS) | Public-API demo only; no Kover/SKIE/BCV/Dokka (correctly excluded) ✅ |
| `meshlink-proof` | AGP library + Detekt + Spotless | Android only (26/37), instrumented tests | Real-device BLE validation; Android-only by design ✅ |
| `meshlink-benchmark` | Kotlin JVM + kotlinx-benchmark + Detekt + Spotless | JVM only | JMH benchmarks; JVM smoke tests ✅ |

All consumer modules use `jvmToolchain(21)` and the kotlinlang/Spotless/ktfmt style, consistent with `:meshlink`.

## 7. Spec / docs vs. build config

**Result: PASS with one finding (see §8).**

- `docs/explanation/module-structure.md` module table ("`meshlink` runs on JVM + Android + iOS arm64 (device)") matches the build config ✅.
- `README.md` project-structure table ("JVM + Android + iOS device targets") matches ✅.
- `SPEC.md` §2.1 module block ("JVM + Android + iOS"), §12.3 minimums (Android API 26, iOS 14), §12.4 runtime deps (`kotlinx-coroutines-core` + `meshlink-crypto` v0.1.1) all match ✅.
- `bootstrap-project-tooling.md` JDK/tooling guidance matches CI ✅.
- `CONSTITUTION.md` Technical Constraints (Android API 26, iOS 14, two runtime deps, `libs.meshlink.crypto`) all match ✅.

## 8. Findings — doc/build inconsistency (RECOMMENDATION)

**Finding 8.1 — ADR states an incorrect Android `minSdk`.**

`docs/decisions/crypto/meshlink-crypto-dependency.md` §Context (line 41) describes the shared target set as:

> Both repositories share the same organization, toolchain (Kotlin 2.4.10, AGP 9.x), **target set (JVM, Android API 21+, iOS arm64)**, and package namespace (`ch.trancee.meshlink`).

and §Decision point 5 (line 89) states:

> All template modules (`meshlink`, `meshlink-reference`, `meshlink-proof`) are bumped from 36 to 37. `minSdk` is unchanged (26/21 respectively).

**This is inaccurate for the MeshLink template.** All three template modules declare `minSdk = 26` (`meshlink`, `meshlink-reference`, and `meshlink-proof` — confirmed by grep). The "API 21+" / "26/21 respectively" figure is a copy of `MeshLink-crypto`'s own minimum (`meshlink-crypto` targets API 21), not MeshLink's. MeshLink's binding minimum is **Android API 26**, as stated in `CONSTITUTION.md` (§Technical Constraints), `SPEC.md` §12.3, and the actual `build.gradle.kts` files.

This is a stale/leftover fact that was generalized from the sibling `meshlink-crypto` project to MeshLink itself. The ADR is historical record and is *amended* (not rolled back), so the recommendation is a small clarifying edit rather than a rewrite — the "Android API 21+" phrasing was MeshLink-crypto's target, not MeshLink's, and should be corrected to avoid future contributors bumping `meshlink-proof` to API 21 on the strength of this document.

> Note: this audit is a *verification* ticket (AFK research). It does **not** author the doc fix — that is a separate edit to a decision record and should be done explicitly (ADR lives under the protected `docs/decisions/crypto/` path per `.github/CODEOWNERS`, so it needs `@trancee` review).

## 9. Conclusion

The Gradle build configuration and version catalog are **aligned** with the conventions documented in `AGENTS.md`, `CONSTITUTION.md`, and `copilot-instructions.md`:

- ✅ Version catalog: all versions pinned exactly, no floating markers.
- ✅ Toolchain: Kotlin 2.4.10, AGP 9.3.1, JDK 21 — consistent across build files, CI, and docs.
- ✅ `explicitApi()` enabled in `meshlink/build.gradle.kts`.
- ✅ KMP targets correct: JVM + Android (minSdk 26, compileSdk 37) + iOS arm64 device only.
- ✅ BCV applied at root with `ignoredProjects` excluding the three non-shipped modules.
- ✅ Docs (`module-structure.md`, `SPEC.md`, `README.md`, `bootstrap-project-tooling.md`) match the build config.

The single divergence is the historical ADR in §8.1, which records "Android API 21+"/`minSdk 26/21` — a leftover from `meshlink-crypto`'s minimum SDK, not MeshLink's (which is API 26 everywhere it is enforced). Recommended fix: correct that sentence in the ADR; otherwise no build-config drift exists.
