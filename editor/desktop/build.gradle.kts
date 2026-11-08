import java.util.Locale
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
}

// Desktop implementation of the editor photo ports (ImageIO region reads,
// sibling-file PNG copy export) plus the embeddable DesktopPhotoEditor and a
// dev harness launched via the `runEditor` task. JVM-only for now.
kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        named("desktopMain") {
            dependencies {
                api(project(":editor:io"))
                api(project(":editor:ui"))
                api(project(":editor:video-codec"))
                implementation(compose.desktop.currentOs)
            }
        }
        named("desktopTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(compose.desktop.currentOs)
                implementation(compose.desktop.uiTestJUnit4)
            }
        }
    }
}

// Packaged native runtime: builds editor/native with
// REFRA_EDITOR_BUILD_JNI=ON on the local host toolchain (current OS/arch
// only — untested targets are not claimed) and stages the result as a jar
// resource for DesktopEditorNativeLoader. No system-installed libraries
// are used.
val nativeSourceDir = rootProject.file("editor/native")
val nativeBuildDir = layout.buildDirectory.dir("nativeRuntime")
val nativeLibDir = nativeBuildDir.map { it.dir("lib") }
val nativeLibraryName = System.mapLibraryName("refra_editor_jni")
val nativeInputs = files(
    nativeSourceDir.resolve("CMakeLists.txt"),
    nativeSourceDir.resolve("src/color.c"),
    nativeSourceDir.resolve("src/color_jni.c"),
    nativeSourceDir.resolve("include/refra_editor.h")
)
val javaHome = providers.systemProperty("java.home")
val nativeResourcesDir = layout.buildDirectory.dir("generated/nativeResources")

val configureNativeRuntime = tasks.register<Exec>("configureNativeRuntime") {
    inputs.files(nativeInputs)
    inputs.property("javaHome", javaHome)
    outputs.file(nativeBuildDir.map { it.file("CMakeCache.txt") })
    environment("JAVA_HOME", javaHome.get())
    val buildDirPath = nativeBuildDir.get().asFile.absolutePath
    commandLine(
        "cmake",
        "-S", nativeSourceDir.absolutePath,
        "-B", buildDirPath,
        "-DREFRA_EDITOR_BUILD_JNI=ON",
        "-DREFRA_EDITOR_BUILD_TESTS=OFF",
        "-DCMAKE_BUILD_TYPE=Release",
        "-DJAVA_HOME=${javaHome.get()}",
        "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=$buildDirPath/lib",
        "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=$buildDirPath/lib",
        "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY_RELEASE=$buildDirPath/lib",
        "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY_RELEASE=$buildDirPath/lib"
    )
}

val buildNativeRuntime = tasks.register<Exec>("buildNativeRuntime") {
    dependsOn(configureNativeRuntime)
    inputs.files(nativeInputs)
    // A reconfigure changes the cache even when sources do not; make it part
    // of this task's invalidation.
    inputs.file(nativeBuildDir.map { it.file("CMakeCache.txt") })
        .withPropertyName("nativeRuntimeConfiguration")
    outputs.file(nativeLibDir.map { it.file(nativeLibraryName) })
    commandLine(
        "cmake", "--build", nativeBuildDir.get().asFile.absolutePath,
        "--config", "Release", "--target", "refra_editor_jni"
    )
}

// Same normalization as DesktopEditorNativeLoader's host classifier.
val nativeOs = System.getProperty("os.name").lowercase(Locale.ROOT).let { name ->
    when {
        name.contains("mac") -> "macos"
        name.contains("windows") -> "windows"
        name.contains("linux") -> "linux"
        else -> name.replace(Regex("[^a-z0-9]+"), "-")
    }
}
val nativeArch = System.getProperty("os.arch").lowercase(Locale.ROOT).let { arch ->
    when (arch) {
        "aarch64", "arm64" -> "arm64"
        "amd64", "x86_64", "x64" -> "x64"
        else -> arch.replace(Regex("[^a-z0-9]+"), "-")
    }
}

