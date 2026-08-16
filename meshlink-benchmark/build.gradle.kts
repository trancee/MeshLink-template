// meshlink-benchmark is a JVM-only smoke-benchmark module. The real-device
// benchmark fleet is out of scope for this Gradle skeleton — see
// docs/explanation/module-structure.md. The kotlinx-benchmark plugin will be
// wired in once actual benchmarks are written; for now this is a plain
// Kotlin/JVM module depending on :meshlink so `check` has something real to
// run.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":meshlink"))
    testImplementation(kotlin("test"))
}

detekt { buildUponDefaultConfig = true }

spotless {
    kotlin {
        ktfmt().kotlinlangStyle()
    }
    kotlinGradle {
        ktfmt().kotlinlangStyle()
    }
}
