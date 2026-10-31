# Experimental BLE spike contract

This contract belongs only to `meshlink-proof`. It is not an accepted change
to SPEC, the production wire format, security requirements or service UUIDs.
The Android application requires API 26; the separate native Swift app
targets iOS 15. Neither app links the MeshLink SDK or crypto dependency.

## GATT attributes

| Attribute | UUID | Behavior |
|---|---|---|
| Primary service | `c247f4e0-18b7-4d48-a73c-7a62f4860190` | Connectable foreground advertisement |
| Request | `c247f4e1-18b7-4d48-a73c-7a62f4860190` | Central writes with response |
| Response | `c247f4e2-18b7-4d48-a73c-7a62f4860190` | Peripheral notifies subscribed central |
| Response CCCD | `00002902-0000-1000-8000-00805f9b34fb` | Notifications enabled before READY |

The custom 128-bit UUIDs deliberately isolate this experiment from production
discovery. No manufacturer data, local name, bonding or encrypted attribute
permissions are used. All data is public synthetic test data.

## Packet format

Every request and response is exactly 20 bytes, compatible with the
ATT MTU 23 baseline. Larger MTUs are neither requested nor required.
Each run consists of 100 sequential request/response exchanges.

| Offset | Width | Meaning |
|---|---|---|
| 0 | 2 | Magic `4d 4c` |
| 2 | 1 | Experimental version `01` |
| 3 | 1 | Kind: request `00`, response `01` |
| 4 | 4 | Random run token, echoed unchanged; no security guarantee |
| 8 | 4 | Zero-based sequence, big-endian; 0 through 99 |
| 12 | 8 | Request: `00 01 02 03 04 05 06 07`; response: `ff fe fd fc fb fa f9 f8` |

Example with token 42, sequence zero:

```text
request:  4d 4c 01 00 00 00 00 2a 00 00 00 00 00 01 02 03 04 05 06 07
response: 4d 4c 01 01 00 00 00 2a 00 00 00 00 ff fe fd fc fb fa f9 f8
```

The central verifies the entire response, not just its length. The peripheral
checks magic, version, kind, sequence, token continuity and synthetic payload.
No fragmentation, prepared writes, retries or resume behavior is supported.

## Bounds and lifecycle

Only one peer and one request/response exchange are active. The Android client
retains at most one early notification until its write callback completes.
The Android server retains at most one early next request until notification
completion; the Swift server retains at most one notification when
`updateValue` reports backpressure and retries only on the ready callback.
These callbacks reflect platform completion/queue acceptance, not authenticated
application delivery.

The 45-second deadline starts when a role starts and covers discovery,
subscription, user delay and exchange. Starting again invalidates old callbacks.
Stop and leaving the foreground release the radio work. Android disconnect
cleanup waits for the disconnect callback, with a two-second close fallback.
No scan/connect retry loop, foreground service, background mode or state
restoration is configured.

Only the central emits PASSED after all 100 replies are verified. The
peripheral reports verified requests and submitted responses, not a delivery
guarantee. Both roles must be restarted before another run.

## Evidence boundaries

This probe can establish a foreground GATT path on tested device pairs. It
cannot establish a mesh topology, secure session, always-on background
availability, CoC fallback, bulk throughput, energy budget or SDK size.
Its elapsed-time display includes UI and callback overhead.

The Kotlin runner's host tests do not execute either platform adapter. The
Swift coordinator is a separate experimental implementation; Apple compilation
does not establish physical-device delivery. See
[run the spike](../how-to/run-ble-spike.md) for prerequisites.

## Recorded validation: 2026-10-31

Base revision: `038bfc14a11dd1e6a776831ec4fa4bd84f534de7`.
The Android peer runs used that revision with the local `GattPeripheral.kt`
change removing the multiple-advertisement prerequisite. The unchanged probe
requires only one advertisement. The tested APK's SHA-256 is:

