# MeshLink Constitution

Binding engineering rules for MeshLink. This file is the single source of
truth for code quality, testing, cross-platform consistency, performance,
and governance. If other docs conflict with this file, this file wins.

Keywords: **MUST** = required, no exceptions without an amendment. **SHOULD**
= strong default, deviations need a stated reason. **MAY** = optional.

## Core Principles

### I. Rigorous Code Quality

Rationale: this is a crypto library — subtle defects are exploitable.

- Never surface private keys, shared secrets, session keys, KDF/HKDF
  output, or other raw key material in logs, exceptions, crash reports, or
  any other diagnostic output, anywhere in the runtime (not just crypto
  test code). Use structured identifiers (algorithm, key id, provider
  label, stage) for diagnostics instead. See
  `docs/decisions/crypto/private-key-handling.md` for provider, persistence,
  memory, redaction, and test requirements.
- Any input crossing a trust boundary (BLE wire payloads, pairing data,
  on-disk config, or other external/untrusted sources) MUST be validated
  and rejected with a typed error on failure — never left to throw an
  unhandled exception, crash, or panic. Prefer sealed result types over
  exceptions for parse failures on untrusted data.
- MeshLink MUST fail closed by default. When identity, authentication,
  authorization, integrity, replay status, protocol compatibility,
  persisted security state, or configuration validity is uncertain, the
  affected operation MUST be denied, dropped, disconnected, paused, or
  rolled back to its last known-good state. It MUST NOT silently trust,
  continue with partially validated state, downgrade security, reuse stale
  keys, fall back to plaintext, or regenerate identity after unexplained
  storage corruption. A fallback is permitted only when it is explicitly
  specified, preserves the required security properties, is observable,
  and has its own tests. Failure scope SHOULD be the smallest safe unit
  (frame, transfer, peer, or instance) rather than an unrelated process
  crash. Every fail-closed branch MUST return or emit a typed, redacted
  reason and be covered by tests. See
  `docs/decisions/crypto/identity-binding-and-fail-closed.md` for the
  project-wide operational interpretation.
- Detekt MUST pass with zero suppressions. Test-code suppressions MUST
  include an inline justification comment.
- ktfmt formatting MUST be applied before every commit; no manual style
  deviations.
- Identifiers (packages, classes, functions, properties, parameters,
  variables) MUST use full, descriptive words. Invented abbreviations or
  contractions (`cfg`, `mgr`, `idx`, `tmp`, `msg`, or single-letter names
  beyond a trivial loop counter) are prohibited. Established
  industry/domain acronyms used as their own proper term (BLE, GATT,
  UUID, MTU, AEAD, SHA256, API) are not abbreviations of an intended full
  word and remain allowed as-is.
- Public API MUST be tracked by Binary Compatibility Validator (BCV). Any
  `.api` diff MUST include a version-bump rationale in the PR.
- The shipped `:meshlink` artifact follows its own semver release version
  (MAJOR = breaking API/wire change, MINOR = additive API, PATCH = fix) —
  distinct from this constitution's own version number. Public API
  removals MUST go through a `@Deprecated` grace period of at least one
  MINOR release before deletion.
- Kotlin `explicitApi()` MUST stay enabled (`meshlink/build.gradle.kts`);
  all public declarations need explicit visibility + return types.
- No `TODO` comments in merged code — track unfinished work as issues.
- Tooling (Kotlin/Gradle toolchain, Detekt, ktfmt, Kover, BCV plugin,
  kotlinx-benchmark, yamllint, gitleaks, CI actions) MUST run the latest
  stable release of each component, upgraded together so the whole
  toolchain stays mutually compatible. Hold back a version only for a
  documented, temporary compatibility blocker.
