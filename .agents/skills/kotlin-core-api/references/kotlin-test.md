# Kotlin Test (kotlin.test)

The `kotlin.test` library provides multiplatform testing assertions and annotations. The same test source compiles to JUnit 4, JUnit 5, or TestNG based on runner dependency.

## Assertions

### Equality
```kotlin
import kotlin.test.*

assertEquals(expected, actual)              // structural equality
assertEquals(expected, actual, "message")
assertNotEquals(expected, actual)
assertSame(expected, actual)                // referential equality (===)
assertNotSame(expected, actual)
assertEquals(3.14, value, absoluteTolerance = 0.01)  // double tolerance
assertEquals(3.14, value, absoluteTolerance = 0.01, "message")
```

### Boolean Checks
```kotlin
assertTrue(condition)
assertTrue(condition, "message")
assertFalse(condition)
assertFalse(condition, "message")
```

### Null Checks
```kotlin
assertNull(value)
assertNull(value, "message")
assertNotNull(value)
assertNotNull(value, "message")
```

### Numeric
```kotlin
assertEquals(5, value)
assertNotEquals(0, value)
assertEquals(0.0, value, 0.001)           // tolerance
```

### Exception Testing
```kotlin
assertFails {
    throw IllegalArgumentException("bad")
}                                            // catches any exception

val ex = assertFails {
    throw IllegalArgumentException("bad")
}
assertEquals("bad", ex.message)

val specific = assertFailsWith<IllegalArgumentException> {
    throw IllegalArgumentException("bad")
}

assertEquals("bad", specific.message)

// Multiple exception types
assertFailsWith(IllegalArgumentException::class, ArithmeticException::class) {
    throw IllegalArgumentException()
}
```

### Collection Assertions
```kotlin
val list = listOf(1, 2, 3)

assertEquals(3, list.size)
assertTrue(list.contains(2))
assertContentEquals(listOf(1, 2, 3), list)      // element-wise
assertContentEquals(arrayOf(1, 2, 3), array)    // for arrays
```

### Custom Assertions
```kotlin
fun <T> assertIsSorted(list: List<T>, comparator: Comparator<T>) {
    assertTrue(list.zipWithNext().all { comparator.compare(it.first, it.second) <= 0 }) {
        "List is not sorted: $list"
    }
}
```

## Test Annotations

### Basic
```kotlin
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlin.test.BeforeClass
import kotlin.test.AfterClass
import kotlin.test.Ignore

class MyTest {
    @BeforeTest
    fun setUp() {
        // runs before each test
    }

    @AfterTest
    fun tearDown() {
        // runs after each test
    }

    @Test
    fun testSomething() {
        assertEquals(4, 2 + 2)
    }

    @Ignore("Not implemented yet")
    @Test
    fun testIgnored() {
        // will be ignored
    }
}
```

### Class-level Lifecycle (JVM)
```kotlin
companion object {
    @BeforeClass
    @JvmStatic
    fun beforeAll() {
        // runs once before all tests
    }

    @AfterClass
    @JvmStatic
    fun afterAll() {
        // runs once after all tests
    }
}
```

### Ignored Tests
```kotlin
@Ignore("Reason for ignoring")
class IgnoredTestSuite {
    @Test
    fun test() { }
}

// Individual
@Test
@Ignore
fun ignoredTest() { }
```

## Runner Integration

### Gradle Setup

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test.junit5"))
            }
        }
        iosTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

// For JVM tests, specify runner
tasks.test {
    useJUnitPlatform()  // for junit5
}
```

### JUnit 4
```kotlin
// testImplementation(kotlin("test.junit"))
// testImplementation("junit:junit:4.13.2")
tasks.test {
    useJUnit()
}
```

### JUnit 5
```kotlin
// testImplementation(kotlin("test.junit5"))
// testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
tasks.test {
    useJUnitPlatform()
}
```

### TestNG
```kotlin
// testImplementation(kotlin("test.testng"))
// testImplementation("org.testng:testng:7.10.2")
tasks.test {
    useTestNG()
}
```

## Multiplatform Testing

```kotlin
// commonTest source set works on all platforms
// The same @Test functions run with platform-specific runner

expect fun platformSpecificTest(): String

class CommonTest {
    @Test
    fun testCommon() {
        assertEquals(4, 2 + 2)
    }
    
    @Test
    fun testPlatformSpecific() {
        assertEquals("expected", platformSpecificTest())
    }
}

// jvmTest
actual fun platformSpecificTest(): String = "jvm value"

// iosTest
actual fun platformSpecificTest(): String = "ios value"
```

## Custom Assertion Helpers

```kotlin
fun <T> assertAllElementsMatch(list: List<T>, predicate: (T) -> Boolean) {
    assertTrue(list.all(predicate), "Not all elements match: $list")
}

fun assertIsSorted(vararg values: Int) {
    assertTrue(values.toList() == values.sorted())
}
```