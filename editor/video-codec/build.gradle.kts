import java.util.Locale
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
}

// Host classifier for the desktop natives (JavaCPP's naming scheme).
val desktopClassifier = run {
    val os = System.getProperty("os.name").lowercase(Locale.ROOT)
    val arch = System.getProperty("os.arch").lowercase(Locale.ROOT)
    val osPart = when {
        os.contains("mac") -> "macosx"
        os.contains("windows") -> "windows"
        else -> "linux"
    }
    val archPart = when (arch) {
        "aarch64", "arm64" -> "arm64"
        else -> "x86_64"
    }
    "$osPart-$archPart"
}

// FFmpeg-based video decode/encode behind the editor's video ports, shared by
// the Android and desktop targets through jvmSharedMain. Codecs come from the
// upstream ByteDeco JavaCV/JavaCPP presets (pinned, non-GPL); native binaries
// ride as Maven classifier artifacts — desktop resolves the host classifier
// at runtime scope, Android's .so files are extracted below into generated
// jniLibs consumed by :editor:android. There is no system ffmpeg dependency.
kotlin {
    android {
        namespace = "com.dot.gallery.editor.videocodec"
        compileSdk = 37
        minSdk = 29
        withHostTest {}
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
        // Same jvmSharedMain trick as :editor:io — the FFmpeg wrapping code
        // (grabber/recorder, swscale pixel conversion, audio resample reads)
        // is identical on Android and desktop JVM.
        val jvmSharedMain = create("jvmSharedMain") {
            dependsOn(commonMain.get())
        }
        named("androidMain") { dependsOn(jvmSharedMain) }
        named("desktopMain") { dependsOn(jvmSharedMain) }
        named("androidMain") {
            dependencies {
                implementation(libs.androidx.core.ktx)
            }
        }

        commonMain.dependencies {
            api(project(":editor:render"))
            api(project(":editor:io"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jvmSharedMain.dependencies {
            // javacv API surface only — isTransitive=false keeps the
            // platform/OpenCV/camera stack out; javacpp and ffmpeg bases plus
            // the needed native classifiers are pinned explicitly. These are
            // `api`: public signatures surface AVFrame/Buffer types.
            api("org.bytedeco:javacv:1.5.13") { isTransitive = false }
            api("org.bytedeco:javacpp:1.5.13")
            api("org.bytedeco:ffmpeg:8.0.1-1.5.13")
        }
        named("desktopMain") {
            dependencies {
                runtimeOnly("org.bytedeco:javacpp:1.5.13:$desktopClassifier")
                runtimeOnly("org.bytedeco:ffmpeg:8.0.1-1.5.13:$desktopClassifier")
            }
        }
        named("desktopTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

// Android FFmpeg/JavaCPP natives: resolve the pinned classifier artifacts and
// extract only the .so payload into generated jniLibs. The output directory
// is wired into :editor:android's jniLibs via `extractVideoNatives` below;
// nothing is committed and no system ffmpeg is used.
val androidNativeArtifacts = configurations.create("androidVideoNativeArtifacts") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    for (abi in listOf("android-arm64", "android-x86_64")) {
        androidNativeArtifacts("org.bytedeco:ffmpeg:8.0.1-1.5.13:$abi")
        androidNativeArtifacts("org.bytedeco:javacpp:1.5.13:$abi")
    }
}

val videoNativeLibDir = layout.buildDirectory.dir("generated/videoJniLibs")

val extractVideoNatives = tasks.register<Copy>("extractVideoNatives") {
    // Jar layout: lib/<android-abi>/<name>.so — strip the lib/ prefix so
    // files land at jniLibs/<abi>/<name>.so. 32-bit artifacts are not
    // published by ByteDeco; the loader reports the gap as
    // UnsupportedVideoException at open time. Artifacts are version-pinned,
    // so resolving eagerly at configuration time is safe and keeps the task
    // action free of script-object captures (configuration-cache clean).
    androidNativeArtifacts.forEach { artifact ->
        from(zipTree(artifact)) {
            include("lib/**/*.so")
            eachFile { path = path.removePrefix("lib/") }
            includeEmptyDirs = false
        }
    }
    into(videoNativeLibDir)
}

// The generated tree is consumed as jniLibs by :editor:android (see its
// build.gradle.kts — the KMP android library target has no jniLibs DSL).
// Declared here so the consumer can task-depend on it by name.
val videoNativeLibOutput = videoNativeLibDir
