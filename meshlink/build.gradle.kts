import org.gradle.api.GradleException
import org.jetbrains.kotlin.konan.target.HostManager

// Validate YAML specs at configuration time - use root project's specs directory
val specsDir = project.rootProject.file("specs")
val requiredFiles =
    listOf(
        "enums.yaml",
        "data-models.yaml",
        "state-machines.yaml",
        "diagnostic-events.yaml",
        "settings.yaml",
        "wire-frames.yaml",
        "cross-ref-index.yaml",
    )

for (name in requiredFiles) {
    val file = project.rootProject.file("specs/$name")
    if (!file.exists()) {
        throw GradleException("Missing specs/$name")
    }
}

println("✓ All YAML spec files present")

// :meshlink is the single artifact this repository ships. Per
// CONSTITUTION.md's Technical Constraints, only this module: is validated for 100%
// coverage (Kover), has its public API frozen (Binary Compatibility
// Validator), and is documented/exported via Dokka and SKIE. None of that
// applies to meshlink-reference/meshlink-proof/meshlink-benchmark.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.kover)
    alias(libs.plugins.dokka)
    alias(libs.plugins.skie)
}

kotlin {
    explicitApi()
    jvmToolchain(21)

    android {
        namespace = "ch.trancee.meshlink"
        compileSdk = 36
        minSdk = 26
        // Enables local unit tests (androidHostTest); device tests are not
        // needed here since :meshlink itself has no Android-specific code.
        withHostTestBuilder {}.configure {}
    }

    // Host tests (fast, JVM-only) in addition to the shipped Android target.
    jvm()

    // Kotlin/Native cannot cross-compile Apple targets on a non-macOS host,
    // so these are only registered when Gradle evaluates the build on
    // macOS (matches the "ios" job split in .github/workflows/ci.yml).
    if (HostManager.hostIsMac) {
        listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "MeshLink"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain.dependencies { implementation(libs.skie.configuration.annotations) }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}

detekt { buildUponDefaultConfig = true }

ktfmt { kotlinLangStyle() }

kover { reports { verify { rule { minBound(100) } } } }

skie {
    isEnabled.set(true)
    analytics { disableUpload.set(true) }
    build { produceDistributableFramework() }
}
