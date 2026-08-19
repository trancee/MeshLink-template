# Research #16 — Spec File Completeness and Validation Audit

**Ticket:** #16 — Audit spec file completeness and validation
**Parent:** #15 — Wayfinder Map
**Date:** 2026-08-19
**Status:** Resolved

## Method

Static verification of the spec-validation pipeline: ran `scripts/validate-specs.sh`,
ran `yamllint` on `specs/` and `SPEC.md`, parsed every YAML file with PyYAML and `yq`,
grepped SPEC.md for ADR and docs/explanation references, compared on-disk files against
all script-maintained lists, and cross-referenced the CI workflow and Gradle build
configuration to determine what is actually enforced in automation.

## Executive Summary

| Layer | Status |
|---|---|
| `validate-specs.sh` (script) | **PASS** — all 7 checks exit 0 |
| `yamllint specs/` (direct) | **PASS** — exit 0, zero errors |
| `yamllint SPEC.md` (direct) | **FAIL** — 30+ errors (expected: markdown is not YAML) |
| YAML parse validity (PyYAML/yq) | **FAIL** — `specs/codecs/frames.yaml` line 496-498 has a structural YAML error |
| CI enforces `validate-specs.sh` | **NO** — script is not in `ci.yml` or git hooks |
| CI enforces YAML parse validity | **NO** — `.yamllint` ignores `specs/`; yamllint misses the structural error anyway |
| Enum consistency check | **NO-OP** — step 4 is a placeholder |

The script passes, but it checks file existence and simple grep patterns — not YAML
validity, internal consistency, or enum/code alignment. A real structural YAML error in
`frames.yaml` goes completely undetected by every automated gate.

---

## 1. `scripts/validate-specs.sh` — All 7 Steps Pass

Running `bash scripts/validate-specs.sh` produces exit code 0 with all checks green:

```text
1. Checking required YAML spec files...           ✓ 7/7 files exist
2. Validating SPEC-ANCHOR references in code...   ✓ 23/23 anchors match SPEC.md
3. Checking SPEC.md required sections...          ✓ 15/15 sections present
4. Checking enum consistency...                    (no-op placeholder)
5. Checking settings consistency...               ✓ settings.yaml ↔ MeshLinkSettings.kt
6. Checking ADR references...                      ✓ 23/23 docs/decisions/*.md exist
7. Checking reference docs...                      ✓ 15/15 docs/reference/*.md exist
```

**However**, this script validates presence and simple string matching — it does NOT
parse YAML, check internal consistency, or compare spec values against generated code.

## 2. yamllint — Two Distinct Behaviors

### 2a. `yamllint specs/` — PASS (exit 0, no output)

All seven spec YAML files parse cleanly under yamllint's default rules.

### 2b. `yamllint SPEC.md` — 30+ ERRORS (expected)

yamllint reports `syntax error: expected alphabetic or numeric character, but found '*'`
and 30+ `line too long` errors. This is **expected behavior**: SPEC.md is a Markdown
document, not a YAML file. In CI, `yamllint .` processes only `*.yaml`/`*.yml` files by
default (confirmed: `yamllint --version` = 1.38.0, which uses `yamllint`'s default
file-type filter). SPEC.md is never linted by CI.

### 2c. **yamllint does NOT detect the real YAML structural error**

```text
yamllint specs/codecs/frames.yaml      → exit 0 (no errors found)
python3 yaml.safe_load(frames.yaml)    → PARSER ERROR at line 497
yq eval '.' specs/codecs/frames.yaml   → PARSER ERROR
```

yamllint uses libyaml, which apparently accepts the mixed mapping/sequence structure
that PyYAML (YAML 1.1) and yq (Go yaml.v3) reject. A controlled test
(`echo 'a: b\n  - c' | yamllint -`) also returns exit 0 — yamllint silently accepts
this class of structural ambiguity. This is a **yamllint limitation**, not a config
issue.

## 3. **BUG: `frames.yaml` Has a YAML Structural Error**

### Location

`specs/codecs/frames.yaml`, lines 496-498:

```yaml
frame_type_enum:
  description: "Explicit UByte codes; encoders never use declaration ordinal. Derived from enums.yaml FrameType — kept here as a wire codec reference convenience."
  - name: MESH_ENVELOPE      ← YAML parser error: "expected <block end>, but found '-'"
    code: "0x00"
  - name: ROUTE_ADVERTISEMENT
    code: "0x01"
  ...
```

