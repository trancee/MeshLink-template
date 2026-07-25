# Kotlin stdlib — Math & Random

## kotlin.math

### Constants
```kotlin
PI                                     // π ≈ 3.14159...
E                                        // e ≈ 2.71828...
NaN                                   // Not-a-Number
POSITIVE_INFINITY
NEGATIVE_INFINITY
```

### Trigonometric
```kotlin
sin(PI / 2)                            // 1.0
cos(0.0)                               // 1.0
tan(PI / 4)                            // ~1.0
asin(1.0)                              // π/2
acos(0.0)                              // π/2
atan(1.0)                              // π/4
atan2(y = 3.0, x = 4.0)                // atan(3/4), handles quadrants
hypot(x = 3.0, y = 4.0)                // sqrt(3² + 4²) = 5.0
```

### Exponential & Logarithmic
```kotlin
exp(1.0)                               // e¹
expm1(0.01)                            // eˣ - 1 (accurate for small x)
ln(E)                                  // natural log = 1.0
ln(x, base = 10)                        // log base 10 (Kotlin 2.1+)
log(x, 2.0)                            // log base 2
log10(1000.0)                          // 3.0
log2(8.0)                              // 3.0
pow(2.0, 10.0)                         // 2¹⁰ = 1024.0
(2.0).pow(10)                          // same as pow(2.0, 10.0)
sqrt(16.0)                             // 4.0
cbrt(27.0)                             // 3.0 (cube root)
```

### Rounding & Sign
```kotlin
abs(-5)                                // 5
abs(-5.0)                              // 5.0
sign(-5.0)                             // -1.0
sign(0.0)                              // 0.0
sign(5.0)                              // 1.0

floor(3.7)                             // 3.0
ceil(3.2)                              // 4.0
round(3.5)                             // 4.0
roundToInt(3.5)                        // 4
truncate(3.7)                          // 3.0

roundToLong(1234567.5)                 // 1234568L
```

### Min/Max/Clamp
```kotlin
min(3, 5)                              // 3
max(3, 5)                              // 5
minOf(3, 5, 1, 9)                      // 1
maxOf(3, 5, 1, 9)                      // 9
minOfWithOrNull(0, compareBy { it.length })  // null for empty
maxOfWithOrNull(0, compareBy { it.length })
minOfWith(0) { it.length }
maxOfWith(0) { it.length }

// Clamp (Kotlin 1.9+)
3.0.clamp(0.0, 10.0)                  // 3.0
15.0.clamp(0.0, 10.0)                 // 10.0 (clamped)
```

### Floating-Point Utilities
```kotlin
isFinite(1.0)                          // true
isInfinite(1.0/0)                      // true
isNaN(0.0/0)                           // true
ulp(1.0)                               // unit in last place
nextUp(1.0)                            // next representable
nextDown(1.0)                          // previous representable
nextTowards(1.0, 2.0)                  // next toward target
```

### Integer Utilities
```kotlin
5.countOneBits()                         // 2 (population count)
5.countTrailingZeroBits()              // 0
5.countLeadingZeroBits()               // 29
5.rotateLeft(1)                        // 10
5.rotateRight(1)                       // 2 (unsigned rotation)
5.takeHighestOneBit()                  // 4
5.takeLowestOneBit()                   // 1
```

## kotlin.random

### Basic Usage
```kotlin
import kotlin.random.Random

// Default random
val rand = Random.Default

// Random values
rand.nextInt()                           // random Int
rand.nextInt(until = 100)                // 0..99
rand.nextInt(from = 10, until = 100)     // 10..99
rand.nextLong()
rand.nextLong(until = 1000L)
rand.nextDouble()                        // 0.0..1.0
rand.nextDouble(from = 0.0, until = 1.0)
rand.nextFloat()
rand.nextBoolean()
rand.nextBytes(16)                       // ByteArray
rand.nextBits(8)                         // random Int with 8 bits

// Unsigned
rand.nextUInt()
rand.nextULong()
rand.nextUByte()
rand.nextUShort()
```

### Random Collections
```kotlin
rand.nextInts(10)                        // Sequence<Int>
rand.nextLongs(10)
rand.nextBooleans(5)
rand.nextDoubleArray(10)
rand.nextFloatArray(10)

// Shuffle
val list = mutableListOf(1, 2, 3)
rand.shuffle(list)                       // in-place shuffle

// Random from collection
val item = list.random()
val item = list.random(rand)
val items = list.shuffled()              // new shuffled list
```

### Seeded Random
```kotlin
val seeded = Random(42)                  // deterministic
val seed = Random(42).nextInt()          // always same value
```

### SplitMix Random (Kotlin 2.0+)
```kotlin
val split = Random.SplitMix64(42)
split.nextLong()
```

## kotlin.experimental (bit manipulation)

```kotlin
import kotlin.experimental.*

// For UInt/ULong (Kotlin 1.9+)
val flags: UInt = 0u
val withBit = flags.set(index = 2, bitValue = true)
val bit = flags.get(index = 2)
val cleared = flags.clear(index = 2)
```