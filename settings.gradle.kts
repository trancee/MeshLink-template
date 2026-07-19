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
