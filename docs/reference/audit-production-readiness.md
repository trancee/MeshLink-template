# MeshLink-template Production-Readiness Audit

**Map:** [#15 — Wayfinder Map](https://github.com/trancee/MeshLink-template/issues/15)  
**Date:** 2026-08-19  
**Auditor:** Wayfinder (automated research subagents)  
**Status:** Complete — ready for implementation

---

## Executive Summary

The MeshLink-template repository is **production-ready for implementation** at the scaffold level: CI/CD quality gates are correctly configured, the build system is sound, and the API surface is stable. However, the audit identified **2 critical gaps**, **1 known high-priority gap**, and **7 medium/low findings** across spec validation, documentation cross-references, and test infrastructure.

| Category | Findings | Status |
|---|---|---|
| CI/CD quality gates | 0 | **PASS** — all 7 Constitution gates configured, actions current |
| API/Binary compatibility | 0 | **PASS** — `:meshlink:apiCheck` + full quality suite green |
| Build configuration | 0 | **PASS** — all versions/targets/explicitApi correct (1 doc-only rec) |
| Spec validation pipeline | 0 | **PASS** — validate-specs.sh in CI + hooks; yamllint covers specs/ |
| Spec-to-code traceability | 2 | **GAPS** — 15 source files missing from spec map; 14 SPEC-ANCHOR annotations orphaned |
| Spec-to-docs alignment | 1 | **BROKEN** — 30 broken SPEC.md anchor links across 14/15 reference docs |
| Test infrastructure | 1 | **GAP** — test organization deviations (3 wrong packages, 5 missing tests) |
| Wire codec | 1 | **KNOWN GAP** — intentionally unimplemented (Vertical slice 4, scaffold plan) |

**Verdict:** The template's scaffold, CI plumbing, and quality-gate configuration are sound enough for an implementer to begin building wire codec, routing, transport, and trust subsystems. The findings above should be addressed during implementation, with 0 remaining critical gaps. No spec validation pipeline gaps remain.

---

## Methodology

The audit was charted as a [wayfinder map](https://github.com/trancee/MeshLink-template/issues/15) with 8 parallel research tickets, each resolved by a dedicated research subagent. Findings are documented in detail in `research/*.md` files. This report synthesizes those findings into a single cohesive document.

| Research ticket | Area | Files |
|---|---|---|
| [#16](https://github.com/trancee/MeshLink-template/issues/16) | Spec file completeness & validation | `research/spec-completeness.md` |
| [#17](https://github.com/trancee/MeshLink-template/issues/17) | Spec-to-code traceability alignment | `research/spec-to-code-alignment.md` |
| [#18](https://github.com/trancee/MeshLink-template/issues/18) | Spec-to-reference-docs alignment | `research/spec-to-docs-alignment.md` |
| [#19](https://github.com/trancee/MeshLink-template/issues/19) | API dump (BCV) alignment | `research/api-dump-alignment.md` |
| [#20](https://github.com/trancee/MeshLink-template/issues/20) | CI/CD quality gates | `research/ci-cd-quality-gates.md` |
| [#21](https://github.com/trancee/MeshLink-template/issues/21) | Test infrastructure & coverage | `research/test-infrastructure.md` |
| [#22](https://github.com/trancee/MeshLink-template/issues/22) | Build configuration & version catalog | `research/build-config-alignment.md` |
| [#23](https://github.com/trancee/MeshLink-template/issues/23) | Wire codec scaffold gap | `research/wire-codec-gap.md` |

---

## Findings

### ✅ Passed: No action needed

#### API dump (BCV) alignment — [#19](https://github.com/trancee/MeshLink-template/issues/19)

`:meshlink:apiCheck` and `:meshlink:jvmApiCheck` both pass. Full quality gate suite (detekt, spotlessCheck, koverVerify, build) all green. API dump is current: no stale entries, no un-dumped declarations, no explicitApi violations.

#### CI/CD quality gates — [#20](https://github.com/trancee/MeshLink-template/issues/20)

CI is a complete and correct representation of all 7 Constitution-required gates: Spotless (`spotlessCheck`), Detekt, Kover (`koverVerify`), BCV (`apiCheck`), gitleaks, yamllint, and markdownlint (`check-markdown.sh`). All 3 GitHub Actions on latest majors: `actions/checkout@v7`, `actions/setup-java@v5`, `gradle/actions/wrapper-validation@v6`. The `verify` job covers all 4 modules; the `ios-build` job compiles iOS arm64 only (simulator correctly excluded — no BLE radio). `.github/CODEOWNERS` covers all CONSTITUTION.md protected paths.

#### Build configuration — [#22](https://github.com/trancee/MeshLink-template/issues/22)

All versions, targets, explicitApi, and BCV configuration are correct. Kotlin 2.4.10, JDK 21, AGP 9.3.1, Android minSdk 26, compileSdk 37, iOS arm64 only. Version catalog pins all dependencies exactly. BCV applied at root with `ignoredProjects` for non-shipped modules. One doc-only recommendation: ADR `meshlink-crypto-dependency.md` has a stale minSdk 21 reference → see follow-up ticket [#34](https://github.com/trancee/MeshLink-template/issues/34).

---

### ⚠️ Critical findings

#### 1. Power-assert compiler plugin not configured — [#21](https://github.com/trancee/MeshLink-template/issues/21) → [#32](https://github.com/trancee/MeshLink-template/issues/32) ✅ RESOLVED

**Severity:** Critical  
**File:** `meshlink/build.gradle.kts`

AGENTS.md explicitly requires power-assert for all test assertions, but the `kotlin("plugin.power-assert")` plugin was absent from `meshlink/build.gradle.kts` and `gradle/libs.versions.toml`. Test failure diagnostics used plain `assertEquals` messages instead of rich multi-line power-assert output showing intermediate values.

**Resolution:** [#32 — Closed] Configured `org.jetbrains.kotlin.plugin.power-assert` (Kotlin 2.4.10 built-in) in the version catalog, root `build.gradle.kts` with `apply false`, and `:meshlink` module with `powerAssert { functions = listOf(...) }` transforming all `kotlin.test.*` assertion functions. All quality gates pass (Spotless, Detekt, Kover 100%, BCV apiCheck, tests green).

#### 2. frames.yaml has a YAML structural error — [#16](https://github.com/trancee/MeshLink-template/issues/16) → [#25](https://github.com/trancee/MeshLink-template/issues/25) ✅ RESOLVED

**Severity:** Critical  
**File:** `specs/codecs/frames.yaml` (lines 496–527)

A mapping key (`description`) was followed by sequence items (`- name:`) at the same indentation level — invalid YAML per strict parsers (PyYAML, yq). This error was **not detected** by `yamllint`, `validate-specs.sh`, or CI — every automated gate passed silently.

**Resolution:** [#25 — Closed] Wrapped sequence items under a proper `values:` sub-key, matching the `FrameType` enum pattern in `enums.yaml`. Verified with `yaml.safe_load` (15 entries parse correctly), `yamllint` (exit 0), and `validate-specs.sh` (all checks pass).

---

### ⚠️ High findings

#### 3. Wire codec entirely unimplemented — [#23](https://github.com/trancee/MeshLink-template/issues/23) → [#35](https://github.com/trancee/MeshLink-template/issues/35)

**Severity:** High (known, not a bug)  
**File:** `meshlink/src/commonMain/kotlin/ch/trancee/meshlink/wire/` (empty directory)

The `wire/` source directory contains zero `.kt` files. The full codec spec exists (~1,394 YAML lines: 14 frames, 20 enums, 15 models) with zero implementation. Only partial scaffolding exists in `model/Enums.kt` (FrameType enum and FrameCode constants). The traceability map correctly marks this as `not_implemented`. This is tracked as **Vertical slice 4** in `specs/tests/scaffold-alignment-plan.md`.

**Follow-up:** [#35 — Implement wire codec scaffold (Vertical slice 4)](https://github.com/trancee/MeshLink-template/issues/35)

---

### ⚠️ Medium findings

#### 4. validate-specs.sh not in CI or git hooks — [#16](https://github.com/trancee/MeshLink-template/issues/16) → [#26](https://github.com/trancee/MeshLink-template/issues/26) ✅ RESOLVED

The spec validation script ran only locally and was never invoked in `.github/workflows/ci.yml` or `.githooks/`. The only CI enforcement was a hardcoded file-existence check in `meshlink/build.gradle.kts`. This is why the `frames.yaml` YAML structural error (#25) passed all gates silently.

**Resolution:** [#26 — Closed] Integrated `validate-specs.sh` into:

- **CI**: Added 'Spec validation' step in `.github/workflows/ci.yml` (after YAML lint)
- **Pre-commit hook**: Added spec validation trigger on `specs/`, `SPEC.md`, `docs/decisions/`, `docs/reference/` changes
- **Pre-push hook**: Same trigger, full verification tier
- Added `scripts/validate-specs.sh` to `bash -n` hook validation list

#### 5. .yamllint ignores specs/ + enum validation is a no-op — [#16](https://github.com/trancee/MeshLink-template/issues/16) → [#27](https://github.com/trancee/MeshLink-template/issues/27)

The `.yamllint` config ignores the entire `specs/` directory, and `validate-specs.sh` step 4 (enum consistency) is a placeholder that echoes a message instead of performing a check. The enum catalog (`specs/codecs/enums.yaml`) and code (`model/Enums.kt`) can silently drift.

**Follow-up:** [#27 — Configure yamllint to cover specs/ and replace no-op enum validation](https://github.com/trancee/MeshLink-template/issues/27)

#### 6. 30 broken SPEC.md/doc cross-reference links — [#18](https://github.com/trancee/MeshLink-template/issues/18) → [#31](https://github.com/trancee/MeshLink-template/issues/31)

30 broken SPEC.md anchor links across 14 of 15 reference docs. Root causes: (1) missing section-number prefixes in anchors (e.g., `#security-layer` instead of `#7-security-layer`), (2) unsupported Pandoc `{#id}` attribute syntax in SPEC.md. The same broken-anchor pattern affects ADRs in `docs/decisions/`.

**Follow-up:** [#31 — Fix 30 broken SPEC.md/doc cross-reference links](https://github.com/trancee/MeshLink-template/issues/31)

#### 7. SPEC.md content consistency issues — [#16](https://github.com/trancee/MeshLink-template/issues/16) → [#28](https://github.com/trancee/MeshLink-template/issues/28)

Five ADRs exist on disk but are not referenced in SPEC.md. §10 (Power Management) is missing from the traceability table. §7 Security row has inconsistent ADR path prefixes. `setting-model` naming is inconsistent (singular vs plural).

**Follow-up:** [#28 — Fix SPEC.md content consistency](https://github.com/trancee/MeshLink-template/issues/28)

#### 8. Spec-to-code traceability gaps — [#17](https://github.com/trancee/MeshLink-template/issues/17) → [#29](https://github.com/trancee/MeshLink-template/issues/29)

15 source files exist in `meshlink/src/commonMain/` but are not listed in any `code_files` entry in the traceability map. 14 SPEC-ANCHOR-annotated files are orphaned from their corresponding `code_files` lists. The enums anchor spans 3 files but lists only `Enums.kt`.

**Follow-up:** [#29 — Fix spec-to-code traceability gaps](https://github.com/trancee/MeshLink-template/issues/29)

#### 9. Test organization deviations — [#21](https://github.com/trancee/MeshLink-template/issues/21) → [#33](https://github.com/trancee/MeshLink-template/issues/33)

JUnit 5 platform adapter is implicit (not explicitly configured). 3 test files are in the wrong package directory. 5 source files lack dedicated unit tests.

**Follow-up:** [#33 — Fix test organization](https://github.com/trancee/MeshLink-template/issues/33)

---

### ⚠️ Low findings

#### 10. not_implemented precision issues — [#17](https://github.com/trancee/MeshLink-template/issues/17) → [#30](https://github.com/trancee/MeshLink-template/issues/30)

Two `implementation_status: not_implemented` entries are imprecise: "MeshLinkSettings DSL integration" and "Message and transfer handles" have real data-type implementations and tests, only the functional wiring is missing (because `MeshLink.kt` is a TODO scaffold).

**Follow-up:** [#30 — Fix precision in not_implemented traceability entries](https://github.com/trancee/MeshLink-template/issues/30)

#### 11. Stale minSdk reference in ADR — [#22](https://github.com/trancee/MeshLink-template/issues/22) → [#34](https://github.com/trancee/MeshLink-template/issues/34)

`docs/decisions/crypto/meshlink-crypto-dependency.md` references minSdk 21 (leftover from the sibling meshlink-crypto project). All MeshLink modules use minSdk 26.

**Follow-up:** [#34 — Fix stale minSdk reference in ADR](https://github.com/trancee/MeshLink-template/issues/34)

---

## Follow-up tickets

All findings are tracked as GitHub issues, labeled `from:audit-map`:

| # | Title | Severity | Triage |
|---|---|---|---|
| [#25](https://github.com/trancee/MeshLink-template/issues/25) | Fix frames.yaml YAML structural error | Critical | ✅ Closed |
| [#26](https://github.com/trancee/MeshLink-template/issues/26) | Integrate validate-specs.sh into CI and git hooks | Medium | ✅ Closed |
| [#27](https://github.com/trancee/MeshLink-template/issues/27) | Configure yamllint to cover specs/ + replace no-op enum validation | Medium | ✅ Closed |
| [#28](https://github.com/trancee/MeshLink-template/issues/28) | Fix SPEC.md content consistency (orphan ADRs, traceability, naming) | Medium | `ready-for-agent` |
| [#29](https://github.com/trancee/MeshLink-template/issues/29) | Fix spec-to-code traceability gaps (15 missing files, orphaned SPEC-ANCHOR) | Medium | `ready-for-agent` |
| [#30](https://github.com/trancee/MeshLink-template/issues/30) | Fix precision in not_implemented traceability entries | Low | `ready-for-agent` |
| [#31](https://github.com/trancee/MeshLink-template/issues/31) | Fix 30 broken SPEC.md/doc cross-reference links + Pandoc syntax | Medium | `ready-for-agent` |
| [#32](https://github.com/trancee/MeshLink-template/issues/32) | Configure power-assert compiler plugin | Critical | ✅ Closed |
| [#33](https://github.com/trancee/MeshLink-template/issues/33) | Fix test organization (JUnit5, 3 wrong packages, 5 missing tests) | Medium | `ready-for-agent` |
| [#34](https://github.com/trancee/MeshLink-template/issues/34) | Fix stale minSdk 21 reference in ADR | Low | `ready-for-human` |
| [#35](https://github.com/trancee/MeshLink-template/issues/35) | Implement wire codec scaffold (Vertical slice 4) | High | `ready-for-human` |

**Recommendation:** Before starting wire codec implementation ([#35](https://github.com/trancee/MeshLink-template/issues/35)), address [#29](https://github.com/trancee/MeshLink-template/issues/29) (spec-to-code traceability gaps). The wire codec is the highest-priority implementation item — all routing, transport, and transfer work depends on it.

---

## Conclusion

The MeshLink-template repository's infrastructure (CI/CD, build config, API surface, dependency pinning) is **production-ready**. The template correctly scaffolds the data model, settings, diagnostics, and utilities per the 4-vertical-slice plan in `specs/tests/scaffold-alignment-plan.md`.

The two critical gaps from the audit (power-assert plugin and frames.yaml YAML error) are now resolved. The spec validation pipeline is fully closed. Remaining gaps are in **documentation integrity** (broken cross-references, traceability drift), **spec-to-code traceability** (15 missing source files, orphaned SPEC-ANCHOR annotations), and **test infrastructure** (test organization) — all straightforward to fix and do not block implementation. The one known major gap (wire codec) is explicitly planned as Vertical slice 4 and is not a defect.

**Readiness verdict:** The template is ready for implementers to begin building the wire codec, routing, transport, and trust subsystems. The 7 remaining follow-up tickets above should be addressed as part of, or before, the implementation phase.