```text
daab30d0a79acf0a3fc06150985086ae806f1917e7441c058b7b5fd6adeeff21
```

| Alias | Physical model | OS / API | Firmware build |
|---|---|---|---|
| Huawei | Huawei P10 lite, `WAS-LX1A` | Android `8.0.0` / `26` | `WAS-LX1A 8.0.0.394(C432)` |
| Nothing | Nothing Phone (2), `A065` | Android `16` / `36` | `B4.1-260818-1726` |

Both apps were foregrounded for peer exchanges. UI controls and results were
observed using Android's built-in `uiautomator dump` and ADB input, not Appium.
Location permission was granted and Location services enabled on Huawei;
Nearby devices permission was granted on Nothing. Distance was not measured.

| Scenario | Observed outcome |
|---|---|
| Huawei central, no peer | `FAILED`, deadline reached at displayed run time `45075 ms`; submitted/verified `0/0`. Platform scan duration `45031 ms`, zero filtered results. |
| Huawei central, Stop during discovery | `STOPPED` at displayed run time `2805 ms`; platform scan duration `2780 ms`. Probe scanner starts/stops `2/2` after this and the timeout run. |
| Huawei central, leave foreground during discovery | Returning to the app showed `STOPPED` at `3810 ms`; platform scan duration `3769 ms`. Probe scanner starts/stops `3/3`. |
| Huawei peripheral, original prerequisite | Immediate `FAILED` at `33 ms`, before starting advertising. |
| Nothing central / Huawei peripheral, prerequisite removed | Two fresh runs failed with `Connection ended: 133/0` at `432 ms` and `758 ms`; submitted/verified `0/0`. A filtered discovery led to a connection attempt, but the responding peer's identity was not independently confirmed. |
| Huawei central / Nothing peripheral, prerequisite removed | Ten fresh exchange attempts: eight `PASSED` with submitted/verified `100/100`; two failed before subscription with `133/0`. See per-run results below. |

After successful exchanges, Nothing displayed verified requests/submitted
replies `100/100` and the expected peer-disconnect failure. Delivery evidence
is Huawei's central `PASSED`, not Nothing's submission counter.

### Android repetition and interrupted exchange

Each attempt explicitly stopped and restarted both roles. Attempts 2 through
10 used a bounded host script selecting controls from UIAutomator text/bounds;
it did not retry a connection within a run. All passing attempts reached
subscription `READY` on both peers before Send.

| Attempt | Central outcome | Submitted / verified | Displayed run ms | Exchange ms |
|---|---|---|---|---|
| 1 | `PASSED` | `100/100` | `26263` | `11084` |
| 2 | `PASSED` | `100/100` | `19688` | `11699` |
| 3 | `FAILED: Connection ended: 133/0` | `0/0` | `1071` | `0` |
| 4 | `PASSED` | `100/100` | `18802` | `11015` |
| 5 | `PASSED` | `100/100` | `19178` | `11059` |
| 6 | `PASSED` | `100/100` | `20582` | `11161` |
| 7 | `PASSED` | `100/100` | `19717` | `11663` |
| 8 | `PASSED` | `100/100` | `18648` | `11051` |
| 9 | `FAILED: Connection ended: 133/0` | `0/0` | `1507` | `0` |
| 10 | `PASSED` | `100/100` | `20646` | `11341` |

These are ten attempts, not ten successful runs. Connection reliability is
unresolved; successful exchange timings alone omit startup failures and do
not establish a performance baseline.

Separate interrupted-exchange checks followed the repetition series. Two
setup attempts failed before subscription with `133/0` at `782 ms` and
`1562 ms`; these are outside the ten-attempt table. The first transfer
inspection failed because `uiautomator dump` could not obtain an idle screen.
The existing XML was stale and was not accepted as mid-transfer evidence;
that transfer completed all 100 pairs before Stop.

