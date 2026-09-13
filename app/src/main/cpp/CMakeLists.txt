# SPDX-FileCopyrightText: 2023-2026 IacobIacob01
# SPDX-License-Identifier: Apache-2.0
#
# Builds the `heiftiles` JNI shared library. It links the prebuilt static libheif + libde265
# (produced by scripts/native/build-heif.sh into heif/<ABI>/) to expose libheif's memory-bounded
# tiled decode. If no prebuilt exists for the current ABI, a stub .so is built instead and the
# Kotlin layer falls back to its non-native region-decode path.

cmake_minimum_required(VERSION 3.22.1)
project(heiftiles LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
file(REAL_PATH "${CMAKE_CURRENT_SOURCE_DIR}" REFRA_SOURCE_DIR_REAL)
file(REAL_PATH "${CMAKE_CURRENT_BINARY_DIR}" REFRA_BINARY_DIR_REAL)
file(REAL_PATH "${CMAKE_ANDROID_NDK}" REFRA_NDK_DIR_REAL)
add_compile_options(
        "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=/usr/src/refra-jni"
        "-ffile-prefix-map=${REFRA_SOURCE_DIR_REAL}=/usr/src/refra-jni"
        "-ffile-prefix-map=${CMAKE_CURRENT_BINARY_DIR}=/usr/src/refra-jni-build"
        "-ffile-prefix-map=${REFRA_BINARY_DIR_REAL}=/usr/src/refra-jni-build"
        "-ffile-prefix-map=${CMAKE_ANDROID_NDK}=/opt/android-ndk"
        "-ffile-prefix-map=${REFRA_NDK_DIR_REAL}=/opt/android-ndk")

set(HEIF_PREBUILT "${CMAKE_CURRENT_SOURCE_DIR}/heif/${ANDROID_ABI}")

add_library(heiftiles SHARED heif_tiles_jni.cpp)

find_library(log-lib log)
find_library(dl-lib dl)

add_library(metadata_brotli SHARED metadata_brotli_jni.cpp)
target_link_libraries(metadata_brotli ${dl-lib})

set(JP2_PREBUILT "${CMAKE_CURRENT_SOURCE_DIR}/jp2codec/${ANDROID_ABI}")
add_library(openjpeg SHARED jp2_bridge_jni.cpp)
if(EXISTS "${JP2_PREBUILT}/lib/libopenjpeg.a")
    message(STATUS "openjpeg: using source-built static codec for ${ANDROID_ABI}")
    target_link_libraries(openjpeg
            "-Wl,--whole-archive"
            "${JP2_PREBUILT}/lib/libopenjpeg.a"
            "-Wl,--no-whole-archive"
            ${log-lib})
else()
    message(WARNING "openjpeg: no source-built codec for ${ANDROID_ABI}; building stub. "
            "Run scripts/native/build-jp2.sh all to enable JPEG 2000 decode for every ABI.")
    target_link_libraries(openjpeg ${log-lib})
endif()
target_link_options(openjpeg PRIVATE "-Wl,-z,max-page-size=16384")

if(EXISTS "${HEIF_PREBUILT}/lib/libheif.a" AND EXISTS "${HEIF_PREBUILT}/lib/libde265.a")
    message(STATUS "heiftiles: using prebuilt libheif for ${ANDROID_ABI}")

    add_library(heif STATIC IMPORTED)
    set_target_properties(heif PROPERTIES
            IMPORTED_LOCATION "${HEIF_PREBUILT}/lib/libheif.a")

    add_library(de265 STATIC IMPORTED)
    set_target_properties(de265 PROPERTIES
            IMPORTED_LOCATION "${HEIF_PREBUILT}/lib/libde265.a")

    target_include_directories(heiftiles PRIVATE "${HEIF_PREBUILT}/include")
    target_compile_definitions(heiftiles PRIVATE HAVE_LIBHEIF=1)

    # Order matters for static linking: libheif references libde265 symbols, so heif before de265.
    target_link_libraries(heiftiles heif de265 ${log-lib})
else()
    message(WARNING "heiftiles: no prebuilt libheif for ${ANDROID_ABI}; building stub. "
            "Run scripts/native/build-heif.sh all to enable native tiled HEIC decode for every ABI.")
    target_link_libraries(heiftiles ${log-lib})
endif()

# ---------------------------------------------------------------------------------------------
# imgstream: scanline (row-streaming) JPEG + PNG encoder. Links prebuilt static libjpeg-turbo +
# libpng + zlib (produced by scripts/native/build-imgcodec.sh into imgcodec/<ABI>/). Lets the
# editor bake write a full-resolution result one strip at a time without holding the whole output
# bitmap. Missing prebuilt => stub, and Kotlin falls back to the whole-bitmap encode.
set(IMGCODEC_PREBUILT "${CMAKE_CURRENT_SOURCE_DIR}/imgcodec/${ANDROID_ABI}")

add_library(imgstream SHARED img_stream_jni.cpp)

if(EXISTS "${IMGCODEC_PREBUILT}/lib/libjpeg.a" AND EXISTS "${IMGCODEC_PREBUILT}/lib/libpng.a" AND EXISTS "${IMGCODEC_PREBUILT}/lib/libz.a")
    message(STATUS "imgstream: using prebuilt libjpeg-turbo + libpng for ${ANDROID_ABI}")

    add_library(jpeg STATIC IMPORTED)
    set_target_properties(jpeg PROPERTIES
            IMPORTED_LOCATION "${IMGCODEC_PREBUILT}/lib/libjpeg.a")

    add_library(png STATIC IMPORTED)
    set_target_properties(png PROPERTIES
            IMPORTED_LOCATION "${IMGCODEC_PREBUILT}/lib/libpng.a")

    add_library(z STATIC IMPORTED)
    set_target_properties(z PROPERTIES
            IMPORTED_LOCATION "${IMGCODEC_PREBUILT}/lib/libz.a")

    target_include_directories(imgstream PRIVATE "${IMGCODEC_PREBUILT}/include")
    target_compile_definitions(imgstream PRIVATE HAVE_IMGCODEC=1)

    # libpng depends on zlib, so png before z.
    target_link_libraries(imgstream jpeg png z ${log-lib})
else()
    message(WARNING "imgstream: no prebuilt libjpeg-turbo/libpng for ${ANDROID_ABI}; building stub. "
            "Run scripts/native/build-imgcodec.sh all to enable streaming JPEG/PNG encode for every ABI.")
    target_link_libraries(imgstream ${log-lib})
endif()

# ---------------------------------------------------------------------------------------------
# heifenc: tiled HEIC/AVIF encoder. Links prebuilt static libheif (WITH encoders) + x265 + aom +
# libde265 (produced by scripts/native/build-heif-encode.sh into heifenc/<ABI>/). Encodes a large
# edited image as a libheif grid — one tile at a time — so the whole decoded image is never held.
# Missing prebuilt => stub, and Kotlin falls back to the whole-bitmap HeifCoder encode.
set(HEIFENC_PREBUILT "${CMAKE_CURRENT_SOURCE_DIR}/heifenc/${ANDROID_ABI}")

add_library(heifenc SHARED heif_encode_jni.cpp)

if(EXISTS "${HEIFENC_PREBUILT}/lib/libheif.a" AND EXISTS "${HEIFENC_PREBUILT}/lib/libx265.a" AND EXISTS "${HEIFENC_PREBUILT}/lib/libaom.a" AND EXISTS "${HEIFENC_PREBUILT}/lib/libde265.a")
    message(STATUS "heifenc: using prebuilt libheif (encoders) + x265 + aom for ${ANDROID_ABI}")

    add_library(heif_enc STATIC IMPORTED)
    set_target_properties(heif_enc PROPERTIES IMPORTED_LOCATION "${HEIFENC_PREBUILT}/lib/libheif.a")
    add_library(x265 STATIC IMPORTED)
    set_target_properties(x265 PROPERTIES IMPORTED_LOCATION "${HEIFENC_PREBUILT}/lib/libx265.a")
    add_library(aom STATIC IMPORTED)
    set_target_properties(aom PROPERTIES IMPORTED_LOCATION "${HEIFENC_PREBUILT}/lib/libaom.a")
    add_library(de265_enc STATIC IMPORTED)
    set_target_properties(de265_enc PROPERTIES IMPORTED_LOCATION "${HEIFENC_PREBUILT}/lib/libde265.a")

    target_include_directories(heifenc PRIVATE "${HEIFENC_PREBUILT}/include")
    target_compile_definitions(heifenc PRIVATE HAVE_HEIFENC=1)

    # libheif references x265/aom/de265 symbols, so libheif must come first. x265/aom are C++ and
    # pull in the STL; link it explicitly.
    target_link_libraries(heifenc heif_enc x265 aom de265_enc ${log-lib})
else()
    message(WARNING "heifenc: no prebuilt libheif-encoders/x265/aom for ${ANDROID_ABI}; building stub. "
            "Run scripts/native/build-heif-encode.sh all to enable tiled HEIC/AVIF encode for every ABI.")
    target_link_libraries(heifenc ${log-lib})
endif()

# ---------------------------------------------------------------------------------------------
# rawcodec: native RAW decode (LibRaw) + 8/16-bit TIFF export (libtiff). Links prebuilt static
# libraw + libtiff + libjpeg + zlib (produced by scripts/native/build-libraw.sh into
# rawcodec/<ABI>/). Gives true demosaiced RAW rendering for all LibRaw-supported camera formats.
# Missing prebuilt => stub, and Kotlin falls back to the embedded-JPEG-preview path.
set(RAWCODEC_PREBUILT "${CMAKE_CURRENT_SOURCE_DIR}/rawcodec/${ANDROID_ABI}")

add_library(rawcodec SHARED raw_codec_jni.cpp)

if(EXISTS "${RAWCODEC_PREBUILT}/lib/libraw.a" AND EXISTS "${RAWCODEC_PREBUILT}/lib/libtiff.a" AND EXISTS "${RAWCODEC_PREBUILT}/lib/libjpeg.a" AND EXISTS "${RAWCODEC_PREBUILT}/lib/libz.a")
    message(STATUS "rawcodec: using prebuilt LibRaw + libtiff for ${ANDROID_ABI}")

    add_library(raw STATIC IMPORTED)
    set_target_properties(raw PROPERTIES IMPORTED_LOCATION "${RAWCODEC_PREBUILT}/lib/libraw.a")
    add_library(tiff STATIC IMPORTED)
    set_target_properties(tiff PROPERTIES IMPORTED_LOCATION "${RAWCODEC_PREBUILT}/lib/libtiff.a")
    add_library(raw_jpeg STATIC IMPORTED)
    set_target_properties(raw_jpeg PROPERTIES IMPORTED_LOCATION "${RAWCODEC_PREBUILT}/lib/libjpeg.a")
    add_library(raw_z STATIC IMPORTED)
    set_target_properties(raw_z PROPERTIES IMPORTED_LOCATION "${RAWCODEC_PREBUILT}/lib/libz.a")

    target_include_directories(rawcodec PRIVATE "${RAWCODEC_PREBUILT}/include")
    target_compile_definitions(rawcodec PRIVATE HAVE_LIBRAW=1)

    # Link order (static): LibRaw + libtiff reference libjpeg/zlib symbols, so they come first.
    # LibRaw is C++ and pulls in the STL.
    target_link_libraries(rawcodec raw tiff raw_jpeg raw_z ${log-lib})
else()
    message(WARNING "rawcodec: no prebuilt LibRaw/libtiff for ${ANDROID_ABI}; building stub. "
            "Run scripts/native/build-libraw.sh all to enable native RAW decode/export for every ABI.")
    target_link_libraries(rawcodec ${log-lib})
endif()
