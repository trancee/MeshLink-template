---
name: test-smell-detection
description: Deep-dive audit using the full testsmells.org 19-smell academic catalog for tests in any language. Every finding maps to a named, citable smell from the research literature. Polyglot: Kotlin (JUnit/Kotest), Java, Python, TypeScript, Go, Ruby, Rust, Swift, C++, PowerShell. INVOKE ONLY when explicitly asked for the academic 19-smell catalog or citable smell names. DO NOT USE for general audits — use test-anti-patterns.
license: MIT
---

<objective>
Conduct a formal audit of test code in Kotlin (or any supported language) using
the complete 19-smell taxonomy from the academic literature (test smells.org).
Each finding maps to a named, citable smell with research-backed severity
classification.

The 19 test smells:
1. **Assertion Roulette** — multiple assertions without comments or clear
   failure messages; hard to know which failed
2. **Exception Handling** — empty catch blocks, or catching broad exceptions
3. **Factory Dump** — tests with excessive object creation/setup
4. **Inspectors** — tests that inspect internal state not part of the public contract
5. **Mystery Guest** — tests that depend on external systems (filesystem, DB, network)
6. **Nested Try** — deeply nested try/catch/finally blocks
7. **Non-deterministic Tests** — tests that pass sometimes and fail others
8. **Eager Test** — one test testing too many things
9. **Sensitive Equality** — using `equals` on objects with custom equality
10. **Test Map** — tests that duplicate domain logic
11. **Asserting on Non-interesting Events** — asserting on things the test
    shouldn't care about
12. ** Dependency in Action** — tests reaching deep into another module's internals
13. **Testing Without Abstracting** — no helper methods; repetitive setup
14. **Orphaned Tests** — tests whose fixtures were removed but tests remain
15. **Test Code Duplication** — near-identical test methods across classes
16. **Magic Number Test** — unexplained literals in assertions
17. **Conditional Test Logic** — if/else inside test methods
18. **Sleepy Test** — `Thread.sleep` or `delay` without virtual time
19. **Loosely Coupled Test** — tests that don't verify enough; pass too easily
</objective>

<quick_start>
1. Read `test-analysis-extensions` and load `extensions/kotlin.md` for Kotlin-specific patterns
2. For each test method, scan against the 19-smell catalog above
3. Report each finding with: smell name, file path, line number, severity
   (High/Medium/Low per academic literature), evidence, and suggested fix
4. Aggregate into a ranked table (most severe first)
</quick_start>

<when_to_use>
- User explicitly asks for the 19-smell academic catalog
- User asks "what are the test smells in my code?"
- User wants citable, research-backed smell names for a review
</when_to_use>

<when_not_to_use>
- General pragmatic audit — use `test-anti-patterns`
- Writing new tests — use `code-testing-generator`
- Simple anti-pattern scan — use `test-anti-patterns`
</when_not_to_use>

<seealso>
- `test-anti-patterns` — pragmatic (non-academic) test quality audit
- `assertion-quality` — focused assertion diversity metrics
- `test-analysis-extensions` — Kotlin-specific reference data (required dependency)
</seealso>
