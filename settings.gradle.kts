pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "meshlink"

// See docs/explanation/module-structure.md for what each module is for and
// why meshlink-reference/meshlink-proof are kept separate.
include(":meshlink")
include(":meshlink-reference")
include(":meshlink-proof")
include(":meshlink-benchmark")

// MeshLink-crypto is a git submodule at meshlink-crypto/. Its :crypto module
// provides all RFC-standard crypto primitives (SHA-256, HKDF, HMAC, X25519,
// Ed25519, ChaCha20-Poly1305) with pure-Kotlin implementations. The composite
// build makes it available as a dependency without requiring a Maven Central
// release (currently 0.1.0-SNAPSHOT). See docs/explanation/module-structure.md.
//
// When :crypto ships its first stable release to Maven Central, this
// includeBuild can be removed in favor of a version-pinned coordinate in
// libs.versions.toml.
includeBuild("meshlink-crypto")
