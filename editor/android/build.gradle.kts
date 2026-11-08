import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlin.compose.compiler)
}

// Android implementation of the editor photo ports (BitmapRegionDecoder /
// BitmapFactory reads, MediaStore-scoped PNG copy export) plus the exported
// EditorActivity host for :editor:ui.
android {
    namespace = "com.dot.gallery.editor.android"
    compileSdk = 37
    ndkVersion = providers.gradleProperty("refra.ndkVersion").get()

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                // The JNI bridge only — standalone kernel tests stay on the
                // host toolchain (see editor/native).
                arguments(
                    "-DREFRA_EDITOR_BUILD_JNI=ON",
                    "-DREFRA_EDITOR_BUILD_TESTS=OFF"
                )
                targets("refra_editor_jni")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("../native/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// editor:ui ships its composeResources on the classpath (not AAR assets), so
// they resolve through classLoader fallback inside this library's test APK.
// Packaged FFmpeg/JavaCPP natives: :editor:video-codec resolves the pinned
// android-arm64/android-x86_64 classifier artifacts and extracts their .so
// payload into generated/videoJniLibs. That generated tree is this library's
// main jniLibs source — 32-bit ABIs get no slice, which the codec module's
// capability gate reports as a typed UnsupportedVideoException at open time.
evaluationDependsOn(":editor:video-codec")
val extractVideoNatives =
    project(":editor:video-codec").tasks.named("extractVideoNatives")

// The same generated 'Sample coast.mp4' the desktop harness and
// integration test exercise, staged into androidTest assets so the native
// capture test runs on real moving footage instead of the synthetic
// colour-band fixture (see TEST_ASSETS.md). Regenerated on demand by
// :editor:desktop:generateVideoSample — no binary is committed.
evaluationDependsOn(":editor:desktop")
val stageVideoCaptureSample = tasks.register<Copy>("stageVideoCaptureSample") {
    dependsOn(":editor:desktop:generateVideoSample")
    from(rootProject.file("editor/desktop/build/fixtures")) {
        include("sample-coast.mp4")
    }
    into(layout.buildDirectory.dir("generated/videoCaptureAssets"))
}

android {
    sourceSets {
        named("main") {
            // Static path only — AGP rejects Provider srcDirs; the producer
            // task dependency is wired on every packaging task below.
            jniLibs.srcDir(
                rootProject.file(
                    "editor/video-codec/build/generated/videoJniLibs"
                )
            )
        }
        named("androidTest") {
            assets.srcDir("build/generated/videoCaptureAssets")
        }
    }
}

tasks.matching {
    it.name.contains("JniLib") ||
        (it.name.startsWith("pre") && it.name.endsWith("Build"))
}.configureEach { dependsOn(extractVideoNatives) }

tasks.matching { it.name.endsWith("AndroidTestAssets") }
    .configureEach { dependsOn(stageVideoCaptureSample) }

// The editor's scanline JPEG encoder (editor/native/src/jpeg_jni.c) links the
// same prebuilt libjpeg-turbo the app's image pipeline uses. Gradle builds this
// library before :app:androidApp, so on a clean tree those artifacts would not
// exist yet when the editor's CMake configures and the encoder would compile to
// stubs. Depend on the producing task explicitly, per ABI.
val imgcodecTaskByAbi = mapOf(
    "arm64-v8a" to "buildImgcodecNativeArm64V8a",
    "armeabi-v7a" to "buildImgcodecNativeArmeabiV7a",
    "x86" to "buildImgcodecNativeX86",
    "x86_64" to "buildImgcodecNativeX8664"
)
tasks.configureEach {
    val abi = imgcodecTaskByAbi.keys.firstOrNull { name.endsWith("[$it]") }
    if (name.startsWith("configureCMake") && abi != null) {
        dependsOn(":app:androidApp:${imgcodecTaskByAbi.getValue(abi)}")
    }
}

dependencies {
    api(project(":editor:io"))
    api(project(":editor:ui"))
    api(project(":editor:video-codec"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.compose.activity)
    // JPEG XL export (libjxl packaged as a shared library). The app already
    // ships the same artifact for JXL decoding, so this adds no APK weight.
    implementation(libs.jxl.coder)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.compose.lifecycle.runtime)

    testImplementation(libs.junit)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.compose.ui.test.manifest)
}
