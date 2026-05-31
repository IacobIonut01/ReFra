import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kspAndroid)
    alias(libs.plugins.roomPlugin)
    alias(libs.plugins.hiltAndroid)
    alias(libs.plugins.baselineProfilePlugin)
    alias(libs.plugins.kotlin.compose.compiler)
    id("kotlin-parcelize")
    alias(libs.plugins.kotlinSerialization)
    id("apk-versioning")
    id("manifest-config")
}

manifestConfig {
    if (isOffline) {
        stripPermissions.set(
            listOf(
                "android.permission.INTERNET",
                "android.permission.ACCESS_WIFI_STATE",
                "android.permission.ACCESS_NETWORK_STATE",
                "android.permission.CHANGE_WIFI_MULTICAST_STATE"
            )
        )
    }
}

val abiVersionCodes = mapOf(
    "arm64-v8a" to 4,
    "armeabi-v7a" to 3,
    "x86_64" to 2,
    "x86" to 1,
    "universal" to 0
)

apkVersioning {
    flavorVersionCodes.set(abiVersionCodes)
    versionCodeMultiplier.set(10)
    outputFileName.set("{appName}-{versionName}-{versionCode}{suffix}-{ml}-{abi}-{buildType}")
    variables.put("appName", "ReFra")
    val offlineSuffix = if (isOffline) "-offline" else ""
    variables.put("suffix", offlineSuffix)
}