### Root Cause

The `frame_type_enum` key has a `description` string value followed by sequence
entries (`- name:`) at the same indentation level. A YAML mapping cannot transition
to a sequence at the same level — the `description` key's value is either a string or
the sequence, not both.

### Redundancy

The `frame_type_enum` block (15 entries, lines 496-527) is a **verbatim duplicate** of
the `FrameType` enum already defined in `specs/codecs/enums.yaml` (lines 52-103, also
15 entries). The description on line 497 even says "Derived from enums.yaml FrameType
— kept here as a wire codec reference convenience."

### Impact

- Any tool that parses `frames.yaml` as YAML (e.g., code generation, validation
  scripts using PyYAML/yq) will fail with a parser error at line 497.
- The error is **invisible to all CI gates**: yamllint ignores `specs/` per `.yamllint`
  config, and even when run directly on the file, yamllint does not detect it.
- `validate-specs.sh` only checks file existence, not YAML parse validity.
- The Gradle configuration-time check in `meshlink/build.gradle.kts` (lines 7-27) also
  only checks file existence.

### Suggested Fix

Either:

- **Remove** the redundant `frame_type_enum` block entirely (it duplicates `enums.yaml`),
  or
- **Wrap** it in a proper `values:` sub-key to make it valid YAML:

  ```yaml
  frame_type_enum:
    description: "..."
    values:
      - name: MESH_ENVELOPE
        code: "0x00"
  ```

## 4. CI Does NOT Run `validate-specs.sh`

### What CI Actually Runs (`.github/workflows/ci.yml`)

```text
gitleaks detect         → secret scanning
yamllint .              → YAML lint (specs/ EXCLUDED by .yamllint config)
./scripts/check-markdown.sh → markdownlint + lychee link checking
:spotlessCheck          → code formatting
:detekt                 → static analysis
:meshlink:build         → compile + test
:meshlink:koverVerify   → coverage gate
:meshlink:apiCheck      → BCV
```

**`validate-specs.sh` is NOT invoked in CI, in git hooks, or by any Gradle task.**
Searching `ci.yml`, `.githooks/`, `settings.gradle.kts`, and `build.gradle.kts` for
`validate-specs` returns zero matches.

### Gradle Configuration-Time Check (in `meshlink/build.gradle.kts` lines 7-27)

The Gradle build has an **inline** spec check that verifies file existence:

```kotlin
val requiredFiles = listOf(
    "specs/codecs/enums.yaml",
    "specs/codecs/models.yaml",
    "specs/codecs/frames.yaml",
    "specs/protocol/state-machines.yaml",
    "specs/catalogs/diagnostic-events.yaml",
    "specs/catalogs/settings.yaml",
    "specs/traceability/specification-map.yaml",
)
for (path in requiredFiles) {
    if (!file.exists()) { throw GradleException("Missing $path") }
}
```

This is **not** `validate-specs.sh` — it is a simpler inline check that verifies only
file existence. It does NOT parse YAML, check section headers, validate anchors, or
compare enums against code. The `copilot-instructions.md` claim that
"`scripts/validate-specs.sh` validates at Gradle configuration time (enforced in
`meshlink/build.gradle.kts`)" is **inaccurate** — the Gradle check is a separate,
simpler existence-only check, and `validate-specs.sh` is not wired into the build at all.

## 5. Enum Validation Is a No-Op Placeholder

Step 4 of `validate-specs.sh`:

```bash
# 4. Validate enums.yaml matches Enums.kt (placeholder)
echo "4. Checking enum consistency..."
echo "  (Full enum validation requires Kotlin AST parsing - run detekt/Kover)"
```

No comparison is performed between `specs/codecs/enums.yaml` (20 enums, 51 total values)
and `meshlink/src/.../model/Enums.kt` (which declares `FrameType`, `TransferKind`,
`PayloadDecision`, `TransferResultCode`, etc.). The comment delegates to "detekt/Kover"
but neither tool performs this cross-check.

## 6. Orphan ADRs — 5 Files On Disk, Not Referenced in SPEC.md

