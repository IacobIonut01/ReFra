package com.dot.gallery.editor.render

/**
 * Loads the test-only JNI bridge library built by the Gradle-native parity
 * tasks. Never referenced from production code.
 */
internal object NativeColorTestBridge {

    init {
        System.load(
            requireNotNull(System.getProperty("refra.editor.native.testLibrary")) {
                "Run desktopTest through Gradle to build the native conformance bridge"
            }
        )
    }

    external fun apply(
        source: FloatArray,
        target: FloatArray,
        width: Int,
        height: Int,
        exposure: Float,
        contrast: Float,
        saturation: Float,
        highlights: Float,
        shadows: Float,
        whites: Float,
        blacks: Float
    ): Int
}
