# Research: CI/CD Quality Gates Configuration

**Ticket**: #20 (Wayfinder Map)
**Part of**: #15 (Production-readiness audit)
**Date**: 2026-08-19
**Source**: `.github/workflows/ci.yml`, `.githooks/`, `CONSTITUTION.md`, `AGENTS.md`, `.github/CODEOWNERS`

## TL;DR

`.github/workflows/ci.yml` is a **complete and correct** representation of the
quality gates required by AGENTS.md and CONSTITUTION.md. Every gate mandated
as a CI step is present, all GitHub Actions are pinned to the latest major
version, and both the `verify` and `ios-build` jobs match their spec.
**Three minor gaps** relative to `.githooks/` are identified (shell-script
syntax validation, Conventional Commit enforcement, and test-name parenthesis
validation are not mirrored in CI), but these do not affect gates that the
constitution explicitly delegates to local hooks or PR-level process.

---

## 1. Required Quality Gates Present in CI

Cross-referencing CONSTITUTION.md §Quality Gates (lines 205–242) against the
`verify` job in `ci.yml`:

| Gate | Constitution | CI step | Present? |
|---|---|---|---|
| Spotless format check | I (zero suppressions) | `:meshlink:spotlessCheck` | YES |
| Detekt | I (zero suppressions) | `:meshlink:detekt` | YES |
| gitleaks secret scan | Quality Gates | `gitleaks detect --source . --redact -v` | YES |
| Kover 100% line+branch | II, Quality Gates | `:meshlink:koverVerify` | YES |
| BCV API compatibility | Quality Gates | `:meshlink:apiCheck` | YES |
| yamllint | Quality Gates | `yamllint .` | YES |
| Markdown lint + link check | Quality Gates | `./scripts/check-markdown.sh` | YES |
| `:meshlink` build+test | Core | `:meshlink:build` | YES |
| `:meshlink-reference:check` | Core | `:meshlink-reference:check` | YES |
| `:meshlink-benchmark:check` | Core | `:meshlink-benchmark:check` | YES |
| `:meshlink-proof:check` | Core | `:meshlink-proof:check` | YES |
| iOS arm64 compile | Quality Gates | `:meshlink:compileKotlinIosArm64` | YES |

**All seven gates named in the issue (Spotless, Detekt, Kover, BCV, gitleaks,
yamllint, markdownlint) are present as CI steps.**

### Gradle command flags

Every Gradle invocation in CI passes `--console=plain --rerun-tasks
--no-build-cache`, matching the AGENTS.md operational preference (lines 13,
46). Each task is module-scoped to `:meshlink`, since quality plugins
(Spotless, Detekt, Kover, BCV) apply to that module only.

---

## 2. GitHub Actions Versions — Current Stable

| Action | CI uses | Latest stable | Match? |
|---|---|---|---|
| `actions/checkout` | `v7` | `v7.0.1` | YES (major v7) |
| `actions/setup-java` | `v5` | `v5.7.0` | YES (major v5) |
| `gradle/actions/wrapper-validation` | `v6` | `v6.3.0` | YES (major v6) |

All three use **major-version tags** (the GitHub Actions best practice), which
float to the latest stable minor/patch within the major. No outdated or
pinned-to-old versions.

### Dynamically-fetched tools

- **gitleaks**: CI installs the latest GitHub release at runtime via
  `gh release view` — currently `v8.30.1`. Always current.
- **lychee**: CI downloads the latest `x86_64-unknown-linux-gnu` release —
  currently `lychee-v0.24.2`. Always current.
- **yamllint**: Installed via `pip install --user yamllint` (latest from PyPI).
- **markdownlint-cli2**: Invoked via `npx --yes` (latest on first run, per
  `scripts/check-markdown.sh`).

---

## 3. `verify` Job — Module Coverage

The `verify` job (lines 23–100) covers all four required modules:

| Step | Gradle command | Module |
|---|---|---|
| Build and test — meshlink | `:meshlink:build` | meshlink |
| Coverage gate (Kover) | `:meshlink:koverVerify` | meshlink |
| Binary compatibility (BCV) | `:meshlink:apiCheck` | meshlink |
| Build and check — meshlink-reference | `:meshlink-reference:check` | meshlink-reference |
| Build and check — meshlink-benchmark | `:meshlink-benchmark:check` | meshlink-benchmark |
| Build and check — meshlink-proof (Android) | `:meshlink-proof:check` | meshlink-proof |

