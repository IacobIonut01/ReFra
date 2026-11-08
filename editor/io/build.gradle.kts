import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
}

// Platform-neutral photo I/O shared by the editor backends: streaming PNG
// encoding and EXIF orientation parsing over JVM-style streams. The pure
// java.io/java.util.zip implementations live in jvmSharedMain, consumed by
// both the Android and desktop targets.
kotlin {
    android {
        namespace = "com.dot.gallery.editor.io"
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
        // Intermediate source set for code that is identical on Android and
        // desktop JVM (java.io / java.util.zip based writers and parsers).
        val jvmSharedMain = create("jvmSharedMain") {
            dependsOn(commonMain.get())
        }
        named("androidMain") { dependsOn(jvmSharedMain) }
        named("desktopMain") { dependsOn(jvmSharedMain) }

        commonMain.dependencies {
            api(project(":editor:render"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
