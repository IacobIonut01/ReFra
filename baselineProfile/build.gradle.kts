import com.android.build.api.dsl.ManagedVirtualDevice
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    buildTypes {
        create("staging") {
        }
        create("benchmarkStaging") {
        }
        create("nonMinifiedStaging") {
        }
    }
    flavorDimensions += listOf("abi")
    productFlavors {
        create("arm64-v8a") {
            dimension = "abi"
            ndk.abiFilters.add("arm64-v8a")
        }
        create("armeabi-v7a") {
            dimension = "abi"
            ndk.abiFilters.add("armeabi-v7a")
        }
        create("x86_64") {
            dimension = "abi"
            ndk.abiFilters.add("x86_64")
        }
        create("x86") {
            dimension = "abi"
            ndk.abiFilters.add("x86")
        }
    }
    namespace = "com.dot.baselineprofile"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        minSdk = 30
        targetSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    testOptions.managedDevices.devices {
        create<ManagedVirtualDevice>("pixel9proxl") {
            device = "Pixel 9 Pro XL"
            apiLevel = 36
            systemImageSource = "google_apis_playstore"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

// This is the configuration block for the Baseline Profile plugin.
// You can specify to run the generators on a managed devices or connected devices.
baselineProfile {
    //managedDevices += "pixel6Api31"
    useConnectedDevices = true
    
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}