// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.hiltAndroid) apply false
    alias(libs.plugins.kspAndroid) apply false
    alias(libs.plugins.roomPlugin) apply false
    alias(libs.plugins.androidTest) apply false
    alias(libs.plugins.baselineProfilePlugin) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}