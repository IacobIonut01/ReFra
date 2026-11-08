import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
}

// Cropper interaction machinery (state, gestures, overlay drawing) is platform-free,
// so commonMain serves both the Android app and the standalone desktop editor.
// The bitmap pipeline (ImageCropper composable + CropAgent) stays in androidMain
// because it is built on android.graphics.Bitmap.
kotlin {
    android {
        namespace = "com.smarttoolfactory.cropper"
        compileSdk = 37
        minSdk = 29
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
            implementation(libs.cmp.foundation)
            implementation(libs.cmp.ui)
            implementation(libs.cmp.runtime)
            implementation(libs.cmp.material3)
            implementation(libs.cmp.material.icons.extended)
            implementation(libs.kotlinx.coroutines.core)

            api(project(":app:libs:gesture"))
        }
    }
}
