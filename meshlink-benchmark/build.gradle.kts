// meshlink-benchmark is a JVM-only microbenchmark module using kotlinx-benchmark
// (JMH-based) for the :meshlink library. Benchmarks live in src/jmh/kotlin;
// smoke unit tests live in src/test/kotlin. The plugin is wired up; benchmark
// scenarios will expand once the wire codec and routing layers are implemented.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.benchmark)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
}

kotlin { jvmToolchain(21) }

benchmark {
    configurations.register("test") {
        iterations = 3
        iterationTime = 1L
        iterationTimeUnit = "s"
    }
}

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
