import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenExec

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // Android variant so :app:docs:capture (the on-device capture tier) can iterate
    // the same commonMain scene registry the wasmJs live host serves.
    android {
        namespace = "com.dot.gallery.docs.showcase"
        compileSdk = 37
        minSdk = 29
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "docs-showcase.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":app:core:ui"))
            implementation(project(":app:core:domain"))
            implementation(project(":app:core:data"))
            implementation(project(":app:feature:settings"))
            implementation(project(":app:feature:timeline"))
            implementation(project(":app:feature:albums"))
            implementation(project(":app:feature:library"))
            implementation(project(":app:feature:classifier"))
            implementation(project(":app:feature:setup"))
            implementation(project(":app:feature:vault"))
            implementation(project(":app:feature:ignored"))
            implementation(project(":app:feature:picker"))
            implementation(project(":app:feature:help"))
            implementation(project(":app:feature:exif"))
            implementation(project(":app:feature:viewer"))
            implementation(libs.cmp.runtime)
            implementation(libs.cmp.foundation)
            implementation(libs.cmp.material3)
            implementation(libs.cmp.ui)
            implementation(libs.cmp.components.resources)
            implementation(libs.cmp.material.icons.extended)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.haze)
            implementation(libs.haze.blur)
            implementation(libs.haze.blur.materials)
        }
        androidMain.dependencies {
            implementation(project(":app:commonApp"))
            implementation(project(":editor:android"))
        }
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

compose.resources {
    packageOfResClass = "com.dot.gallery.docs.showcase.generated.resources"
}

// Same asset-packaging workaround as core/ui: under com.android.kotlin.multiplatform.library
// CMP never gets an assets output dir, so copy prepared composeResources onto the androidMain
// classpath ourselves (the resource reader falls back to classLoader.getResourceAsStream).
val packageAndroidComposeResources by tasks.registering(Copy::class) {
    dependsOn(
        "prepareComposeResourcesTaskForCommonMain",
        "prepareComposeResourcesTaskForAndroidMain"
    )
    from(layout.buildDirectory.dir("generated/compose/resourceGenerator/preparedResources/commonMain/composeResources"))
    from(layout.buildDirectory.dir("generated/compose/resourceGenerator/preparedResources/androidMain/composeResources"))
    into(
        layout.buildDirectory.dir(
            "generated/composeResAssets/composeResources/com.dot.gallery.docs.showcase.generated.resources"
        )
    )
}
kotlin.sourceSets.getByName("androidMain").resources.srcDir(
    packageAndroidComposeResources.map { it.destinationDir.parentFile.parentFile }
)

// The default production pipeline (repeated -O3 with --gufa and --type-ssa) needs over 14 GB
// for this bundle and fails on a 32 GB machine. The docs host keeps Kotlin 2.4.10's feature
// flags and replaces only the pass list with -O2 -Oz: about 2.3 GB, 19.6 MB of wasm.
tasks.withType<BinaryenExec>().configureEach {
    binaryenArguments.set(
        listOf(
            "--enable-gc",
            "--enable-reference-types",
            "--enable-exception-handling",
            "--enable-bulk-memory",
            "--enable-nontrapping-float-to-int",
            "--no-inline=kotlin.wasm.internal.throwValue",
            "--no-inline=kotlin.wasm.internal.getKotlinException",
            "--no-inline=kotlin.wasm.internal.jsToKotlinStringAdapter",
            "--inline-functions-with-loops",
            "--traps-never-happen",
            "--fast-math",
            "--closed-world",
            "-O2",
            "-Oz",
        )
    )
}
