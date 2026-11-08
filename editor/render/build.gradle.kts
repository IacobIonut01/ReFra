import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
}

// Reference render path: frames, CPU renderer, backend fallback policy and the
// cancellable preview scheduler. Depends on editor/core only.
kotlin {
    android {
        namespace = "com.dot.gallery.editor.render"
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
        commonMain.dependencies {
            api(project(":editor:core"))
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Test-only JNI conformance bridge: configures and builds editor/native with
// REFRA_EDITOR_BUILD_JNI_TEST_BRIDGE=ON so desktopTest can load the shared
// library with an absolute System.load path and compare the C kernel against
// the Kotlin reference. The bridge is never packaged or shipped.
val nativeParitySourceDir = rootProject.file("editor/native")
val nativeParityBuildDir = layout.buildDirectory.dir("nativeParity")
val nativeParityLibDir = nativeParityBuildDir.map { it.dir("lib") }
val nativeParityLibraryName = System.mapLibraryName("refra_editor_test_jni")
val nativeParityInputs = files(
    nativeParitySourceDir.resolve("CMakeLists.txt"),
    nativeParitySourceDir.resolve("src/color.c"),
    nativeParitySourceDir.resolve("include/refra_editor.h"),
    nativeParitySourceDir.resolve("tests/color_jni_test.c")
)
val javaHome = providers.systemProperty("java.home")

val configureNativeParity = tasks.register<Exec>("configureNativeParity") {
    inputs.files(nativeParityInputs)
    inputs.property("javaHome", javaHome)
    outputs.file(nativeParityBuildDir.map { it.file("CMakeCache.txt") })
    environment("JAVA_HOME", javaHome.get())
    // CMake reads backslashes in -D values as escapes, so Windows paths go
    // through with forward slashes.
    val buildDirPath = nativeParityBuildDir.get().asFile.invariantSeparatorsPath
    val javaHomePath = File(javaHome.get()).invariantSeparatorsPath
    commandLine(
        "cmake",
        "-S", nativeParitySourceDir.absolutePath,
        "-B", buildDirPath,
        "-DREFRA_EDITOR_BUILD_JNI_TEST_BRIDGE=ON",
        "-DCMAKE_BUILD_TYPE=Release",
        "-DJAVA_HOME=$javaHomePath",
        "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=$buildDirPath/lib",
        "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=$buildDirPath/lib",
        "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY_RELEASE=$buildDirPath/lib",
        "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY_RELEASE=$buildDirPath/lib"
    )
}

val buildNativeParity = tasks.register<Exec>("buildNativeParity") {
    dependsOn(configureNativeParity)
    inputs.files(nativeParityInputs)
    // A reconfigure changes the cache even when sources do not; make it part
    // of this task's invalidation.
    inputs.file(nativeParityBuildDir.map { it.file("CMakeCache.txt") })
        .withPropertyName("nativeParityConfiguration")
    outputs.file(nativeParityLibDir.map { it.file(nativeParityLibraryName) })
    commandLine(
        "cmake", "--build", nativeParityBuildDir.get().asFile.absolutePath,
        "--config", "Release", "--target", "refra_editor_test_jni"
    )
}

// desktopTest depends on the bridge: a missing or failed native build fails
// the test run rather than silently skipping it.
tasks.named<Test>("desktopTest") {
    dependsOn(buildNativeParity)
    // Dependency execution alone does not invalidate Test: a C-only change
    // rebuilds the library but would leave this task UP-TO-DATE. Track the
    // produced artifact itself.
    inputs.file(nativeParityLibDir.map { it.file(nativeParityLibraryName) })
        .withPropertyName("nativeParityLibrary")
    // Absolute path: Test.systemProperty does not unwrap providers.
    systemProperty(
        "refra.editor.native.testLibrary",
        nativeParityLibDir.get().file(nativeParityLibraryName).asFile.absolutePath
    )
}
