# Kotlin Reflection (kotlin.reflect)

The `kotlin.reflect` library provides runtime reflection for Kotlin types. **It is a separate dependency from `kotlin-stdlib`.**

## Setup

```kotlin
// build.gradle.kts
dependencies {
    implementation(kotlin("reflect"))
}
```

## KClass — Type Metadata

```kotlin
import kotlin.reflect.KClass
import kotlin.reflect.full.*

val kClass: KClass<MyClass> = MyClass::class
val kClass2 = String::class
val kClass3: KClass<*> = MyClass::class

// Basic properties
kClass simpleName           // "MyClass"
kClass qualifiedName          // "com.example.MyClass"
kClass isAbstract             // Boolean
kClass isSealed               // Boolean
kClass isData                 // Boolean
kClass isOpen                 // Boolean
kClass isCompanion           // Boolean
kClass isInner                // Boolean
kClass isFun                 // Boolean
kClass isValue               // Kotlin 2.0+ inline class

// Constructors
kClass constructors         // Collection<KFunction<T>>
kClass primaryConstructor    // KFunction<T>?
val instance = kClass.createInstance()    // T or throws

// Supertypes
kClass supertypes           // List<KType>
kClass allSuperclasses      // Set<KClass<*>>
kClass allSupertypes         // Set<KType>
kClass isSubclassOf(other)  // Boolean
kClass isSuperclassOf(other)  // Boolean
kClass isSubtypeOf(type)    // Boolean
kClass isSupertypeOf(type)  // Boolean

// Members
kClass memberFunctions        // Collection<KFunction<*>> (incl. inherited)
kClass declaredMemberFunctions  // only declared
kClass memberProperties     // Collection<KProperty<*>>
kClass declaredMemberProperties
kClass memberExtensionFunctions
kClass staticFunctions        // static methods (JVM)
kClass staticProperties       // static properties (JVM)

// Annotations
kClass hasAnnotation<Deprecated>()  // Boolean
kClass findAnnotation<MyAnnotation>()  // MyAnnotation?
kClass findAnnotations<MyAnnotation>()  // List<MyAnnotation>
```

## KFunction — Callable Functions

```kotlin
import kotlin.reflect.KFunction

val func: KFunction<*> = MyClass::myMethod

// Call
func.call(instance, arg1, arg2)           // Any? (throws if wrong arg count/types)
func.callBy(mapOf(instance to instance, param to value))

// Information
func name                        // "myMethod"
func returnType                  // KType
func parameters                  // List<KParameter>
func extensionReceiverParameter    // KParameter?
func instanceParameter           // KParameter? (for member functions)
func valueParameters             // parameters (excluding receiver)
func isSuspend                   // Boolean
func isAbstract
func isOpen
func isFinal
```

## KParameter — Function Parameters

```kotlin
import kotlin.reflect.KParameter

val param: KParameter = func.parameters[0]

param name                     // "paramName"
param kind                     // INSTANCE, EXTENSION_RECEIVER, VALUE
param type                     // KType
param isOptional               // Boolean
param isVararg                 // Boolean
```

## KProperty — Properties

```kotlin
import kotlin.reflect.KProperty

// Property reference
val prop = MyClass::myProperty   // KProperty<...>

// Read
prop.get(instance)               // Any?
val value: Int = prop get instance

// Write (mutable)
val mutable = MyClass::myMutable  // KMutableProperty<...>
val mutable = MyClass::myMutableProperty
mutable.set(instance, newValue)
mutable.setter.call(instance, newValue)
myMutableProperty set instance to newValue

// Information
prop name
prop returnType
prop getter
prop setter                      // KProperty.Setter? (null if read-only)
prop isAbstract
prop isOpen
prop isFinal
prop isLateinit                // Boolean
prop isConst                   // compile-time constant
prop extensionReceiverParameter
```

## KType — Type Information

```kotlin
import kotlin.reflect.KType

val type: KType = String::class.createType()
val nullable: KType = String::class.createType(nullable = true)

val listType = List::class.createType(
    arguments = listOf(
        typeProjection(String::class.createType())
    )
)

type classifier                 // KClass<*>?
type arguments                  // List<TypeProjection>
type isMarkedNullable           // Boolean
type toString()                 // "kotlin.String" or "kotlin.String?"
```

## TypeProjection & Variance

```kotlin
// Type projections
typeProjection(String::class.createType(), Variance.INVARIANT)
typeProjection(String::class.createType(), Variance.COVARIANT)  // out
typeProjection(String::class.createType(), Variance.CONTRAVARIANT)  // in

// Star projection
val star = List::class.starProjectedType
```

## Annotations on Reflected Elements

```kotlin
// Check annotation presence
kClass hasAnnotation<Deprecated>()
func hasAnnotation<MyAnnotation>()
prop hasAnnotation<MyAnnotation>()

// Get annotation
kClass findAnnotation<Deprecated>()
func findAnnotation<MyAnnotation>()
prop findAnnotation<MyAnnotation>()

// Get all annotations
kClass findAnnotations<Deprecated>()
```

## JVM Extensions (kotlin.reflect.jvm)

```kotlin
import kotlin.reflect.jvm.*

// KClass to Java
val javaClass: Class<*> = String::class.java
val javaClass = kClass.java

// KFunction to Java
val method = String::length.javaMethod
method?.isAccessible = true

// KProperty to Java
val field = MyClass::myProperty.javaField
val getter = MyClass::myProperty.javaGetter
val setter = MyClass::myMutableProperty.javaSetter

// Extension function
val ext = MyClass::myExtension.javaMethod
myExtension.javaMethod
```

## Reflection Utilities

```kotlin
// Cast safely
val instance: Any = "hello"
val str: String = kClass.safeCast(instance)  // String? (null if wrong type)

// Unsafe cast
val str: String = kClass.cast(instance)      // throws if wrong type

// Call by reflection
val func = String::toUpperCase
func.call("hello")                           // "HELLO"

// Get delegate
val delegate = MyClass::delegatedProperty.getDelegate(instance)

// Companion object
val companion = MyClass::class.companionObject
val companionInstance = MyClass::class.companionObjectInstance
```

## Context Parameters (Kotlin 2.4+)

```kotlin
// Check context parameters
val kClass = MyContextDependentClass::class
kClass.contextParameters              // List<KType>
```

## Performance Notes

- Reflection is **slow** — avoid in hot paths
- Use `kClass.memberFunctions.first { }` instead of repeated lookups
- For serialization, prefer `kotlinx.serialization`
- For compile-time metadata, use `kotlinx.metadata-jvm`