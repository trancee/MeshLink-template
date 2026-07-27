# Diagnostic Callback Threading Model

**Status:** Locked — 2026-07-26

## Decision

**All diagnostic callbacks execute on a dedicated `MeshLink` coroutine dispatcher** (IO-limited, not Main thread). This applies to both `eventCallback` and `emitToLog`.

## Threading Architecture

```text
┌────────────────────────────────────────────────────────────┐
│                    MeshLink Core                           │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │  Routing    │  │  Transfer   │  │  Crypto     │  ...    │
│  │  (Dispatch) │  │  (Dispatch) │  │  (Dispatch) │         │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘         │
│         │                │                │                │
│         ▼                ▼                ▼                │
│  ┌─────────────────────────────────────────────────────┐   │
│  │            DiagnosticEmitter (single)               │   │
│  │  - Batches events                                   │   │
│  │  - Forwards to callback on MeshLink dispatcher      │   │
│  └────────────────────┬────────────────────────────────┘   │
│                       │                                    │
│         ┌─────────────┴─────────────┐                      │
│         ▼                           ▼                      │
│  ┌───────────────┐          ┌─────────────────┐            │
│  │ eventCallback │          │  emitToLog      │            │
│  │ (user code)   │          │  (logcat/oslog) │            │
│  └───────────────┘          └─────────────────┘            │
└────────────────────────────────────────────────────────────┘
```

## Dispatcher Configuration

```kotlin
// meshlink/src/commonMain/kotlin/ch/trancee/meshlink/diagnostics/DiagnosticDispatcher.kt

object DiagnosticDispatcher {
    /** 
     * Dedicated dispatcher for diagnostic callbacks.
     * - Limited parallelism (2) to prevent callback overload
     * - Not Main thread (no UI blocking)
     * - Not Default (not competing with CPU-intensive work)
     */
    val dispatcher: CoroutineDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
    
    /** For testing: override with TestDispatcher */
    @VisibleForTesting
    var testOverride: CoroutineDispatcher? = null
    
    val effective: CoroutineDispatcher
        get() = testOverride ?: dispatcher
}
```

## Callback Signature

```kotlin
// meshlink/src/commonMain/kotlin/ch/trancee/meshlink/MeshLinkSettings.kt

data class MeshLinkSettings(
    // ... existing fields ...
    
    /** 
     * Callback for diagnostic events. 
     * 
     * EXECUTION: On `DiagnosticDispatcher.dispatcher` (background thread).
     * DO NOT: Perform blocking I/O, long computations, or UI operations directly.
     * DO: Forward to your own dispatcher, log, send to analytics, update UI via Main.
     * 
     * @param event The diagnostic event (sealed hierarchy, see DiagnosticEvent.kt)
     */
    val eventCallback: ((DiagnosticEvent) -> Unit)? = null,
    
    /** 
     * Also emit events to platform log (logcat on Android, os_log on iOS).
     * Default: false (opt-in for production).
     */
    val emitToLog: Boolean = false,
)
```

## User Code Guidelines

### ✅ Correct Usage

```kotlin
// Forward to your own dispatcher
val settings = MeshLinkSettings(
    eventCallback = { event ->
        // Option 1: Forward to your logging dispatcher
        myLoggingDispatcher.launch { 
            logger.log(event) 
        }
        
        // Option 2: Update UI via Main dispatcher
        if (event is DiagnosticEvent.TransferSessionTransitionEvent) {
            mainDispatcher.launch { 
                uiState.updateTransferProgress(event) 
            }
        }
        
        // Option 3: Send to analytics (non-blocking)
        analyticsDispatcher.launch { 
            analytics.track(event) 
        }
    }
)
```

### ❌ Incorrect Usage

```kotlin
// DON'T: Block on the diagnostic dispatcher
eventCallback = { event ->
    Thread.sleep(100) // BLOCKS diagnostic pipeline!
    database.insert(event) // BLOCKING I/O!
}

// DON'T: Update UI directly
eventCallback = { event ->
    textView.text = event.toString() // WRONG THREAD!
}

// DON'T: Heavy computation
eventCallback = { event ->
    val hash = heavyCryptoHash(event) // STARVES OTHER CALLBACKS!
}
```

## emitToLog Implementation

### Android (logcat)

