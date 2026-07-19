# About MeshLink's module structure

MeshLink is split into four Gradle modules, each answering a different
question. Understanding the split avoids duplicating effort in the wrong
module and clarifies which check gate (see [AGENTS.md](../../AGENTS.md))
actually proves what.

| Module | Question it answers | Runs on |
|---|---|---|
| `meshlink` | The shipped library itself — the public API and its implementation. | JVM (host tests) + Android + iOS targets |
| `meshlink-reference` | Does the **public developer-facing API** work the way a real integrating app would use it? A sample/reference app that consumes only what a third-party developer could consume, with a real UI built in Kotlin Compose Multiplatform (use the `compose-multiplatform` skill when working on it). | Android + iOS targets |
| `meshlink-proof` | Does the **internal runtime** actually behave correctly on real Android hardware — e.g. which crypto provider or fallback path gets selected on a given Android SDK tier, or real BLE advertising/scanning/GATT/L2CAP behavior? Needs low-level access to runtime internals that a public-API-only app shouldn't expose. | Real Android devices (see note below on emulators) |
| `meshlink-benchmark` | Are throughput, latency, and memory within the recorded performance budget (Principle IV)? | JVM (smoke) + real-device fleet |

## Why `meshlink-reference` and `meshlink-proof` are kept separate

They validate different things and need different levels of access:

- `meshlink-reference` should only ever call MeshLink through its public API
  surface — if something requires reaching into internals to test, it
  doesn't belong here. It exists to catch DX/integration regressions, has a
  real UI (Kotlin Compose Multiplatform — read the `compose-multiplatform`
  skill before writing or reviewing any of it) exercised through Android
  and iOS test targets, and its scope should never grow to need anything
  beyond the public API.
- `meshlink-proof` exists because JVM host tests cannot observe
  real Android crypto-provider selection or hardware-backed capability
  differences across SDK tiers — that only happens with a real Android
  runtime. See
  [android-crypto-fallback-proof.md](../decisions/crypto/android-crypto-fallback-proof.md)
  for a concrete example of the kind of runtime-only behavior this module
  exists to catch. It is currently Android-only and flat (not nested under
  a platform sub-path); a future iOS proof harness would need its own
  separate module rather than nesting under this one.

Folding `meshlink-proof` into `meshlink-reference` as an
instrumented test source set was considered and rejected: it would force
the reference app to expose internal/friend access to runtime crypto
internals, blurring the "public API only" boundary that gives
`meshlink-reference` its value as a DX regression check.

## Dokka, SKIE, and 100% coverage apply to `meshlink` only

Dokka (API documentation generation), SKIE (Swift-friendly signatures for
the Kotlin/Native iOS binary), and the 100% line/branch coverage gate
(Principle II) all exist to guarantee quality of the **public**
`:meshlink` API — that's only a concern for the one module that's
actually shipped as a library.

`meshlink-reference`, `meshlink-proof`, and `meshlink-benchmark` are internal
consumers and test harnesses, not published artifacts, so generating docs or
Swift-friendly wrappers for them would document/wrap internals nobody
outside the repo will ever see, and holding them to a 100% coverage bar
would burn effort proving internal test/reference code rather than the
shipped protocol surface. Wiring Dokka/SKIE into those modules, or gating
their merges on coverage, is out of scope; see the
[Technical Constraints](../../CONSTITUTION.md#technical-constraints) for the
binding rule.

## Emulators and simulators never cover real BLE behavior

Android emulators and the iOS simulator do not implement real BLE radios.
Emulators remain usable for non-radio proof work (e.g. crypto-provider
selection, per Principle II) but MUST NOT be added as a test target for
actual BLE behavior (advertising, scanning, GATT/L2CAP connections) —
that coverage only comes from real hardware in `meshlink-proof`. This
applies to `meshlink-reference`'s Android/iOS targets too: they validate
UI and public-API integration, not real BLE behavior — never treat a
green `meshlink-reference` run as BLE proof. See
[Principle II](../../CONSTITUTION.md#ii-exhaustive-testing-standards).
