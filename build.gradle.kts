// Root build file. Individual modules apply their own plugins (see each
// module's build.gradle.kts and docs/explanation/module-structure.md).
// Dokka/SKIE/Kover are scoped to :meshlink only, per CONSTITUTION.md's
// Technical Constraints, so they are declared directly in
// meshlink/build.gradle.kts instead of at the root.
//
// Binary Compatibility Validator is the one exception: it must be applied
// at the root (subprojects are configured automatically), so it is applied
// here and scoped to :meshlink via ignoredProjects below.

plugins {
    // Ensures the Kotlin/Android Gradle plugins resolve a single version
    // across all subprojects without forcing every module to declare them.
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktfmt) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.skie) apply false
    alias(libs.plugins.binary.compatibility.validator)
}

apiValidation {
    // :meshlink is the only artifact this repository ships; its public API
    // is what gets frozen and validated. See CONSTITUTION.md's Technical
    // Constraints.
    ignoredProjects.addAll(listOf("meshlink-reference", "meshlink-proof", "meshlink-benchmark"))
}