On a fresh subscribed pair, Send was followed by Stop without a mid-transfer
UI dump. Huawei then displayed `STOPPED`, submitted/verified `26/25`, run
`19912 ms`, exchange `2833 ms`. A second observation after a five-second wait
showed the same state, counters, and timings. Nothing displayed `26/26` and
the expected peer-disconnect failure at run `21740 ms`. This verifies
interruption and no subsequent visible progress, not delivery of the
unverified 26th response.

### Android connection diagnostics

Two additional setup-only attempts were made after the repetition and
interrupted-exchange checks, with explicit Stop on both peers and a
five-second pause between attempts. No requests were submitted.

| Setup attempt | Huawei central result | Platform observation |
|---|---|---|
| 1 | Subscription `READY` at `2539 ms` | Client registration, connection, and service discovery returned status `0`; notification subscription completed. |
| 2 | `FAILED: Connection ended: 133/0` at `845 ms` | Client registration returned status `0`, then connection returned `133` approximately `432 ms` after `connect`; no service discovery was initiated. |

The successful attempt's prior GATT client was closed and unregistered before
the failing attempt began. Both used direct `autoConnect=false` connections.
The two attempts selected different device addresses, compared locally without
publishing addresses. Address rotation or selection of another nearby probe
could explain this difference; neither was established. The failing peer's
identity was not independently bound to Nothing. These logs do not justify
changing connection policy or attributing `133` to a specific device defect.

Nothing remained at `STARTING`, `0/0` after the failed attempt. Both probes
were then explicitly stopped. Huawei's scanner accounting was `19/19`;
Nothing recorded final server unregistration and an advertising stop timestamp.
Advertiser/server records are platform diagnostics, not independent RF
verification. Further connection diagnosis requires an isolated test pair and
correlation of the discovered address with the intended advertising peer.

### Android reverse-role paired diagnostics

One further bounded Huawei-peripheral / Nothing-central attempt captured
platform logs from both probe processes. Huawei server registration and service
addition returned status `0`; `startAdvertising` was logged at `20:05:03.116`.
No connection-state callback appeared in the captured Huawei probe-process
stream. Huawei's UI remained `STARTING`, with `0/0` counters.

Nothing registered its scanner and GATT client with status `0`, initiated a
direct connection at `20:05:03.859`, and received `133`, disconnected, at
`20:05:04.149`, approximately `290 ms` later. No service discovery was logged.
Its UI reported `FAILED: Connection ended: 133/0`, `0/0`, at `361 ms`.

These observations narrow the failure to connection establishment, not payload
exchange. They do not prove that advertising succeeded over the air or that the
selected address belonged to Huawei. The address was not independently bound
to the intended advertiser. Huawei peripheral delivery remains unverified.
Both probes were stopped; Huawei logged server close/unregistration, and
Nothing logged client unregistration and close. Temporary UI dumps were
removed. No Bluetooth reset, retry loop, or adapter change was introduced.

### Android peer-isolation controls

A read-only exact-package inventory queried all 39 reachable Android ADB
transports. Only Huawei and Nothing had `ch.trancee.meshlink.proof` installed;
the other 37 transports returned no exact package match. Some transports refer
to the same physical phone, so these are not 39 independent devices. No
unrelated app was stopped. This inventory does not cover devices outside the
ADB fleet, iPhones, or other apps advertising the experimental UUID.

With Nothing explicitly stopped, Huawei's central scanned until the deadline:
`FAILED` at displayed run `45065 ms`, submitted/verified `0/0`. Platform
diagnostics recorded `45039 ms` scanning and zero matching results. This is
an independent receiving-phone observation that no matching advertisement was
detected during this control interval, not proof of permanent RF silence.

Starting Nothing peripheral then Huawei central produced subscription `READY`
on both: Huawei at `2101 ms`, Nothing at `2892 ms`, with `0/0` counters. After
explicitly stopping both roles, waiting five seconds, and restarting them,
Huawei failed with `133/0` at `1425 ms`. Client registration returned status `0`;
connection failed approximately `693 ms` after `connect`, before service
discovery. The prior client's close/unregister completed before this attempt.
Both probes were then stopped.

