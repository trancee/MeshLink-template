# Kotlin stdlib — Collections

The `kotlin.collections` package provides `List`, `Set`, `Map`, and their mutable variants. Immutable collections cannot be modified after creation; mutable ones can.

## Creating Collections

```kotlin
// Read-only (immutable)
val list = listOf(1, 2, 3)
val set = setOf("a", "b", "c")
val map = mapOf("a" to 1, "b" to 2)

// Mutable
val mutableList = mutableListOf(1, 2, 3)
val mutableSet = mutableSetOf("a", "b")
val mutableMap = mutableMapOf("a" to 1)

// Empty
val emptyList = emptyList<Int>()
val emptySet = emptySet<String>()
val emptyMap = emptyMap<String, Int>()

// Builders (type-safe)
val builtList = buildList {
    add(1)
    addAll(listOf(2, 3))
    += 4
}
val builtMap = buildMap {
    put("a", 1)
    "b" to 2
}
val builtSet = buildSet {
    add(1)
    add(2)
}
```

## Transform Operations

```kotlin
list.map { it * 2 }                    // [2, 4, 6]
list.mapIndexed { i, v -> i + v }        // [0+1, 1+2, 2+3]
list.mapNotNull { it.takeIf { it > 1 } }  // [2, 3]
list.mapNotNullIndexed { i, v -> v.takeIf { v > 0 }?.let { i } }
list.flatMap { listOf(it, it * 10) }   // [1, 10, 2, 20, 3, 30]
list.flatten()                           // flatten nested lists
```

## Filter Operations

```kotlin
list.filter { it > 1 }                 // [2, 3]
list.filterNot { it > 1 }               // [1]
list.filterIndexed { i, _ -> i % 2 == 0 }  // even indices
list.filterIsInstance<String>()          // type filter
list.filterNotNull()                     // remove nulls
list.filterToString()                    // to string if matches
```

## Search Operations

```kotlin
list.any { it > 2 }                    // true
list.all { it > 0 }                    // true
list.none { it < 0 }                   // true
list.count { it > 1 }                  // 2
list.find { it > 1 }                   // 2 (or null)
list.findLast { it > 1 }               // 3
list.first() / .firstOrNull()          // first element
list.last() / .lastOrNull()             // last element
list.single() / .singleOrNull()        // exactly one
list.elementAt(0) / .elementAtOrNull()
list.elementAtOrElse(10) { -1 }
list.indexOf(2)
list.indexOfFirst { it > 1 }
list.indexOfLast { it > 1 }
list.lastIndexOf(2)
list.binarySearch(2)
list.binarySearchBy(5) { it }
```

## Aggregate Operations

```kotlin
list.sum()                               // 1+2+3 = 6
list.sumOf { it }                        // transform then sum
list.sumOf { it * 2 }
list.average()                           // 2.0
list.minOrNull() / .maxOrNull()         // T?
list.minBy { -it } / .maxBy { it }      // minimum/maximum by selector
list.minWithOrNull(compareBy { it }) / .maxWithOrNull(compareBy { it })
list.fold(0) { acc, x -> acc + x }       // 6 (with initial)
list.reduce { acc, x -> acc + x }        // 6 (first as initial)
list.foldIndexed(0) { i, acc, x -> acc + i + x }
list.reduceIndexed { i, acc, x -> acc + i + x }
list.scan(0) { acc, x -> acc + x }       // [0, 1, 3, 6]
list.runningFold(0) { acc, x -> acc + x }
list.runningReduce { acc, x -> acc + x }
```

## Slice Operations

```kotlin
list.take(2)                             // [1, 2]
list.takeLast(2)                         // [2, 3]
list.takeWhile { it < 3 }                // [1, 2]
list.drop(2)                             // [3]
list.dropLast(2)                         // [1]
list.dropWhile { it < 2 }                // [2, 3]
list.slice(0..2)                           // [1, 2, 3]
list.getOrElse(10) { -1 }                // -1 (or element)
```

## Chunk & Window

