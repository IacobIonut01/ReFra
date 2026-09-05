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
                "android.permission.CHANGE_WIFI_MULTICAST_STATE",
                "android.permission.ACCESS_FINE_LOCATION"
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
val pinnedNdkVersion = Properties().run {
    rootProject.file("gradle.properties").inputStream().use { load(it) }
    getProperty("refra.ndkVersion") ?: error("Missing refra.ndkVersion in gradle.properties")
}

apkVersioning {
    flavorVersionCodes.set(abiVersionCodes)
    versionCodeMultiplier.set(10)
    outputFileName.set("{appName}-{versionName}-{versionCode}{suffix}-{ml}-{abi}-{buildType}")
    variables.put("appName", "ReFra")
    val offlineSuffix = if (isOffline) "-offline" else ""
    variables.put("suffix", offlineSuffix)
}

val copySegmentModelsTask = tasks.register<Copy>("copySegmentModels") {
    from(file("../ml-models/segment")) {
        include("mobile_sam_image_encoder.onnx")
        include("sam_mask_decoder_single.onnx")
    }
    into(layout.buildDirectory.dir("generated/assets/ml-models"))
}

val nativeDownloadCache = rootProject.file("build/native-downloads")
val nativeCommonScript = rootProject.file("scripts/native/native-common.sh")
val nativeStacks = listOf(
    Triple("Heif", "build-heif.sh", "heif"),
    Triple("Imgcodec", "build-imgcodec.sh", "imgcodec"),
    Triple("HeifEncode", "build-heif-encode.sh", "heifenc"),
    Triple("Libraw", "build-libraw.sh", "rawcodec")
)
val nativeAbiTaskSuffixes = mapOf(
    "arm64-v8a" to "Arm64V8a",
    "armeabi-v7a" to "ArmeabiV7a",
    "x86_64" to "X8664",
    "x86" to "X86"
)
val nativeSourceOverrides = listOf(
    "NATIVE_SOURCES_DIR",
    "LIBDE265_SOURCE_DIR",
    "LIBHEIF_SOURCE_DIR",
    "ZLIB_SOURCE_DIR",
    "LIBPNG_SOURCE_DIR",
    "LIBJPEG_TURBO_SOURCE_DIR",
    "X265_SOURCE_DIR",
    "AOM_SOURCE_DIR",
    "LIBRAW_SOURCE_DIR",
    "LIBTIFF_SOURCE_DIR"
)
val nativeTasksByAbi = nativeAbiTaskSuffixes.mapValues { (abi, suffix) ->
    nativeStacks.map { (stack, scriptName, outputName) ->
        val script = rootProject.file("scripts/native/$scriptName")
        tasks.register<Exec>("build${stack}Native$suffix") {
            group = "build"
            workingDir(rootProject.projectDir)
            commandLine("bash", script.absolutePath, abi)
            environment("NATIVE_OFFLINE", "0")
            environment("NATIVE_SOURCE_ARCHIVES_DIR", "")
            environment("NATIVE_DOWNLOAD_CACHE", nativeDownloadCache.absolutePath)
            environment("NATIVE_OUTPUT_BASE", file("src/main/cpp").absolutePath)
            nativeSourceOverrides.forEach { environment(it, "") }
            inputs.file(script)
            inputs.file(nativeCommonScript)
            inputs.file(rootProject.file("gradle.properties"))
            inputs.property("ndkVersion", pinnedNdkVersion)
            inputs.property("nativeSourceMode", "download")
            outputs.dir(file("src/main/cpp/$outputName/$abi"))
        }
    }
}

tasks.configureEach {
    nativeTasksByAbi.forEach { (abi, nativeTasks) ->
        if (name.startsWith("configureCMake") && name.endsWith("[$abi]")) {
            nativeTasks.forEach { nativeTask ->
                dependsOn(nativeTask)
                inputs.files(nativeTask.map { it.outputs.files })
            }
        }
    }
}

