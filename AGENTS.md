# Agent Rules

Operational rules for any agent (human or AI) in this repository.
`CONSTITUTION.md` is the authoritative source for engineering rules — don't
duplicate its content here, just link to it.

New clone? Run `./scripts/bootstrap.sh` once before anything else — see
[How to bootstrap MeshLink's local tooling](docs/how-to/bootstrap-project-tooling.md).

## Agent Operational Preferences

- **Git commits**: Never commit with `git commit` without explicit user approval. Always ask before creating a commit.
- **Gradle invocations**: Always pass `--rerun` and disable the build cache (e.g. `--no-build-cache`). Do not rely on cached outputs.
- **Kover coverage gate**: Always achieve 100% line and branch coverage (`:meshlink`) before committing. Run `./gradlew :meshlink:koverVerify` to confirm. If coverage drops, add tests to close the gap.
- **API dump**: Update the MeshLink API dump (`meshlink/api/jvm/meshlink.api`) in the same change set for any public API change. Regenerate with `./gradlew :meshlink:jvmApiDump`.
- **Docs/specs**: Update docs (README, KDoc, ADRs, `docs/`) and specs (`specs/`, `SPEC.md`) in the same change set as the code they describe — never as a follow-up.

## Workflow (in order)

1. Read relevant skill files before implementation, refactors, or any task
   where an established best practice applies (e.g. testing, security,
   platform-specific patterns); include a `Skills Used` summary in the
   completion report.
2. Start every feature/fix with the `/tdd` skill: write a failing test at
   an agreed seam, then implement only enough to pass it, one cycle at a
   time. See [II. Testing](CONSTITUTION.md#ii-exhaustive-testing-standards).
3. Keep files within the size/split limits in
   [V. Maintainable Design](CONSTITUTION.md#v-maintainable-design-and-change-isolation).
4. Update docs (README, KDoc, ADRs, `docs/`) in the same change set as the
   code they describe — never as a follow-up.
5. Commit with Conventional Commits after each unit of work, unless an
   auto-commit hook already did it. See
   [Quality Gates](CONSTITUTION.md#quality-gates) for the commit format and
   the feature-branch requirement. AI-assisted commits MUST include a
   `Co-authored-by:` trailer identifying the agent.
6. Before opening a PR, run the code `/review` skill and resolve any
   genuine issues it finds.
7. Use the `gh` CLI for GitHub operations (issues, PRs, repos, workflow
   runs) instead of raw API calls.
8. When a design or implementation choice has multiple reasonable
   approaches (not a routine call covered by an existing rule), present
   the options and wait for the user's decision instead of picking alone.

## Build, Test & Lint Commands

> **Note**: All `./gradlew` calls must include `--rerun` and `--no-build-cache` to ensure fresh execution.

`.githooks/` run the first two tiers automatically; CI
(`.github/workflows/ci.yml`) is authoritative — see
[Quality Gates](CONSTITUTION.md#quality-gates).

| Tier | When | Runs |
| --- | --- | --- |
| Fast | Every commit, touched modules only | `gitleaks protect --staged`, `./gradlew :meshlink:ktfmtFormat`, `./gradlew :meshlink:detekt` |
| Full | Every push | `gitleaks detect`, `./gradlew :meshlink:build` |
| CI (authoritative) | Every PR | Full tier, plus `koverVerify` (100% coverage gate), `apiCheck`, `ktfmtCheck`, and iOS simulator tests |

Both tiers also run `yamllint` and `./scripts/check-markdown.sh` (markdownlint-cli2 +
lychee) whenever `.yml`/`.yaml`/`.md` files are touched — see
[How to bootstrap MeshLink's local tooling](docs/how-to/bootstrap-project-tooling.md)
for what these tools check and how to install them.

Run any of these manually, scoped to the module you're editing (e.g.
`./gradlew :meshlink-reference:detekt`), to check your work before
committing or pushing. See
[About MeshLink's module structure](docs/explanation/module-structure.md)
for what each module is for.

## Test File Organization

1. **1:1 Mapping** — Each source file gets exactly one test file with the same base name + `Test` suffix:
   - `TransferId.kt` → `TransferIdTest.kt`
   - `MessageId.kt` → `MessageIdTest.kt`
   - `TransferSession.kt` → `TransferSessionTest.kt`

2. **Package Matching** — Test files live in the same package as the source under `commonTest/`:
   - `src/commonMain/kotlin/ch/trancee/meshlink/model/TransferId.kt`
   - `src/commonTest/kotlin/ch/trancee/meshlink/model/TransferIdTest.kt`

3. **Single Responsibility** — Test files test ONLY their corresponding source file:
   - No mixed tests (e.g., TransferSession tests in TransferIdTest.kt)
   - Shared/contract types tested in dedicated contract test files (e.g., `PublicApiContractTest.kt`)

## Test Patterns

- Use constructor directly: `TransferId(42u)` not `TransferId.fromUInt(42u)`
- Only use factory methods (`fromUInt`, `fromBytes`) when explicitly testing those functions
- Compare raw values via `toUInt()` / `toByteArray()` — avoid `(id as Any).toString()` coercion
- Always import packages — never use fully qualified class names in code body:
  - `import ch.trancee.meshlink.model.PeerIdentity` then use `PeerIdentity.ZERO`
  - Not: `ch.trancee.meshlink.model.PeerIdentity.ZERO`
- One Act per test; Arrange/Act/Assert visually separated (blank line between each)
- Test names use backticks with descriptive sentences:
  - `` `zero id has fixed representation` ``
  - `` `decimal id round trips through toString and fromUInt` ``

## Engineering rules

Binding rules on code quality, testing, cross-platform consistency,
performance, and maintainable design all live in `CONSTITUTION.md`:
[I. Code Quality](CONSTITUTION.md#i-rigorous-code-quality) ·
[II. Testing](CONSTITUTION.md#ii-exhaustive-testing-standards) ·
[III. Cross-Platform Consistency](CONSTITUTION.md#iii-user-experience-consistency) ·
[IV. Performance](CONSTITUTION.md#iv-performance-requirements) ·
[V. Maintainable Design](CONSTITUTION.md#v-maintainable-design-and-change-isolation) ·
[Quality Gates](CONSTITUTION.md#quality-gates) ·
[Technical Constraints](CONSTITUTION.md#technical-constraints) ·
[Governance](CONSTITUTION.md#governance).

Day-to-day conventions below constitutional level live in `docs/`.