The script checks ADRs referenced in SPEC.md (step 6), but does NOT check the reverse
direction — whether all ADRs on disk are referenced in SPEC.md. Five ADR files exist
but are never linked from SPEC.md:

| ADR File | Tracked in traceability map? | Referenced elsewhere? |
|---|---|---|
| `docs/decisions/crypto/meshlink-crypto-dependency.md` | No | Yes — CONSTITUTION.md, README.md, module-structure.md, meshlink-crypto-api.md |
| `docs/decisions/model/mesh-size-limits.md` | Yes (`adrs_by_area.model`) | Yes — docs/reference/data-models.md |
| `docs/decisions/storage/persistence-strategy.md` | Yes (`adrs_by_area.storage`) | Yes — private-key-handling.md |
| `docs/decisions/transfer/payload-identity-and-naming.md` | Yes (`adrs_by_area.transfer`) | Yes — public-api-and-lifecycle.md |
| `docs/decisions/transfer/transfer-source-sink-contract.md` | Yes (`adrs_by_area.transfer`) | Yes — docs/reference/transfer.md |

These are not strictly "stale" — they are tracked in the traceability map or
referenced by other docs. But SPEC.md does not link to them, creating a documentation
gap for anyone reading only SPEC.md.

## 7. `docs/explanation/` References Not Checked by the Script

The script only checks `docs/decisions/*.md` references (step 6). SPEC.md also
references two `docs/explanation/` files, which the script does **not** validate:

| Reference | Exists? |
|---|---|
| `docs/explanation/why-meshlink-wire-codec.md` (SPEC.md line 70) | Yes ✓ |
| `docs/explanation/module-structure.md` (SPEC.md line 1566) | Yes ✓ |

Additionally, `docs/explanation/peer-lifecycle.md` exists on disk and is referenced from
`docs/reference/power.md` and `docs/README.md`, but is **not linked from SPEC.md** even
though SPEC.md §11.2 describes the peer lifecycle states (CONNECTED → DISCONNECTED → GONE)
inline without a cross-reference.

## 8. Extra `docs/reference/` Files Not in REF_DOCS List

The `REF_DOCS` array in `validate-specs.sh` lists 15 reference docs. All 15 exist.
However, three additional reference docs exist on disk that are **not** in the list and
are therefore not validated by the script:

| File | Exists? | In REF_DOCS? |
|---|---|---|
| `docs/reference/data-models.md` | Yes | No |
| `docs/reference/device-test-matrix.md` | Yes | No |
| `docs/reference/meshlink-crypto-api.md` | Yes | No |

The script only errors on missing files; it does not warn about extra files.

## 9. `setting-model` Naming Inconsistency

SPEC.md declares a SPEC-ANCHOR using a singular form:

| Location | Value |
|---|---|
| SPEC.md line 1512 | `**SPEC-ANCHOR**: \`setting-model\`` (singular) |
| SPEC.md TOC (line 22) | `#14-settings-model` (plural) |
| SPEC.md traceability map (line 1569) | `SPEC.md#settings-model` (plural) |
| Section header (line 1424) | `## 14. Settings Model` (no `{#anchor}` tag) |
| Code | No file declares `SPEC-ANCHOR: setting-model` |

The validate-specs.sh script checks code→SPEC.md direction only. If a code file were
added with `SPEC-ANCHOR: setting-model`, the script would check for `{#setting-model}`
in SPEC.md (not found — the section has no anchor tag), then check for `^## setting-model`
or `^### setting-model` (not found — the section is `## 14. Settings Model`). It would
emit a WARNING (not fail), since the script uses `echo "⚠ WARNING"` rather than `exit 1`
for anchor mismatches.

## 10. Missing §10 in Traceability Index Table

The traceability index table at SPEC.md lines 1563-1579 lists rows for §1 through §15,
but **§10 (Power Management) is missing**. The sequence jumps:
`§9 Transfer → §11 Diagnostics → §12 Build Quality → §13 Testing → §14 Settings → §15 Future`.

The §10 Power Management content (lines 1214-1263) exists in SPEC.md and has an ADR
reference (`docs/decisions/power/power-mode-behavior.md`), but the table omits the row.

## 11. Inconsistent ADR Path Prefixes in Traceability Table

SPEC.md line 1571 (`§7 Security` row) lists ADR paths with mixed formats — some full,
some partial:

