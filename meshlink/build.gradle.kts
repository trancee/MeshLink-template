import dev.detekt.gradle.Detekt
import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.gradle.api.GradleException
import org.jetbrains.kotlin.konan.target.HostManager

// Validate YAML specs at configuration time - use root project's specs directory
val specsDir = project.rootProject.file("specs")
val requiredFiles =
    listOf(
        "specs/codecs/enums.yaml",
        "specs/codecs/models.yaml",
        "specs/codecs/frames.yaml",
        "specs/protocol/state-machines.yaml",
        "specs/catalogs/diagnostic-events.yaml",
        "specs/catalogs/settings.yaml",
        "specs/traceability/specification-map.yaml",
    )

for (path in requiredFiles) {
    val file = project.rootProject.file(path)
    if (!file.exists()) {
        throw GradleException("Missing $path")
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
    alias(libs.plugins.spotless)
    alias(libs.plugins.kover)
    alias(libs.plugins.dokka)
    alias(libs.plugins.skie)
}

kotlin {
    explicitApi()
    jvmToolchain(21)

    compilerOptions {
        // Suppress pre-existing warning in ConstantTimeTest.kt for unsigned types.
        optIn.add("kotlin.ExperimentalUnsignedTypes")
    }

    android {
        namespace = "ch.trancee.meshlink"
        compileSdk = 37
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
        listOf(iosArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "MeshLink"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.skie.configuration.annotations)
            implementation(libs.kotlinx.coroutines.core)
            // MeshLink-crypto KMP module — provides SHA-256, HKDF, HMAC, X25519,
            // Ed25519, and ChaCha20-Poly1305 with pure-Kotlin implementations and
            // per-primitive native dispatch. Consumed as a version-pinned Maven
            // Central dependency (ch.trancee.meshlink:meshlink-crypto, v0.1.1,
            // via libs.meshlink.crypto in gradle/libs.versions.toml). The iOS
            // simulator target was removed because BLE radios are not available
            // in the iOS simulator; non-radio logic is covered by JVM host tests.
            // See docs/explanation/module-structure.md and
            // docs/decisions/crypto/meshlink-crypto-dependency.md.
            implementation(libs.meshlink.crypto)
        }

        commonTest.dependencies { implementation(kotlin("test")) }
    }
}

detekt { buildUponDefaultConfig = true }

tasks.withType<Detekt>().configureEach {
    setSource(files("src/commonMain/kotlin", "src/commonTest/kotlin"))
    include("**/*.kt")
}

spotless {
    kotlin {
        ktfmt().kotlinlangStyle()
    }
    kotlinGradle {
        ktfmt().kotlinlangStyle()
    }
}

kover {
    reports {
        verify {
            rule("line coverage") {
                bound {
                    aggregationForGroup = AggregationType.COVERED_PERCENTAGE
                    coverageUnits = CoverageUnit.LINE
                    minValue = 100
                }
            }
            rule("branch coverage") {
                bound {
                    aggregationForGroup = AggregationType.COVERED_PERCENTAGE
                    coverageUnits = CoverageUnit.BRANCH
                    minValue = 100
                }
            }
        }
    }
}

// Guarantee the XML report runs whenever ./gradlew check executes,
// even if tests were previously cached.
tasks.check { dependsOn(tasks.koverXmlReport) }

skie {
    isEnabled.set(true)
    analytics { disableUpload.set(true) }
    build { produceDistributableFramework() }
}
