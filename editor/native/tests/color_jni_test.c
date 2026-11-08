/* Test-only JNI bridge between desktopTest and the C colour kernel so the
 * parity tests can compare native output against the Kotlin reference on the
 * same input. Not part of the production library — nothing in the app loads
 * this; it exists only under REFRA_EDITOR_BUILD_JNI_TEST_BRIDGE. */
#include "refra_editor.h"

#include <jni.h>

JNIEXPORT jint JNICALL
Java_com_dot_gallery_editor_render_NativeColorTestBridge_apply(
    JNIEnv *env, jobject self, jfloatArray source, jfloatArray target,
    jint width, jint height, jfloat exposure, jfloat contrast,
    jfloat saturation, jfloat highlights, jfloat shadows, jfloat whites,
    jfloat blacks) {
    (void)self;
    if (source == NULL || target == NULL || width <= 0 || height <= 0) {
        return 1;
    }
    /* The kernel permits exact in-place calls, but the bridge keeps source
     * strictly read-only for the test contract. */
    if ((*env)->IsSameObject(env, source, target)) {
        return 1;
    }
    /* Bound width before the stride multiply for narrow size_t hosts. */
    if ((size_t)width > SIZE_MAX / 4u) {
        return 1;
    }
    {
        const jsize src_count = (*env)->GetArrayLength(env, source);
        const jsize dst_count = (*env)->GetArrayLength(env, target);
        const size_t stride = (size_t)width * 4u;
        jfloat *src = (*env)->GetFloatArrayElements(env, source, NULL);
        jfloat *dst;
        int status;
        if (src == NULL) {
            return 1; /* pending JNI exception on allocation failure */
        }
        dst = (*env)->GetFloatArrayElements(env, target, NULL);
        if (dst == NULL) {
            (*env)->ReleaseFloatArrayElements(env, source, src, JNI_ABORT);
            return 1;
        }
        status = refra_editor_color_rgba_f32(
            src, (size_t)src_count, dst, (size_t)dst_count, (uint32_t)width,
            (uint32_t)height, stride, stride, exposure, contrast, saturation,
            highlights, shadows, whites, blacks);
        (*env)->ReleaseFloatArrayElements(env, target, dst, 0);
        (*env)->ReleaseFloatArrayElements(env, source, src, JNI_ABORT);
        return (jint)status;
    }
}