The negative and positive controls support local peer isolation, but the
failed connection's selected address is still not independently bound to the
intended advertiser. Intermittent connection failure persists under these
controls. No automatic retry, Bluetooth reset, or speculative connection-policy
change was introduced.

An isolated diagnostic using public `BluetoothAdapter` APIs under the Huawei
probe's app UID reported Bluetooth enabled, an available BLE advertiser, and
`isMultipleAdvertisementSupported=false`. The original combined guard therefore
rejected an available advertiser without attempting the required single
advertisement. With that guard removed, GATT server/service registration
returned status `0` and the platform logged `startAdvertising`. These callbacks
do not prove successful Huawei peripheral delivery. GATT status `133` is a
generic connection failure, not a verified hardware or firmware root cause.

Both Android probes were explicitly stopped afterward. Huawei's probe scanner
record showed starts/stops `4/4` after the initial checks and `17/17` after
repetition and interrupted-exchange checks. The remaining active scan belonged
to Google, not the probe. Huawei's probe GATT server was no longer registered;
Nothing's server history recorded `REASON_UNREGISTER_SERVER` for the final
attempt. This is platform attribution, not independent RF proof that all
advertising stopped.

### Android Bluetooth-disable controls

With explicit approval for the device-wide effect, Huawei Bluetooth was
disabled through public `BluetoothAdapter.disable()` under the probe app UID,
without backgrounding the activity. The Nothing phone's Bluetooth settings
were unchanged. Both disable calls were accepted; platform state reported
`enabled: false`, `state: BLE_ON`. This disables ordinary Bluetooth operation,
but is not proof that all BLE radio activity ceased.

During absent-peer discovery, the probe remained `STARTING` after disabling
Bluetooth, then failed at `45055 ms` with the run-deadline error and `0/0`
counters. Restoring Bluetooth to `enabled: true`, `state: ON` did not restart
the failed run.

A fresh Huawei-central / Nothing-peripheral connection reached `READY` at
`1740 ms`. Bluetooth was then disabled approximately one second after Send.
The probe remained `SENDING`, with `16 submitted / 15 verified`, at the first
post-disable observation. Bluetooth was restored to `ON`; the exchange did not
resume and failed at `45037 ms`, with the same counters and the run-deadline
error (`30136 ms` displayed exchange time).

These checks demonstrate bounded termination, not immediate Bluetooth-state
failure reporting. No automatic reconnect or recovery was observed. Both
probes were explicitly stopped; Huawei scanner attribution recorded `24/24`
starts/stops, and Nothing recorded server unregistration for the final run.
The temporary toggle executable and UI dump were removed. No Bluetooth-state
handling or connection-policy changes were made.

### Android Stop during connection establishment

Two bounded Huawei-central / Nothing-peripheral attempts tested cancellation
timing using probe-process `BluetoothGatt` logs. In the first, observing
`connect()` on the host and then issuing Stop took `816 ms`; the platform
reported `133` approximately `700 ms` after `connect()`. This attempt therefore
does not establish Stop during connection establishment.

In the second, adjacent Start Central and Stop inputs avoided the host
round-trip trigger delay. Platform logs recorded `connect()` at
`20:02:52.217`, successful client registration at `20:02:52.228`, and
`cancelOpen()` at `20:02:52.750`, without a preceding connection-state callback
in the captured probe-process stream. The two-second fallback called `close()`
at `20:02:54.755`, followed by client unregistration.

The UI reported `STOPPED`, `0/0`, and `720 ms` run time, unchanged at a later
observation after an additional five-second wait. This supports cancellation
of a pending direct connection and bounded client cleanup on this pair; it
does not prove controller-level cancellation or behavior on other devices.
Huawei scanner attribution recorded `26/26` starts/stops. Nothing's server
history recorded unregistration after both attempts. Both probes were stopped,
and the temporary UI dump was removed. No adapter code changes were needed.

