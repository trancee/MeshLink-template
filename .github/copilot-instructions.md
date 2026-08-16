# Copilot Instructions — MeshLink Template

This file guides Copilot (and other AI agents) to follow MeshLink's conventions.
The authoritative rulebook is [`AGENTS.md`](../AGENTS.md) and
[`CONSTITUTION.md`](../CONSTITUTION.md). This file adds AI-specific context
without duplicating constitutional content.

## Language & Framework

- **Language**: Kotlin (multiplatform) — Kotlin 2.4.10 per `gradle/libs.versions.toml`
- **Build system**: Gradle 8.5+ (wrapper at `./gradlew`), Kotlin DSL (`*.gradle.kts`)
- **Multiplatform targets**: JVM (host tests), Android (library, minSdk 26, compileSdk 37),
  iOS arm64 (device, macOS-only)
- **Version catalog**: All dependency versions pinned in `gradle/libs.versions.toml` — always
  use version catalog references (`libs.xxx`) rather than hardcoded coordinates
- **Java toolchain**: JDK 21 (Temurin) — set via `jvmToolchain(21)` in `meshlink/build.gradle.kts`
  and `actions/setup-java@v4` in CI
- **Kotlin coding style**: `kotlin.code.style=official` in `gradle.properties`;
  `ktfmt` (kotlinlang style) formats all code — no manual style deviations

## Module Structure

| Module | Purpose | Key Gradle command |
|---|---|---|
| `meshlink` | Shipped library — public API + implementation. JVM + Android + iOS arm64 (device). | `./gradlew :meshlink:build` |
| `meshlink-reference` | Reference/compatibility app — public API only, Compose Multiplatform UI. | `./gradlew :meshlink-reference:check` |
| `meshlink-proof` | Real-device validation — Android/iOS BLE behavior. | `./gradlew :meshlink-proof:check` |
| `meshlink-benchmark` | Performance benchmarks — JVM + real-device fleet. | `./gradlew :meshlink-benchmark:check` |

See [`docs/explanation/module-structure.md`](../docs/explanation/module-structure.md).

## Code Style & Linting

- **ktfmt** — `./gradlew :meshlink:ktfmtFormat` (before commit) / `ktfmtCheck` (CI gate)
  — kotlinlang style, auto-applied by `.githooks/pre-commit`
- **Detekt** — `./gradlew :meshlink:detekt` — zero suppressions allowed (CONSTITUTION Principle I)
  Test-code suppressions require inline justification
- **API validation** — `./gradlew :meshlink:jvmApiDump` regenerates
  `meshlink/api/jvm/meshlink.api`; `apiCheck` runs in CI
- **Explicit API** — `explicitApi()` is enabled in `meshlink/build.gradle.kts`;
  all public declarations need explicit visibility and return types

## Testing

- **Test runner**: Kotlin test (`kotlin("test")`) in `commonTest`
- **Test framework**: JUnit 5 platform adapter (Kotlin Multiplatform)
- **AAA pattern**: Arrange/Act/Assert with blank lines between steps — one Act per test
- **Test naming**: Backtick descriptive sentences — `` `zero id has fixed representation` ``
- **Coverage gate**: 100% line + branch coverage for `:meshlink` only —
  `./gradlew :meshlink:koverVerify`
- **Wycheproof vectors**: Crypto tested against `ch.trancee.meshlink:wycheproof` test vectors
  in `meshlink/src/commonTest/resources/wycheproof/`
- **No emulator BLE**: Android emulators / iOS simulators don't have real BLE radios —
  `meshlink-proof` validates real-device BLE behavior; host tests cover non-radio logic only
- **Power-assert**: Use power-assert for assertions; `assertEquals` only for pure structural comparisons

### Test File Organization

- 1:1 mapping: `Foo.kt` → `FooTest.kt` in the same package under `commonTest/`
- Use constructor directly: `TransferId(42u)` not `TransferId.fromUInt(42u)`
- Always import packages — never use fully qualified class names in the code body

## Build & Test Commands

```sh
# Format + lint (fast, run locally before every commit)
./gradlew :meshlink:ktfmtFormat :meshlink:detekt

# Build + test (JVM host tests)
./gradlew :meshlink:build --rerun --no-build-cache

# Full verification
./gradlew :meshlink:koverVerify :meshlink:apiCheck

```

> **Gradle invocations must always pass `--rerun` and `--no-build-cache`**
> (AGENTS.md Operational Preferences).

## Conventions Mined from PR Reviews

