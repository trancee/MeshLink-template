---
name: test-gap-analysis
description: >-
  Find or close verified gaps in existing tests via pseudo-mutation analysis.
  For any supported language: analyze production code by reasoning about
  hypothetical mutations, then confirm them against the real test suite.
  Reveals blind spots where tests pass but would continue to pass even if
  the code were broken (sign flips, off-by-one, removed null checks).
  DO NOT USE for new suites (use code-testing-generator), smells/assertion
  audits (use test-anti-patterns), or actual mutation tools.
license: MIT
---

<objective>
Analyze production code in Kotlin (or any supported language) by reasoning about
hypothetical mutations, then confirming each reported survivor by actually
applying it and re-running the covering tests.

This skill uses **static pseudo-mutation** to find mutation candidates at the
speed of code review, then **confirms each reported survivor by actually
applying it and re-running the covering tests**. Reasoning finds the
candidates; execution decides the verdict.

The mutation catalog covers:
- Arithmetic operator mutations (`+` → `-`, `*` → `/`, etc.)
- Comparison operator mutations (`<` → `>`, `==` → `!=`, etc.)
- Return value mutations (changing a returned constant, flipping a boolean)
- Conditional boundary mutations (`<` → `<=`, `>` → `>=`)
- Negation mutations (removing `!`, flipping `true`/`false`)
- Statement deletion (removing a null check, removing a guard)
- Variable reference mutations (swapping argument order in `assertEquals`)
</objective>

<quick_start>
1. Read `test-analysis-extensions` and load `extensions/kotlin.md` for Kotlin assertion/framework awareness
2. Select production code to analyze (e.g., a recently changed function)
3. For each line, reason about plausible mutations from the catalog above
4. For each mutation candidate, check whether existing tests would fail
5. **Confirm each survivor** by applying the mutation, running the test suite, and observing whether tests still pass
6. Report each verified gap with: file path, line number, mutation applied, test coverage status, suggested test addition
</quick_start>

<when_to_use>
- User asks "would my tests catch a bug in this code?"
- User wants to find weak or shallow tests
- User asks "where are my test blind spots?"
- Evaluating test effectiveness beyond raw coverage percentages
- Reviewing PRs that modify production logic
</when_to_use>

<when_not_to_use>
- Writing new test suites — use `code-testing-generator`
- Anti-pattern audits — use `test-anti-patterns`
- Assertion quality analysis — use `assertion-quality`
- The formal test smell catalog — use `test-smell-detection`
</when_not_to_use>

<seealso>
- `test-anti-patterns` — pragmatic test quality audit
- `assertion-quality` — assertion variety and depth metrics
- `test-analysis-extensions` — Kotlin-specific reference data (required dependency)
</seealso>
