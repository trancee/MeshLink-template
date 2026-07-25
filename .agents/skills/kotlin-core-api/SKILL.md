---
name: kotlin-core-api
description: Kotlin Core API reference covering kotlin-stdlib (kotlin, kotlin.collections, kotlin.text, kotlin.io, kotlin.ranges, kotlin.sequences, kotlin.math, kotlin.random, kotlin.properties, kotlin.annotation, kotlin.comparisons, kotlin.system, kotlin.experimental, kotlin.concurrent), kotlin.test (assertions, test annotations, runner integration), and kotlin.reflect (KClass, KFunction, KProperty, full reflection, JVM extensions). Use when asking about the Kotlin standard library, "how do I use listOf/map/filter", "what's the difference between Sequence and List", "how to write assertions with kotlin.test", "how to use reflection in Kotlin", "kotlin.math functions", "kotlin.random", or any kotlinlang.org/api/core topic.
metadata:
  version: 1.0.0
  date: 2026-07-25
  reference: https://kotlinlang.org/api/core/
  model: poolside/laguna-m.1:free
---

# Kotlin Core API

## Quick Start

The Kotlin Core API at [kotlinlang.org/api/core](https://kotlinlang.org/api/core/) is the complete reference for three foundational Kotlin libraries:

- **kotlin-stdlib** — the standard library, automatically imported into every Kotlin file
- **kotlin-test** — the multiplatform testing framework
- **kotlin-reflect** — runtime reflection (`kotlin.reflect`)

> **No import needed for stdlib.** Every function, type, and property in `kotlin-stdlib` is available without an import statement. Only `kotlin.reflect`, `kotlin.io.path`, `kotlin.io.encoding`, and platform-specific packages require explicit imports.

## Essential Principles

- **Read-only collections by default.** `listOf`, `setOf`, `mapOf` return immutable views. Use `mutableListOf` etc. when mutation is needed.
- **Sequences are lazy.** `asSequence()` defers all intermediate operations until a terminal operation runs. Use for large collections or expensive chains.
- **`Result<T>` for functional error handling.** Wraps success or failure without exceptions. Use `runCatching { }`, `mapCatching`, `onSuccess`/`onFailure`.
- **Scope functions differ by receiver and return.** `let`/`run` return the lambda result; `also`/`apply` return the receiver. `let`/`also` use `it`; `run`/`apply`/`with` use `this`.
- **`check()` / `require()` / `assert()` for preconditions.** `require` throws `IllegalArgumentException` (caller fault); `check` throws `IllegalStateException` (object state); `assert` is disabled in production.
- **kotlin.test is multiplatform.** `assertEquals`, `assertTrue`, `expect/actual` test bodies — the same test source compiles to JUnit 4, JUnit 5, or TestNG depending on the runner dependency.
- **Reflection is opt-in.** `kotlin.reflect` is a separate dependency. Use `KClass` for type metadata, `KFunction` for callable references, `KProperty` for property access.
- **HexFormat for hex encoding/decoding.** Kotlin 1.9+ provides `toHexString()`, `hexToInt()`, etc. for numbers and byte arrays with customizable format (prefix, suffix, separator, case).

## Routing

| Topic | Reference |
|-------|-----------|
| Core types (`Any`, `Unit`, `Nothing`, `Result`, `Pair`, `Triple`, `Lazy`), scope functions, `runCatching`, `TODO`, `check`/`require`/`assert`, operators, comparisons | `references/stdlib-core.md` |
| Collections (`List`, `Set`, `Map`), mutable variants, collection operations (filter, map, fold, groupBy, zip, chunked, windowed, aggregate, associate) | `references/stdlib-collections.md` |
| Text (`String`, `Regex`, `trim`, `split`, `replace`), I/O (`File`, `BufferedReader`, `use`, `kotlin.io.path`, `kotlin.io.encoding`), HexFormat | `references/stdlib-text-io.md` |
| Lazy sequences (`asSequence`, `filter`, `map`, `take`, `drop`, `chunked`, `windowed`, `scan`, `zipWithNext`), ranges (`1..10`, `until`, `downTo`, `step`) | `references/stdlib-sequences-ranges.md` |
| Math (`kotlin.math`: `sin`, `cos`, `sqrt`, `pow`, `abs`, `round`, `min`, `max`, `log`, `atan2`, `hypot`), random (`Random`, `nextInt`, `nextDouble`, `nextBoolean`, `Random.Default`) | `references/stdlib-math-random.md` |
| Annotations, comparisons, delegated properties, experimental APIs, concurrency primitives, system operations | `references/stdlib-misc.md` |
| Testing: `kotlin.test` assertions (`assertEquals`, `assertTrue`, `assertNull`, `assertSame`), `@Test`/`@BeforeTest`/`@AfterTest`, runner integration | `references/kotlin-test.md` |
| Reflection: `KClass`, `KFunction`, `KProperty`, `createInstance`, `memberProperties`, `memberFunctions`, JVM extensions (`java`, `javaMethod`, `isAccessible`) | `references/kotlin-reflect.md` |

Start with `references/stdlib-core.md` for the most commonly used stdlib features. Go to `references/stdlib-collections.md` for collection operations, `references/kotlin-test.md` for testing, or `references/kotlin-reflect.md` for reflection.
</tool_call>