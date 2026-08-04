# MeshLink-crypto dependency integration

**Status:** Accepted — 2026-08-14

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

1. **Consumer**: `MeshLink-crypto` is integrated as a git submodule at
   `meshlink-crypto/` and wired via Gradle composite build
   (`includeBuild("meshlink-crypto")` in `settings.gradle.kts`). The `:crypto`
   module is declared as `implementation("ch.trancee.meshlink:crypto")` in
   `:meshlink`'s `commonMain` source set.

2. **Submodule + composite build over Maven artifact**: `MeshLink-crypto` is
   currently at version `0.1.0-SNAPSHOT` with no git tags and no Maven Central
   release. Snapshots cannot be published to Maven Central (Central accepts
   releases only). `publishToMavenLocal` is local-machine-only and unsuitable
   for CI or team development. A git submodule + composite build resolves
   these constraints: it works with the current SNAPSHOT version, pins a
   specific commit for reproducibility, and makes source-level edits in
   `MeshLink-crypto` immediately available without a publish cycle.

   When `MeshLink-crypto` ships its first stable release to Maven Central,
   the `includeBuild` can be replaced with a version-pinned coordinate in
   `gradle/libs.versions.toml`.

3. **Constitutional exception**: `CONSTITUTION.md` §Technical Constraints
   limits the shipped `:meshlink` artifact to one runtime dependency
   (`kotlinx-coroutines-core`). `ch.trancee.meshlink:crypto` is added as a
   second exception. The crypto module is dependency-free by design (no
   third-party runtime deps), so the transitive footprint is unchanged.

4. **API surface**: The `:crypto` module's `Crypto` object, `SecretKey`/`PublicKey`/
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

- **Positive**: Leverages battle-tested, RFC-compliant, Wycheproof-validated
  crypto primitives. Eliminates duplicate in-house implementations that
  would need the same verification investment. Same-namespace, same-toolchain
  integration is seamless.
- **Negative**: `:meshlink` gains a second runtime dependency. This is a
  binding change to `CONSTITUTION.md` and must be documented here.
- **Neutral**: The submodule requires `git clone --recurse-submodules` and
  CI submodule checkout. Once the crypto module reaches a stable release,
  migration to a Maven coordinate is straightforward (remove `includeBuild`,
  remove submodule, add version-pinned dependency).

## Related

- [CONSTITUTION.md §Technical Constraints](../../../CONSTITUTION.md#technical-constraints)
- [Integration guide: MeshLink-crypto](https://github.com/trancee/MeshLink-crypto/blob/main/docs/how-to/integrate-kmp.md)
- [ADR-0006: Module layout (MeshLink-crypto)](https://github.com/trancee/MeshLink-crypto/blob/main/docs/adr/0006-module-layout.md)
- [ADR-0007: Build quality toolchain](https://github.com/trancee/MeshLink-crypto/blob/main/docs/adr/0007-build-quality-toolchain.md)
