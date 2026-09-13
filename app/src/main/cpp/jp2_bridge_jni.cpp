/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

#include <jni.h>

extern "C" JNIEXPORT jintArray JNICALL
Java_com_gemalto_jp2_JP2Decoder_decodeJP2ByteArray(
        JNIEnv* env,
        jclass,
        jbyteArray data,
        jint reduce,
        jint layers);

extern "C" JNIEXPORT jintArray JNICALL
Java_com_gemalto_jp2_JP2Decoder_readJP2HeaderByteArray(
        JNIEnv* env,
        jclass,
        jbyteArray data);

extern "C" JNIEXPORT jintArray JNICALL
Java_com_dot_gallery_core_decoder_format_Jp2ImageDecoder_decodeJp2ByteArray(
        JNIEnv* env,
        jobject,
        jbyteArray data,
        jint reduce,
        jint layers) {
    return Java_com_gemalto_jp2_JP2Decoder_decodeJP2ByteArray(
            env,
            nullptr,
            data,
            reduce,
            layers);
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_dot_gallery_core_decoder_format_Jp2ImageDecoder_readJp2HeaderByteArray(
        JNIEnv* env,
        jobject,
        jbyteArray data) {
    return Java_com_gemalto_jp2_JP2Decoder_readJP2HeaderByteArray(
            env,
            nullptr,
            data);
}