android {
    namespace = "com.dot.gallery"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dot.gallery"
        minSdk = 29
        targetSdk = 37
        versionCode = 50001
        versionName = "5.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        val offlinePrefix = if (isOffline) "-offline" else ""
        base.archivesName.set("ReFra-${versionName}-$versionCode$offlinePrefix")
    }

    lint.baseline = file("lint-baseline.xml")

    signingConfigs {
        create("release") {
            storeFile = file("release_key.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appProvider"] = "com.dot.gallery.debug.media_provider"
            buildConfigField("Boolean", "ALLOW_ALL_FILES_ACCESS", "$allowAllFilesAccess")
            buildConfigField("Boolean", "OFFLINE_MODE", "$isOffline")
            buildConfigField("Boolean", "MAPS_ENABLED", "$includeMaps")
            buildConfigField("Boolean", "IMMICH_ENABLED", "$includeImmich")
            buildConfigField("Boolean", "OWNCLOUD_ENABLED", "$includeOwncloud")
            buildConfigField(
                "String",
                "CONTENT_AUTHORITY",
                "\"com.dot.gallery.debug.media_provider\""
            )
            buildConfigField("Boolean", "ENABLE_INDEXING", "false")
            buildConfigField("Boolean", "ALLOW_INSECURE_TLS", "true")
        }
        getByName("release") {
            manifestPlaceholders += mapOf(
                "appProvider" to "com.dot.gallery.media_provider"
            )
            isMinifyEnabled = true
            isShrinkResources = true
            setProguardFiles(
                listOf(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            )
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("Boolean", "ALLOW_ALL_FILES_ACCESS", "$allowAllFilesAccess")
            buildConfigField("Boolean", "OFFLINE_MODE", "$isOffline")
            buildConfigField("Boolean", "MAPS_ENABLED", "$includeMaps")
            buildConfigField("Boolean", "IMMICH_ENABLED", "$includeImmich")
            buildConfigField("Boolean", "OWNCLOUD_ENABLED", "$includeOwncloud")
            buildConfigField("String", "CONTENT_AUTHORITY", "\"com.dot.gallery.media_provider\"")
            buildConfigField("Boolean", "ENABLE_INDEXING", "true")
            buildConfigField("Boolean", "ALLOW_INSECURE_TLS", "true")
        }
        create("staging") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            isMinifyEnabled = false
            isShrinkResources = false
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            manifestPlaceholders["appProvider"] = "com.dot.staging.debug.media_provider"
            buildConfigField(
                "String",
                "CONTENT_AUTHORITY",
                "\"com.dot.staging.debug.media_provider\""
            )
            buildConfigField("Boolean", "ALLOW_ALL_FILES_ACCESS", "$allowAllFilesAccess")
            buildConfigField("Boolean", "ENABLE_INDEXING", "true")
            buildConfigField("Boolean", "OFFLINE_MODE", "$isOffline")
            buildConfigField("Boolean", "MAPS_ENABLED", "$includeMaps")
            buildConfigField("Boolean", "IMMICH_ENABLED", "$includeImmich")
            buildConfigField("Boolean", "OWNCLOUD_ENABLED", "$includeOwncloud")
            buildConfigField("Boolean", "ALLOW_INSECURE_TLS", "true")
        }
        create("gplay") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            applicationIdSuffix = ".gplay"
            ndk.debugSymbolLevel = "FULL"
            manifestPlaceholders["appProvider"] = "com.dot.gallery.gplay.media_provider"
            buildConfigField("Boolean", "ALLOW_ALL_FILES_ACCESS", "false")
            buildConfigField("Boolean", "OFFLINE_MODE", "$isOffline")
            buildConfigField("Boolean", "MAPS_ENABLED", "$includeMaps")
            buildConfigField("Boolean", "IMMICH_ENABLED", "$includeImmich")
            buildConfigField("Boolean", "OWNCLOUD_ENABLED", "$includeOwncloud")
            buildConfigField("String", "CONTENT_AUTHORITY", "\"com.dot.gallery.gplay.media_provider\"")
            buildConfigField("Boolean", "ENABLE_INDEXING", "true")
            buildConfigField("Boolean", "ALLOW_INSECURE_TLS", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    assetPacks += listOf(":ml-models")

    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
    }

    sourceSets {
        getByName("main") {
            // Conditional maps/offline source set
            if (!isOffline) {
                kotlin.srcDir("src/maps/kotlin")
            } else {
                kotlin.srcDir("src/offline/kotlin")
            }
            // Conditional cloud provider source sets
            if (includeImmich) {
                kotlin.srcDir("src/immich/kotlin")
            } else {
                kotlin.srcDir("src/noimmich/kotlin")
            }
            if (includeOwncloud) {
                kotlin.srcDir("src/owncloud/kotlin")
            } else {
                kotlin.srcDir("src/noowncloud/kotlin")
            }
        }
        // For withML APK builds, include ML model assets directly
        // (asset packs are AAB-only, so for APK builds we inline them)
        val isBundleBuild = gradle.startParameter.taskNames.any {
            it.contains("bundle", ignoreCase = true)
        }
        if (!isBundleBuild) {
            maybeCreate("WithML").apply {
                assets.srcDirs("../ml-models/src/main/assets")
            }
        }
    }

    flavorDimensions += listOf("abi", "ml")
    productFlavors {
        abiVersionCodes.forEach { (abi, _) ->
            create(abi) {
                dimension = "abi"
                if (abi == "universal") {
                    ndk.abiFilters.addAll(listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a"))
                } else {
                    ndk.abiFilters.add(abi)
                }
            }
        }
        create("WithML") {
            dimension = "ml"
            buildConfigField("Boolean", "ML_MODELS_BUNDLED", "true")
        }
        create("NoML") {
            dimension = "ml"
            buildConfigField("Boolean", "ML_MODELS_BUNDLED", "false")
        }
    }

}

room {
    schemaDirectory("$projectDir/schemas/")
}

composeCompiler {
    includeSourceInformation = true
    stabilityConfigurationFiles = listOf(
        rootProject.layout.projectDirectory.file("stability_config.conf")
    )
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

dependencies {
    implementation(libs.androidx.lifecycle.process)
    runtimeOnly(libs.androidx.profileinstaller)
    implementation(project(":libs:cropper"))
    implementation(project(":libs:panoramaviewer"))
    "baselineProfile"(project(mapOf("path" to ":baselineprofile")))

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // Core - Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.compose.lifecycle.runtime)

    // Compose
    implementation(libs.compose.activity)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.startup.runtime)

    // Compose - Shimmer
    implementation(libs.compose.shimmer)
    // Compose - Material3
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window.size)
    implementation(libs.androidx.adaptive)
    implementation(libs.androidx.adaptive.layout)
    implementation(libs.androidx.adaptive.navigation)

    // Compose - Accompanists
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.accompanist.drawablepainter)

    // Android MDC - Material
    implementation(libs.material)

    // Kotlin - Coroutines
    implementation(libs.kotlinx.coroutines.core)
    runtimeOnly(libs.kotlinx.coroutines.android)

    // Kotlin - Immutable Collections
    implementation(libs.kotlinx.collections.immutable)

    implementation(libs.kotlinx.serialization.json)

    // Dagger - Hilt
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.dagger.hilt)
    implementation(libs.androidx.hilt.common)
    implementation(libs.androidx.hilt.work)
    ksp(libs.dagger.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    // Room
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    // Kotlin Extensions and Coroutines support for Room
    implementation(libs.room.ktx)

    // SQLCipher for encrypted Room database
    implementation(libs.sqlcipher.android)
    implementation(libs.sqlite.ktx)

    // Coders
    implementation(libs.jxl.coder.coil)
    implementation(libs.avif.coder.coil)

    // Sketch
    implementation(libs.sketch.compose)
    implementation(libs.sketch.view)
    implementation(libs.sketch.animated.gif)
    implementation(libs.sketch.animated.heif)
    implementation(libs.sketch.animated.webp)
    implementation(libs.sketch.extensions.compose)
    implementation(libs.sketch.http.ktor)
    implementation(libs.sketch.svg)
    implementation(libs.sketch.video)

    // Glide
    implementation(libs.glide.compose)
    ksp(libs.glide.ksp)

    // Exo Player
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.hls)

    // Exif Interface
    implementation(libs.androidx.exifinterface)
    implementation(libs.metadata.extractor)

    // NanoHTTPD - Embedded HTTP server for FCast media serving
    implementation(libs.nanohttpd)

    // Datastore Preferences
    implementation(libs.datastore.prefs)

    // Fuzzy Search
    implementation(libs.fuzzywuzzy.kotlin)

    // Aire
    implementation(libs.aire)

    // Subsampling
    implementation(libs.zoomimage.compose.glide)
    implementation(libs.zoomimage.compose.sketch)

    // Splashscreen
    implementation(libs.androidx.core.splashscreen)

    // Jetpack Security
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)

    // Composables - Core
    implementation(libs.core)

    // Worker
    implementation(libs.androidx.work.runtime.ktx)

    // Composable - Scrollbar
    implementation(libs.lazycolumnscrollbar)

    // ONNX Runtime (CPU + NNAPI)
    implementation(libs.onnxruntime.android)

    // Haze
    implementation(libs.haze)
    implementation(libs.haze.materials)

    // MapLibre Native SDK
    if (includeMaps) {
        implementation(libs.maplibre.native)
    }

    implementation(libs.okhttp)
    if (includeImmich || includeOwncloud) {
        implementation(libs.okhttp.logging)
    }

    // Immich
    if (includeImmich) {
        implementation(libs.retrofit)
        implementation(libs.retrofit.kotlinx.serialization)
        implementation(libs.retrofit.converter.gson)
    }

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    debugImplementation(libs.compose.ui.tooling)
    debugRuntimeOnly(libs.compose.ui.test.manifest)
}

