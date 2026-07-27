# iOS Background BLE Configuration Details

**Status:** Locked — 2026-07-27

## Context

The iOS background BLE ADR (`ios-background-ble.md`) defines the background strategy (Core Bluetooth state preservation/restoration, reduced duty cycle, `beginBackgroundTask`). However, it does not specify the required Xcode project configuration, Info.plist keys, or background task duration limits — all of which are needed before the iOS platform glue can be implemented.

## Xcode Project Configuration

### Required Capabilities

The iOS app target must enable the following capabilities in Xcode:

| Capability | Purpose | Required |
|------------|---------|----------|
| `Background Modes` → `Uses Bluetooth LE accessories` | Allows BLE central/peripheral operation in background | Yes |
| `Background Modes` → `Acts as a Bluetooth LE accessory` | Allows BLE peripheral (advertise) operation in background | Yes (dual-role) |

### Required Info.plist Keys

```xml
<key>UIBackgroundModes</key>
<array>
    <string>bluetooth-central</string>
    <string>bluetooth-peripheral</string>
</array>

<key>CBManagerOptionShowPowerAlertKey</key>
<true/>

<key>CBManagerOptionRestoreIdentifierKey</key>
<string>ch.trancee.meshlink.cbmanager</string>

<key>NSBluetoothAlwaysUsageDescription</key>
<string>MeshLink requires Bluetooth to discover and communicate with nearby peers.</string>

<key>NSBluetoothPeripheralUsageDescription</key>
<string>MeshLink advertises as a BLE peripheral for nearby peers to discover.</string>
```

### Minimum Deployment Target

- iOS 14.0 (matches platform minimum in CONSTITUTION.md)
- `CBManagerOptionRestoreIdentifierKey` is supported from iOS 10+; state preservation/restoration improves in iOS 13+

## Background Task Duration

### iOS Background Task Limits

iOS provides a finite time window for background execution after the app enters the background:

| Scenario | Typical Time | Hard Limit |
|----------|-------------|------------|
| `beginBackgroundTask` (standard) | ~30 seconds | 3 minutes (system-dependent) |
| BLE central operation (iOS 13+) | ~10 seconds after app suspension | System-managed |
| `CBPeripheralManager` advertising | Continues indefinitely | No limit (system-managed) |
| `CBCentralManager` scan + connect | Continues with throttling | System-managed |

### Implication for MeshLink Retry Budgets

The retry budgets per power mode (HIGH=60s, MEDIUM=30s, LOW=15s) **exceed** the typical background task window. This means:

1. **In background, transfers that require retry will fail early** — the background task expires before the retry budget is exhausted
2. **The transfer layer MUST check application state** before starting a retry timer; if the app is in background, use the remaining background task time as the effective retry budget
3. **Transfers started in foreground** that exceed the background task window will be suspended; the state machine must checkpoint progress and resume when the app returns to foreground or is relaunched via state restoration

### Recommended Approach

```kotlin
// meshlink/src/iosMain/kotlin/ch/trancee/meshlink/ios/BackgroundTaskManager.kt

class BackgroundTaskManager {
    private var backgroundTaskId: UIBackgroundTaskIdentifier = .invalid
    private var remainingBackgroundTime: Duration = Duration.ZERO

    fun begin(): UIBackgroundTaskIdentifier {
        val id = UIApplication.shared.beginBackgroundTask {
            // Expiration handler
            end()
        }
        remainingBackgroundTime = UIApplication.shared.backgroundTimeRemaining.seconds
        return id
    }

    fun remainingTime(): Duration = remainingBackgroundTime.minimumOf(
        UIApplication.shared.backgroundTimeRemaining.seconds
    )

    fun end() {
        if (backgroundTaskId != .invalid) {
            UIApplication.shared.endBackgroundTask(backgroundTaskId)
            backgroundTaskId = .invalid
        }
    }
}
```

### Retry Budget Adjustment in Background

| Power Mode | Foreground Retry Budget | Background Effective Budget |
|------------|------------------------|----------------------------|
| HIGH | 60s | min(60s, backgroundTimeRemaining) ≈ ~30s |
| MEDIUM | 30s | min(30s, backgroundTimeRemaining) ≈ ~20s |
| LOW | 15s | min(15s, backgroundTimeRemaining) ≈ ~10s |

If the effective budget is < 5 seconds, the transfer should suspend and resume when the app returns to foreground, rather than failing.

## State Restoration Checkpointing

The `TransferCoordinator` MUST checkpoint progress at each of these points:

1. **After each successfully sent chunk** — checkpoint chunkIndex and bytesSent
2. **After each successfully received SACK** — checkpoint which chunks are confirmed received
3. **On app backgrounding** — checkpoint all in-progress transfer state to persisted storage
4. **On app relaunch via state restoration** — restore checkpointed transfers and resume

Checkpoint data is stored in the `Keychain` (not `UserDefaults`) because it contains transfer session metadata that should not be accessible to other apps.

## Diagnostics

New diagnostic events needed:

```yaml
- name: BackgroundTaskExpiredEvent
  fields:
    - sessionId: SessionId
    - remainingBudget: Duration
    - chunksCompleted: UInt
    - reason: String  # "background_time_expired" | "system_killed"

- name: TransferResumedEvent
  fields:
    - sessionId: SessionId
    - chunksResumedFrom: UInt
    - source: String  # "checkpoint" | "fresh_start"
```

## Platform-Specific Notes

### Android vs iOS Background Behavior

| Aspect | Android | iOS |
|--------|---------|-----|
| Background BLE scan | Continue (with PendingIntent on API 31+) | Throttled (~1 scan/30s) |
| Background BLE advertise | Continue (foreground service required) | Continue (system-managed) |
| Background GATT connection | Continue (with foreground service) | Limited (~3-4 concurrent) |
| Background L2CAP CoC | Continue (with foreground service) | Allowed only if connection exists |
| Background data transfer | Continue (with foreground service) | Continue with throttling |
| Background task expiration | No hard limit (foreground service) | ~3 minutes (system-dependent) |

## Related

- [iOS Background BLE ADR](ios-background-ble.md) — parent decision
- [Core Bluetooth Skill](../../../.agents/skills/core-bluetooth/SKILL.md)
- [KMP iOS Integration](../../../.agents/skills/kmp-ios-integration/SKILL.md)
- [Transfer Layer Spec](../../../SPEC.md#9-transfer-layer)
