# Spec-to-Reference-Docs Alignment Audit

**Ticket**: [#18 — Verify spec-to-reference-docs alignment](https://github.com/trancee/MeshLink-template/issues/18)
**Scope**: `docs/reference/*.md`, `docs/decisions/*.md`, `docs/README.md`, `SPEC.md`, `specs/**/*.yaml`
**Method**: Parsed all SPEC.md heading anchors from GitHub's live rendered DOM (ground truth), then validated every `SPEC.md#fragment`, ADR, and spec-file link across all reference docs and the ADR index.

---

## Executive Summary

The reference docs are **thin navigation facades that mostly don't navigate**. **30 anchor links are broken** across 14 of 15 reference docs, driven by two root causes:

1. **Section-number omission** (14 docs) — Every top-level SPEC.md section link omits the leading section number that GitHub embeds in auto-generated anchors (e.g. `#security-layer` instead of `#7-security-layer`).
2. **Unsupported `{#id}` syntax** (1 link) — SPEC.md uses Pandoc/MkDocs-style `{#explicit-id}` attribute syntax, which GitHub does **not** support. The `{#id}` becomes literal text in the rendered heading, producing long mangled anchors that no reference doc targets correctly.

Two ADR index issues and one broken YAML anchor were also found. The same broken-anchor pattern is **repo-wide** — it pervades every ADR in `docs/decisions/` as well.

---

## Methodology

GitHub renders SPEC.md server-side then annotates heading IDs client-side. I loaded the live GitHub blob page for `SPEC.md`, extracted every rendered heading's permalink `href` (which is the canonical GitHub anchor), and built the authoritative anchor set. I then wrote a Python validator that:

- Parsed all `##`/`###` headers from SPEC.md and computed GitHub-style slugs (verified to match the live DOM 1:1).
- Extracted every `[text](url)` link from all 18 files in `docs/reference/`.
- Checked each `SPEC.md#fragment` against the authoritative anchor set.
- Checked every ADR and spec-file path for existence (file + YAML key).
- Cross-referenced the ADR index in `docs/README.md` against files on disk.

---

## Root Cause 1: Missing Section-Number Prefix (14 reference docs, 15 unique broken anchors)

GitHub's heading-anchor algorithm lowercases the full heading text, strips punctuation (periods, ampersands, parentheses), and converts spaces to hyphens — **it does not strip leading section numbers**. The SPEC.md **Table of Contents itself** confirms the correct anchors (e.g. `#1-vision--product-pillars`, `#7-security-layer`).

**Only `data-models.md` got it right** (`#3-core-data-models` matches `## 3. Core Data Models`). Every other reference doc omitted the number:

| Reference Doc | Broken Link | Correct Anchor |
|---|---|---|
| `vision.md` | `#vision--product-pillars` | `#1-vision--product-pillars` |
| `architecture.md` | `#architecture-overview` | `#2-architecture-overview` |
| `discovery.md` | `#discovery--identity` | `#4-discovery--identity` |
| `trust.md` | `#trust-model-tofu` | `#5-trust-model-tofu` |
| `transport.md` | `#transport-layer` | `#6-transport-layer` |
| `security.md` | `#security-layer` | `#7-security-layer` |
| `routing.md` | `#routing-layer` | `#8-routing-layer` |
| `transfer.md` | `#transfer-layer` | `#9-transfer-layer` |
| `power.md` | `#power-management` | `#10-power-management` |
| `diagnostics.md` | `#diagnostics--events` | `#11-diagnostics--events` |
| `build-quality.md` | `#build--quality-constraints` | `#12-build--quality-constraints` |
| `testing.md` | `#testing--verification` | `#13-testing--verification` |
| `settings.md` | `#settings-model` | `#14-settings-model` |
| `future-work.md` | `#future-work` | `#15-future-work` |
| `power.md` (extra) | `#diagnostics--events` | `#11-diagnostics--events` |

Each broken anchor appears **twice** (once in the `> Specification:` header line and once in `## Quick Links`), yielding **30 broken link instances** total.

### Root cause analysis

`data-models.md` proves the author knows the correct convention (`#3-core-data-models`). The other 14 docs were written with a different, wrong convention. This is likely because an editor or template generated anchors from the heading *text without the number* (e.g. stripping "3. " before slugifying), which does not match GitHub's behavior.

---

## Root Cause 2: GitHub Does Not Support `{#id}` Attribute Syntax (1 broken link + systemic risk)

SPEC.md uses Pandoc/MkDocs-style explicit-ID attributes on 20+ sub-headings:

```markdown
### 7.6 Error Hierarchy (Sealed) {#error-hierarchy} {#error-code}
### 3.1 PeerIdentity {#peer-identity-model}
### 7.2 Fail-Closed Rules {#fail-closed}
### 10.1 Power Modes {#power-mode-settings}
```

On GitHub, these `{#id}` tokens become **literal text** inside the heading, producing mangled anchors:

| SPEC.md Heading (as written) | GitHub Actual Anchor |
|---|---|
| `### 7.6 Error Hierarchy (Sealed) {#error-hierarchy} {#error-code}` | `#76-error-hierarchy-sealed-error-hierarchy-error-code` |
| `### 3.1 PeerIdentity {#peer-identity-model}` | `#31-peeridentity-peer-identity-model` |
| `### 7.2 Fail-Closed Rules {#fail-closed}` | `#72-fail-closed-rules-fail-closed` |
| `### 10.1 Power Modes {#power-mode-settings}` | `#101-power-modes-power-mode-settings` |

The SPEC.md author left `SPEC-ANCHOR` comments (lines 293, 1210, 1262) documenting the *intended* ids (`transfer-session-model`, `power-mode-settings`), suggesting awareness of a docs-site renderer that supports attribute syntax. But with no `mkdocs.yml` or equivalent in the repo, the docs render on GitHub where this syntax is inert.

**Broken link caused by this**: `data-models.md` links to `#76-error-hierarchy-sealed` (line 33), expecting the `{#error-hierarchy}` to be stripped — but GitHub's actual anchor is `#76-error-hierarchy-sealed-error-hierarchy-error-code`.

**Latent risk**: Any future reference doc linking to these `{#id}` values (`#peer-identity-model`, `#constant-time`, `#replay-window`, `#fail-closed`, `#power-mode-settings`, etc.) will be broken on GitHub the same way.

---

## Root Cause 3: Stale Heading Reference in ADR (not a reference doc)

The `docs/decisions/model/data-model.md` ADR (line 178) links to:

```text
[SPEC.md §3.4.1](../../../SPEC.md#transfer-session-state-transitions)
```

Two problems:

- The heading `transfer-session-state-transitions` **does not exist** anywhere in SPEC.md.
- The section number `§3.4.1` is wrong — `§3.4` is `RouteCandidate`, and `TransferSession` is `§3.5`. The correct heading is `### 3.5 TransferSession {#transfer-session-model}` → GitHub anchor `#35-transfersession-transfer-session-model`.

The SPEC.md even documents the intended anchor via a `SPEC-ANCHOR` comment (line 293): ``transfer-session-model``.

---

## Finding 4: Two Orphaned ADRs Not in the `docs/README.md` ADR Index

Every ADR listed in the `docs/README.md` "ADR Index" table (29 entries) **exists on disk** — no missing files. However, **two legitimate ADRs are absent from the index**:

| Orphaned ADR | Status | Referenced By |
|---|---|---|
| `docs/decisions/crypto/key-rotation-propagation.md` | Locked — 2026-07-31 (70 lines) | `trust.md`, `security.md`, `index.md` (Security row), `SPEC.md` line 1571 |
| `docs/decisions/crypto/meshlink-crypto-dependency.md` | Amended — 2026-08-16 (122 lines) | `meshlink-crypto-api.md`, `docs/README.md` Quick Links |

Both are substantive decision records (not stubs). `key-rotation-propagation.md` is even cited in the SPEC.md Traceability Index (line 1571), yet it is missing from the `docs/README.md` ADR Index table. The `docs/reference/index.md` table **does** list `key-rotation-propagation.md` (under Security Layer), creating an inconsistency between the two indexes.

---

## Finding 5: Repo-Wide Scope of the Broken-Anchor Pattern

The missing-section-number convention is **not limited to reference docs** — it pervades every ADR in `docs/decisions/`. Examples extracted from ADRs:

| ADR | Broken Link | Correct Anchor |
|---|---|---|
| `crypto-design.md` | `#trust-model-tofu` | `#5-trust-model-tofu` |
| `identity-binding-and-fail-closed.md` | `#trust-model-tofu` | `#5-trust-model-tofu` |
| `private-key-handling.md` | `#security-layer` | `#7-security-layer` |
| `replay-window.md` | `#replay-window` | `#75-replay-window-replay-window` |
| `constant-time-policy.md` | `#constant-time` | `#74-constant-time-policy-constant-time` |
| `crypto-design.md` | `#trust-model-tofu` | `#5-trust-model-tofu` |
| `flow-delivery.md` | `#diagnostics--events` | `#11-diagnostics--events` |
| `connectable-advertisement.md` | `#advertisement-format` | `#41-advertisement-format` |
| `mesh-hash-derivation.md` | `#discovery--identity` | `#4-discovery--identity` |
| `data-model.md` | `#core-data-models` | `#3-core-data-models` |
| `data-model.md` | `#power-mode-settings` | `#101-power-modes-power-mode-settings` |
| `data-model.md` | `#transfer-session-state-transitions` | (heading does not exist) |
| `error-hierarchy.md` | `#error-hierarchy-sealed` | `#76-error-hierarchy-sealed-error-hierarchy-error-code` |
| `mesh-size-limits.md` | `#routing-layer` | `#8-routing-layer` |
| `settings-model.md` | `#settings-model` | `#14-settings-model` |
| `power-mode-behavior.md` | `#power-management` | `#10-power-management` |
| `routing-design.md` | `#routing-layer` | `#8-routing-layer` |
| `transfer-identifier.md` | `#transfer-session-model` | `#35-transfersession-transfer-session-model` |
| `transfer-identifier.md` | `#transfer-layer` | `#9-transfer-layer` |
| `public-api-and-lifecycle.md` | `#public-api-surface` | `#23-public-api-surface-meshlink-public-api` |
| `background-operation.md` | `#transport-layer` | `#6-transport-layer` |

This confirms the issue is a **systemic authoring convention**, not an isolated mistake.

---

## Finding 6: Broken YAML Spec-File Anchor

`power.md` (line 5) links to:

```text
specs/codecs/enums.yaml#power-mode
```

The file exists, but `power-mode` is **not** a YAML key. The enum in `enums.yaml` is named `PowerMode` (under the top-level `enums` list, accessed as `enums[].name`). The correct anchor would be `power-mode` only if the renderer supported YAML key anchors and lowercased the name — but GitHub does not support anchor fragments in YAML views at all.

For comparison, the companion link `specs/catalogs/settings.yaml#power_mode_parameter_mapping` in the same doc **does** match a real top-level YAML key (`power_mode_parameter_mapping` exists in settings.yaml).

---

## Findings: What Is Correct

| Category | Status |
|---|---|
| ADR paths referenced by reference docs | All 14 ADRs referenced exist on disk |
| Spec file paths referenced by reference docs | All 5 spec files exist on disk |
| ADR index in `docs/README.md` (29 entries) | All 29 files exist on disk |
| ADR fragment links (`#peerhint-is-a-rotating-advertisement-hint`) | Valid — matches rendered anchor |
| CONSTITUTION.md anchors (`#technical-constraints`, `#ii-exhaustive-testing-standards`) | Valid |
| `.agents/skills/wycheproof/SKILL.md` references | Valid — file exists at repo root |
| `meshlink/build.gradle.kts` references | Valid — file exists |
| All 15 SPEC.md sections have a corresponding reference doc | Yes — one facade per section |

---

## Non-Issues (Intentional)

- **`meshlink-crypto-api.md` and `device-test-matrix.md` are not in the `docs/reference/index.md` table.** These are special-purpose docs (external-dependency API guide and a device-fleet matrix), intentionally not mapped to a single SPEC.md section.
- **`meshlink-crypto-api.md` does not link to SPEC.md.** It documents the external `meshlink-crypto` v0.1.1 artifact and uses plain-text section references ("SPEC §5.1") rather than hyperlinks. This is a usage guide, not a layer facade.
- **`routing.md` Quick Links references `peer-hint-and-identity-races.md` (a discovery ADR).** The file exists; the cross-domain reference is questionable but not broken.

---

## Recommendations

1. **Adopt `{#id}`-supported rendering** (e.g. MkDocs with `attr_list` extension, or a GitHub Action that post-processes). This would make the Pandoc-style `{#id}` attributes in SPEC.md work as intended. Without it, GitHub heading anchors remain mangled and long.

2. **Fix SPEC.md anchors in all docs** — either:
   - **(A)** Update all `SPEC.md#fragment` links across reference docs and ADRs to use the actual GitHub-generated anchors (with section-number prefix), or
   - **(B)** If using an `{#id}`-supporting renderer: standardize all SPEC.md headings to include explicit `{#id}` attributes, update `SPEC-ANCHOR` comments into actual `{#id}` tokens, and have reference docs link to the explicit ids (not to numbered or partial slugs).

3. **Add the two orphaned ADRs** (`key-rotation-propagation.md`, `meshlink-crypto-dependency.md`) to the `docs/README.md` ADR Index table.

4. **Fix the stale `#transfer-session-state-transitions` link** in `data-model.md` ADR to point to the real heading (`#35-transfersession-transfer-session-model` on GitHub, or `#transfer-session-model` if `{#id}` is supported).

5. **Fix the `enums.yaml#power-mode` anchor** in `power.md` — either to `#power-mode` if a renderer lowercases enum names, or to a convention that matches actual YAML keys. Consider whether YAML anchor fragments are supported at all in the chosen doc stack.

6. **Remove redundant `data-models.md` duplication**: the `#3-core-data-models` link is correct; ensure all other docs follow the same numbered-anchor convention.