val isOffline: Boolean
    get() {
        val fl = rootProject.file("app.properties")
        return try {
            val properties = Properties()
            properties.load(FileInputStream(fl))
            properties.getProperty("OFFLINE", "false").toBoolean()
        } catch (_: Exception) {
            false
        }
    }

val includeMaps: Boolean
    get() = !isOffline

val allowAllFilesAccess: Boolean
    get() {
        val fl = rootProject.file("app.properties")

        return try {
            val properties = Properties()
            properties.load(FileInputStream(fl))
            properties.getProperty("ALL_FILES_ACCESS", "true").toBoolean()
        } catch (_: Exception) {
            true
        }
    }

val includeImmich: Boolean
    get() {
        if (isOffline) return false
        val fl = rootProject.file("app.properties")
        return try {
            val properties = Properties()
            properties.load(FileInputStream(fl))
            properties.getProperty("INCLUDE_IMMICH", "false").toBoolean()
        } catch (_: Exception) {
            false
        }
    }

val includeOwncloud: Boolean
    get() {
        if (isOffline) return false
        val fl = rootProject.file("app.properties")
        return try {
            val properties = Properties()
            properties.load(FileInputStream(fl))
            properties.getProperty("INCLUDE_OWNCLOUD", "false").toBoolean()
        } catch (_: Exception) {
            false
        }
    }