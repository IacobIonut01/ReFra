package com.dot.gallery.editor.io

/**
 * JNI surface of the packaged `refra_editor_jni` library (editor/native,
 * src/color_jni.c). The loader that binds it is host-specific
 * ([EditorNativeLoader]); this object only declares the ABI.
 *
 * Keep the class and method names stable — Android consumer ProGuard rules
 * keep them verbatim.
 */
internal object NativeColorBridge {

    /** The library's `REFRA_EDITOR_ABI_VERSION`. */
    external fun abiVersion(): Int

    /**
     * Runs `refra_editor_color_rgba_f32` over a whole frame. [source] and
     * [target] are linear-light straight-alpha RGBA rows of
     * `width * height * 4` floats; strides are `width * 4`. Returns the
     * kernel status — 0 on success, non-zero on invalid input.
     */
    external fun applyColor(
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