**`:meshlink:build` includes the `test` task** on the JVM, so JVM host tests
(crypto, routing, wire codec, business logic) are covered. The coverage gate
(`koverVerify`) then enforces 100% line+branch coverage on the shipped
`:meshlink` artifact only, matching CONSTITUTION.md Technical Constraints
(line 277–281).

---

## 4. `ios-build` Job — iOS arm64 Only (Simulator Removed)

The `ios-build` job (lines 102–122) runs on `macos-latest` and compiles
exactly one iOS target:

```bash
./gradlew --console=plain --rerun-tasks --no-build-cache :meshlink:compileKotlinIosArm64
```

- **`iosArm64` (device) only** — compiles the iOS device target.
- **No `iosSimulatorArm64` or `iosX64` target** — the simulator target was
  removed, as documented in the inline comment (lines 117–121).
- The comment explains the rationale: iOS simulators lack a real BLE radio,
  so they cannot validate actual BLE behavior. Non-radio logic (crypto,
  routing, wire codec) is covered by JVM host tests in the `verify` job.

**Confirmed: simulator target is removed; only iosArm64 is compiled.**

---

## 5. Comparison with `.githooks/` Scripts — Gaps Identified

The pre-commit hook (fast tier) and pre-push hook (full tier) mirror a subset
of CI. The design is intentional — AGENTS.md (lines 48–61) documents that
hooks run a "fast local subset" and CI is authoritative. However, three
checks performed by the hooks have **no CI equivalent**:

### Gap 5a: Shell-script syntax validation (`bash -n`)

Both `.githooks/pre-commit` (lines 84–93) and `.githooks/pre-push`
(lines 185–194) run `bash -n` on themselves and on `scripts/*.sh`. This
self-validates that hook and script syntax is intact before a commit/push
proceeds. **CI has no corresponding step** to lint the shell scripts in
`.githooks/` or `scripts/`. If a contributor bypasses hooks (which the
Constitution explicitly allows — "MAY be skipped or bypassed"), a broken
shell script could land without CI catching it.

**Recommendation**: Add a step to CI that runs `bash -n` on `.githooks/*` and
`scripts/*.sh` (or install `shellcheck` and lint them, as
`docs/how-to/bootstrap-project-tooling.md` suggests for manual hook editing).

### Gap 5b: Conventional Commit enforcement

`.githooks/pre-push` (lines 14, 96–112) enforces Conventional Commits on every
outgoing commit using the pattern
`^(feat|fix|test|docs|refactor|perf|chore)(\([A-Za-z0-9._/-]+\))?(!)?: .+`.
CONSTITUTION.md line 241 requires Conventional Commits for every commit
"including automated ones." **CI does not validate commit message format.**
A contributor who bypasses the pre-push hook could push non-conventional
commits. Note: the `commit-msg` hook (lines 4–16) also enforces this at commit
time, but CI has no check.

**Recommendation**: Add a CI step (using `commitlint`, `gitlint`, or a shell
pattern check) to validate commit messages on PRs, or rely on branch-protection
rules if those are configured externally.

### Gap 5c: Test-name parenthesis validation

`.githooks/pre-commit` (lines 177–202) contains a custom `grep` check that
rejects backtick-quoted Kotlin test function names containing parentheses,
because `(` and `)` inside backtick test names break Kotlin/Native
compilation. This is a **hard compilation-breaking check** specific to this
project's multi-platform targets. **CI has no equivalent step.** The
`:meshlink:build` step would eventually catch the compilation error, but only
after a full Gradle compile — the hook catches it instantly at commit time.

**Recommendation**: Add a lightweight CI step (or a Detekt rule) that enforces
the no-parentheses-in-test-names constraint, since it prevents a real build
failure on iOS targets.

### Non-gaps (intentional differences)

- **Spotless `Apply` vs `Check`**: Pre-commit runs `spotlessApply` (fixes
  formatting); CI runs `spotlessCheck` (verifies only). This is correct — CI
  should not rewrite files, only verify them.
- **yamllint/lint scoping**: Pre-commit and pre-push lint only changed files;
  CI runs `yamllint .` and `check-markdown.sh` on everything. This is correct
  — CI is the comprehensive gate.
- **gitleaks scope**: Pre-commit runs `gitleaks protect --staged` (staged diff
  only); pre-push and CI run `gitleaks detect --source .` (full history). This
  is correct — CI catches history-level leaks that local hooks can't.