```kotlin
list.chunked(2)                          // [[1, 2], [3]]
list.chunked(2) { it.sum() }             // [3, 3]
list.windowed(2)                         // [[1, 2], [2, 3]]
list.windowed(2, 2)                      // [[1, 2], [3]]
list.windowed(3, partialWindows = true)  // include partial windows
list.windowed(2) { it.sum() }              // [3, 5]
```

## Distinct & Unique

```kotlin
list.distinct()                          // unique elements
list.distinctBy { it % 2 }               // unique by key
list.intersect(setOf(2, 3, 4))          // [2, 3]
list.union(setOf(2, 3, 4))             // [1, 2, 3, 4]
```

## Sort Operations

```kotlin
list.sorted()                            // [1, 2, 3]
list.sortedDescending()                  // [3, 2, 1]
list.sortedBy { -it }                   // sort by selector
list.sortedByDescending { it }
list.sortedWith(compareBy({ it }))       // custom comparator
list.sortedWith(reverseOrder())
list.reversed()                          // [3, 2, 1]
list.asReversed()                        // view
```

## Partition & Group

```kotlin
val (evens, odds) = list.partition { it % 2 == 0 }
list.groupBy { it % 2 }                  // Map<Int, List<Int>>
list.groupBy({ it % 2 }, { it * 2 })    // Map<Int, List<Int>>
list.groupingBy { it % 2 }.eachCount()    // Map<Int, Int>
list.associate { it to it * 2 }          // Map<Int, Int>
list.associateBy { it % 2 }              // Map<Int, Int>
list.associateBy({ it }, { it * 2 })
list.associateWith { it * 2 }
list.associateTo(mutableMapOf()) { it to it * 2 }
```

## Combine Operations

```kotlin
val a = listOf(1, 2, 3)
val b = listOf("a", "b", "c")
a.zip(b)                                 // [(1,a), (2,b), (3,c)]
a.zip(b) { x, y -> "$x$y" }              // ["1a", "2b", "3c"]
a.zipWithNext()                          // [(1,2), (2,3)]
a.zipWithNext { x, y -> y - x }          // [1, 1]
a + b                                    // [1, 2, 3, "a", "b", "c"]
a - listOf(2)                            // [1, 3]
```

## Set Operations

```kotlin
setA union setB
setA intersect setB
setA subtract setB
```

## Map Operations

```kotlin
val map = mapOf("a" to 1, "b" to 2)

map.keys                                 // Set<String>
map.values                               // List<Int>
map.entries                              // Set<Map.Entry<String, Int>>
map["a"]                                 // 1 (or null)
map.getOrDefault("c", 0)                 // 0
map.getOrElse("c") { -1 }                // -1
map.getOrThrow("c")                      // throws
map.filter { (k, v) -> v > 1 }          // {b=2}
map.filterKeys { it.startsWith("a") }
map.filterValues { it > 1 }
map.map { (k, v) -> k to v * 2 }
map.mapKeys { it.key.uppercase() }
map.mapValues { it.value * 10 }
map + ("c" to 3)                         // add entry
map - "a"                                // remove entry
map.toList()                             // [(a=1), (b=2)]
map.toSortedMap()                        // sorted by key
map.toMutableMap()                       // mutable copy
```

## Mutable Collection Operations

```kotlin
// MutableList
val list = mutableListOf(1, 2, 3)
list.add(4)
list.addAll(listOf(5, 6))
list.remove(3)
list.removeAt(0)
list.removeFirst() / .removeLast()
list.removeFirstOrNull() / .removeLastOrNull()
list[0] = 10
list.set(0, 10)
list.sort()
list.sortByDescending { it }
list.sortWith(compareBy({ it }))
list.shuffle()
list.reverse()
list.fill(0)
list.clear()

// MutableMap
val map = mutableMapOf("a" to 1)
map["b"] = 2
map.put("c", 3)
map.putAll(mapOf("d" to 4))
map.remove("a")
map.removeFirst() / .removeLast()
map.computeIfAbsent("a") { 0 }
map.computeIfPresent("a") { _, v -> v + 1 }
map.merge("a", 1) { old, new -> old + new }
map.clear()
```