The repo is young (PRs #1–#4 so far); conventions are stable from
`CONSTITUTION.md` and `AGENTS.md`:

- Convention Commits required on every commit (`feat:`, `fix:`, `test:`, `docs:`, etc.)
- Constitution Check required in every PR description (principle-by-principle I–V)
- Any `.api` diff requires a version-bump rationale in the PR
- Matching iOS docs required for any Android doc change to a public API
- Design memo in `docs/decisions/<area>/` required for crypto/routing/wire changes

## Maintenance Matrix

This matrix traces the dependency graph and change cascades across the repo.
When modifying a source area, update ALL downstream consumers listed below.

### Wire Codec (`meshlink/src/*/kotlin/ch/trancee/meshlink/wire/`)

- **Specs**: `specs/codecs/models.yaml`, `specs/codecs/frames.yaml`, `specs/codecs/enums.yaml`
- **Traceability**: `specs/traceability/specification-map.yaml`
- **Impact cascade**: Wire format code → wire codec tests in
  `meshlink/src/commonTest/kotlin/.../wire/` → API dump in `meshlink/api/jvm/meshlink.api`
  if public types change → Wycheproof test vectors if codec affects crypto framing
- **Golden rule**: Wire field/enum codes are never reinterpreted or reused (CONSTITUTION §Technical Constraints)

### Routing (`meshlink/src/*/kotlin/ch/trancee/meshlink/routing/`)

- **Specs**: `specs/protocol/state-machines.yaml`
- **Docs**: `docs/reference/routing.md`, `docs/decisions/routing/`
- **Impact cascade**: Routing state machine changes → routing tests →
  `docs/reference/routing.md` (behavior docs) → ADR in `docs/decisions/routing/`

### Trust & Identity (`meshlink/src/*/kotlin/ch/trancee/meshlink/model/`)

- **Specs**: `specs/catalogs/settings.yaml` (trust settings)
- **Docs**: `docs/reference/trust.md`, `docs/decisions/crypto/identity-binding-and-fail-closed.md`
- **Impact cascade**: Identity model changes → Trust model tests → API dump →
  diagnostics catalog if new error codes (`specs/catalogs/diagnostic-events.yaml`)

### Settings / Config DSL (`meshlink/src/*/kotlin/ch/trancee/meshlink/settings/`)

- **Specs**: `specs/catalogs/settings.yaml`
- **Impact cascade**: Settings schema changes → Settings tests → API dump →
  reference app usage in `meshlink-reference/` → user-facing docs in `docs/reference/settings.md`

### Diagnostics (`meshlink/src/*/kotlin/ch/trancee/meshlink/diagnostics/`)

- **Specs**: `specs/catalogs/diagnostic-events.yaml`
- **Docs**: `docs/reference/diagnostics.md`
- **Impact cascade**: New diagnostic event → catalog update → diagnostics tests →
  docs update

### Crypto primitives (`ch.trancee.meshlink:meshlink-crypto`)

- **Dependency**: `ch.trancee.meshlink:meshlink-crypto:0.1.0` (Maven Central, via `libs.meshlink.crypto` in `gradle/libs.versions.toml`)
- **Gradle wiring**: `implementation(libs.meshlink.crypto)` in `meshlink/build.gradle.kts`; no `includeBuild` in `settings.gradle.kts`
- **Docs**: `docs/reference/trust.md`, `docs/decisions/crypto/meshlink-crypto-dependency.md`
- **Impact cascade**: Crypto changes → `:meshlink` recompile → Wycheproof test vectors in `meshlink/src/commonTest/resources/wycheproof/` → API dump if public crypto API changes

### Transfer / Reliable Delivery (`meshlink/src/*/kotlin/ch/trancee/meshlink/transfer/`)

- **Specs**: `specs/protocol/state-machines.yaml` (transfer state machine)
- **Docs**: `docs/reference/transfer.md`, `docs/explanation/why-meshlink-wire-codec.md`
- **Impact cascade**: Transfer protocol changes → transfer tests → spec updates →
  docs updates

### Spec files (`specs/`)

- **Catalogs**: `specs/catalogs/*.yaml` — diagnostic events, settings
- **Codecs**: `specs/codecs/*.yaml` — models, frames, enums
- **Protocol**: `specs/protocol/state-machines.yaml`
- **Traceability**: `specs/traceability/specification-map.yaml`
- **Validation**: `scripts/validate-specs.sh` validates at Gradle configuration time
  (enforced in `meshlink/build.gradle.kts` — missing spec files throw `GradleException`)
- **Impact cascade**: Any spec change → run `scripts/validate-specs.sh` → update
  generated/derived code → update tests → update docs

### Git hooks & CI (`.githooks/`, `.github/workflows/`)

- **Files**: `.githooks/pre-commit`, `.githooks/pre-push`, `.githooks/commit-msg`
- **CI**: `.github/workflows/ci.yml` (authoritative), `.github/workflows/copilot-setup-steps.yml`
- **Impact cascade**: Hook/CI changes → sync AGENTS.md Quality Gates table →
  update bootstrap docs in `docs/how-to/bootstrap-project-tooling.md`

## PR & Commit Standards

- **Branch**: Always feature branches from `main`; never commit to `main` directly
- **Conventional Commits**: `feat:`, `fix:`, `test:`, `docs:`, `refactor:`, `perf:`, `chore:`
- **Co-authored commits**: `Co-authored-by: Your Agent <agent@example.com>`
- **PR must include**: Constitution Check (principle-by-principle I–V), version-bump
  rationale for `.api` diffs, matching iOS docs for Android API doc changes
- **Protected paths** (require `@trancee` review per `.github/CODEOWNERS`):
  `meshlink/src/*/kotlin/.../crypto/`, `meshlink/src/*/kotlin/.../routing/`,
  `meshlink/src/*/kotlin/.../wire/`, `docs/decisions/crypto/`, `docs/decisions/routing/`,
  `.github/workflows/`, `.githooks/`, `CONSTITUTION.md`, `AGENTS.md`