```kotlin
// meshlink/src/androidMain/kotlin/ch/trancee/meshlink/android/diagnostics/AndroidLogEmitter.kt

class AndroidLogEmitter : LogEmitter {
    override fun emit(event: DiagnosticEvent) {
        val tag = "MeshLink.${event.category}" // e.g., "MeshLink.transfer"
        val msg = event.toLogString()
        
        when (event.severity) {
            DiagnosticSeverity.DEBUG -> Log.d(tag, msg)
            DiagnosticSeverity.INFO -> Log.i(tag, msg)
            DiagnosticSeverity.WARN -> Log.w(tag, msg)
            DiagnosticSeverity.ERROR -> Log.e(tag, msg)
        }
    }
}
```

### iOS (os_log)

```swift
// meshlink/src/iosMain/kotlin/ch/trancee/meshlink/ios/diagnostics/IosLogEmitter.swift

import os.log

class IosLogEmitter : LogEmitter {
    private let log = OSLog(subsystem: "ch.trancee.meshlink", category: "diagnostics")
    
    override fun emit(event: DiagnosticEvent) {
        let category = event.category // "transfer", "routing", etc.
        let osLog = OSLog(subsystem: "ch.trancee.meshlink", category: category)
        let message = event.toLogString()
        
        switch event.severity {
        case .debug: os_log(.debug, log: osLog, "%{public}@", message)
        case .info:  os_log(.info, log: osLog, "%{public}@", message)
        case .warn:  os_log(.default, log: osLog, "%{public}@", message)
        case .error: os_log(.error, log: osLog, "%{public}@", message)
        }
    }
}
```

## Event Structure for Logging

```kotlin
// meshlink/src/commonMain/kotlin/ch/trancee/meshlink/diagnostics/DiagnosticEvent.kt

public sealed interface DiagnosticEvent {
    val timestamp: Instant
    val category: String // "route", "transport", "transfer", "power", "handshake", "key_rotation", "noise"
    val severity: DiagnosticSeverity
    
    fun toLogString(): String = "$category ${this::class.simpleName} $payload"
    
    val payload: String // JSON or key=value pairs
}

enum class DiagnosticSeverity {
    DEBUG, INFO, WARN, ERROR
}
```

## Testing

```kotlin
// meshlink/src/commonTest/kotlin/ch/trancee/meshlink/diagnostics/DiagnosticDispatcherTest.kt

class DiagnosticDispatcherTest {
    @Test
    fun `callback executes on diagnostic dispatcher`() = runTest {
        val testDispatcher = testScheduler
        DiagnosticDispatcher.testOverride = testDispatcher
        
        var capturedContext: CoroutineContext? = null
        
        val settings = MeshLinkSettings(
            eventCallback = { capturedContext = coroutineContext[CoroutineDispatcher] }
        )
        
        // Emit event
        DiagnosticEmitter.emit(DiagnosticEvent.TestEvent())
        
        advanceUntilIdle()
        
        assertEquals(testDispatcher, capturedContext)
    }
    
    @Test
    fun `blocking callback does not block other events`() = runTest {
        val testDispatcher = testScheduler
        DiagnosticDispatcher.testOverride = testDispatcher
        
        var count = 0
        val latch = CompletableDeferred<Unit>()
        
        val settings = MeshLinkSettings(
            eventCallback = { event ->
                if (event is SlowEvent) {
                    // Simulate blocking (bad practice, but test resilience)
                    Thread.sleep(50)
                }
                count++
                if (count == 2) latch.complete(Unit)
            }
        )
        
        DiagnosticEmitter.emit(SlowEvent())
        DiagnosticEmitter.emit(FastEvent())
        
        // Should not timeout - dispatcher handles blocking gracefully (limited parallelism)
        awaitResult(latch.await())
        
        assertEquals(2, count)
    }
}
```

## Performance Characteristics

| Metric | Target | Implementation |
|--------|--------|----------------|
| Callback latency | < 1 ms | Lock-free channel, minimal work in emitter |
| Throughput | 10,000 events/sec | Batched dispatch, bounded queue |
| Memory overhead | < 100 KB | Object pooling for frequent events |
| Blocking tolerance | 2 concurrent blocked | Fixed thread pool (2) isolates blocking |

## Migration Note

If you previously used `eventCallback` on Main thread (incorrect assumption):

```kotlin
// OLD (wrong assumption)
eventCallback = { event ->
    runOnUiThread { updateUI(event) } // Was accidentally working on some platforms
}

// NEW (explicit)
eventCallback = { event ->
    mainDispatcher.launch { updateUI(event) }
}
```

## Related

- [MeshLinkSettings Spec](../../../specs/settings.yaml)
- [Diagnostic Events Spec](../../../specs/diagnostic_events.yaml)
- [CONSTITUTION.md §IV Performance Requirements](../../../CONSTITUTION.md)
- [Kotlin Coroutines Skill](../../../.agents/skills/kotlin-coroutines/SKILL.md)
