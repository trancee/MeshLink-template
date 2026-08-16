# PR: {{title}}

## Description

<!-- Briefly describe what this change does and why. Link to relevant issues: Fixes #123 -->

## Changes

<!-- Summarize the key changes. Use bullet points. -->

- <!-- change 1 -->
- <!-- change 2 -->

## How to Test

<!-- Steps to verify the change manually, plus which automated tests cover it. -->

1. <!-- step 1 -->
2. <!-- step 2 -->

## Constitution Check

<!-- Required per CONSTITUTION.md — Quality Gates. Brief principle-by-principle note. -->

| Principle | Compliance |
| --- | --- |
| I. Code Quality | <!-- Detekt clean, Spotless applied, private-key handling ✅/N/A --> |
| II. Testing | <!-- 100% coverage maintained, Wycheproof vectors updated --> |
| III. UX Consistency | <!-- Cross-platform parity maintained or N/A --> |
| IV. Performance | <!-- Benchmarks unaffected or updated --> |
| V. Maintainable Design | <!-- Change follows existing seams, no premature abstraction --> |

## Notes for Reviewers

<!-- Any context on design decisions, trade-offs, or things that need attention. -->
<!--
  - If this PR touches crypto/, routing/, or wire/ paths, a design memo in
    docs/decisions/<area>/ should be in this PR or already merged.
  - If this PR changes any .api dump, a version-bump rationale must be provided above.
  - If this PR changes docs for a public API on Android, matching iOS docs must be included.
-->

<!-- Co-authored-by: Your Agent <agent@example.com> -->
