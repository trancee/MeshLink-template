// meshlink-proof is the real-device BLE proof harness. Per this project's
// BLE-testing rules (BLE does not work on emulators/simulators), it is
// Android-only and is only ever exercised on real hardware — never as
// emulator coverage. See docs/explanation/module-structure.md.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
}

android {
    namespace = "ch.trancee.meshlink.proof"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":meshlink"))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
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
