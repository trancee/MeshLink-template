import org.jetbrains.kotlin.konan.target.HostManager

// meshlink-reference is a reference/demo consumer of the public :meshlink
// API only. It intentionally carries none of :meshlink's quality gates
// (Dokka, SKIE, 100% coverage, API validation) — see
// CONSTITUTION.md's Technical Constraints and
// docs/explanation/module-structure.md.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktfmt)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "ch.trancee.meshlink.reference"
        compileSdk = 36
        minSdk = 26
    }

    if (HostManager.hostIsMac) {
        listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "MeshLinkReference"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":meshlink"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
        }
    }
}

detekt { buildUponDefaultConfig = true }

ktfmt { kotlinLangStyle() }
