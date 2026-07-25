# Kotlin stdlib — Sequences & Ranges

## Sequences (Lazy Evaluation)

Sequences defer all intermediate operations until a terminal operation is invoked. Use for large collections or expensive operation chains.

```kotlin
// Create
val seq = sequenceOf(1, 2, 3)
val seq2 = listOf(1, 2, 3).asSequence()
val seq3 = generateSequence(0) { it + 1 }
val seq4 = sequence {
    var i = 0
    while (true) yield(i++)  // infinite sequence
}

// Intermediate (lazy) operations
seq.filter { it > 1 }
seq.map { it * 2 }
seq.mapIndexed { i, v -> i + v }
seq.mapNotNull { it.takeIf { it > 0 } }
seq.flatMap { listOf(it, it * 10) }
seq.flatten()
seq.distinct()
seq.distinctBy { it % 2 }
seq.sorted()
seq.take(5)
seq.takeLast(5)
seq.drop(5)
seq.slice(0..2)
seq.zipWithNext()
seq.zipWithNext { x, y -> y - x }
seq.onEach { println(it) }
seq.onEachIndexed { i, v -> println("$i: $v") }
seq.scan(0) { acc, x -> acc + x }          // running accum
seq.scanIndexed(0) { i, acc, x -> acc + i + x }

// Terminal operations (trigger evaluation)
seq.toList()
seq.toSet()
seq.toMutableList()
seq.count()
seq.sum()
seq.sumOf { it * it }
seq.product()                               // since Kotlin 1.9
seq.first() / .firstOrNull()
seq.last() / .lastOrNull()
seq.single() / .singleOrNull()
seq.find { it > 5 }
seq.any { it > 5 }
seq.all { it > 0 }
seq.none { it < 0 }
seq.joinToString(", ")
seq.joinTo(StringBuilder())
seq.reduce { acc, x -> acc + x }
seq.fold(0) { acc, x -> acc + x }
seq.foldIndexed(0) { i, acc, x -> acc + i + x }
seq.reduceRight { x, acc -> x + acc }
seq.forEach { println(it) }
seq.forEachIndexed { i, v -> println("$i: $v") }
```

### Sequence Builder Pattern

```kotlin
// Fibonacci sequence
val fibonacci: Sequence<Int> = sequence {
    var a = 0
    var b = 1
    yield(a)
    yield(b)
    while (true) {
        val next = a + b
        yield(next)
        a = b
        b = next
    }
}

// Paging
fun <T> Sequence<T>.paginate(pageSize: Int): Sequence<List<T>> = sequence {
    val page = ArrayList<T>(pageSize)
    forEach { item ->
        page.add(item)
        if (page.size == pageSize) {
            yield(page.toList())
            page.clear()
        }
    }
    if (page.isNotEmpty()) yield(page.toList())
}
```

## Ranges & Progressions

```kotlin
// Range creation
val intRange: IntRange = 1..10           // 1, 2, ..., 10
val openRange: IntRange = 1..<10         // 1, 2, ..., 9 (exclusive)
val longRange: LongRange = 1L..10L
val charRange: CharRange = 'a'..'z'
val uintRange: UIntRange = 1u..10u
val ulongRange: ULongRange = 1uL..10uL

// Reverse
val down: IntProgression = 10 downTo 1
val step: IntProgression = (1..10) step 2

// Until (exclusive)
val until: IntRange = 1.until(10)         // 1..9

// Check
5 in 1..10
'c' in 'a'..'z'
15 !in 1..10

// Iterate
for (i in 1..10) { }
for (i in 1..<10) { }
for (i in 10 downTo 1) { }
for (i in 1..10 step 2) { }
for (c in 'a'..'z') { }

// Properties
range.first / .last
range.start / .endInclusive
range.step
range.isEmpty()
range.count()
```

### Progression Types

| Type | Range | Progression |
|------|-------|-------------|
| `Int` | `IntRange` | `IntProgression` |
| `Long` | `LongRange` | `LongProgression` |
| `Char` | `CharRange` | `CharProgression` |
| `UInt` | `UIntRange` | `UIntProgression` |
| `ULong` | `ULongRange` | `ULongProgression` |
| `Byte` | — | `IntProgression` (via extension) |
| `Short` | — | `IntProgression` (via extension) |

### Progressions from Non-Integral Types

```kotlin
// Byte/Short ranges produce IntProgression
(1.toByte()..5.toByte()).step(2)
('a'.code..'z'.code).map { it.toChar() }
```

## Windowed & Chunked Extensions

```kotlin
// Windowed
list.windowed(2)                         // [[1, 2], [2, 3]]
list.windowed(3) { it.sum() }            // [3, 6]
list.windowed(2, 2)                      // [[1, 2], [3]]
list.windowed(3, partialWindows = true)  // [[1, 2, 3], [2, 3], [3]]
list.windowed(2, transform = { a, b -> b - a })  // [1, 1]

// Chunked
list.chunked(2)                           // [[1, 2], [3]]
list.chunked(2) { it.sum() }             // [3, 3]
list.chunked(2).flatten()                // [1, 2, 3]

// Sliding window with step
list.windowed(3, 2)                      // [[1, 2, 3], [2, 3]]
```

## Slice Operations

```kotlin
// Regular slice
list.slice(0..2)                         // [1, 2, 3]
list.slice(listOf(0, 2))                 // [1, 3]

// Get with default
list.getOrElse(10) { -1 }                  // -1
list.getOrElse(1) { -1 }                 // 2
```

## Distinct Operations

```kotlin
list.distinct()                            // unique
list.distinctBy { it % 2 }                 // unique by key
list.distinctBy { it.length }              // for strings
seq.distinct()
seq.distinctBy { it % 2 }
```

## Grouping Extensions

```kotlin
list.groupBy { it % 2 }
list.groupBy({ it % 2 }, { it * 2 })
list.groupingBy { it % 2 }.eachCount()
list.groupingBy { it }.fold(0) { acc, x -> acc + x }
list.groupingBy { it }.reduce { acc, x -> acc + x }
list.groupingBy { it }.aggregate { key, acc, elem, update -> acc + elem }
```