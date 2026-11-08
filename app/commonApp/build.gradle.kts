import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.codingfeline.buildkonfig.compiler.FieldSpec
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.kotlinSerialization)
    kotlin("plugin.parcelize")
    alias(libs.plugins.kspAndroid)
    alias(libs.plugins.buildkonfig)
}

kotlin {
    android {
        namespace = "com.dot.gallery"
        compileSdk = 37
        minSdk = 29
        // Android res/ support is opt-in on the KMP library — this owns the app's
        // resources (strings, drawables, xml) so its R class stays com.dot.gallery.R.
        androidResources {
            enable = true
        }
        // Host (JVM) unit tests + device tests for androidMain — friend modules,
        // so test code keeps access to internal members.
        withJava()
        withHostTest {}
        withDeviceTest {}
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.add("-Xcontext-parameters")
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlin.inject.runtime)
            // compose compiler plugin runs on every target and needs the runtime
            implementation(libs.cmp.runtime)
            // Res class + accessors for src/commonMain/composeResources
            implementation(libs.cmp.components.resources)
            // First-ported commonMain files use domain helpers (or, volume)
            api(project(":app:core:domain"))
            // Cloud capability contracts + entities ported to commonMain
            api(project(":app:core:data"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.okio)
        }

        androidMain.dependencies {
            api(project(":app:core:ui"))
            api(project(":app:core:domain"))
            api(project(":app:core:data"))
            api(project(":app:ml-models"))
            api(project(":app:feature:settings"))
            api(project(":app:feature:albums"))
            api(project(":app:feature:setup"))
            api(project(":app:feature:help"))
            api(project(":app:feature:vault"))
            api(project(":app:feature:timeline"))
            api(project(":app:feature:library"))
            api(project(":app:feature:ignored"))
            api(project(":app:feature:classifier"))
            api(project(":app:feature:exif"))
            api(project(":app:feature:picker"))
            api(project(":app:feature:viewer"))
            api(project(":app:feature:editor"))
            api(project(":app:feature:search"))
            api(project(":app:libs:cropper"))
            api(project(":app:libs:panoramaviewer"))

            // Core
            api(libs.androidx.core.ktx)
            api(libs.androidx.appcompat)

            // Lifecycle
            api(libs.androidx.lifecycle.runtime.ktx)
            api(libs.compose.lifecycle.runtime)
            api(libs.androidx.lifecycle.process)

            // Compose
            api(libs.compose.activity)
            api(libs.compose.ui)
            api(libs.compose.ui.graphics)
            api(libs.compose.ui.tooling.preview)
            api(libs.compose.material.icons.extended)
            api(libs.androidx.graphics.shapes)
            api(libs.androidx.startup.runtime)
            api(libs.compose.shimmer)
            api(libs.compose.material3)
            api(libs.compose.material3.window.size)
            api(libs.androidx.adaptive)
            api(libs.androidx.adaptive.layout)
            api(libs.androidx.adaptive.navigation)
            api(libs.accompanist.permissions)
            api(libs.androidx.navigation.compose)
            api(libs.accompanist.drawablepainter)
            api(libs.material)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.coroutines.android)
            api(libs.kotlinx.collections.immutable)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlin.inject.runtime)
            api(libs.javax.inject)

            // Room / SQLite
            api(libs.sqlcipher.android)
            api(libs.sqlite.ktx)

            // Coders
            api(libs.jxl.coder.coil)
            api(libs.avif.coder.coil)
            api(libs.androidsvg)
            api(libs.nga.tiff)

            // Sketch
            api(libs.sketch.compose)
            api(libs.sketch.view)
            api(libs.sketch.animated.gif)
            api(libs.sketch.animated.heif)
            api(libs.sketch.animated.webp)
            api(libs.sketch.extensions.compose)
            api(libs.sketch.http.ktor)
            api(libs.sketch.svg)
            api(libs.sketch.video)

            // Glide
            api(libs.glide)
            api(libs.glide.compose)

            // ExoPlayer
            api(libs.androidx.media3.exoplayer)
            api(libs.androidx.media3.ui)
            api(libs.androidx.media3.ui.compose)
            api(libs.androidx.media3.session)
            api(libs.androidx.media3.exoplayer.dash)
            api(libs.androidx.media3.exoplayer.hls)

            api(libs.androidx.exifinterface)
            api(libs.metadata.extractor)
            api(libs.nanohttpd)
            api(libs.datastore.prefs)
            api(libs.fuzzywuzzy.kotlin)
            api(libs.aire)
            api(libs.zoomimage.compose.sketch)
            api(libs.androidx.core.splashscreen)
            api(libs.androidx.security.crypto)
            api(libs.androidx.biometric)
            api(libs.core)
            api(libs.kmpworkmanager)
            api(libs.kmpworkmanager.http)
            api(libs.kmpworker.annotations)
            api(libs.onnxruntime.android)
            api(libs.haze)
            api(libs.haze.blur)
            api(libs.haze.blur.materials)
            api(libs.haze.glass)
            api(libs.haze.glass.material3)

            if (includeMaps) {
                api(libs.maplibre.native)
            }

            api(libs.okhttp)
            if (includeImmich || includeOwncloud || includeNextcloud || includeWebdav) {
                api(libs.okhttp.logging)
            }
            if (includeImmich) {
                api(libs.retrofit)
                api(libs.retrofit.kotlinx.serialization)
                api(libs.retrofit.converter.gson)
            }
            if (includeSmb || includeNfs) {
                api(libs.androidx.media3.inspector)
                api(libs.androidx.media3.inspector.frame)
            }
            if (includeSmb) {
                api(libs.smbj)
            }
            if (includeNfs) {
                api(libs.nfs.client)
            }
        }

        named("androidHostTest") {
            dependencies {
                implementation(libs.junit)
                implementation(libs.mockwebserver)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kmpworker.testing)
            }
        }

        named("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.test.ext.junit)
                implementation(libs.espresso.core)
                implementation(libs.room3.testing)
                implementation(libs.sqlite.bundled)
                implementation(libs.mockwebserver)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.compose.ui.test.junit4)
                implementation(libs.androidx.uiautomator)
                implementation(libs.kmpworker.testing)
                // Compose test rules need the manifest-provided host activity
                // merged into the test APK.
                implementation(libs.compose.ui.test.manifest)
            }
        }
    }
}

