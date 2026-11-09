/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 *
 * JNI bridge for the viewer's "codes in photos" scanner (com.dot.gallery.core.insight.CodeScanNative).
 *
 * Two calls, both allocation-free on the Java heap when nothing is found:
 *  - nativePrepareFrame locks a software Bitmap and box-downscales it into 8-bit luminance in a
 *    caller-owned direct ByteBuffer (reused across scans).
 *  - nativeScan runs zxing-cpp's reader over that buffer and returns the decoded texts (or null),
 *    writing format, content type and the four corner points of each code into a caller-owned
 *    IntArray.
 *
 * Without HAVE_ZXING (an ABI without the source-built static library) everything is a stub that
 * reports "unavailable", so the .so still loads and the Kotlin side simply never scans.
 */

#include <jni.h>
#include <android/bitmap.h>
#include <cstdint>
#include <cstring>

#ifdef HAVE_ZXING
#include <ZXing/ReadBarcode.h>
#include <string>
#endif

namespace {

// Per-code layout of the meta IntArray: format, contentType, then x0,y0 .. x3,y3 (top-left,
// top-right, bottom-right, bottom-left in frame pixels).
constexpr int kMetaStride = 10;

inline uint8_t lumaRgba(const uint8_t *p) {
    return static_cast<uint8_t>((77 * p[0] + 150 * p[1] + 29 * p[2]) >> 8);
}

inline uint8_t luma565(uint16_t v) {
    const int r = ((v >> 11) & 0x1F) * 255 / 31;
    const int g = ((v >> 5) & 0x3F) * 255 / 63;
    const int b = (v & 0x1F) * 255 / 31;
    return static_cast<uint8_t>((77 * r + 150 * g + 29 * b) >> 8);
}

// Area-average downscale (or 1:1 copy) of [src] into [dst] (dw x dh, tightly packed).
template<typename PixelLuma>
void downscale(const uint8_t *src, int sw, int sh, int stride, int bpp,
               uint8_t *dst, int dw, int dh, PixelLuma luma) {
    for (int y = 0; y < dh; ++y) {
        const int y0 = static_cast<int>(static_cast<int64_t>(y) * sh / dh);
        int y1 = static_cast<int>(static_cast<int64_t>(y + 1) * sh / dh);
        if (y1 <= y0) y1 = y0 + 1;
        for (int x = 0; x < dw; ++x) {
            const int x0 = static_cast<int>(static_cast<int64_t>(x) * sw / dw);
            int x1 = static_cast<int>(static_cast<int64_t>(x + 1) * sw / dw);
            if (x1 <= x0) x1 = x0 + 1;
            uint32_t sum = 0;
            uint32_t count = 0;
            for (int yy = y0; yy < y1; ++yy) {
                const uint8_t *row = src + static_cast<size_t>(yy) * stride;
                for (int xx = x0; xx < x1; ++xx) {
                    sum += luma(row + static_cast<size_t>(xx) * bpp);
                    ++count;
                }
            }
            dst[static_cast<size_t>(y) * dw + x] = static_cast<uint8_t>(sum / count);
        }
    }
}

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_dot_gallery_core_insight_CodeScanNative_nativeIsAvailable(JNIEnv *, jclass) {
#ifdef HAVE_ZXING
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

// Writes the frame size into outSize[0..1]; returns false for unsupported bitmaps (hardware,
// F16, A8) or a buffer too small for the result.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_dot_gallery_core_insight_CodeScanNative_nativePrepareFrame(
        JNIEnv *env, jclass, jobject bitmap, jobject buffer, jint maxSide, jintArray outSize) {
    if (bitmap == nullptr || buffer == nullptr || outSize == nullptr || maxSide <= 0) return JNI_FALSE;
    auto *dst = static_cast<uint8_t *>(env->GetDirectBufferAddress(buffer));
    const jlong capacity = env->GetDirectBufferCapacity(buffer);
    if (dst == nullptr || capacity <= 0) return JNI_FALSE;

    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;
    if (info.width == 0 || info.height == 0) return JNI_FALSE;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 && info.format != ANDROID_BITMAP_FORMAT_RGB_565) {
        return JNI_FALSE;
    }

    const int sw = static_cast<int>(info.width);
    const int sh = static_cast<int>(info.height);
    int dw = sw;
    int dh = sh;
    const int longSide = sw > sh ? sw : sh;
    if (longSide > maxSide) {
        dw = static_cast<int>(static_cast<int64_t>(sw) * maxSide / longSide);
        dh = static_cast<int>(static_cast<int64_t>(sh) * maxSide / longSide);
        if (dw < 1) dw = 1;
        if (dh < 1) dh = 1;
    }
    if (static_cast<jlong>(dw) * dh > capacity) return JNI_FALSE;

    void *pixels = nullptr;
    // Fails for hardware bitmaps, which the Kotlin side copies to software first.
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS || pixels == nullptr) {
        return JNI_FALSE;
    }
    const auto *src = static_cast<const uint8_t *>(pixels);
    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        downscale(src, sw, sh, static_cast<int>(info.stride), 4, dst, dw, dh,
                  [](const uint8_t *p) { return lumaRgba(p); });
    } else {
        downscale(src, sw, sh, static_cast<int>(info.stride), 2, dst, dw, dh,
                  [](const uint8_t *p) {
                      uint16_t v;
                      std::memcpy(&v, p, sizeof(v));
                      return luma565(v);
                  });
    }
    AndroidBitmap_unlockPixels(env, bitmap);

    jint size[2] = {dw, dh};
    env->SetIntArrayRegion(outSize, 0, 2, size);
    return JNI_TRUE;
}

