# Android BLE Permissions Strategy

**Status:** Locked — 2026-07-26

## Decision

**Use modern permissions (API 31+) with legacy fallback (API 26-30)** via runtime checks. Never require `ACCESS_FINE_LOCATION` for BLE operations on Android 12+.

## Permission Mapping

| Android Version | Permissions Required | Notes |
|-----------------|---------------------|-------|
| API 34+ (Android 14) | `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` + `FOREGROUND_SERVICE_CONNECTED_DEVICE` | `neverForLocation` enforced |
| API 31-33 (Android 12-13) | `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` | `neverForLocation` available |
| API 29-30 (Android 10-11) | `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_FINE_LOCATION` | Legacy; location required for scan results |
| API 26-28 (Android 8-9) | `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_FINE_LOCATION` | Legacy; location required |

## Implementation Strategy

### Manifest Declaration

```xml
<!-- Modern permissions (API 31+) -->
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:neverForLocation="true" />

<!-- Legacy permissions (API < 31) -->
<uses-permission android:name="android.permission.BLUETOOTH"
    android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"
    android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"
    android:maxSdkVersion="30" />

<!-- Foreground service for background BLE (API 26+) -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<!-- BLE feature declaration -->
<uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />
```

### Runtime Permission Helper

```kotlin
// meshlink/src/androidMain/kotlin/ch/trancee/meshlink/android/permissions/BlePermissions.kt

sealed interface BlePermissionResult {
    data class Granted(val permissions: List<String>) : BlePermissionResult
    data class Denied(val permissions: List<String>, val permanentlyDenied: Boolean) : BlePermissionResult
}

object BlePermissions {
    private val MODERN_PERMISSIONS = listOf(
        "android.permission.BLUETOOTH_ADVERTISE",
        "android.permission.BLUETOOTH_CONNECT",
        "android.permission.BLUETOOTH_SCAN",
    )
    
    private val LEGACY_PERMISSIONS = listOf(
        "android.permission.BLUETOOTH",
        "android.permission.BLUETOOTH_ADMIN",
        "android.permission.ACCESS_FINE_LOCATION",
    )
    
    private val FOREGROUND_PERMISSIONS = listOf(
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
        "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
    )
    
    /** Returns all permissions required for current API level. */
    fun requiredPermissions(): List<String> = when {
        Build.VERSION.SDK_INT >= 31 -> MODERN_PERMISSIONS + FOREGROUND_PERMISSIONS
        else -> LEGACY_PERMISSIONS + FOREGROUND_PERMISSIONS
    }
    
    /** Checks if all required permissions are granted. */
    fun areAllGranted(context: Context): Boolean =
        requiredPermissions().all { ContextCompat.checkSelfPermission(context, it) == PERMISSION_GRANTED }
    
    /** Requests missing permissions via ActivityCompat. */
    fun requestPermissions(activity: Activity, requestCode: Int): Boolean {
        val missing = requiredPermissions().filter { 
            ContextCompat.checkSelfPermission(activity, it) != PERMISSION_GRANTED 
        }
        if (missing.isEmpty()) return true
        ActivityCompat.requestPermissions(activity, missing.toTypedArray(), requestCode)
        return false
    }
    
    /** Whether we should show rationale for any denied permission. */
    fun shouldShowRationale(activity: Activity): Boolean =
        requiredPermissions().any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
}
```

### Scanner/Advertiser Wrapper

```kotlin
// meshlink/src/androidMain/kotlin/ch/trancee/meshlink/android/ble/Scanner.kt

@SuppressLint("MissingPermission") // Guarded by permission check
fun BluetoothLeScanner.startScan(
    filters: List<ScanFilter>?,
    settings: ScanSettings,
    callback: ScanCallback
): Boolean {
    if (!BlePermissions.areAllGranted(context)) return false
    // ... actual scan call
}
```

## Android 14+ Foreground Service Types

| Use Case | Foreground Service Type |
|----------|------------------------|
| Active BLE connections (GATT/L2CAP) | `connectedDevice` |
| Background scanning (PendingIntent) | `dataSync` |
| Advertising while backgrounded | `dataSync` |

**Rule:** Any BLE operation that must continue when app is not visible requires a foreground service with appropriate type. MeshLink starts foreground service on `MeshLink.start()` and stops on `MeshLink.stop()`.

## Android 17 (API 35) Changes

Per [android-17-ble-migration](../../../.agents/skills/android-17-ble-migration/SKILL.md):

1. **Scan rate limiting**: 5 scan starts per 30 seconds per app — batch scans, reuse `ScanCallback`
2. **Background scan suspension**: Use `PendingIntent`-based scanning for background; `BluetoothLeScanner.startScan()` stops when screen off
3. **BluetoothSocket read loop**: Must check for `-1` (EOF) explicitly — `InputStream.read()` returns `-1` on disconnect, not throw
4. **MAC rotation**: PeerIdentity is stable random (not MAC), so unaffected
5. **USE_LOOPBACK_INTERFACE**: Not needed for MeshLink (no local loopback)

## Testing

- `androidHostTest`: Permission logic with Robolectric shadows (`ShadowBluetoothAdapter`, `ShadowBluetoothLeScanner`)
- Device tests (`meshlink-proof`): Real permission flows on API 26, 29, 31, 34

## Related

- [Android BLE Skill](../../../.agents/skills/android-ble/SKILL.md)
- [Android 17 BLE Migration Skill](../../../.agents/skills/android-17-ble-migration/SKILL.md)
- [CONSTITUTION.md §III Cross-Platform Consistency](../../../CONSTITUTION.md)