// Conditional source sets mirrored from the old app module — provider code lives
// in sibling src/<name>/kotlin dirs, gated by app.properties.
kotlin.sourceSets.getByName("androidMain").kotlin.apply {
    if (!isOffline) {
        srcDir("src/maps/kotlin")
        srcDir("src/cloud/kotlin")
    } else {
        srcDir("src/offline/kotlin")
        srcDir("src/nocloud/kotlin")
    }
    if (includeImmich) srcDir("src/immich/kotlin") else srcDir("src/noimmich/kotlin")
    if (includeOwncloud) srcDir("src/owncloud/kotlin") else srcDir("src/noowncloud/kotlin")
    if (includeNextcloud) srcDir("src/nextcloud/kotlin") else srcDir("src/nonextcloud/kotlin")
    if (includeOwncloud || includeNextcloud || includeWebdav) srcDir("src/webdav/kotlin")
    if (includeWebdav) srcDir("src/genericwebdav/kotlin") else srcDir("src/nogenericwebdav/kotlin")
    if (includeSmb || includeNfs) srcDir("src/netfs/kotlin")
    if (includeSmb) srcDir("src/smb/kotlin") else srcDir("src/nosmb/kotlin")
    if (includeNfs) srcDir("src/nfs/kotlin") else srcDir("src/nonfs/kotlin")
}

if (includeMaps) {
    kotlin.sourceSets.getByName("androidHostTest").kotlin.srcDir("src/mapsTest/kotlin")
}

// Room migration tests read schema JSONs from the test APK's assets via
// MigrationTestHelper. The KMP deviceTest exposes no assets srcDir API, so the
// schemas live in :app:core:data/schemas and are synced into the conventional
// src/androidDeviceTest/assets dir (the only asset source of the test variant).
val syncDeviceTestSchemaAssets = tasks.register<Sync>("syncDeviceTestSchemaAssets") {
    from(rootProject.file("app/core/data/schemas"))
    into("src/androidDeviceTest/assets")
    // Keep hand-authored assets (test12.avif); clean only stale schema output.
    preserve { exclude("com.dot.gallery.data.InternalDatabase/**") }
}
tasks.matching { it.name == "mergeAndroidDeviceTestAssets" }.configureEach {
    dependsOn(syncDeviceTestSchemaAssets)
}

// CMP's copyAndroidDeviceTestComposeResourcesToAndroidAssets writes into the shared
// build/generated/assets tree that mergeAndroidMainAssets consumes; Gradle 9 rejects
// the implicit dependency, so the ordering must be declared explicitly.
tasks.matching { it.name == "mergeAndroidMainAssets" }.configureEach {
    mustRunAfter("copyAndroidDeviceTestComposeResourcesToAndroidAssets")
}

