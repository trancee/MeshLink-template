// Standalone synthetic-byte probe; no production MeshLink runtime or crypto.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.power.assert)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
}

android {
    namespace = "ch.trancee.meshlink.proof"
    compileSdk = 37

    defaultConfig {
        applicationId = "ch.trancee.meshlink.proof"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(21) }

dependencies {
    compileOnly(libs.androidx.annotation)
    testImplementation(kotlin("test-junit"))
}

powerAssert { functions = listOf("kotlin.test.assertTrue", "kotlin.test.assertEquals") }

detekt { buildUponDefaultConfig = true }

spotless {
    kotlin {
        ktfmt().kotlinlangStyle()
    }
    kotlinGradle {
        ktfmt().kotlinlangStyle()
    }
}
