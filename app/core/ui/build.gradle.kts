import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.kotlinSerialization)
    kotlin("plugin.parcelize")
    alias(libs.plugins.androidLint)
}

kotlin {
    android {
        namespace = "com.dot.gallery.core.ui"
        compileSdk = 37
        minSdk = 29
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        lint {
            baseline = file("lint-baseline.xml")
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    targets.configureEach {
        if (name == "android") {
            compilations.configureEach {
                compileTaskProvider.configure {
                    compilerOptions.freeCompilerArgs.addAll(
                        "-P",
                        "plugin:org.jetbrains.kotlin.parcelize:additionalAnnotation=com.dot.gallery.domain.parcel.Parcelize"
                    )
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":app:core:domain"))
            implementation(project(":app:core:data"))
            // org.jetbrains.compose.* redirector coords (the deprecated compose.* accessors
            // resolved to these): they forward to androidx on the Android target and carry
            // the wasmJs npm metadata the androidx artifacts do not publish.
            implementation(libs.cmp.runtime)
            implementation(libs.cmp.foundation)
            implementation(libs.cmp.material3)
            implementation(libs.compose.lifecycle.runtime)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.cmp.ui)
            implementation(libs.cmp.ui.backhandler)
            // api: StringResource appears on public types (e.g. SelectionAction.labelRes)
            api(libs.cmp.components.resources)
            // components-uiToolingPreview is the only artifact shipping
            // org.jetbrains.compose.ui.tooling.preview on wasmJs — the new
            // ui-tooling-preview jar publishes only the androidx package there.
            implementation(libs.cmp.components.ui.tooling.preview)
            implementation(libs.ui.tooling.preview)
            implementation(libs.cmp.material.icons.extended)
            implementation(libs.material.kolor)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlin.inject.runtime)
            implementation(libs.sketch.compose)
            implementation(libs.haze)
            implementation(libs.haze.blur)
            implementation(libs.haze.glass)
            implementation(libs.haze.glass.material3)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.compose.activity)
            implementation(libs.androidx.lifecycle.runtime.ktx)
            implementation(libs.javax.inject)
            implementation(libs.compose.material3.window.size)
            implementation(libs.compose.shimmer)
            implementation(libs.datastore.prefs)
        }
    }
}

// Default derivation produced `gallery.core.ui.generated.resources`; pin it to the
// module namespace so imports stay `com.dot.gallery.core.ui.generated.resources.Res`.
compose.resources {
    packageOfResClass = "com.dot.gallery.core.ui.generated.resources"
    // public: app callers resolve shared strings (e.g. SelectionAction.labelRes) cross-module
    publicResClass = true
}

// `variant.sources.assets` is null under com.android.kotlin.multiplatform.library, so CMP
// registers copyAndroidMainComposeResourcesToAndroidAssets without an output dir and
// composeResources never reach the APK. Package them onto the androidMain classpath
// ourselves -- the resource reader falls back to classLoader.getResourceAsStream for
// anything not in assets. NOTE: CMP's androidMain `Font(path, assets)` bypasses the
// resource reader and reads ONLY through the AssetManager, so fonts additionally need
// real assets -- the `:app:androidApp` module merges every module's generated/composeResAssets
// output into its own variant assets for that reason.
val packageAndroidComposeResources by tasks.registering(Copy::class) {
    dependsOn(
        "prepareComposeResourcesTaskForCommonMain",
        "prepareComposeResourcesTaskForAndroidMain"
    )
    from(layout.buildDirectory.dir("generated/compose/resourceGenerator/preparedResources/commonMain/composeResources"))
    from(layout.buildDirectory.dir("generated/compose/resourceGenerator/preparedResources/androidMain/composeResources"))
    into(layout.buildDirectory.dir("generated/composeResAssets/composeResources/com.dot.gallery.core.ui.generated.resources"))
}
kotlin.sourceSets.getByName("androidMain").resources.srcDir(
    // srcDir at the composeResAssets root (parent of the Copy destination) so resource
    // paths keep their composeResources/<package> prefix; the provider carries the dep
    packageAndroidComposeResources.map { it.destinationDir.parentFile.parentFile }
)
