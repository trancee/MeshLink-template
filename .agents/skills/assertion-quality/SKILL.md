---
name: assertion-quality
description: Measures assertion variety and depth in existing test code — finds shallow tests that barely verify anything. Polyglot: .NET, Python, TypeScript, Java, Go, Ruby, Rust, Swift, Kotlin, PowerShell, C++. Invoke when the user asks whether assertions are weak, shallow, trivial, always-true, self-referential, or lack diversity. DO NOT USE for writing/fixing assertions (use code-testing-generator), mutation reasoning (use test-gap-analysis), or general anti-pattern audits (use test-anti-patterns).
license: MIT
---

<objective>
Analyze test code in Kotlin (or any supported language) to measure how varied
and meaningful the assertions are. Produce a metrics report revealing whether
tests verify different facets of correctness — not just "output equals X" but
also structure, exceptions, state transitions, side effects, and invariants.

This skill checks for:
- **Trivial assertions**: Only `assertNotNull(result)` / `assertTrue(b)` / truthiness checks
- **Single-value obsession**: Always checking one field or return value
- **No negative assertions**: Never checking what should *not* happen
- **No state checks**: Not verifying object state changes
- **No structural checks**: Only asserting top-level values, missing nested objects
- **Assertion-free tests**: Tests that call but never verify
- **Tautological assertions**: `assertEquals(x, x)`, `assertTrue(x == x)`
- **Swallowed assertions**: `assertThrows` without verifying the exception message or type

<language-specific>
For Kotlin, read `test-analysis-extensions` and load `extensions/kotlin.md` for:
- JUnit 5 assertions: `assertEquals`, `assertTrue`, `assertFalse`, `assertNull`, `assertNotNull`, `assertThrows`, `fail`
- Kotest matchers: `shouldBe`, `shouldBeTrue`, `shouldBeFalse`, `shouldBeNull`, `shouldNotBeNull`, `shouldThrow`, `shouldBeInstanceOf`, `shouldContain`, `shouldMatch`
- AssertK: `assertThat`, `isEqualTo`, `isNull`, `isInstanceOf`, `isTrue`, `contains`, `containsExactly`
- MockK: `verify(exactly = 1) { mock.method() }` — counts as a state/side-effect assertion
- Coroutine test assertions: within `runTest { }` blocks
</language-specific>
</objective>

<quick_start>
1. Read `test-analysis-extensions` and load `extensions/kotlin.md`
2. For each test method, catalog all assertion calls and their categories: equality, boolean, null, throws, type, string, collection, structural, state, negative
3. Compute assertion diversity metrics: total assertions, assertion types used, assertion-free tests, shallow-only tests (only one assertion type)
4. Report findings as a per-test table with assertion count, assertion types, and a quality assessment (Shallow / Adequate / Strong)
5. Highlight tests with only truthiness/presence checks or no assertions at all
</quick_start>

<when_to_use>
- User asks to evaluate assertion quality or depth
- User asks "are my assertions meaningful?"
- User wants assertion diversity metrics
- User suspects tests give false confidence despite passing
</when_to_use>

<when_not_to_use>
- Writing new tests — use `code-testing-generator`
- Anti-pattern audits beyond assertions — use `test-anti-patterns`
- Mutation reasoning — use `test-gap-analysis`
- Formal smell catalog — use `test-smell-detection`
</when_not_to_use>

<seealso>
- `test-anti-patterns` — broader test quality audit including non-assertion issues
- `test-gap-analysis` — mutation-based gap detection
- `test-analysis-extensions` — Kotlin-specific reference data (required dependency)
</seealso>
