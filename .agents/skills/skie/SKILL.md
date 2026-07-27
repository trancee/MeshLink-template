---
name: skie
description: SKIE (Touchlab) reference — Kotlin Native compiler plugin improving Swift interop for KMP. Restores language features lost in Kotlin→ObjC→Swift bridge — exhaustive enums, sealed classes with onEnum(of:), default arguments, global functions without FileKt, suspend as Swift async with cancellation, Flows as AsyncSequence. Preview features (Flows in SwiftUI, Combine bridge). Installation (Gradle plugin co.touchlab.skie). Configuration (skie{} DSL, per-feature annotation/Gradle keys, warning suppression, isEnabled toggle). Distributable framework build config (Swift library evolution, XCFrameworks, debug source paths). Analytics opt-out. Known issues (cinterop framework names, missing Foundation import, lambda type args, Gradle cache). Migration guide. Compatibility (Kotlin 2.0.0–2.4.10, Swift 5.8+/Xcode 14.3+). Use when configuring SKIE, consuming Kotlin types from Swift, migrating to SKIE, or any SKIE question.
---

<objective>
Provide comprehensive, accurate reference for the SKIE compiler plugin — everything needed to install, configure, use, and troubleshoot SKIE in a Kotlin Multiplatform project targeting iOS/macOS via Swift.
</objective>

<overview>
SKIE (pronounced "sky") is a Kotlin Native compiler plugin by Touchlab that improves Swift interop for Kotlin Multiplatform. Without SKIE, Kotlin communicates with Swift only through Objective-C, losing many language features. SKIE modifies the Xcode Framework produced by the Kotlin compiler to restore these features. It requires no changes to how you distribute or consume KMP frameworks.

- **Current version:** 0.10.14 (released July 27, 2026)
- **Kotlin compatibility:** 2.0.0 through 2.4.10
- **Swift compatibility:** 5.8+ (Xcode 14.3+)
- **Gradle plugin ID:** `co.touchlab.skie`
- **Configuration annotations:** `co.touchlab.skie:configuration-annotations:0.10.14`
- **Analytics:** SKIE collects non-identifying analytics by default (see `<analytics>`); opt-out is supported.
</overview>

<installation>
**Step 1:** Locate the KMP module that creates Xcode Frameworks (has `kotlin("native.cocoapods")` plugin or a `framework` block inside the `kotlin` configuration).

**Step 2:** Add the SKIE Gradle plugin:

```kotlin
// build.gradle.kts
plugins {
    id("co.touchlab.skie") version "0.10.14"
}
```

The plugin only needs to be applied in the module that creates Xcode Frameworks. SKIE will instrument all code exported in that Framework, including exported dependencies.

Ensure `mavenCentral()` is in your plugin repositories (settings.gradle.kts):
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

**Gradle cache issue:** If Gradle fails to resolve SKIE artifacts after a new release, run `./gradlew dependencies --refresh-dependencies`.

**Step 3:** For existing projects, read the migration notes before building. For new projects, build your Xcode Framework and start using SKIE features.
</installation>

<features>

## Enums — Exhaustive Switching

SKIE generates wrapping Swift enums for Kotlin enums, enabling exhaustive `switch` without `default`. The original Kotlin enum is still available prefixed with `__` (e.g., `__Turn`).

**Kotlin:**
```kotlin
enum class Turn { Left, Right }
```

**Swift with SKIE:**
```swift
func changeDirection(turn: Turn) {
    switch turn {
    case .left:  goLeft()
    case .right: goRight()
    }
}
```

