# Kotlin stdlib — Core Types & Functions

The `kotlin` package is automatically imported into every Kotlin source file. No import statement is needed.

## Core Types

### `Any`
The root of the Kotlin type hierarchy. Every non-null Kotlin class implicitly inherits from `Any`.
- `equals(other: Any?): Boolean` — structural equality (overridden by data classes)
- `hashCode(): Int` — hash code
- `toString(): String` — string representation

### `Unit`
The type with exactly one value: `Unit`. Used when a function has no meaningful return value (Kotlin's equivalent of `void`).

```kotlin
fun log(message: String): Unit = println(message)
// Unit can be omitted: fun log(message: String) = println(message)
```

### `Nothing`
The "bottom type" — a type that can never be instantiated. Used to mark functions that always throw or never return.
```kotlin
fun fail(message: String): Nothing = throw IllegalArgumentException(message)
```
`Nothing?` is the type of `null` — useful for representing absence.

### `Result<T>`
A wrapper for a value that may succeed or fail, without throwing exceptions. Use `runCatching` to create one.

```kotlin
val result: Result<Int> = runCatching { "123".toInt() }
result.onSuccess { println("Parsed: $it") }
result.onFailure { println("Failed: $it") }

// Transform:
val doubled: Result<Int> = result.map { it * 2 }
val recovered: Result<Int> = result.recover { 0 }
val value: Int = result.getOrElse { -1 }
```

Key methods: `isSuccess`, `isFailure`, `getOrNull`, `getOrThrow`, `onSuccess`, `onFailure`, `map`, `mapCatching`, `recover`, `recoverCatching`, `fold`.

### `Pair<A, B>` and `Triple<A, B, C>`
Lightweight containers for two or three values.

```kotlin
val pair: Pair<String, Int> = "answer" to 42
val (name, age) = pair  // destructuring

val triple = Triple("x", 1, true)
```

### `Lazy<T>`
Deferred initialization. Use `lazy { }` delegate.

```kotlin
val expensive: String by lazy {
    computeExpensiveValue()
}
// Thread-safe by default (LazyThreadSafetyMode.SYNCHRONIZED)
val unsafeLazy: String by lazy(LazyThreadSafetyMode.NONE) { compute() }
```

## Scope Functions

| Function | Receiver | Returns | Use case |
|----------|----------|---------|----------|
| `let` | `it` | lambda result | null checks, chaining |
| `run` | `this` | lambda result | null checks, object init |
| `run` (top-level) | — | lambda result | block of statements |
| `with` | `this` | lambda result | calling multiple methods on same object |
| `apply` | `this` | receiver | object configuration |
| `also` | `it` | receiver | side effects, debugging |

```kotlin
// let — null check + chain
val length = nullable?.let { it.length } ?: 0

// run — null check + result
val len = str?.run { length }

// apply — configure an object
val file = File("data.txt").apply {
    setReadable(true)
    setWritable(true)
}

// also — side effects
val list = mutableListOf<Int>().also { println("Created list") }

// run — top-level block
val result = run {
    val a = computeA()
    val b = computeB()
    a + b
}
```

## Error Handling Functions

- `require(condition, lazyMessage)` — throws `IllegalArgumentException` if condition is false (caller's fault)
- `check(condition, lazyMessage)` — throws `IllegalStateException` if condition is false (object's state is wrong)
- `assert(condition, lazyMessage)` — only checked when assertions are enabled (`-ea`)
- `requireNotNull(value)` — throws if null, returns non-null
- `checkNotNull(value)` — throws if null, returns non-null
- `error(message)` — always throws `IllegalStateException`
- `TODO(reason)` — always throws `NotImplementedError`

```kotlin
fun divide(a: Int, b: Int): Int {
    require(b != 0) { "Division by zero" }
    check(a >= 0) { "a must be non-negative" }
    return a / b
}
```

## Deep Recursion (Kotlin 1.9+)

For recursive algorithms that may cause stack overflow on JVM/JS:
```kotlin
val factorial: DeepRecursiveFunction<Long, Long> = DeepRecursiveFunction { n ->
    if (n <= 1) 1 else n * callRecursive(n - 1)
}
val result = factorial(100)  // No StackOverflowError
```

## Operator Functions

```kotlin
// Arithmetic
5.inc()      // 6
5.dec()      // 4
5.unaryPlus()   // 5
5.unaryMinus()  // -5
5.inv()      // bitwise NOT (complement)

// Bitwise (infix)
5 and 3      // 1
5 or 2       // 7
5 xor 3      // 6

// Shifts
5 shl 2      // 20 (signed shift left)
5 shr 1      // 2 (signed shift right)
-5 ushr 1    // unsigned shift right

// Range
5.rangeTo(10)  // 5..10 (IntRange)
```

## Utility Functions

```kotlin
// repeat
repeat(3) { println("Hello $it") }  // prints 3 times

// run (top-level)
run {
    val x = 1
    x + 2
}  // returns 3

// TODO
fun unfinished(): Int = TODO("Not implemented yet")

// lazy
val lazyValue: String by lazy { "computed" }

// sequence
val seq = sequence {
    var i = 0
    while (true) yield(i++)
}
```

## Comparisons (kotlin.comparisons)

```kotlin
import kotlin.comparisons.*

// Comparators
compareBy<String> { it.length }
compareByDescending<String> { it.length }
nullsFirst<String>()
nullsLast<String>()
naturalOrder<String>()
reverseOrder<String>()

// Multi-comparison
compareBy<String>({ it.length}, { it })  // then compare
```

## Properties & Delegates

```kotlin
import kotlin.properties.Delegates

// Observable
var name: String by Delegates.observable("<no name>") { _, old, new ->
    println("$old -> $new")
}

// Vetoable
var score: Int by Delegates.vetoable(0) { _, _, new ->
    new in 0..100
}

// Not-null
var later: String by Delegates.notNull<String>()

// Lazy
val computed: String by lazy { calculate() }
```