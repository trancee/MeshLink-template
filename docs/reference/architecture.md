# Architecture Overview

> **Specification**: [SPEC.md §2](../../SPEC.md#architecture-overview)  
> **Design rationale**: [Module Structure](../explanation/module-structure.md)  
> **Machine-readable**: [meshlink/build.gradle.kts](../../meshlink/build.gradle.kts)

## Platform-Specific Notes

### Android

- `meshlink` module produces AAR with `android` and `jvm` targets
- `androidMain` contains BLE glue: `BluetoothAdapter`, `BluetoothLeScanner`, `BluetoothGatt`, `BluetoothServerSocket`
- Fallback crypto for API 26-32 in `androidMain` (pure-Kotlin X25519, Ed25519, ChaCha20-Poly1305, HKDF, HMAC)
- `androidHostTest` for crypto fallback validation on host JVM
- Gradle: `com.android.library` + `org.jetbrains.kotlin.multiplatform` plugins

### iOS

- `meshlink` module produces XCFramework with `iosArm64` (device) target
- `iosMain` contains BLE glue: `CBCentralManager`, `CBPeripheralManager`, `CBPeripheral`, `CBL2CAPChannel`
- Crypto uses CryptoKit / Security.framework; no fallback needed (iOS 14+ has all primitives)
- Swift interop via SKIE plugin: sealed classes → Swift enums, suspend → async, Flow → AsyncSequence
- Gradle: `org.jetbrains.kotlin.multiplatform` + `co.touchlab.skie` plugins

### Desktop (JVM)

- `meshlink` module produces JAR for testing and `meshlink-benchmark`
- Virtual transport for multi-node integration tests (no BLE hardware)
- Same `commonMain` business logic; no platform glue needed

## Quick Links

- [SPEC.md §2 — Full architecture details](../../SPEC.md#architecture-overview)
- [Module Structure Explanation](../explanation/module-structure.md)
- [CONSTITUTION.md Technical Constraints](../../CONSTITUTION.md#technical-constraints)
