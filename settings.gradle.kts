pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
        mavenLocal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        mavenLocal()
    }
}
rootProject.name = "Gallery"
include(":app")
include(":baselineprofile")
include(":libs:gesture")
include(":libs:cropper")
include(":libs:panoramaviewer")
include(":ml-models")