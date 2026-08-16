# MeshLink-crypto dependency integration

**Status:** Amended — 2026-08-16 (originally Accepted — 2026-08-14)

> **Amendment (2026-08-16):** Upgraded the pinned meshlink-crypto dependency
> from v0.1.0 to v0.1.1. This minor version adds three missing public APIs that
> were gaps in v0.1.0: `Crypto.deriveX25519PublicKey` / `KeyExchange.deriveX25519PublicKey`,
> `Crypto.ed25519PublicKeyFromPrivate` / `Signer.ed25519PublicKeyFromPrivate` (public key
> derivation from private keys — was `internal` only in v0.1.0), and `Crypto.randomBytes`
> (was `internal` randomBytes, now public). The v0.1.1 release also fixes the empty
> Javadoc JAR bug (261 bytes → full Dokka HTML) and bundles Markdown API docs into
> the `-javadoc.jar` for AI tooling. No breaking changes to the existing v0.1.0 API;
> the new methods are purely additive. Decision point 1's version reference is
> updated; all other points unchanged.
>
> **Amendment (2026-08-16, original):** `MeshLink-crypto` shipped its first stable release
> (v0.1.0) to Maven Central. The integration was migrated from a git submodule
> and Gradle composite build to a version-pinned Maven Central dependency
> (`ch.trancee.meshlink:meshlink-crypto:0.1.0`, declared in
> `gradle/libs.versions.toml` as `libs.meshlink.crypto`). The `includeBuild`
> call was removed from `settings.gradle.kts`, the `meshlink-crypto/` submodule
> and `.gitmodules` were deleted, and CI submodule checkout steps were removed.
> The `iosSimulatorArm64` KMP target was dropped from `:meshlink` (BLE radios are
> not available in the iOS simulator; non-radio logic is covered by JVM host
> tests in `commonTest`). Decision points 1 and 2 below have been amended
> accordingly; points 3–5 are unchanged.
> This document records the decision to consume [`MeshLink-crypto`](https://github.com/trancee/MeshLink-crypto)
> as an external dependency rather than building crypto primitives in-house.

## Context

Phase B of the implementation plan called for authoring SHA-256, HKDF, HMAC,
X25519, Ed25519, and ChaCha20-Poly1305 directly in
`meshlink/src/commonMain/kotlin/ch/trancee/meshlink/crypto/`. A sibling
repository, `MeshLink-crypto`, already provides all seven RFC-standard
primitives as a Kotlin Multiplatform module (`ch.trancee.meshlink.crypto`)
with pure-Kotlin implementations, per-primitive native dispatch, Wycheproof
test-vector validation, and constant-time lint.

Both repositories share the same organization, toolchain (Kotlin 2.4.10,
AGP 9.x), target set (JVM, Android API 21+, iOS arm64), and package
namespace (`ch.trancee.meshlink`).

## Decision

1. **Consumer (amended)**: `MeshLink-crypto` is consumed as a version-pinned
   Maven Central dependency. The coordinate
   `ch.trancee.meshlink:meshlink-crypto` (v0.1.1) is declared in
   `gradle/libs.versions.toml` as `libs.meshlink.crypto` and used as
   `implementation(libs.meshlink.crypto)` in `:meshlink`'s `commonMain` source
   set. (Previously: git submodule + Gradle composite build
   `includeBuild("meshlink-crypto")` with `implementation("ch.trancee.meshlink:crypto")`.)

2. **Maven Central over submodule + composite build (amended)**: `MeshLink-crypto`
   was originally at version `0.1.0-SNAPSHOT` with no git tags and no Maven Central
   release. Snapshots cannot be published to Maven Central (Central accepts
   releases only). `publishToMavenLocal` is local-machine-only and unsuitable
   for CI or team development. A git submodule + composite build resolved
   these constraints: it worked with the current SNAPSHOT version, pinned a
   specific commit for reproducibility, and made source-level edits in
   `MeshLink-crypto` immediately available without a publish cycle.

   With the first stable release now on Maven Central, the version-pinned
   coordinate in `libs.versions.toml` supersedes the submodule. The dependency
   is reproducible, works in CI, and can be upgraded with a single version
   bump. The `iosSimulatorArm64` target was removed from `:meshlink` (BLE
   radios are not available in the iOS simulator; non-radio logic is covered by
   JVM host tests in `commonTest`), because the published `0.1.0` artifact
   does not include `iosSimulatorArm64` variant metadata.

3. **Constitutional exception**: `CONSTITUTION.md` §Technical Constraints
   limits the shipped `:meshlink` artifact to one runtime dependency
   (`kotlinx-coroutines-core`). `ch.trancee.meshlink:meshlink-crypto` is added as a
   second exception. The crypto module is dependency-free by design (no
   third-party runtime deps), so the transitive footprint is unchanged.

4. **API surface**: The crypto module's `Crypto` object, `SecretKey`/`PublicKey`/
   `PrivateKey` handles, and `CryptoProvider` interface become the backing
   implementation for `:meshlink`'s cryptographic operations. `:meshlink`
   does not re-export crypto types in its public API — it consumes them
   internally through its own `CryptoProvider` abstraction (see
   [crypto-design.md](crypto-design.md)). The BCV API dump for `:meshlink`
   is therefore unaffected by adding the dependency.

5. **compileSdk bump**: `MeshLink-crypto`'s `:crypto` module declares
   `compileSdk = 37` (its current maximum). The AAR metadata compatibility
   check requires consuming modules to compile against API 37+. All template
   modules (`meshlink`, `meshlink-reference`, `meshlink-proof`) are bumped
   from 36 to 37. `minSdk` is unchanged (26/21 respectively).

## Consequences

+ **Positive**: Leverages battle-tested, RFC-compliant, Wycheproof-validated
  crypto primitives. Eliminates duplicate in-house implementations that
  would need the same verification investment. Same-namespace, same-toolchain
  integration is seamless. Removing the git submodule simplifies the
  onboarding path (no `--recurse-submodules` needed) and reduces CI setup
  surface. The v0.1.1 upgrade adds public key derivation and random byte
  generation APIs that remove the `internal`-only workarounds that v0.1.0
  required.

+ **Negative**: `:meshlink` gains a second runtime dependency. This was a
  binding change to `CONSTITUTION.md`, documented here per Principle V.
  Additionally, the `iosSimulatorArm64` target was removed from `:meshlink`,
  meaning non-radio logic (crypto, routing, wire codec) is only tested via
  JVM host tests, not on an iOS simulator binary. This is acceptable: the
  iOS simulator has no real BLE radio, and `meshlink-proof` validates real
  iOS device behavior on physical hardware.

+ **Neutral (original)**: The submodule required `git clone --recurse-submodules`
  and CI submodule checkout. Once the crypto module reached a stable release,
  migration to a Maven coordinate was straightforward (remove `includeBuild`,
  remove submodule, add version-pinned dependency). **This migration has now
  been completed (2026-08-16).**

## Related

+ [CONSTITUTION.md §Technical Constraints](../../../CONSTITUTION.md#technical-constraints)
+ [Integration guide: MeshLink-crypto](https://github.com/trancee/MeshLink-crypto/blob/main/docs/how-to/integrate-kmp.md)
+ [ADR-0006: Module layout (MeshLink-crypto)](https://github.com/trancee/MeshLink-crypto/blob/main/docs/adr/0006-module-layout.md)
+ [ADR-0007: Build quality toolchain](https://github.com/trancee/MeshLink-crypto/blob/main/docs/adr/0007-build-quality-toolchain.md)
+ [v0.1.1 changelog (MeshLink-crypto)](https://github.com/trancee/MeshLink-crypto/blob/main/CHANGELOG.md)
