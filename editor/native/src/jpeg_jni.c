/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 *
 * Scanline JPEG encoder for the editor's Android export.
 *
 * The full-resolution result is produced one horizontal band at a time, so a
 * lossless PNG of a camera photo blows up to tens of megabytes (an RGBA PNG of
 * a 50 MP frame is ~60 MB against the JPEG original's ~8 MB). This bridge lets
 * the same band pipeline write a JPEG instead, encoding the source's own
 * quality so the copy stays in the same size class as the original while
 * keeping every pixel of the edited resolution.
 *
 * Built against the prebuilt static libjpeg-turbo produced by
 * scripts/native/build-imgcodec.sh (the same artifact :app:androidApp links).
 * When that artifact is not present every entry point degrades to a stub
 * reporting "unavailable"; the Kotlin side then falls back to the lossless PNG
 * export, so the library always loads.
 */

#include <jni.h>

#ifdef HAVE_JPEG_ENCODER

#include <setjmp.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#include <jpeglib.h>

#define JNI_FN(name) Java_com_dot_gallery_editor_android_JpegScanlineEncoder_##name

typedef struct {
    struct jpeg_error_mgr pub;
    jmp_buf jump;
} EncoderError;

typedef struct {
    FILE *file;
    struct jpeg_compress_struct cinfo;
    EncoderError err;
    int width;
} JpegEncoder;

/* libjpeg's default handler calls exit() on a fatal error — never acceptable
 * inside an app library, so unwind to the enclosing entry point instead. */
static void encoderErrorExit(j_common_ptr cinfo) {
    EncoderError *err = (EncoderError *) cinfo->err;
    longjmp(err->jump, 1);
}

static void encoderEmitMessage(j_common_ptr cinfo) {
    (void) cinfo;
}

/* Opens a buffered stdio stream on a dup() of the Java-owned fd so fclose()
 * releases only our copy; the caller still owns the original descriptor. */
static FILE *fdopenDup(int fd, const char *mode) {
    int dupFd = dup(fd);
    if (dupFd < 0) return NULL;
    FILE *file = fdopen(dupFd, mode);
    if (file == NULL) close(dupFd);
    return file;
}

static void destroyEncoder(JpegEncoder *enc, int closeFile) {
    if (enc == NULL) return;
    jpeg_destroy_compress(&enc->cinfo);
    if (closeFile && enc->file != NULL) fclose(enc->file);
    free(enc);
}

JNIEXPORT jboolean JNICALL JNI_FN(nativeSelfTest)(JNIEnv *env, jobject self) {
    (void) env;
    (void) self;
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL JNI_FN(nativeOpen)(
        JNIEnv *env, jobject self, jint fd, jint width, jint height, jint quality) {
    (void) env;
    (void) self;
    if (fd < 0 || width <= 0 || height <= 0) return 0;

    JpegEncoder *enc = (JpegEncoder *) calloc(1, sizeof(JpegEncoder));
    if (enc == NULL) return 0;
    enc->width = width;
    enc->file = fdopenDup(fd, "wb");
    if (enc->file == NULL) {
        free(enc);
        return 0;
    }

    enc->cinfo.err = jpeg_std_error(&enc->err.pub);
    enc->err.pub.error_exit = encoderErrorExit;
    enc->err.pub.output_message = encoderEmitMessage;
    if (setjmp(enc->err.jump) != 0) {
        destroyEncoder(enc, 1);
        return 0;
    }

    jpeg_create_compress(&enc->cinfo);
    jpeg_stdio_dest(&enc->cinfo, enc->file);
    enc->cinfo.image_width = (JDIMENSION) width;
    enc->cinfo.image_height = (JDIMENSION) height;
    enc->cinfo.input_components = 3;
    enc->cinfo.in_color_space = JCS_RGB;
    jpeg_set_defaults(&enc->cinfo);
    int q = quality;
    if (q < 1) q = 1;
    if (q > 100) q = 100;
    jpeg_set_quality(&enc->cinfo, q, TRUE);
    jpeg_start_compress(&enc->cinfo, TRUE);
    return (jlong) (intptr_t) enc;
}

JNIEXPORT jboolean JNICALL JNI_FN(nativeWriteRows)(
        JNIEnv *env, jobject self, jlong handle, jintArray argb, jint rowCount) {
    (void) self;
    JpegEncoder *enc = (JpegEncoder *) (intptr_t) handle;
    if (enc == NULL || argb == NULL || rowCount <= 0) return JNI_FALSE;

    jsize length = (*env)->GetArrayLength(env, argb);
    if ((int64_t) enc->width * (int64_t) rowCount > (int64_t) length) {
        return JNI_FALSE;
    }
    jint *pixels = (*env)->GetIntArrayElements(env, argb, NULL);
    if (pixels == NULL) return JNI_FALSE;

    JSAMPLE *row = (JSAMPLE *) malloc((size_t) enc->width * 3);
    if (row == NULL) {
        (*env)->ReleaseIntArrayElements(env, argb, pixels, JNI_ABORT);
        return JNI_FALSE;
    }

    if (setjmp(enc->err.jump) != 0) {
        free(row);
        (*env)->ReleaseIntArrayElements(env, argb, pixels, JNI_ABORT);
        return JNI_FALSE;
    }

    jboolean ok = JNI_TRUE;
    for (int y = 0; y < rowCount; ++y) {
        const jint *src = pixels + (size_t) y * (size_t) enc->width;
        for (int x = 0; x < enc->width; ++x) {
            const jint c = src[x];
            row[x * 3 + 0] = (JSAMPLE) ((c >> 16) & 0xFF); // R
            row[x * 3 + 1] = (JSAMPLE) ((c >> 8) & 0xFF);  // G
            row[x * 3 + 2] = (JSAMPLE) (c & 0xFF);         // B
        }
        JSAMPROW rp = row;
        if (jpeg_write_scanlines(&enc->cinfo, &rp, 1) != 1) {
            ok = JNI_FALSE;
            break;
        }
    }
    free(row);
    (*env)->ReleaseIntArrayElements(env, argb, pixels, JNI_ABORT);
    return ok;
}

JNIEXPORT jboolean JNICALL JNI_FN(nativeFinish)(JNIEnv *env, jobject self, jlong handle) {
    (void) env;
    (void) self;
    JpegEncoder *enc = (JpegEncoder *) (intptr_t) handle;
    if (enc == NULL) return JNI_FALSE;

    if (setjmp(enc->err.jump) != 0) {
        destroyEncoder(enc, 1);
        return JNI_FALSE;
    }

    jpeg_finish_compress(&enc->cinfo);
    jpeg_destroy_compress(&enc->cinfo);
    /* The Kotlin side treats a short or unflushed file as a failed export, so
     * every buffered byte must reach the descriptor before reporting success. */
    int flushFailed = fflush(enc->file) != 0;
    int closeFailed = fclose(enc->file) != 0;
    enc->file = NULL;
    free(enc);
    return (flushFailed || closeFailed) ? JNI_FALSE : JNI_TRUE;
}

JNIEXPORT void JNICALL JNI_FN(nativeAbort)(JNIEnv *env, jobject self, jlong handle) {
    (void) env;
    (void) self;
    JpegEncoder *enc = (JpegEncoder *) (intptr_t) handle;
    if (enc == NULL) return;
    destroyEncoder(enc, 1);
}

#else /* !HAVE_JPEG_ENCODER */

#define JNI_FN(name) Java_com_dot_gallery_editor_android_JpegScanlineEncoder_##name

JNIEXPORT jboolean JNICALL JNI_FN(nativeSelfTest)(JNIEnv *env, jobject self) {
    (void) env;
    (void) self;
    return JNI_FALSE;
}

JNIEXPORT jlong JNICALL JNI_FN(nativeOpen)(
        JNIEnv *env, jobject self, jint fd, jint width, jint height, jint quality) {
    (void) env;
    (void) self;
    (void) fd;
    (void) width;
    (void) height;
    (void) quality;
    return 0;
}

JNIEXPORT jboolean JNICALL JNI_FN(nativeWriteRows)(
        JNIEnv *env, jobject self, jlong handle, jintArray argb, jint rowCount) {
    (void) env;
    (void) self;
    (void) handle;
    (void) argb;
    (void) rowCount;
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL JNI_FN(nativeFinish)(JNIEnv *env, jobject self, jlong handle) {
    (void) env;
    (void) self;
    (void) handle;
    return JNI_FALSE;
}

JNIEXPORT void JNICALL JNI_FN(nativeAbort)(JNIEnv *env, jobject self, jlong handle) {
    (void) env;
    (void) self;
    (void) handle;
}

#endif /* HAVE_JPEG_ENCODER */
