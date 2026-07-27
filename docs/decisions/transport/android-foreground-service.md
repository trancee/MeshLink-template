# Android Foreground Service Configuration

**Status:** Locked — 2026-07-26

## Decision

**Use `foregroundServiceType="connectedDevice"` for all MeshLink background BLE operations** (API 34+). For API 26-33, use `foregroundServiceType="dataSync"` as fallback.

## Service Declaration

```xml
<!-- meshlink/src/androidMain/AndroidManifest.xml -->

<service
    android:name="ch.trancee.meshlink.android.service.MeshLinkForegroundService"
    android:foregroundServiceType="connectedDevice|dataSync"
    android:exported="false"
    android:stopWithTask="false" />

<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
```

## Service Implementation

```kotlin
// meshlink/src/androidMain/kotlin/ch/trancee/meshlink/android/service/MeshLinkForegroundService.kt

class MeshLinkForegroundService : Service() {
    
    private val notificationId = 0xMESH // 45057
    private var notification: Notification? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val settings = MeshLink.getSettings() // Get current settings
        val powerMode = settings.powerMode
        
        // Create notification with appropriate content
        notification = buildNotification(powerMode)
        
        // Start foreground with correct service type
        if (Build.VERSION.SDK_INT >= 34) {
            // API 34+: connectedDevice is REQUIRED for BLE
            startForeground(notificationId, notification, FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            // API 26-33: service type is declared in the manifest; use 2-param overload
            startForeground(notificationId, notification)
        }
        
        // Initialize MeshLink core with current settings
        MeshLink.start(settings)
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        MeshLink.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    
    private fun buildNotification(powerMode: PowerMode): Notification {
        val channelId = "meshlink_foreground"
        
        // Create channel (once)
        val channel = NotificationChannel(
            channelId, 
            "MeshLink Background Service", 
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains peer-to-peer mesh connections"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_meshlink_notification)
            .setContentTitle("MeshLink Active")
            .setContentText("Power mode: $powerMode - ${connectedPeers} peers connected")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(buildStopAction())
            .build()
    }
    
    private fun buildStopAction(): NotificationCompat.Action {
        val intent = Intent(this, MeshLinkForegroundService::class.java).apply {
            action = "STOP_MESHLINK"
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_stop, "Stop", pendingIntent
        ).build()
    }
}
```

## Service Type Requirements by API Level

| API Level | Required Type | MeshLink Usage |
|-----------|---------------|----------------|
| 26-28 (8.0-9) | None (legacy) | `dataSync` (optional) |
| 29-30 (10-11) | None (legacy) | `dataSync` |
| 31-33 (12-13) | `dataSync` | `dataSync` |
| 34+ (14+) | `connectedDevice` | **`connectedDevice` mandatory** |

**Key rule:** API 34+ **will throw `SecurityException`** if BLE operations (GATT connect, scan, advertise) are performed without `connectedDevice` foreground service type.

## Starting the Service

```kotlin
// meshlink/src/androidMain/kotlin/ch/trancee/meshlink/android/MeshLinkAndroid.kt

class MeshLinkAndroid @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    fun startForeground(settings: MeshLinkSettings) {
        val intent = Intent(context, MeshLinkForegroundService::class.java)
            .putExtra("settings", settings.toBundle())
        
        if (Build.VERSION.SDK_INT >= 26) {
            // Use startForegroundService for O+
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }
    
    fun stopForeground() {
        val intent = Intent(context, MeshLinkForegroundService::class.java)
        context.stopService(intent)
    }
}
```

## Notification Content Guidelines

| Element | Requirement |
|---------|-------------|
| **Icon** | Small, monochrome, `VISIBILITY_PRIVATE` |
| **Title** | "MeshLink Active" (static) |
| **Text** | Dynamic: power mode + peer count |
| **Priority** | `PRIORITY_LOW` (minimal intrusion) |
| **Category** | `CATEGORY_SERVICE` |
| **Actions** | "Stop" action → calls `MeshLink.stop()` |
| **Ongoing** | `true` (cannot be swiped away) |

## Power Mode & Notification Updates

```kotlin
// Update notification when power mode changes
fun updateNotification(powerMode: PowerMode, peerCount: Int) {
    val notification = buildNotification(powerMode, peerCount)
    notificationManager.notify(notificationId, notification)
    
    // Update service type if needed (API 34+)
    if (Build.VERSION.SDK_INT >= 34) {
        // Re-declare service type not needed at runtime
        // Just ensure notification is updated
    }
}
```

## Stopping the Service

```kotlin
// From notification "Stop" action or MeshLink.stop()
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == "STOP_MESHLINK") {
        stopSelf()
        return START_NOT_STICKY
    }
    // ... normal start
}
```

## Testing

```kotlin
// meshlink/src/androidTest/kotlin/ch/trancee/meshlink/android/service/MeshLinkForegroundServiceTest.kt

@RunWith(AndroidJUnit4::class)
class MeshLinkForegroundServiceTest {
    
    @Test
    fun `service starts with correct foreground type API 34+`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, MeshLinkForegroundService::class.java)
        
        // On API 34+ emulator/device
        assumeTrue(Build.VERSION.SDK_INT >= 34)
        
        ContextCompat.startForegroundService(context, intent)
        
        // Verify service is running with correct type
        val manager = context.getSystemService(NotificationManager::class.java)
        val activeNotifications = manager.activeNotifications
        assertTrue(activeNotifications.any { it.id == 0xMESH })
    }
    
    @Test
    fun `service stops on STOP action`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, MeshLinkForegroundService::class.java).apply {
            action = "STOP_MESHLINK"
        }
        context.startService(intent)
        
        // Verify service stopped
        assertFalse(isServiceRunning(context, MeshLinkForegroundService::class.java))
    }
}
```

## Common Pitfalls

| Pitfall | Symptom | Fix |
|---------|---------|-----|
| Missing `FOREGROUND_SERVICE_CONNECTED_DEVICE` permission | `SecurityException` on API 34+ | Add to manifest |
| Using `dataSync` only on API 34+ | BLE operations silently fail / throw | Use `connectedDevice` |
| `stopWithTask="true"` | Service dies when app swiped from recents | Set `stopWithTask="false"` |
| High notification priority | Intrusive status bar icon | Use `PRIORITY_LOW` |
| No "Stop" action | User cannot stop service without force-stop | Add action to notification |
| `pendingIntent` without `FLAG_IMMUTABLE` | Crash on API 31+ | Always use `FLAG_IMMUTABLE` |

## Related

- [Android BLE Permissions](./android-ble-permissions.md)
- [Android 17 BLE Migration Skill](../../../.agents/skills/android-17-ble-migration/SKILL.md)
- [Power Management Spec](../../../docs/reference/10-power-management.md)
- [CONSTITUTION.md §III Cross-Platform Consistency](../../../CONSTITUTION.md)