val stageNativeRuntime = tasks.register<Copy>("stageNativeRuntime") {
    dependsOn(buildNativeRuntime)
    from(nativeLibDir) { include(nativeLibraryName) }
    into(nativeResourcesDir.map { it.dir("native/$nativeOs-$nativeArch") })
}

// The packaged JNI library ships inside the jar under native/<os>-<arch>/;
// the loader resolves it from the classpath only (DesktopEditorNativeLoader).
kotlin.sourceSets.named("desktopMain") {
    resources.srcDir(stageNativeRuntime.map { nativeResourcesDir.get() })
}

// desktopTest exercises the packaged resource path end to end: a C-only
// change rebuilds the library and must invalidate the test run.
tasks.named<Test>("desktopTest") {
    dependsOn(stageNativeRuntime)
    inputs.file(nativeLibDir.map { it.file(nativeLibraryName) })
        .withPropertyName("nativeRuntimeLibrary")
}

// Dev workbench: `./gradlew :editor:desktop:runEditor` opens a welcome
// window with a native Open dialog; `-PeditorFile=/abs/media` opens that
// file directly, routed to photo or video by extension. Every picked file
// is its own document window. Lives in desktopTest so the library itself
// stays application-free.
val desktopTestCompilation =
    kotlin.targets.getByName("desktop").compilations.getByName("test")

val runEditor by tasks.registering(JavaExec::class) {
    group = "application"
    description =
        "Runs the editor workbench (optional -PeditorFile=/path/media)"
    dependsOn("desktopTestClasses")
    classpath = files(
        desktopTestCompilation.output.allOutputs,
        desktopTestCompilation.runtimeDependencyFiles
    )
    mainClass.set("com.dot.gallery.editor.desktop.EditorDesktopHarnessKt")
    // Compose owns AWT on the main thread itself — the macOS first-thread
    // flag deadlocks the window before it can map (AWT never registers).
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        jvmArgs("-Xdock:name=ReFra Editor")
    }
    doFirst {
        providers.gradleProperty("editorFile").orNull?.let { args(it) }
    }
}

// Dev harness: `./gradlew :editor:desktop:runVideoEditor -PeditorFile=/abs/video.mp4`.
val runVideoEditor by tasks.registering(JavaExec::class) {
    group = "application"
    description =
        "Runs the video editor on a real file (-PeditorFile=/path/video.mp4)"
    dependsOn("desktopTestClasses")
    classpath = files(
        desktopTestCompilation.output.allOutputs,
        desktopTestCompilation.runtimeDependencyFiles
    )
    mainClass.set("com.dot.gallery.editor.desktop.VideoDesktopHarnessKt")
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        jvmArgs("-Xdock:name=ReFra Video Editor")
    }
    doFirst {
        val editorFile = providers.gradleProperty("editorFile").orNull
            ?: throw GradleException(
                "runVideoEditor requires -PeditorFile=/absolute/video.mp4"
            )
        args(editorFile)
    }
}

// Test-only sample clip for UI captures / the harness: regenerates
// editor/desktop/build/fixtures/sample-coast.mp4 from the packaged FFmpeg
// runtime and the committed editor_sample.jpg — no binary in the repo.
val generateVideoSample by tasks.registering(JavaExec::class) {
    group = "application"
    description =
        "Generates the 'Sample coast.mp4' test clip into build/fixtures/"
    dependsOn("desktopTestClasses")
    classpath = files(
        desktopTestCompilation.output.allOutputs,
        desktopTestCompilation.runtimeDependencyFiles
    )
    mainClass.set("com.dot.gallery.editor.desktop.SampleVideoFixture")
    // No -Pinput: the output path is fixed, so args resolve eagerly.
    args(
        "-",
        layout.buildDirectory.file("fixtures/sample-coast.mp4")
            .get().asFile.absolutePath
    )
}