```text
docs/decisions/crypto/crypto-design.md, identity-binding-and-fail-closed.md,
constant-time-policy.md, replay-window.md, key-rotation-propagation.md,
error-hierarchy.md
```

The paths `identity-binding-and-fail-closed.md`, `constant-time-policy.md`,
`replay-window.md`, `key-rotation-propagation.md` are missing the `docs/decisions/crypto/`
prefix. `error-hierarchy.md` is missing the `docs/decisions/model/` prefix. These
shortened paths do not match the script's grep pattern
(`docs/decisions/[^"`,)]+\.md`) and would not be validated by step 6.

## 12. `SPEC-ANCHOR` in Test File Has Trailing Parenthesis

`meshlink/src/commonTest/kotlin/ch/trancee/meshlink/RoutingPolicyTest.kt` line 15:

```kotlin
// Arrange — expected TTL per priority (SPEC-ANCHOR: ttl-by-priority)
```

The trailing `)` after `ttl-by-priority)` means the script would extract
`ttl-by-priority)` (with paren). However, the script only searches `$MESHLINK_SRC`
(`commonMain/`), not `commonTest/`, so this test file's anchor is **not checked at all**.
The production file `RoutingPolicy.kt` correctly declares `SPEC-ANCHOR: ttl-by-priority`
and passes.

## 13. `.yamllint` Config Excludes `specs/` From Linting

`.yamllint` (lines 1-9):

```yaml
# MeshLink keeps its orchestration cockpit under specs/ as tracked YAML
# artifacts. They intentionally omit the `---` document-start marker and use
# long inline strings, so they are excluded from the repo-wide yamllint run
# (`.github/workflows/ci.yml` runs `yamllint .`). Source/gradle YAML is still
# fully linted.
ignore: |
  specs/
```

**Rationale**: Spec files omit `---` document-start markers and use long inline strings.
**Trade-off**: This means no YAML syntax or style errors in `specs/` are ever caught by
CI. The `frames.yaml` structural error (§3 above) is one consequence.

---

## Gap Summary

```text
validate-specs.sh            →  PASS (all 7 steps, exit 0)
  ├─ but: enum check is no-op (step 4 = placeholder echo)
  ├─ but: doesn't check docs/explanation/ references
  ├─ but: doesn't flag orphan ADRs (code→SPEC only, no reverse)
  ├─ but: doesn't flag extra docs/reference/ files
  └─ but: doesn't parse YAML validity
  
yamllint (CI)                →  PASS (but specs/ is IGNORED by config)
  ├─ but: even without ignore, yamllint misses frames.yaml structural error
  └─ SPEC.md is markdown (correctly skipped by default file filtering)
  
Gradle config-time check     →  PASS (file existence only, not validate-specs.sh)
  └─ copilot-instructions.md claim is INACCURATE (different check)

CI                           →  Does NOT run validate-specs.sh
                                Does NOT parse spec YAML validity
                                Runs yamllint (specs/ excluded)
                                Runs gitleaks, markdownlint, lychee, Spotless,
                                Detekt, Kover, BCV
```

## Recommendations (non-exhaustive)

1. **Fix the YAML structural error in `frames.yaml`** — the `frame_type_enum` block is
   malformed and is also a redundant duplicate of `FrameType` in `enums.yaml`; either
   remove it or wrap its entries under a `values:` key.

2. **Remove the `ignore: specs/` exclusion in `.yamllint`** and add `document-start:
   disable` to the rules (already present) so spec YAML files are linted without
   requiring the `---` marker. Note: even if this is done, yamllint 1.38.0 will not catch
   the `frames.yaml` structural error — a PyYAML-based validation step would be needed.

3. **Wire `validate-specs.sh` into CI** (or replace the inline Gradle existence check
   with a call to the script) so the documented validation is actually enforced.

4. **Replace the step-4 enum placeholder** with an actual comparison between
   `specs/codecs/enums.yaml` and the generated `Enums.kt` (e.g., via a Kotlin compiler
   plugin, detekt rule, or a simple script that parses both).

5. **Add reverse ADR checking** — verify that all ADRs on disk are referenced in SPEC.md
   or the traceability map.

6. **Fix the traceability table** — add the missing §10 Power Management row and use
   consistent full ADR paths in the §7 Security row.
