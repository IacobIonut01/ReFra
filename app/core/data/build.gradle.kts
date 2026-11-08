import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kspAndroid)
    alias(libs.plugins.room3Plugin)
    kotlin("plugin.parcelize")
}

kotlin {
    android {
        namespace = "com.dot.gallery.core.data"
        compileSdk = 37
        minSdk = 29
        withHostTest {}
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
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
        named("androidHostTest") {
            dependencies {
                implementation(libs.junit)
            }
        }
        commonMain.dependencies {
            implementation(project(":app:core:domain"))
            implementation(libs.kotlin.inject.runtime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            api(libs.room3.runtime)
            api(libs.room3.migration)
            api(libs.androidx.sqlite)
            api(libs.sqlite.async)
            implementation(libs.cmp.runtime)
            implementation(libs.cmp.ui)
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        androidMain.dependencies {
            implementation(libs.sqlite.bundled)
            implementation(libs.sqlcipher.android)
            implementation(libs.androidx.core.ktx)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.security.crypto)
            implementation(libs.javax.inject)
            implementation(libs.datastore.prefs)
            implementation(libs.androidx.exifinterface)
            // Encrypted media decode + isolated metadata + workers moved from :app
            implementation(project(":app:libs:panoramaviewer"))
            implementation(libs.sketch.core)
            implementation(libs.sketch.animated.core)
            implementation(libs.sketch.video)
            implementation(libs.sketch.view)
            implementation(libs.sketch.animated.gif)
            implementation(libs.jxl.coder.coil)

            implementation(libs.zoomimage.core)
            implementation(libs.metadata.extractor)
            implementation(libs.kotlin.inject.runtime)
            implementation(libs.okio)
            implementation(libs.compose.activity)
        }
        wasmJsMain.dependencies {
            implementation(libs.sqlite.web)
            implementation(libs.kotlinx.browser)
            implementation(libs.ktor.client.js)
        }
    }
}

dependencies {
    add("kspAndroid", libs.room3.compiler)
    add("kspWasmJs", libs.room3.compiler)
    add("kspDesktop", libs.room3.compiler)
}

extensions.configure(androidx.room3.gradle.RoomExtension::class.java) {
    schemaDirectory("$projectDir/schemas")
}
