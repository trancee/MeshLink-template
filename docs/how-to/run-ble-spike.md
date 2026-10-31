# Run the foreground Android and iOS BLE spike

Use this probe to check discovery, subscription and bidirectional synthetic
bytes before implementing MeshLink's production transport. It does **not**
exercise MeshLink, Noise, encryption, routing or transfers. See the
[experimental probe contract](../reference/ble-spike.md).

## Prepare the Android phone

Use physical Android hardware running Android 8 / API 26 or later, with
Bluetooth enabled. On Android 8–11, also enable Location services and grant
the app's location permission for scanning.

```sh
./gradlew :meshlink-proof:assembleDebug --rerun-tasks --no-build-cache
adb devices
adb -s ANDROID_SERIAL install -r meshlink-proof/build/outputs/apk/debug/meshlink-proof-debug.apk
adb -s ANDROID_SERIAL shell am start -n ch.trancee.meshlink.proof/.ProofActivity
```

Replace `ANDROID_SERIAL` with the selected device identifier. In the app,
tap **Grant permissions** and approve the requested permissions.
Do not use an emulator to record BLE results.

## Prepare the iPhone

Use a Mac with Xcode, an Apple signing team and a physical iPhone running
iOS 15 or later. No Kotlin framework or third-party Swift package is needed.

Open `meshlink-proof/ios/MeshLinkProof.xcodeproj` in Xcode. Select the
`MeshLinkProof` target, choose your signing team, and use a unique bundle
identifier if signing requires one. Keep the deployment target at **15.0**.
Select the physical iPhone and run the app.

For an unsigned compilation check on the Mac:

```sh
xcodebuild -project meshlink-proof/ios/MeshLinkProof.xcodeproj \
  -target MeshLinkProof -configuration Debug -sdk iphoneos \
  CODE_SIGNING_ALLOWED=NO build
```

Approve the Bluetooth permission prompt. If the first run times out while
permission is being granted, restart the role. Do not record simulator
execution as BLE evidence.

## Run both role combinations

1. Keep both apps foregrounded. On the first phone, tap **Start peripheral**.
2. On the other phone, tap **Start central**. Wait for **READY** on both.
3. On the central, tap **Send 100 probes from central** within the run's
   45-second deadline. Record the central's final state and counters.

A successful central shows **PASSED**, 100 submitted requests and 100
verified responses. The peripheral verifies requests and submits responses;
its submission count alone is not evidence that responses arrived. A peer
disconnect/unsubscribe after central completion is expected.

Stop both apps, restart both roles and repeat with the roles reversed:
Android central / iOS peripheral, then iOS central / Android peripheral.
Android phones without advertising support can only exercise the first role.

If a run fails, record the displayed error before pressing Stop. Check
permissions, Bluetooth, legacy Location services and advertising capability.
Restart explicitly rather than repeatedly scanning or automatically retrying.
Other nearby copies of this probe can be selected by the scanner; isolate the
test pair. The probe does not authenticate the peer.

## Record evidence

For each role combination, record phone model, exact OS/API version, app
revision, distance, discovery/subscription outcome, final central counters,
exchange elapsed time and failures. Repeat at least ten fresh runs. Include
an actual API 26 phone and an actual iOS 15 phone before marking those
minimum versions as verified; newer devices do not prove older OS behavior.

Also record Stop during scanning/connecting/sending, Bluetooth disabled
during a run, an absent peer reaching the deadline, and leaving the app for
the background. Confirm no continued advertising/scanning after Stop using
an independent observer when available.

Elapsed times include application scheduling and UI overhead. They are not
bulk throughput, battery, memory or production SDK size measurements.
Do not infer background mesh availability from foreground results.

## Run local non-radio checks

```sh
./gradlew :meshlink-proof:testDebugUnitTest :meshlink-proof:assembleDebug \
  :meshlink-proof:detekt :meshlink-proof:spotlessCheck \
  :meshlink-proof:lintDebug --rerun-tasks --no-build-cache
./scripts/check-markdown.sh --offline \
  docs/how-to/run-ble-spike.md docs/reference/ble-spike.md \
  docs/explanation/module-structure.md
```

The host tests cover coordination through start/send/stop, including corrupt
responses, stale callbacks and transport-reported timeouts. They do not
exercise Android callbacks, real radio delivery or the Swift implementation.
Swift syntax parsing on Linux cannot type-check Core Bluetooth/SwiftUI or
prove the Xcode build succeeds.

**Current evidence:** [recorded validation](../reference/ble-spike.md#recorded-validation-2026-10-31)
includes Apple compilation and eight successful Android 8 central / Android 16
peripheral exchanges in ten attempts, with two connection failures. It is not
an Android 8 to iOS 15 radio result.
