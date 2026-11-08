pluginManagement {
    includeBuild("plugins")
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
    // PREFER_SETTINGS rather than FAIL_ON_PROJECT_REPOS: Kotlin's wasmJs tooling
    // injects distribution repos (Node.js, Yarn) into the project at execution
    // time, which the strict mode rejects as "added by unknown code". They are
    // ignored for project deps; the mirrors below serve the tool downloads.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        mavenLocal()
        ivy("https://nodejs.org/dist") {
            name = "Node.js"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            name = "Yarn"
            patternLayout { artifact("v[revision]/[artifact]-v[revision].[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
        ivy("https://github.com/WebAssembly/binaryen/releases/download") {
            name = "Binaryen"
            patternLayout { artifact("version_[revision]/[artifact]-version_[revision]-[classifier].[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.github.webassembly", "binaryen") }
        }
    }
}
rootProject.name = "Gallery"
include(":app:androidApp")
include(":app:commonApp")
include(":app:desktopApp")
include(":app:core:ui")
include(":app:core:domain")
include(":app:core:data")
include(":app:docs:showcase")
include(":app:docs:capture")
include(":app:feature:settings")
include(":app:feature:albums")
include(":app:feature:setup")
include(":app:feature:help")
include(":app:feature:vault")
include(":app:feature:timeline")
include(":app:feature:library")
include(":app:feature:ignored")
include(":app:feature:classifier")
include(":app:feature:exif")
include(":app:feature:picker")
include(":app:feature:viewer")
include(":app:feature:editor")
include(":app:feature:search")
include(":app:baselineprofile")
include(":app:libs:gesture")
include(":app:libs:cropper")
include(":app:libs:panoramaviewer")
include(":app:ml-models")
include(":app:ml-models-pack")
include(":editor:core")
include(":editor:render")
include(":editor:io")
include(":editor:android")
include(":editor:desktop")
include(":editor:ui")
include(":editor:video-codec")