plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("apkVersioning") {
            id = "apk-versioning"
            implementationClass = "ApkVersioningPlugin"
        }
    }
}
