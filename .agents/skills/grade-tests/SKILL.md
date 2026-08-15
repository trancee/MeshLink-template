---
name: grade-tests
description: Grades a specified set of test methods individually and produces a compact markdown table mapping each test (fully-qualified name) to a letter grade (A–F), a score band, and a one-line note — designed to be posted as a PR comment. Polyglot: Kotlin (JUnit/Kotest), Java, .NET, Python, TypeScript, Go, Ruby, Rust, Swift, PowerShell, C++. DO NOT USE for full suite audits (use test-quality-auditor or test-anti-patterns), writing new tests (use code-testing-generator), or coverage measurement.
license: MIT
---

<objective>
Grade a curated list of Kotlin test methods individually and produce a compact
PR-comment-friendly report: one row per test method with a letter grade, a
score band, and a one-line note explaining the grade.

The skill **does not discover tests on its own** — the caller provides the
specific test methods to grade (e.g., new or modified tests in a pull request).

Grading rubric (A–F):

| Grade | Score Range | Criteria |
|-------|-------------|----------|
| A | 90–100 | Thorough assertions, covers happy path + edge cases, no anti-patterns |
| B | 80–89 | Good assertions, minor gaps (e.g., missing one edge case) |
| C | 70–79 | Adequate but shallow — basic assertions, could be stronger |
| D | 60–69 | Weak — minimal assertions, some anti-patterns |
| F | 0–59 | Failing or near-useless — tautological, no real assertions, swallowed exceptions |

Scoring factors:
- **Assertion quality** (40%): diversity, depth, non-triviality
- **Coverage of scenarios** (30%): happy path, edge cases, negative cases
- **Anti-pattern penalties** (30%): deductions for sleep, mystery guest, duplication, tautological assertions
</objective>

<quick_start>
1. Read `test-analysis-extensions` and load `extensions/kotlin.md` for Kotlin assertion/framework awareness
2. Receive a list of test methods to grade (fully-qualified names or file+line spans)
3. For each test method, analyze:
   a. Assertion count and diversity (equality, boolean, null, throws, type, structure)
   b. Scenario coverage (positive, negative, boundary, error path)
   c. Anti-patterns (sleep, mystery guest, swallowing exceptions, tautologies)
4. Compute a score (0–100) and map to letter grade (A–F)
5. Produce a markdown table: `Grade | Test | Score | Note`
</quick_start>

<when_to_use>
- User wants per-test feedback on a curated list of methods
- Reviewing tests added/modified in a pull request
- User asks "are these specific tests good enough?"
- Need a compact PR-comment-friendly test quality summary
</when_to_use>

<when_not_to_use>
- Full suite-wide audit — use `test-quality-auditor` or `test-anti-patterns`
- Writing new tests — use `code-testing-generator`
- Measuring code coverage — use `coverage-analysis`
</when_not_to_use>

<seealso>
- `test-anti-patterns` — pragmatic suite-wide audit
- `test-analysis-extensions` — Kotlin-specific reference data (required dependency)
</seealso>
