# Handoff: validate MeshLink's Android/iOS BLE spike

Prepared 2026-10-31. This is a task handoff, not a normative protocol decision.

## Objective

Continue platform validation for `trancee/MeshLink-template`. The user wants a
small, efficient P2P BLE mesh library supporting **Android 8/API 26 and iOS 15**.

Your immediate task is to **build the prepared iOS probe and verify physical
Android/iOS GATT exchanges**, fixing tightly related harness defects.
Platform evidence should precede production protocol implementation.

## Starting point

- Branch: `spike/gatt-platform-proof`
- Implementation commit: `6576f1ed636307342d6c5b23cd0ce893c87ab8c9`
- Previous host: Linux; worktree was clean after pushing that commit.
- All pending changes, including earlier SPEC and traceability edits, were
  included with user approval.

In an existing checkout, inspect local changes before switching:

```sh
git fetch origin
git switch spike/gatt-platform-proof
git pull --ff-only
```

Read these first:

- [AGENTS.md](../../AGENTS.md) and [CONSTITUTION.md](../../CONSTITUTION.md)
- [Run the BLE spike](../how-to/run-ble-spike.md): execution procedure
- [Experimental spike contract](../reference/ble-spike.md): packet and lifecycle contract
- [Template design review](../explanation/template-design-review.md): critique and proposals

The review is a **proposal**, not adopted normative policy.

## What is implemented

`meshlink-proof` now contains:

- Standalone Android application targeting **API 26+**, with central/peripheral controls.
- Independent native SwiftUI/Core Bluetooth app targeting **iOS 15+**:
  `meshlink-proof/ios/MeshLinkProof.xcodeproj`.
- Kotlin host-tested `ProofRunner` coordinating start/send/stop.

Both apps are **foreground-only, synthetic and unencrypted**. Neither links
the MeshLink SDK or crypto dependency.

The probe uses custom 128-bit service/characteristic UUIDs, subscription
readiness, writes with response and notifications. It exchanges **100
sequential 20-byte request/response pairs**, with one peer and bounded pending
data. A 45-second deadline covers startup through exchange.

Only the **central's PASSED result** proves all responses were verified.
Peripheral submission counters alone do not prove delivery.

The production library's minimum OS settings were **not** changed by this spike.

## Existing evidence and limitations

Passed on Linux:

- Android debug APK build.
- Six Kotlin host tests.
- Spotless, Detekt and Android lint.
- Library `:meshlink:koverVerify` coverage gate.
- Commit/push hooks, including full build gates and secret scanning.
- Markdown and specification validation.

Apple evidence is limited to Swift syntax parsing and parsing of `Info.plist`
and the Xcode project plist.

**No Apple SDK type-check, Xcode build or physical cross-platform radio run
has been completed.** Host tests do not execute either platform adapter or
the Swift coordinator.

Two Android devices became visible through ADB during preparation; neither
was used for the probe. Discover currently available hardware rather than
assuming those devices remain connected.

The specification validator passes, but still reports unresolved source
SPEC-ANCHOR warnings. These warnings are not evidence of specification alignment.

## Execution sequence

1. **Build on macOS.** Open the Xcode project, configure the user's signing
   team and physical iPhone. Preserve deployment target 15.0. Run the unsigned
   compilation check below first. Resolve compilation/project defects
   surgically. Completion: the Apple build succeeds, or a concrete blocker
   and its diagnostic are recorded.
2. **Run physical exchanges.** Follow the run guide for Android installation
   and permissions. Test Android central/iOS peripheral, then reverse roles.
   Restart both roles between runs. Completion: at least ten fresh runs per
   role combination have recorded central outcomes, or missing hardware is
   explicitly documented.
3. **Validate cleanup.** Exercise Stop during discovery/connection/exchange,
   absent-peer timeout, Bluetooth disabled mid-run and leaving the foreground.
   Record failures and verify radio cleanup with an independent observer where
   available. Completion: every scenario has an observed outcome or an
   explicit verification limitation.
4. **Persist evidence.** Record revision, device models, exact OS/API versions,
   roles, counters, timings and failures in repository documentation.
   Completion: observed results are separated from inference and all remaining
   blockers are recorded.

Unsigned Apple compilation check:

```sh
xcodebuild -project meshlink-proof/ios/MeshLinkProof.xcodeproj \
  -target MeshLinkProof -configuration Debug -sdk iphoneos \
  CODE_SIGNING_ALLOWED=NO build
```

Actual Android 8 and iOS 15 hardware is required to claim minimum-version
verification. Newer OS versions do not establish older OS behavior.

If required hardware/signing is unavailable, complete available compilation
checks and report precisely which scenarios remain blocked.

## Rules and scope

Follow repository skills and engineering rules. The user already approved
tests at the **runner start/send/stop boundary** and **physical
discovery/subscription/bidirectional-byte boundary**. Agree any materially new
testing seam or design choice before implementing it.

Every Gradle invocation must include:

```text
--rerun-tasks --no-build-cache
```

Keep production API, crypto, routing and wire behavior separate from this
experiment. Preserve unrelated changes. Obtain fresh approval before
additional commits or pushes.

## Deferred blockers

After platform proof, address the published `meshlink-crypto:0.1.1` public
AEAD API's lack of a supported explicit nonce/associated-data path needed
for Noise. Treat this as a separate dependency/API task.

Routing, root-key rotation and large transfers remain deferred. Foreground
probe success does not establish background mesh availability, bulk
throughput, energy efficiency or SDK size.

## Completion report

Provide Apple build results, physical-device evidence, surgical fixes, exact
checks run, remaining blockers and skills used. Clearly separate
**compiled**, **host-tested** and **radio-verified** outcomes.
