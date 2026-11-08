import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
}

// Shared photo-editor UI: adaptive workspace, tool inspectors, canvas
// pan/zoom and the session-driven top bar. Hosts supply their own theme via
// a wrapper (GalleryTheme on Android) or the bundled EditorTheme.
kotlin {
    android {
        namespace = "com.dot.gallery.editor.ui"
        compileSdk = 37
        minSdk = 29
        withHostTest {}
        withDeviceTest {}
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":editor:render"))
            // The crop window rides the shared cropper: DynamicCropState
            // gestures, the dim+grid+handles DrawingOverlay.
            implementation(project(":app:libs:cropper"))
            api(libs.cmp.runtime)
            api(libs.cmp.foundation)
            api(libs.cmp.material3)
            api(libs.cmp.ui)
            api(libs.cmp.material.icons.extended)
            api(libs.cmp.components.resources)
            api(libs.cmp.components.ui.tooling.preview)
            api(libs.cmp.ui.backhandler)
            api(libs.androidx.navigationevent.compose)
            api(libs.kotlinx.coroutines.core)
            // Haze2: floating chrome blurs the workspace behind it; the
            // EditorUiPreferences blur toggle swaps to an opaque surface
            // with identical geometry (spec: approved v2 chrome material).
            implementation(libs.haze)
            implementation(libs.haze.blur)
            // ZoomImage owns viewing; request-bound Sketch decoders translate
            // editor pixels and zone decoders read the immutable render recipe.
            api(libs.zoomimage.compose.sketch)
            implementation(libs.sketch.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        named("desktopMain") {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
        named("desktopTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(compose.desktop.currentOs)
                implementation(compose.desktop.uiTestJUnit4)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        // Device tests run against internals as a friend module — the AGSL
        // renderer tests need the package-private factory.
        named("androidDeviceTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.junit)
                implementation(libs.androidx.test.ext.junit)
                implementation(libs.androidx.test.runner)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

compose.resources {
    packageOfResClass = "com.dot.gallery.editor.ui.generated.resources"
    publicResClass = true
}

// `variant.sources.assets` is null under com.android.kotlin.multiplatform.library, so CMP
// registers copyAndroidMainComposeResourcesToAndroidAssets without an output dir and
// composeResources never reach the APK. Package them onto the androidMain classpath
// ourselves -- the resource reader falls back to classLoader.getResourceAsStream for
// anything not in assets. (Same workaround as app/feature/editor.)
val packageAndroidComposeResources by tasks.registering(Copy::class) {
    dependsOn(
        "prepareComposeResourcesTaskForCommonMain",
        "prepareComposeResourcesTaskForAndroidMain"
    )
    from(layout.buildDirectory.dir("generated/compose/resourceGenerator/preparedResources/commonMain/composeResources"))
    from(layout.buildDirectory.dir("generated/compose/resourceGenerator/preparedResources/androidMain/composeResources"))
    into(layout.buildDirectory.dir("generated/composeResAssets/composeResources/com.dot.gallery.editor.ui.generated.resources"))
}
kotlin.sourceSets.getByName("androidMain").resources.srcDir(
    packageAndroidComposeResources.map { it.destinationDir.parentFile.parentFile }
)

// The deviceTest variant hits the same null variant.sources.assets gap, so
// CMP cannot wire this task's outputDirectory. Shader device tests consume
// no composeResources — disable the copy rather than fake an assets dir.
// Registered lazily inside onVariants, hence matching().configureEach.
tasks.matching {
    it.name == "copyAndroidDeviceTestComposeResourcesToAndroidAssets"
}.configureEach {
    enabled = false
}

// Keyboard traversal tests tab through every rail cell one key at a time, so
// their run time grows with the tool list and outlasts runTest's default minute.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    systemProperty("kotlinx.coroutines.test.default_timeout", "180s")
}