tasks.configureEach {
    val mergesAssets = name.contains("merge", ignoreCase = true) &&
        name.contains("Assets", ignoreCase = true)
    // The WithML flavor registers the generated ml-models dir as an asset source
    // set. Lint model/report tasks read that directory too, so they must also
    // depend on copySegmentModels to avoid Gradle implicit-dependency failures.
    val readsMlAssets = name.contains("WithML", ignoreCase = true) &&
        name.contains("Lint", ignoreCase = true)
    if (mergesAssets || readsMlAssets) {
        dependsOn(copySegmentModelsTask)
        // Reassemble any split large models (e.g. arcface.onnx) into the ml-models
        // asset dir before the APK asset merge reads it.
        dependsOn(":ml-models:assembleModels")
    }
    // Guard: fail the build if an unmanaged asset exceeds GitHub's 100 MB limit.
    if (name.startsWith("assemble") || name.startsWith("bundle")) {
        dependsOn(":ml-models:checkModelSizes")
    }
}

android {
    namespace = "com.dot.gallery"
    compileSdk = 37

    // Native HEIC tiled decoder (libheif + libde265, built via CMake/NDK). Pinned to the latest
    // stable NDK r29 line and CMake 3.31.x. CMake 4.x is intentionally avoided because it drops
    // support for `cmake_minimum_required(VERSION < 3.5)`, which breaks libde265/libheif scripts.
    ndkVersion = pinnedNdkVersion

    defaultConfig {
        applicationId = "com.dot.gallery"
        minSdk = 29
        targetSdk = 37
        versionCode = 51400
        versionName = "5.1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        val offlinePrefix = if (isOffline) "-offline" else ""
        base.archivesName.set("ReFra-${versionName}-$versionCode$offlinePrefix")
        val cartoBasemapKey = if (includeMaps) {
            providers.environmentVariable("CARTO_BASEMAP_KEY").orNull.orEmpty()
        } else {
            ""
        }
        val escapedCartoBasemapKey = cartoBasemapKey
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        buildConfigField("String", "CARTO_BASEMAP_KEY", "\"$escapedCartoBasemapKey\"")

        externalNativeBuild {
            cmake {
                // Only the tiny JNI is compiled here; libheif/libde265 are linked as prebuilt
                // static libs, so this stays fast.
                cppFlags += "-std=c++17"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
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
            buildConfigField("Boolean", "NEXTCLOUD_ENABLED", "$includeNextcloud")
            buildConfigField("Boolean", "WEBDAV_ENABLED", "$includeWebdav")
            buildConfigField("Boolean", "SMB_ENABLED", "$includeSmb")
            buildConfigField("Boolean", "NFS_ENABLED", "$includeNfs")
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
            buildConfigField("Boolean", "NEXTCLOUD_ENABLED", "$includeNextcloud")
            buildConfigField("Boolean", "WEBDAV_ENABLED", "$includeWebdav")
            buildConfigField("Boolean", "SMB_ENABLED", "$includeSmb")
            buildConfigField("Boolean", "NFS_ENABLED", "$includeNfs")
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
            buildConfigField("Boolean", "NEXTCLOUD_ENABLED", "$includeNextcloud")
            buildConfigField("Boolean", "WEBDAV_ENABLED", "$includeWebdav")
            buildConfigField("Boolean", "SMB_ENABLED", "$includeSmb")
            buildConfigField("Boolean", "NFS_ENABLED", "$includeNfs")
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
            buildConfigField("Boolean", "NEXTCLOUD_ENABLED", "$includeNextcloud")
            buildConfigField("Boolean", "WEBDAV_ENABLED", "$includeWebdav")
            buildConfigField("Boolean", "SMB_ENABLED", "$includeSmb")
            buildConfigField("Boolean", "NFS_ENABLED", "$includeNfs")
            buildConfigField("String", "CONTENT_AUTHORITY", "\"com.dot.gallery.gplay.media_provider\"")
            buildConfigField("Boolean", "ENABLE_INDEXING", "true")
            buildConfigField("Boolean", "ALLOW_INSECURE_TLS", "false")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
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
            // Conditional cloud networking source set
            if (!isOffline) {
                kotlin.srcDir("src/cloud/kotlin")
            } else {
                kotlin.srcDir("src/nocloud/kotlin")
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
            if (includeNextcloud) {
                kotlin.srcDir("src/nextcloud/kotlin")
            } else {
                kotlin.srcDir("src/nonextcloud/kotlin")
            }
            // Shared WebDAV base + capability framework, compiled when any
            // WebDAV-family provider (ownCloud / Nextcloud / generic) is enabled.
            if (includeOwncloud || includeNextcloud || includeWebdav) {
                kotlin.srcDir("src/webdav/kotlin")
            }
            if (includeWebdav) {
                kotlin.srcDir("src/genericwebdav/kotlin")
            } else {
                kotlin.srcDir("src/nogenericwebdav/kotlin")
            }
            // Shared network-filesystem base (loopback bridge + scanner + thumbnailer),
            // compiled when any net-fs provider (SMB / NFS) is enabled.
            if (includeSmb || includeNfs) {
                kotlin.srcDir("src/netfs/kotlin")
            }
            if (includeSmb) {
                kotlin.srcDir("src/smb/kotlin")
            } else {
                kotlin.srcDir("src/nosmb/kotlin")
            }
            if (includeNfs) {
                kotlin.srcDir("src/nfs/kotlin")
            } else {
                kotlin.srcDir("src/nonfs/kotlin")
            }
        }
        // For withML APK builds, include ML model assets directly
        // (asset packs are AAB-only, so for APK builds we inline them)
        val isBundleBuild = gradle.startParameter.taskNames.any {
            it.contains("bundle", ignoreCase = true)
        }
        if (!isBundleBuild) {
            maybeCreate("WithML").apply {
                assets.srcDirs(
                    "../ml-models/src/main/assets",
                    "${layout.buildDirectory.get().asFile}/generated/assets/ml-models"
                )
            }
        }
        if (includeMaps) {
            getByName("test").kotlin.srcDir("src/mapsTest/kotlin")
        }
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
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
    coreLibraryDesugaring(libs.desugar.jdk.libs)
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
    implementation(libs.jp2forandroid)
    implementation(libs.androidsvg)
    implementation(libs.nga.tiff)

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
    implementation(project(":libs:scrollbar"))

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
    if (includeImmich || includeOwncloud || includeNextcloud || includeWebdav) {
        implementation(libs.okhttp.logging)
    }

    // Immich
    if (includeImmich) {
        implementation(libs.retrofit)
        implementation(libs.retrofit.kotlinx.serialization)
        implementation(libs.retrofit.converter.gson)
    }

    // Network filesystem providers (gated)
    if (includeSmb) {
        implementation(libs.smbj)
    }
    if (includeNfs) {
        implementation(libs.nfs.client)
    }

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.mockwebserver)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.compose.ui.test.junit4)
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

val includeNextcloud: Boolean
    get() {
        if (isOffline) return false
        val fl = rootProject.file("app.properties")
        return try {
            val properties = Properties()
            properties.load(FileInputStream(fl))
            properties.getProperty("INCLUDE_NEXTCLOUD", "false").toBoolean()
        } catch (_: Exception) {
            false
        }
    }

val includeWebdav: Boolean
    get() {
        if (isOffline) return false
        val fl = rootProject.file("app.properties")
        return try {
            val properties = Properties()
            properties.load(FileInputStream(fl))
            properties.getProperty("INCLUDE_WEBDAV", "false").toBoolean()
        } catch (_: Exception) {
            false
        }
    }

val includeSmb: Boolean
    get() {
        if (isOffline) return false
        val fl = rootProject.file("app.properties")
        return try {
            val properties = Properties()
            properties.load(FileInputStream(fl))
            properties.getProperty("INCLUDE_SMB", "false").toBoolean()
        } catch (_: Exception) {
            false
        }
    }

val includeNfs: Boolean
    get() {
        if (isOffline) return false
        val fl = rootProject.file("app.properties")
        return try {
            val properties = Properties()
            properties.load(FileInputStream(fl))
            properties.getProperty("INCLUDE_NFS", "false").toBoolean()
        } catch (_: Exception) {
            false
        }
    }