- Dependencies MUST pin exact versions, upgraded promptly to the latest
  mutually compatible stable release — pinning is for reproducibility,
  not for staying on outdated versions. Fix disclosed vulnerabilities per
  the SLA in [Quality Gates](#quality-gates).
- Comment code for *why*, not *what*, so humans can follow it easily.

### II. Exhaustive Testing Standards

Rationale: every branch here carries protocol meaning — untested branches
in crypto, replay protection, or routing are correctness/security risks.

- Full line + branch coverage in CI, zero `@CoverageIgnore` (Kover rules
  in `meshlink/build.gradle.kts`), for the shipped `:meshlink` artifact
  only — see [Quality Gates](#quality-gates) for the exact gate and the
  Technical Constraints note on scoping.
- Use Power-assert for assertions; plain `assertEquals` only for pure
  structural comparisons where Power-assert adds no diagnostic value.
- CI test suite (the default suite run on every PR) MUST be self-validating
  and repeatable. Physical devices, proof apps, and retained benchmarks are
  opt-in extras, never a substitute for automated regression tests.
- Tests MUST show Arrange / Act / Assert as visually distinct steps (blank
  line or comment). One Act per test — split multi-act tests in two.
- Validate crypto correctness against Wycheproof vectors: ChaCha20-Poly1305,
  Ed25519, X25519, HKDF, HMAC-SHA256.
- Multi-node integration tests MUST use the project's virtual harness, not
  real BLE hardware.
- Android/iOS emulators and simulators do not implement real BLE radios —
  never add an emulator/simulator test target as coverage for actual BLE
  behavior (advertising, scanning, GATT/L2CAP connections). Real BLE
  behavior MUST be validated on real hardware (`meshlink-proof`); emulators
  and simulators remain fine for non-radio logic (crypto, routing over the
  virtual harness, wire codec, business logic).
- Prefer `require()` for precondition validation — it is the idiomatic Kotlin
  pattern, throws `IllegalArgumentException` (the correct exception type for
  invalid arguments), and its lazy message lambda is only evaluated on
  failure. Use explicit `if` + `throw` only when a different exception type
  is semantically required (e.g., `IndexOutOfBoundsException` for index
  checks).
- No `while(isActive)` loops for long-running coroutines — use structured
  coroutine patterns.
- Every `when` over a closed set MUST be exhaustive.
- Benchmark these via kotlinx-benchmark on the JVM target: AEAD
  encrypt/decrypt, routing table lookup, wire codec encode/decode.

### III. User Experience Consistency

Rationale: MeshLink promises one library, not two loosely aligned ports.

- Public API (`MeshLink`) MUST be identical in shape on Android and iOS.
  Platform differences hide behind `expect`/`actual`, invisible to callers.
- One config DSL (`meshLinkSettings`) for both platforms; inject
  platform-specific inputs via factory functions, not DSL branches.
- One shared diagnostic-event catalog, same severity tiers and payload
  shapes everywhere. Platform-only codes need a constitutional amendment.
- Errors use sealed exception hierarchies in `commonMain`. Platform
  exceptions get wrapped and MUST NOT leak to consumers.
- State machine (UNINITIALIZED → CONFIGURED → RUNNING → PAUSED/STOPPED) MUST behave
  and emit events identically on both platforms.
- Docs parity: an Android doc change for a public API or cross-platform
  workflow MUST ship the matching iOS doc update in the same change set.
  Platform-only internals (build config, CI, glue code) are exempt.
- Documentation is organized using the Diátaxis framework (tutorials,
  how-to guides, reference, explanation) — see `docs/README.md` and the
  `diataxis` skill before adding or restructuring docs.

### IV. Performance Requirements

Rationale: BLE mesh networking runs under hard power/latency/memory caps.

| Target | Budget |
|---|---|
| Throughput (1-hop L2CAP) | ≥80 KB/s Android (Pixel 6+), ≥60 KB/s iOS (iPhone 12+) |
| Latency (1-hop, 256B, p95) | <50 ms after connection established, both platforms |
| Memory (steady state, 8 peers) | ≤8 MB heap, both platforms |
| Battery (LOW/background idle) | Target ≤5% scan duty cycle; request 500–1000 ms idle connection interval after 5 s without queued work |
| Cold start | <500 ms from `mesh.start()` to first advertisement, both platforms |
| Routing convergence | ≤3 s for 10-node topology change (virtual transport) |
| Wire codec encode/decode | <1 μs/message (JVM benchmark) |

All targets MUST be backed by automated benchmarks. A regression >10% vs.
the last committed benchmark result (or the linked baseline if none exists
yet) blocks merge.

### V. Maintainable Design and Change Isolation

Rationale: simple, cohesive, deletable designs keep change cheap and safe.

- Prefer the simplest design that meets today's approved spec. No
  speculative extension points, frameworks, or config knobs for
  requirements nobody has yet.
- One clear reason to change per module/class/file — keep business logic,
  platform glue, tooling, UI, and benchmarks separated.
- New abstractions need a real reason: demonstrated duplication, a known
  change hotspot, or a stable public contract to preserve. A single call
  site or a guess about the future isn't enough (YAGNI, Rule of Three).
- Minimize coupling, maximize cohesion. Depend on small stable contracts,
  not deep object traversal or internals.
- Prefer composition/factories/strategies over inheritance. Inheritance is
  fine only when subtypes stay substitutable for the base (LSP).
- Hide volatile implementation details behind internal types, interfaces,
  or `expect`/`actual` so refactors don't leak across module boundaries.
- Keep commands (mutate) and queries (read) distinct in naming/behavior
  (CQS). Commands may return a result, but must not pose as passive
  queries.
- Leave touched code/tests/docs at least as clear as you found them (Boy
  Scout Rule); justify any added complexity you don't also clean up.
- Keep files small enough for an AI agent to read and reason about in one
  pass: target ≤300 lines per file/module, hard cap 500. Above the cap,
  split along logical seams (e.g. by responsibility, protocol layer, or
  platform) rather than trimming for length alone — see God Object below.

**Watch for these anti-patterns** in your own changes and in review:

| Smell | Signal | Fix |
|---|---|---|
| God Object | one file/class doing too much, 500+ lines and growing | split into focused modules |
| Shotgun Surgery | 10+ files for one change, same edit repeated everywhere | consolidate — the abstraction boundary is wrong |
| Feature Envy | function uses another module's data more than its own | move it next to the data it needs |
| Premature Abstraction | interface/factory with exactly one implementation | wait for the 2nd/3rd use case |
| Copy-Paste | near-duplicate code blocks | extract + parameterize the difference |
| Magic Numbers/Strings | unexplained literals (`if (retries > 3)`) | name the constant |
| Long Method | 40-50+ lines, deep nesting, hard to scan | extract named sub-steps |
| Excessive Comments | comments restate "what", not "why"; dead commented-out code | self-documenting code; comment only intent/trade-offs |

**Reference vocabulary** (cite these names in reviews instead of "this feels
off"): SOLID (Single Responsibility, Open/Closed, Liskov Substitution,
Interface Segregation, Dependency Inversion), DRY, KISS, YAGNI, Rule of
Three, Principle of Least Surprise, Separation of Concerns, Encapsulation,
Information Hiding, Fail Fast, Defensive Programming, avoid Premature
Optimization. Common refactorings: Extract Method/Class, Replace
Conditional with Polymorphism, Introduce Parameter Object.

## Quality Gates

Every PR MUST, before merge:

- Pass CI (GitHub Actions) — the authoritative gate. `.githooks/`
  pre-commit/pre-push mirror a fast local subset and MAY be skipped or
  bypassed, so local success alone never substitutes for a green CI run.
- Live on a feature branch — never commit directly to `main`.
- Pass Detekt and ktfmt with zero issues (Principle I).
- Pass automated secret-scanning (gitleaks) — no private keys, shared
  secrets, or session material anywhere in the diff or history.
- Meet the 100% line/branch coverage gate for the shipped `:meshlink`
  artifact (Principle II) — `meshlink-reference`, `meshlink-proof`, and
  `meshlink-benchmark` are not held to this gate; see
  [Technical Constraints](#technical-constraints).
- Pass BCV API-compatibility checks and required platform tests
  (Principles I–III).
- Provide benchmark evidence for touched operations (Principle IV).
- Validate any YAML/workflow changes with `yamllint`.
- Ship a corresponding design memo in `docs/decisions/<area>/` for any
  crypto/, routing/, or wire/ change — the same paths `.githooks/pre-push`
  already scopes as sensitive — landing in the same PR or already merged
  before implementation begins.
- Explain, in the PR, any new abstraction/extension point/inheritance
  hierarchy in terms of the Principle V justification (duplication,
  hotspot, stable contract).
- Include a Constitution Check in the PR description: a short
  principle-by-principle (I–V) note on how the change complies or why a
  principle doesn't apply. If a principle is genuinely violated (not just
  inapplicable), add a Complexity Tracking entry instead of merging it
  silently: which principle, why the violation is necessary, and what
  simpler alternative was rejected and why.
- Include a version-bump rationale for any `.api` diff, and KDoc for
  public API changes (Principle I, III).
- Ship matching iOS docs for any Android doc change to a public API
  (Principle III).
- Merge dependency-vulnerability fixes within 5 business days of
  disclosure; CVSS ≥9.0 within 48 hours.
- Use Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`, `refactor:`,
  `perf:`, `chore:`) for every commit, including automated ones.

## Technical Constraints

- Shared logic lives in `commonMain` only; `androidMain`/`iosMain` hold
  `actual` implementations and platform glue only.
- Zero-connectivity operation required — no feature may need a server.
- Minimum platforms: Android API 26, iOS 14. Guard any higher-only API at
  runtime.
- All crypto goes through `CryptoProvider`, validated against Wycheproof.
  No external crypto library ships in the release artifact.
- Deployed MeshLink Wire Codec formats stay backward compatible; explicit
  field/enum codes are never reinterpreted or reused, and breaking changes
  need a major version bump plus migration period.
- The shipped `:meshlink` artifact may depend on exactly one runtime
  dependency: `kotlinx-coroutines-core`. Other modules (app/host/proof/
  benchmark/docs) may add their own runtime deps as long as none leak into
  the shipped `:meshlink` artifact. The config DSL uses `kotlinx-datetime`
  for `Duration` types (§14.1) — this is the acknowledged second exception
  documented in the config model DSL design.
  Adding another one there needs an amendment.
- Dokka (API documentation) and SKIE (Swift-friendly Kotlin/Native interop)
  apply to the shipped `:meshlink` artifact only. `meshlink-reference`,
  `meshlink-proof`, and `meshlink-benchmark` are internal/test-only modules
  and MUST NOT wire in either tool.
- The 100% line/branch coverage gate (Principle II) applies to the shipped
  `:meshlink` artifact only. `meshlink-reference`, `meshlink-proof`, and
  `meshlink-benchmark` are internal/test/reference modules and are not held
  to that gate — their own tests still MUST pass, just without a coverage
  threshold.

## Governance

This constitution outranks READMEs, guides, and comments when they
conflict.

- Propose principle changes in writing: rationale, impact, migration plan,
  documented approval.
- Version using semver: MAJOR = principle removed/redefined incompatibly,
  MINOR = new principle or materially expanded guidance, PATCH =
  clarification/typo/structural cleanup.
- `CHANGELOG.md` for the `:meshlink` release (Principle I) is generated
  from Conventional Commits at release time (e.g. via git-cliff) — not
  hand-maintained between releases.
- Commit automation/templates/hooks default to Conventional Commits and
  MUST be updated whenever commit policy changes.
- Crypto, routing, wire, CI/hook, and governance-document paths are
  protected by `.github/CODEOWNERS`; changes to them need the listed
  owner's review.
- Anything below constitutional level (day-to-day conventions) belongs in
  `docs/`, not here.

**Version**: 1.20.0 | **Ratified**: 2026-04-30 | **Last Amended**: 2026-07-31
