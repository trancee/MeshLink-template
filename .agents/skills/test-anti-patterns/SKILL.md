---
name: test-anti-patterns
description: Audits an existing test file or suite in any language for anti-patterns and quality issues — produces a severity-ranked report (Critical/Warning/Info). Polyglot: .NET, Python, TypeScript, Java, Go, Ruby, Rust, Swift, Kotlin, PowerShell, C++. Invoke when asked to review test quality, find what's wrong with a suite, or check for tests that pass but verify nothing, missing assertions, swallowed exceptions, tautological assertions, broad exceptions, flaky tests, or duplicated tests.
license: MIT
---

<objective>
Perform a quick, pragmatic audit of test code in Kotlin (or any supported language) for anti-patterns that undermine test reliability, maintainability, and diagnostic value. Produce a severity-ranked report: Critical, Warning, Info.

This skill checks for:
- Tests that pass but verify nothing (missing assertions)
- Swallowed exceptions (empty catch blocks, `catch (e: Exception) { }` without assertion)
- Self-comparing / tautological assertions (`assertEquals(x, x)`)
- Broad exception handling (`catch (Exception::class)` swallowing all errors)
- Coverage-touching tests (tests that exist only for coverage, not correctness)
- Flaky or order-dependent tests (`Thread.sleep`, `delay()` without virtual time, shared mutable state)
- Duplicated test logic
- Magic values / unexplained literals in assertions

<language-specific>
For Kotlin, first read the `test-analysis-extensions` skill and load `extensions/kotlin.md` to discover:
- Test framework markers: JUnit 5 `@Test fun foo()`, Kotest spec classes (`StringSpec`, `FunSpec`, `BehaviorSpec`, etc.), Spek `object FooSpec : Spek({ ... })`
- Assertion APIs: JUnit 5 `Assertions`, Kotest matchers, AssertK, MockK `verify`
- Sleep patterns: `Thread.sleep`, `delay()` inside `runBlocking` (smell), `runTest { advanceTimeBy }` (acceptable)
- Skip annotations: `@Disabled`, Kotest `xtest("…")`
- Mystery guest indicators: hard-coded paths, `File.readText()`, real `Retrofit`/`OkHttp` without `MockWebServer`, `Room.databaseBuilder` without `inMemoryDatabaseBuilder`
</language-specific>
</objective>

<quick_start>
1. Read `test-analysis-extensions` to get the Kotlin-specific extension file
2. Scan the target test file(s) or directory for the anti-patterns above
3. Categorize each finding as **Critical** (tests that pass but verify nothing, swallowed exceptions), **Warning** (flaky/order-dependent, duplicates, magic values), or **Info** (minor style issues)
4. Report findings as a markdown list with file path + line number + explanation + suggested fix
</quick_start>

<when_to_use>
- User asks to audit or review test quality
- User asks "what's wrong with my tests?"
- User wants to verify tests are not just for coverage
- Reviewing PRs that add or modify tests
</when_to_use>

<when_not_to_use>
- Writing new tests — use `code-testing-generator` or `writing-mstest-tests`
- Running tests — use `run-tests`
- Deep academic smell catalog — use `test-smell-detection`
- Assertion diversity metrics — use `assertion-quality`
- Coverage/CRAP metrics — use `coverage-analysis`
</when_not_to_use>

<seealso>
- `assertion-quality` — focused on assertion quality and diversity
- `test-smell-detection` — formal 19-smell academic catalog from testsmells.org
- `test-analysis-extensions` — Kotlin-specific reference data (required dependency)
</seealso>