// kotlin-inject graph codegen + Glide @GlideModule codegen on the Android target
dependencies {
    add("kspAndroid", libs.kotlin.inject.compiler.ksp)
    add("kspAndroid", libs.glide.ksp)
    add("kspAndroid", libs.kmpworker.ksp)
}

ksp {
    // Lets kotlin-inject consume javax.inject @Inject/@Singleton annotations.
    arg("me.tatarka.inject.enableJavaxAnnotations", "true")
}

composeCompiler {
    includeSourceInformation = true
    stabilityConfigurationFiles = listOf(
        rootProject.layout.projectDirectory.file("stability_config.conf")
    )
}

compose.resources {
    packageOfResClass = "com.dot.gallery.generated.resources"
}

// `variant.sources.assets` is null under com.android.kotlin.multiplatform.library, so CMP
// registers copyAndroidMainComposeResourcesToAndroidAssets without an output dir and
// composeResources never reach the APK. Package them onto the androidMain classpath
// ourselves -- the resource reader falls back to classLoader.getResourceAsStream for
// anything not in assets.
val packageAndroidComposeResources by tasks.registering(Copy::class) {
    dependsOn(
        "prepareComposeResourcesTaskForCommonMain",
        "prepareComposeResourcesTaskForAndroidMain"
    )
    from(layout.buildDirectory.dir("generated/compose/resourceGenerator/preparedResources/commonMain/composeResources"))
    from(layout.buildDirectory.dir("generated/compose/resourceGenerator/preparedResources/androidMain/composeResources"))
    into(layout.buildDirectory.dir("generated/composeResAssets/composeResources/com.dot.gallery.generated.resources"))
}
kotlin.sourceSets.getByName("androidMain").resources.srcDir(
    // srcDir at the composeResAssets root (parent of the Copy destination) so resource
    // paths keep their composeResources/<package> prefix; the provider carries the dep
    packageAndroidComposeResources.map { it.destinationDir.parentFile.parentFile }
)

// KMP-friendly replacement for AGP's generated BuildConfig — global flags only
// (values identical across app variants). Variant-scoped fields (applicationId,
// versionName, CONTENT_AUTHORITY, ...) stay in androidApp's BuildConfig and are
// pushed into the com.dot.gallery.BuildConfig shim at startup.
buildkonfig {
    packageName = "com.dot.gallery"
    defaultConfigs {
        buildConfigField(FieldSpec.Type.BOOLEAN, "OFFLINE_MODE", isOffline.toString())
        buildConfigField(FieldSpec.Type.BOOLEAN, "MAPS_ENABLED", includeMaps.toString())
        buildConfigField(FieldSpec.Type.BOOLEAN, "IMMICH_ENABLED", includeImmich.toString())
        buildConfigField(FieldSpec.Type.BOOLEAN, "OWNCLOUD_ENABLED", includeOwncloud.toString())
        buildConfigField(FieldSpec.Type.BOOLEAN, "NEXTCLOUD_ENABLED", includeNextcloud.toString())
        buildConfigField(FieldSpec.Type.BOOLEAN, "WEBDAV_ENABLED", includeWebdav.toString())
        buildConfigField(FieldSpec.Type.BOOLEAN, "SMB_ENABLED", includeSmb.toString())
        buildConfigField(FieldSpec.Type.BOOLEAN, "NFS_ENABLED", includeNfs.toString())
        buildConfigField(
            FieldSpec.Type.STRING,
            "CARTO_BASEMAP_KEY",
            if (includeMaps) {
                System.getenv("CARTO_BASEMAP_KEY")?.takeIf(String::isNotBlank)
                    ?: committedCartoBasemapKey
            } else {
                ""
            }
        )
    }
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

// The CARTO basemap key is a public client-side token: it ships inside the APK and is
// sent in tile URL queries. Keeping a copy in the repo (app.properties) makes the
// embedded key identical in every build environment, which F-Droid's reproducible
// build verification requires; the env var still wins when set (CI, local overrides).
val committedCartoBasemapKey: String
    get() {
        val fl = rootProject.file("app.properties")
        return try {
            val properties = Properties()
            properties.load(FileInputStream(fl))
            properties.getProperty("CARTO_BASEMAP_KEY", "").trim().removeSurrounding("\"")
        } catch (_: Exception) {
            ""
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
