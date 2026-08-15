---
name: test-tagging
description: Analyzes test suites in any language and tags each test with standardized traits (positive, negative, critical-path, boundary, smoke, regression, integration, performance, security). Polyglot: Kotlin (JUnit/Kotest), Java, Python, TypeScript, Go, Ruby, Rust, Swift, C++, PowerShell. Auto-edits when the framework has canonical tag syntax, otherwise report-only.
license: MIT
---

<objective>
Analyze an existing test suite in Kotlin (or any supported language) and apply
a standardized set of trait tags to each test method, giving teams visibility
into their test distribution.

Standard trait tags:
- **positive** — verifies correct behavior with valid inputs
- **negative** — verifies error handling with invalid inputs
- **critical-path** — covers core user-facing or security-critical flows
- **boundary** — edge cases: empty, max, min, off-by-one, overflow
- **smoke** — quick sanity check that the build is not broken
- **regression** — verifies a previously fixed bug remains fixed
- **integration** — depends on external systems (DB, network, filesystem)
- **performance** — measures or asserts timing/throughput/memory
- **security** — verifies auth, encryption, sanitization, access control

<language-specific>
For Kotlin, read `test-analysis-extensions` and load `extensions/kotlin.md`.
Tag support capability for Kotlin:
- JUnit 5: **auto-edit** — `@Tag("positive")` (stackable)
- Kotest: **auto-edit** — per-test `.config(tags = setOf(Positive))`, per-spec `override fun tags() = setOf(Positive)`
- TestNG: **auto-edit** — `@Test(groups = ["positive"])`

Tag filters in Gradle:
```kotlin
tasks.test {
    useJUnitPlatform {
        includeTags("positive")
        excludeTags("slow")
    }
}
```
</language-specific>
</objective>

<quick_start>
1. Read `test-analysis-extensions` and load `extensions/kotlin.md`
2. For each test method, classify it against the standard trait tags above
3. Determine tag-support capability (auto-edit or report-only)
4. If auto-edit: add the appropriate `@Tag(...)` or Kotest tag annotation(s) to each test
5. If report-only: produce a table of test → tags for manual application
6. Group findings by tag to show test distribution
</quick_start>

<when_to_use>
- Auditing a test project to understand the mix of test types
- User wants to categorize/label tests with traits
- User wants to see positive vs negative test coverage
- User wants to identify which tests are critical-path
</when_to_use>

<when_not_to_use>
- Writing new tests — use `code-testing-generator`
- Running tests — use `run-tests`
- Smell/anti-pattern audits — use `test-anti-patterns` or `test-smell-detection`
</when_not_to_use>

<seealso>
- `test-anti-patterns` — quality audit
- `test-analysis-extensions` — Kotlin-specific reference data (required dependency)
</seealso>