### Compilation and host checks

The following command passed after the Android prerequisite fix; all six
runner host tests passed:

```sh
ANDROID_HOME="$ANDROID_SDK_ROOT" ./gradlew \
  :meshlink-proof:spotlessApply :meshlink-proof:assembleDebug \
  :meshlink-proof:testDebugUnitTest :meshlink-proof:detekt \
  :meshlink-proof:lintDebug \
  --rerun-tasks --no-build-cache --no-configuration-cache --max-workers=2
```

Earlier in this validation, Xcode `16.4` (`16F6`) compiled and linked the iOS
probe against SDK `18.5`, preserving deployment target `15.0`, after simplifying
the Swift request-byte concatenation expression. Unsigned compilation and a
signed physical-device build passed; installation and launch on iPhone 15
with iOS `17.6.1` succeeded. No iOS radio exchange was performed. iOS work was
then stopped at the user's request; no XCUITest target was added.

### Final local review and gates

The four-file uncommitted diff was reviewed against base revision `038bfc1`
using this experimental contract and the validation handoff. No new
correctness or scope issue was identified. The Android change permits a
single-advertisement attempt when an advertiser is available; it does not
assert hardware support or delivery. The retained Swift change preserves
packet byte order while avoiding the compiler's expression-complexity failure.
Platform-adapter regression coverage remains physical and opt-in, not part of
the runner host tests.

After radio testing stopped, this fresh command passed with all 78 actionable
tasks executed:

```sh
ANDROID_HOME="$ANDROID_SDK_ROOT" ./gradlew \
  :meshlink-proof:spotlessApply :meshlink-proof:spotlessCheck \
  :meshlink-proof:assembleDebug :meshlink-proof:testDebugUnitTest \
  :meshlink-proof:detekt :meshlink-proof:lintDebug \
  :meshlink:koverVerify :meshlink:jvmApiCheck \
  --rerun-tasks --no-build-cache --no-configuration-cache \
  --max-workers=2 --console=plain
```

All six proof runner tests, 431 library JVM tests, and 404 library Android host
tests passed with zero failures, errors, or skips. The library's 100% line and
branch coverage gate and JVM API check passed. The rebuilt APK matched the
recorded SHA-256. No Apple compilation, device operation, or API dump change
was performed during this finalization.

The following supporting checks passed:

```sh
./scripts/validate-specs.sh
./scripts/check-markdown.sh --offline \
  docs/how-to/run-ble-spike.md docs/reference/ble-spike.md
git diff --check
git diff HEAD -- docs/how-to/run-ble-spike.md docs/reference/ble-spike.md \
  meshlink-proof/ios/BleProof.swift \
  meshlink-proof/src/main/kotlin/ch/trancee/meshlink/proof/GattPeripheral.kt |
  gitleaks stdin --redact --config .gitleaks.toml --timeout 60
gitleaks git --redact --config .gitleaks.toml --timeout 90 .
```

Secret scanning found no leaks in the diff or the 95 scanned commits.
Specification validation emitted warnings for five existing unresolved
SPEC-ANCHOR references; none was changed by this spike. Gradle emitted
configuration-resolution and legacy Android API deprecation warnings.
These local checks are not authoritative CI or a full Apple/API platform gate.
No commit or push was made.

### Remaining evidence gaps

Android-to-Android success does not establish Android-to-iOS interoperability.
No Android-to-iOS role combination, ten-run cross-platform repetition, or physical
iOS 15 minimum-version run has been verified. Bluetooth-disable checks cover only
Huawei central during discovery and exchange; they do not establish complete
RF shutdown. Stop during connection establishment and exchange was observed only
for Huawei central / Nothing peripheral. Huawei peripheral delivery remains
unverified. No production SDK, crypto, routing, wire, background availability,
throughput, energy, or size claim follows from these results.