// Returns the decoded texts (one per code, in the same order as the meta records) or null when
// nothing was found. Each text is capped at maxTextBytes UTF-8 bytes.
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_dot_gallery_core_insight_CodeScanNative_nativeScan(
        JNIEnv *env, jclass, jobject buffer, jint width, jint height, jint formats,
        jboolean tryHarder, jint maxCodes, jint maxTextBytes, jintArray meta) {
#ifdef HAVE_ZXING
    if (buffer == nullptr || meta == nullptr || width <= 0 || height <= 0 || maxCodes <= 0) return nullptr;
    const auto *data = static_cast<const uint8_t *>(env->GetDirectBufferAddress(buffer));
    const jlong capacity = env->GetDirectBufferCapacity(buffer);
    if (data == nullptr || static_cast<jlong>(width) * height > capacity) return nullptr;
    if (env->GetArrayLength(meta) < maxCodes * kMetaStride) return nullptr;

    try {
        ZXing::ReaderOptions options;
        options.setFormats(static_cast<ZXing::BarcodeFormat>(formats));
        options.setTryHarder(tryHarder == JNI_TRUE);
        options.setTryRotate(tryHarder == JNI_TRUE);
        options.setTryInvert(tryHarder == JNI_TRUE);
        options.setTryDownscale(true);
        options.setTextMode(ZXing::TextMode::Plain);
        options.setMaxNumberOfSymbols(static_cast<uint8_t>(maxCodes > 255 ? 255 : maxCodes));

        const ZXing::ImageView image(data, width, height, ZXing::ImageFormat::Lum);
        const auto barcodes = ZXing::ReadBarcodes(image, options);

        int count = 0;
        for (const auto &barcode: barcodes) {
            if (barcode.isValid() && count < maxCodes) ++count;
        }
        if (count == 0) return nullptr;

        jclass stringClass = env->FindClass("java/lang/String");
        if (stringClass == nullptr) return nullptr;
        jobjectArray texts = env->NewObjectArray(count, stringClass, nullptr);
        env->DeleteLocalRef(stringClass);
        if (texts == nullptr) return nullptr;

        int index = 0;
        for (const auto &barcode: barcodes) {
            if (!barcode.isValid() || index >= count) continue;
            std::string text = barcode.text();
            if (maxTextBytes > 0 && text.size() > static_cast<size_t>(maxTextBytes)) {
                size_t cut = static_cast<size_t>(maxTextBytes);
                // Do not split a UTF-8 sequence.
                while (cut > 0 && (static_cast<uint8_t>(text[cut]) & 0xC0) == 0x80) --cut;
                text.resize(cut);
            }
            // NewStringUTF expects modified UTF-8; zxing text is standard UTF-8, which only
            // differs for NUL and supplementary characters, so build the string from UTF-16.
            std::u16string utf16;
            utf16.reserve(text.size());
            for (size_t i = 0; i < text.size();) {
                const auto c = static_cast<uint8_t>(text[i]);
                uint32_t cp;
                size_t len;
                if (c < 0x80) { cp = c; len = 1; }
                else if ((c >> 5) == 0x6 && i + 1 < text.size()) { cp = ((c & 0x1F) << 6) | (text[i + 1] & 0x3F); len = 2; }
                else if ((c >> 4) == 0xE && i + 2 < text.size()) { cp = ((c & 0x0F) << 12) | ((text[i + 1] & 0x3F) << 6) | (text[i + 2] & 0x3F); len = 3; }
                else if ((c >> 3) == 0x1E && i + 3 < text.size()) { cp = ((c & 0x07) << 18) | ((text[i + 1] & 0x3F) << 12) | ((text[i + 2] & 0x3F) << 6) | (text[i + 3] & 0x3F); len = 4; }
                else { cp = 0xFFFD; len = 1; }
                i += len;
                if (cp >= 0x10000) {
                    cp -= 0x10000;
                    utf16.push_back(static_cast<char16_t>(0xD800 + (cp >> 10)));
                    utf16.push_back(static_cast<char16_t>(0xDC00 + (cp & 0x3FF)));
                } else {
                    utf16.push_back(static_cast<char16_t>(cp));
                }
            }
            jstring jtext = env->NewString(reinterpret_cast<const jchar *>(utf16.data()),
                                           static_cast<jsize>(utf16.size()));
            if (jtext == nullptr) return nullptr;
            env->SetObjectArrayElement(texts, index, jtext);
            env->DeleteLocalRef(jtext);

            const auto &pos = barcode.position();
            jint record[kMetaStride] = {
                    static_cast<jint>(barcode.format()),
                    static_cast<jint>(barcode.contentType()),
                    pos.topLeft().x, pos.topLeft().y,
                    pos.topRight().x, pos.topRight().y,
                    pos.bottomRight().x, pos.bottomRight().y,
                    pos.bottomLeft().x, pos.bottomLeft().y,
            };
            env->SetIntArrayRegion(meta, index * kMetaStride, kMetaStride, record);
            ++index;
        }
        return texts;
    } catch (...) {
        return nullptr;
    }
#else
    (void) env; (void) buffer; (void) width; (void) height; (void) formats;
    (void) tryHarder; (void) maxCodes; (void) maxTextBytes; (void) meta;
    return nullptr;
#endif
}