**Case naming:** SKIE uses a sophisticated algorithm supporting both UPPER_SNAKE_CASE and PascalCase (Kotlin's default only handles UPPER_SNAKE_CASE). Cases colliding with Swift keywords get a `the` prefix (e.g., `zone` → `theZone`).

**Conversion methods:**
- `turn.toKotlinEnum()` → `__Turn`
- `kotlinEnum.toSwiftEnum()` → `Turn`
- `turn as __Turn` / `kotlinEnum as Turn` (casting works both ways)

**Built-in properties preserved:**
- `name` — returns the Kotlin case name
- `ordinal` — same as Kotlin
- `values()` → replaced by `allCases` (CaseIterable conformance)
- `valueOf(String)` — use `__Turn.valueOf(String)` and convert with `toSwiftEnum()`

**Limitations:**
- Swift enums cannot implement Obj-C protocols, so Kotlin enum interfaces are not carried over. Use `toKotlinEnum()` to pass to functions expecting the interface type.
- Enums in generics: Obj-C generics require class types, so `ResultWrapper<Turn>` becomes `ResultWrapper<__Turn>` in Swift. Use `toSwiftEnum()` on the value.
</features>

<features-sealed>

## Sealed Classes — onEnum(of:)

SKIE generates a wrapping Swift enum for Kotlin sealed classes/interfaces, plus a global `onEnum(of:)` function for conversion.

**Kotlin:**
```kotlin
sealed class Status {
    object Loading : Status()
    data class Error(val message: String) : Status()
    data class Success(val result: SomeData) : Status()
}
```

**Swift with SKIE:**
```swift
func updateStatus(status: Status) {
    switch onEnum(of: status) {
    case .loading:
        showLoading()
    case .error(let data):
        showError(message: data.message)
    case .success(let data):
        showResult(data: data.result)
    }
}
```

**Optional sealed class:** An overload of `onEnum(of:)` accepts an optional, adding a `.none` case.

**Hidden subclasses:** If some subclasses are `internal`/`private`, SKIE generates an `.else` case to handle them.

**Hashable:** SKIE adds `Hashable` conformance to the generated enum when all exposed direct children of the sealed type are classes. Sealed interfaces with interface children require manual `Hashable` implementation via Swift extensions on the generated enum.

**Migration:** This feature should not cause breaking changes.
</features-sealed>

<features-functions>

## Default Arguments

SKIE generates Kotlin overloads that simulate default arguments (since Obj-C has no equivalent).

**Disabled by default** — enable selectively via annotation configuration:

```kotlin
import co.touchlab.skie.configuration.annotations.DefaultArgumentInterop

@DefaultArgumentInterop.Enabled
fun sayHello(message: String = "Hello") {
    println(message)
}
```

Add the annotations dependency:
```kotlin
val commonMain by sourceSets.getting {
    dependencies {
        implementation("co.touchlab.skie:configuration-annotations:0.10.14")
    }
}
```

**Limitations:**
- Generates O(2^n) overloads where n = number of default arguments, capped at 5 by default (max 31 overloads). Raise the cap per-declaration with `@DefaultArgumentInterop.MaximumDefaultArgumentCount(n)` — each extra argument doubles the overload count, so raising it risks long compiles or compiler OOM.
- Does not support interface methods.
- Disabled for 3rd-party library functions by default. Enable via the Gradle `defaultArgumentsInExternalLibraries` flag, then opt in per-declaration — this disables Kotlin native compiler caching for those libraries, which can significantly increase compilation time.
</features-functions>

<features-global>

## Global Functions and Properties

SKIE generates actual global Swift functions, eliminating the `FileKt.` namespace prefix.

**Without SKIE:** `FileKt.globalFunction(i: 1)`
**With SKIE:** `globalFunction(i: 1)`

Original namespaced functions remain available for backward compatibility.
</features-global>

<features-interface-ext>

## Interface Extensions

Interface extension functions become member-style calls instead of static calls.

**Kotlin:**
```kotlin
interface I
class C : I
fun I.interfaceExtension(i: Int): Int = i
```

**Without SKIE:** `FileKt.interfaceExtension(C(), i: 1)`
**With SKIE:** `C().interfaceExtension(i: 1)`
</features-interface-ext>

<features-overloads>

## Overloaded Functions

SKIE preserves original function names for overloads that Kotlin would normally rename with `_` suffix for Obj-C compatibility.

**Without SKIE:** `foo(i: 1)` and `foo(i_: "A")`
**With SKIE:** `foo(i: 1)` and `foo(i: "A")`
</features-overloads>

<features-suspend>

## Suspend Functions — Proper Swift Async

SKIE generates real Swift async functions with a custom runtime bridging Kotlin Coroutines and Swift Concurrency.

**Key improvements over vanilla Kotlin:**
- Two-way cancellation: canceling a Swift `Task` cancels the Kotlin coroutine, and vice versa
- No main-thread restriction: call suspend functions from any thread
- Kotlin `CancellationException` maps to Swift `CancellationError`

**Kotlin:**
```kotlin
class ChatRoom {
    suspend fun send(message: String) { /* ... */ }
}
```

**Swift with SKIE:**
```swift
let chatRoom = ChatRoom()
let task = Task.detached {
    try? await chatRoom.send(message: "some message")
}
task.cancel() // Also cancels the Kotlin coroutine
```

**Generic classes:** Use the `skie()` wrapper for member/extension suspend functions of generic classes:
```swift
let a = A<NSString>()
try await skie(a).foo()
```

**Overriding suspend functions:** Override the `__`-prefixed version in Swift subclasses:
```swift
class B: A {
    override func __foo() async throws -> KotlinInt {
        return KotlinInt(1)
    }
}
```
Note: calls from the overridden function to other async functions lose cancellation bridging.

**Migration note:** SKIE changes threading semantics — Swift 5.7+ runs async functions on background threads by default, while Kotlin Coroutines stay on the calling thread. Add explicit thread switching in suspend functions if your code depends on running on the main thread.
</features-suspend>

<features-flows>

## Flows — AsyncSequence

SKIE converts Kotlin Flows to Swift classes implementing `AsyncSequence`, with preserved generics and two-way cancellation.

**Supported Flow types and their Swift equivalents:**
- `Flow` → `SkieSwiftFlow`
- `SharedFlow` → `SkieSwiftSharedFlow`
- `MutableSharedFlow` → `SkieSwiftMutableSharedFlow`
- `StateFlow` → `SkieSwiftStateFlow`
- `MutableStateFlow` → `SkieSwiftMutableStateFlow`

**Kotlin:**
```kotlin
class ChatRoom {
    val messages: StateFlow<List<String>> = MutableStateFlow(emptyList())
}
```

**Swift with SKIE:**
```swift
class ChatRoomViewModel: ObservableObject {
    let chatRoom = ChatRoom()
    @Published private(set) var messages: [String] = []

    @MainActor
    func activate() async {
        for await messages in chatRoom.messages {
            self.messages = messages // No type cast needed
        }
    }
}
```

**Cancellation:** Flow cancellation from Kotlin ends the Swift `for await` loop (consistent with `AsyncSequence` semantics). Use `withTaskCancellationHandler` if you need explicit cancellation handling.

**Type bridging:** Kotlin `String` in Flow generics becomes Swift `String` (not `NSString`), because SKIE's custom classes aren't constrained by Obj-C's `AnyObject` requirement.

**Nullable type arguments:** `Flow<Int?>` maps to `SkieSwiftOptionalFlow<Int>` (separate class hierarchy from non-optional variants). Convert between them using conversion constructors.

**Limitations:**
- Custom exceptions in Flow cause runtime crash (cannot propagate to Swift)
- Type casting (`as!`, `as?`, `is`) on `SkieKotlin___Flow` is unsafe — use conversion constructors instead
- `SkieSwift___Flow` classes do not inherit from each other
- Flows inside generics (`List<Flow<*>>`, `Map<*, Flow<*>>`, `Flow<Flow<*>>`) and return types of SKIE-generated suspend functions are not auto-converted — use manual conversion: `listOfFlows.map { SkieSwiftFlow(SkieKotlinFlow<KotlinInt>($0)) }`
- Custom Flow types not supported
- No `AsyncSequence` → `Flow` conversion
</features-flows>

<features-swift-bundling>

## Swift Code Bundling

Bundle hand-written Swift code into the Kotlin framework alongside SKIE-generated code.

**Source set locations** (derived from Kotlin source sets):
- `src/commonMain/kotlin` → `src/commonMain/swift`
- `src/iosArm64Main/kotlin` → `src/iosArm64Main/swift`
- `src/macosArm64Main/kotlin` → `src/macosArm64Main/swift`
- `src/${kotlinSourceSetName}/kotlin` → `src/${kotlinSourceSetName}/swift`

Swift source sets follow the Kotlin hierarchy and are only created in the module where SKIE is applied.

**Important:** Swift defaults to `internal` visibility — use `public` explicitly for declarations that need to be visible outside the framework.

The bundled Swift code shares the same Framework module as Kotlin code, so no import is needed to call Kotlin APIs.
</features-swift-bundling>

<preview-features>

## Flows in SwiftUI (Preview)

Enable in Gradle:
```kotlin
skie {
    features {
        enableSwiftUIObservingPreview = true
    }
}
```

**`Observing` view** — observe one or more Flows directly in SwiftUI:
```swift
// With StateFlow or Flow + initial value
Observing(viewModel.counter.withInitialValue(0), viewModel.toggle) { counter, toggle in
    Text("Counter: \(counter), Toggle: \(toggle)")
}

// With initial content view (for non-StateFlow flows)
Observing(viewModel.counter, viewModel.toggle) {
    ProgressView("Waiting...")
} content: { counter, toggle in
    Text("Counter: \(counter), Toggle: \(toggle)")
}
```

**`collect` view modifier** — collect a Flow into a `@State` property:
```swift
@State var counter: KotlinInt = 0

Text("Counter: \(counter)")
    .collect(flow: viewModel.counter, into: $counter)

// Or with async closure for custom processing:
Text("Counter: \(manualCounter)")
    .collect(flow: viewModel.counter) { latestValue in
        manualCounter = latestValue.intValue
    }
```

**v0.10.14+**: Support for animations when using `Observing` SwiftUI view.
</preview-features>

<preview-combine>

## Combine Bridge (Preview)

**Suspend function → `Combine.Future`:**
```kotlin
skie {
    features {
        enableFutureCombineExtensionPreview = true
    }
}
```
```swift
let future = Future(async: helloWorld)
future.sink { error in /* handle */ } receiveValue: { value in print(value) }
```
Note: Futures are hot and invoke immediately. Store the cancellable returned by `sink` to prevent immediate cancellation. Futures don't support cancellation.

**Flow → `Combine.Publisher`:**
```kotlin
skie {
    features {
        enableFlowCombineConvertorPreview = true
    }
}
```
```swift
let publisher = helloWorld().toPublisher()
publisher.sink { value in /* each emitted value */ }
```
Store the cancellable returned by `sink`.
</preview-combine>

<configuration>

## Gradle Configuration

The `skie {}` extension in `build.gradle.kts` configures features globally or selectively.

**Disable a feature globally:**
```kotlin
import co.touchlab.skie.configuration.FlowInterop

skie {
    features {
        group {
            FlowInterop.Enabled(false)
        }
    }
}
```
This sets the default behavior, which can still be overridden by annotations.

**Selective by package prefix:**
```kotlin
skie {
    features {
        group {
            FlowInterop.Enabled(false) // override default for whole project
        }
        group("co.touchlab.skie.types") {
            FlowInterop.Enabled(true) // enable only in this package
        }
    }
}
```
Ordering matters: the last matching occurrence of a given configuration is used.

**Disable entire SKIE:**
```kotlin
skie {
    isEnabled.set(false)
}
```
Useful for debugging or evaluating differences.

## Annotation Configuration

Annotations allow configuring SKIE directly in source code. Add the dependency:
```kotlin
val commonMain by sourceSets.getting {
    dependencies {
        implementation("co.touchlab.skie:configuration-annotations:0.10.14")
    }
}
```

**Examples:**
```kotlin
import co.touchlab.skie.configuration.annotations.FlowInterop

@FlowInterop.Enabled
fun enabledFlow(): Flow<Int> = flowOf(1)

@FlowInterop.Disabled
fun disabledFlow(): Flow<Int> = flowOf(1)
```

## Combining Gradle and Annotation Configuration

By default, annotations always override Gradle configuration. Change this per-group with `overridesAnnotations = true`:
```kotlin
skie {
    features {
        group("co.touchlab.skie.types", overridesAnnotations = true) {
            FlowInterop.Enabled(false)
        }
    }
}
```
Here, Flow interop is disabled in the whole package, even if some declarations have `@FlowInterop.Enabled`.
</configuration>

<analytics>

## Analytics

SKIE collects non-identifying analytics to improve the tool. Optional — you can opt out completely or partially.

**Disable upload only** (inspect captured data in `build/skie/{framework}/{architecture}/analytics`):
```kotlin
skie {
    analytics {
        disableUpload.set(true)
    }
}
```

**Disable entirely:**
```kotlin
skie {
    analytics {
        enabled.set(false)
    }
}
```

**Detailed configuration** — enable/disable specific categories via `additionalConfigurationFlags` / `suppressedConfigurationFlags`:
```kotlin
// Send only SKIE Performance analytics
skie {
    analytics {
        enabled.set(false)
    }
    additionalConfigurationFlags.add(SkieConfigurationFlag.Analytics_SkiePerformance)
}

// Send all except Git statistics
skie {
    suppressedConfigurationFlags.add(SkieConfigurationFlag.Analytics_Git)
}
```

**Data categories collected:**
- **SKIE Performance** — phase timing measurements
- **Gradle Environment** — Gradle/Kotlin/AGP versions, CI detection, JVM/macOS versions
- **Gradle Performance** — link task duration
- **Project** — hashed project ID
- **Modules** — module hashes, versions, export status, declaration statistics (exported/exportable/non-exportable/overridden/IR elements)
- **Hardware** — CPU type/model, processor count, RAM
- **Git Statistics** — commit/tag/contributor/branch counts
- **Compiler Configuration** — Kotlin language/API versions, linker args, debug/static framework, Obj-C generics, memory model
- **Compiler Environment** — JVM/Kotlin Compiler/Xcode versions, available processors, max JVM memory
- **SKIE Configuration** — enabled/disabled features
</analytics>

<known-issues>

## Known Issues and Limitations

| Issue | Description |
|-------|-------------|
| **Gradle caching issue** | Gradle sometimes fails to resolve SKIE artifacts and caches the 404. Fix: `./gradlew dependencies --refresh-dependencies`. If persistent >24h, file an issue. |
| **Missing Foundation classes** | Kotlin/Native transitively exports Foundation; SKIE may change this behavior. Add explicit `import Foundation` in Swift if needed. |
| **Lambda type as type argument** | SKIE cannot generate Swift code containing types with lambdas used as generic type arguments for Kotlin classes. |
| **Cinterop** | SKIE cannot directly generate code that uses types from custom cinterop bindings. |
</known-issues>

<compatibility>

## Compatibility Matrix

| Component | Supported Versions |
|-----------|-------------------|
| **Kotlin** | 2.0.0 – 2.4.10 |
| **Swift** | 5.8+ (Xcode 14.3+) |
| **Gradle** | 7.5+ (tested with latest) |
| **AGP** | 7.4+ (if Android module present) |

**Note:** Minimum officially supported Swift version will increase over time. Plan to support new Swift versions for at least a year after release.
</compatibility>

<migration>

## Migration Guide

Migrating existing projects requires Swift code changes (Kotlin code should not need changes). SKIE is fully configurable, enabling iterative migration.

1. Read the [SKIE features documentation](https://skie.touchlab.co/features) for each feature you want to use.
2. Check the [Migration documentation](https://skie.touchlab.co/migration) and [Touchlab's SKIE Migration Guide](https://touchlab.co/skie-migration) for a customized migration plan.
3. For large projects/teams: prepare a migration plan and ensure all developers are familiar with SKIE.
4. Install SKIE (see [Installation](#installation)) and build — fix compilation errors iteratively.
5. Touchlab offers consulting services for migration assistance.
</migration>

<changelog>

## Recent Changelog Highlights

**0.10.14** (Jul 27, 2026)
- Support for Kotlin 2.4.10
- Support for animations when using `Observing` SwiftUI view
- Fix Gradle Configuration Cache and isolated projects
- Support for preserving documentation from original Kotlin declarations into generated Swift code

**0.10.13** — Improvements
**0.10.12** — Improvements
**0.10.11** — Improvements
**0.10.10** — Improvements
**0.10.9** — Improvements
**0.10.8** — Fixes
**0.10.7** — Improvements
**0.10.6** — Improvements
**0.10.5** — Improvements
**0.10.4** — Improvements
**0.10.0** — New features (major feature release)

See [Changelog](https://skie.touchlab.co/category/changelog) for full history.
</changelog>

<distributable-frameworks>

## Distributable Framework Build Configuration

When building XCFrameworks for distribution, configure SKIE for Swift library evolution and debug source paths:

```kotlin
skie {
    // Enable Swift library evolution support for distributable frameworks
    swiftLibraryEvolution.set(true)

    // Configure XCFramework output
    xcframework {
        // Include debug symbols / source paths
        debugSourcePaths.set(true)
    }
}
```

This ensures the generated Swift code is compatible with Swift's library evolution mode and that consumers can debug into the framework.
</distributable-frameworks>