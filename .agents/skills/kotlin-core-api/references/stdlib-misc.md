# Kotlin stdlib — Annotations, Comparisons, Properties, System

## kotlin.annotation

### Annotation Targets
```kotlin
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FIELD,
    AnnotationTarget.LOCAL_VARIABLE,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPE,
    AnnotationTarget.EXPRESSION
)
```

### Retention
```kotlin
@Retention(AnnotationRetention.SOURCE)   // not in bytecode
@Retention(AnnotationRetention.BINARY)   // in bytecode, not visible at runtime
@Retention(AnnotationRetention.RUNTIME)  // in bytecode, visible at runtime
```

### Built-in Annotations
```kotlin
@Deprecated("Use newMethod()", ReplaceWith("newMethod()"))
@Deprecated("Use newMethod()", ReplaceWith("newMethod()", Import("com.example.newMethod")))
@DeprecatedSinceKotlin("1.5")
@RequiresOptIn("Experimental", RequiresOptIn.Level.WARNING)
@OptIn(ExperimentalStdlibApi::class)
@Suppress("UNCHECKED_CAST")
@PublishedApi
@MustBeDocumented
@Repeatable
@Strictfp
@Throws(IOException::class)
@Transient
```

### JVM-specific
```kotlin
@JvmName("customName")
@JvmStatic
@JvmOverloads
@JvmField
@JvmSynthetic
@JvmMultifileClass
@JvmWildcard
@JvmSuppressWildcards
```

## kotlin.comparisons

```kotlin
import kotlin.comparisons.*

// Comparators
compareBy<String> { it.length }
compareBy({ it.length }, { it })         // then compare by content
compareByDescending<String> { it.length }

// Null handling
nullsFirst<String>()                     // nulls first
nullsLast<String>()                      // nulls last

// Order utilities
naturalOrder<String>()
reverseOrder<String>()
reverseOrder<String>(compareBy { it })

// Combining
list.sortedWith(compareBy({ it }).thenBy({ -it }))
```

## kotlin.properties

### Delegates
```kotlin
import kotlin.properties.Delegates
import kotlin.properties.ReadOnlyProperty

// Observable
var name: String by Delegates.observable("<no name>") { prop, old, new ->
    println("${prop.name}: $old -> $new")
}

// Vetoable
var score: Int by Delegates.vetoable(0) { _, old, new ->
    new >= 0 && new <= 100
}

// Not-null
var later: String by Delegates.notNull<String>()

// Readonly delegate
fun <T> customDelegate(producer: () -> T): ReadOnlyProperty<Any?, T> =
    ReadOnlyProperty { _, _ -> producer() }

val computed: String by customDelegate { calculate() }
```

### Extension property delegates
```kotlin
operator fun <T> ReadOnlyProperty<Any?, T>.provideDelegate(
    thisRef: Any?,
    prop: KProperty<*>
): ReadOnlyProperty<Any?, T>

operator fun <T> MutablePropertyDelegate<Any?, T>.provideDelegate(
    thisRef: Any?,
    prop: KProperty<*>
): MutablePropertyDelegate<Any?, T>
```

## kotlin.system

```kotlin
import kotlin.system.*

// Time measurement
val timeMs = measureTimeMillis {
    // code block
}

// Kotlin 2.0+
val duration = measureTime {
    // code block
}  // kotlin.time.Duration

// Exit
exitProcess(0)

// Memory
val bytes = Runtime.getRuntime().totalMemory()
```

## kotlin.concurrent

### JVM Concurrency
```kotlin
import kotlin.concurrent.thread

thread(start = true, isDaemon = false, priority = 5, name = "worker") {
    // background work
}

// Thread-local
val threadLocal = ThreadLocal<String>()
```

### Atomics (Kotlin 2.0+)
```kotlin
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference

val counter = AtomicInt(0)
counter.incrementAndGet()
counter.decrementAndGet()
counter.getAndIncrement()
counter.compareAndSet(expected = 0, new = 1)
counter.get()

val ref = AtomicReference<String?>(null)
ref.set("value")
ref.get()
ref.compareAndSet(null, "new")
```

## kotlin.experimental

### Bit Flags (for UInt/ULong)
```kotlin
import kotlin.experimental.*

val flags: UInt = 0u
flags.set(index = 2, bitValue = true)
flags.get(index = 2)                     // true/false
flags.clear(index = 2)
```

## kotlin.io.encoding (Kotlin 2.0+)

```kotlin
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
val encoded = Base64.encode("Hello".encodeToByteArray())
@OptIn(ExperimentalEncodingApi::class)
val decoded = Base64.decode(encoded).decodeToString()

// Format
Base64.encode(byteArray)
Base64.decode(string)
```

## Platform-Specific Extensions

### kotlin.js (JS)
```kotlin
import kotlin.js.*

// Promise
Promise { resolve, reject ->
    resolve("OK")
}

// JSON
val json = JSON.parse("""{"a": 1}""")
val str = JSON.stringify(json)
```

### kotlin.native (Native)
```kotlin
import kotlin.native.concurrent.freeze

val obj = MyObject()
obj.freeze()                             // makes immutable and shareable
```

### kotlin.wasm (Wasm)
```kotlin
import kotlin.wasm.*

// Unsafe operations
// Experimental, use with caution
```