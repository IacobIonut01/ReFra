// Top-level build file where you can add configuration options common to all sub-projects/modules.
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootExtension

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.androidLint) apply false
    alias(libs.plugins.kspAndroid) apply false
    alias(libs.plugins.roomPlugin) apply false
    alias(libs.plugins.androidTest) apply false
    alias(libs.plugins.baselineProfilePlugin) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}

// The wasmJs toolchain injects an exact ws pin into every generated
// package.json, so a normal yarn.lock upgrade cannot move it. The
// resolution forces ws to a release with the memory-exhaustion DoS and
// uninitialized-memory-disclosure fixes.
plugins.withType(WasmYarnPlugin::class.java).configureEach {
    extensions.getByType(WasmYarnRootExtension::class.java)
        .resolution("ws", "8.23.0")
}
