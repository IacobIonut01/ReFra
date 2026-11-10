import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
}

// ML engine layer: ONNX runtime sessions, model download/install management
// (ModelManager), face detection (FaceHelper/DetectedFaceBox) and the SAM cutout
// engine (CutoutHelper). The model *files* are delivered by :app:ml-models-pack
// (asset pack / WithML bundled assets) — this module is pure code.
//
// Depends on :app:core:ui for Settings (model-removal prefs), ShareUtils and the
// cutout Res strings. That edge is a layering wart — Settings/ShareUtils should
// eventually drop to domain/data and this edge go away — but moving them is its
// own refactor.
kotlin {
    android {
        namespace = "com.dot.gallery.ml"
        compileSdk = 37
        minSdk = 29
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        // The OCR catalog and recognizer dictionaries ship as class-path resources (ocr/...). The
        // model weights are never bundled; the CLDR coverage snapshot under catalog/coverage is test-only.
        androidMain.get().resources.srcDir("catalog/runtime")
        androidMain.dependencies {
            implementation(project(":app:core:domain"))
            implementation(project(":app:core:data"))
            implementation(project(":app:core:ui"))
            implementation(libs.onnxruntime.android)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.exifinterface)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.javax.inject)
            implementation(libs.kotlin.inject.runtime)
            implementation(libs.cmp.components.resources)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