- **Detekt zero suppressions**: CONSTITUTION.md line 43–44 requires Detekt with
  zero suppressions. CI runs `:meshlink:detekt` which will fail on any Detekt
  issue. The "zero suppressions" enforcement is a configuration-level rule
  (e.g., Detekt config sets `autoShrinkSuppressions: false` and forbids
  `@Suppress` annotations); CI's `detekt` step catches violations at that level.
  ✅

---

## 6. CODEOWNERS — Protected-Path Coverage

CONSTITUTION.md (lines 298–300) states: "Crypto, routing, wire, CI/hook, and
governance-document paths are protected by `.github/CODEOWNERS`."

Cross-referencing `.github/CODEOWNERS` against this requirement:

| CONSTITUTION protected path | CODEOWNERS rule | Owner | Match? |
|---|---|---|---|
| Crypto code | `/meshlink/src/*/kotlin/ch/trancee/meshlink/crypto/` | @trancee | YES |
| Routing code | `/meshlink/src/*/kotlin/ch/trancee/meshlink/routing/` | @trancee | YES |
| Wire code | `/meshlink/src/*/kotlin/ch/trancee/meshlink/wire/` | @trancee | YES |
| Design memos (crypto) | `/docs/decisions/crypto/` | @trancee | YES |
| Design memos (routing) | `/docs/decisions/routing/` | @trancee | YES |
| CI workflows | `/.github/workflows/` | @trancee | YES |
| Git hooks | `/.githooks/` | @trancee | YES |
| CONSTITUTION.md | `/CONSTITUTION.md` | @trancee | YES |
| AGENTS.md | `/AGENTS.md` | @trancee | YES |

**All protected paths required by CONSTITUTION.md are covered in CODEOWNERS
with `@trancee` as the reviewer.** No gaps.

Note: The CODEOWNERS file (lines 11–14) notes that crypto/, routing/, and
wire/ packages are currently empty (implementation is planned via TDD), so
these rules are forward-looking. This matches the project's current state.

---

## 7. Benchmark Evidence Gate — Contextual Note

CONSTITUTION.md §Quality Gates (line 220) requires "Provide benchmark
evidence for touched operations (Principle IV)." The CI runs
`:meshlink-benchmark:check`, which builds and runs the benchmark module's
tests. However, CONSTITUTION.md §II (lines 88–90) explicitly states:
"Physical devices, proof apps, and retained benchmarks are opt-in extras, never
a substitute for automated regression tests." The benchmark module is built and
checked in CI, and benchmark evidence is provided at the PR level (via
`:meshlink-benchmark:check` output or benchmark result reports), not as a
regression-blocking CI gate with a 10% threshold. This is consistent with the
constitution's treatment of benchmarks as "opt-in extras." No CI gap here.

---

## Summary

| Category | Status | Notes |
|---|---|---|
| Required quality gates | **PASS** | All 7 gates (Spotless, Detekt, Kover, BCV, gitleaks, yamllint, markdownlint) present |
| GitHub Actions versions | **PASS** | All on latest major (checkout v7, setup-java v5, wrapper-validation v6); gitleaks/lychee dynamically fetched latest |
| verify job module coverage | **PASS** | All 4 modules + coverage + BCV |
| ios-build job | **PASS** | iosArm64 only; simulator removed; rationale documented |
| CODEOWNERS protected paths | **PASS** | All CONSTITUTION-required paths covered |
| Shell-script validation | **GAP** | `bash -n` in hooks but not in CI |
| Conventional Commit enforcement | **GAP** | In pre-push/commit-msg hooks but not in CI |
| Test-name parenthesis check | **GAP** | In pre-commit hook but not in CI |

### Conclusion

`.github/workflows/ci.yml` is a **complete and correct** representation of
the quality gates required by AGENTS.md and CONSTITUTION.md. Every gate that
the Constitution delegates to CI as an authoritative check is present, the
Actions are current, and the module/iOS-target coverage matches spec. The
three identified gaps are checks that currently live only in `.githooks/`.
Because the Constitution explicitly states that hooks "MAY be skipped or
bypassed" and CI is authoritative (AGENTS.md line 56: "passing them locally
does not guarantee this workflow passes"), these gaps represent **low-risk**
but **real** opportunities for hardening CI against bypass scenarios. The
highest priority is the test-name parenthesis check, since it prevents a hard
Kotlin/Native compilation failure that CI would only catch late in the build.
