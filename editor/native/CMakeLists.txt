cmake_minimum_required(VERSION 3.16)

project(refra_editor C)

# Pure C11 reference kernels; no C++, no platform SDK types.
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_EXTENSIONS OFF)

add_library(refra_editor STATIC src/color.c)
target_include_directories(refra_editor PUBLIC include)

if (MSVC)
    target_compile_options(refra_editor PRIVATE /W4 /WX)
else ()
    target_compile_options(refra_editor PRIVATE -Wall -Wextra -Werror)
    target_link_libraries(refra_editor PUBLIC m)
endif ()

option(REFRA_EDITOR_BUILD_TESTS "Build the standalone kernel tests" ON)
if (REFRA_EDITOR_BUILD_TESTS)
    enable_testing()

    add_executable(refra_editor_color_test tests/color_test.c)
    if (MSVC)
        target_compile_options(refra_editor_color_test PRIVATE /W4 /WX)
    else ()
        target_compile_options(refra_editor_color_test PRIVATE -Wall -Wextra -Werror)
    endif ()
    target_link_libraries(refra_editor_color_test PRIVATE refra_editor)

    add_test(NAME color_rgba_f32 COMMAND refra_editor_color_test)
endif ()

# Production JNI bridge: packaged as refra_editor_jni (Android external-
# native-build .so, desktop staged resource). On Android the NDK supplies
# jni.h; on desktop JNI headers come from find_package — headers only, the
# shared object never links the JVM.
option(REFRA_EDITOR_BUILD_JNI "Build the packaged JNI bridge" OFF)
if (REFRA_EDITOR_BUILD_JNI)
    if (NOT ANDROID)
        find_package(JNI REQUIRED)
    endif ()
    if (ANDROID)
        # The HEIF bridge is C++ (libheif/x265/aom are C++ static libraries).
        enable_language(CXX)
        find_library(refra_editor_log_lib log)
    endif ()
    set_target_properties(refra_editor PROPERTIES POSITION_INDEPENDENT_CODE ON)
    add_library(refra_editor_jni SHARED src/color_jni.c)
    target_include_directories(refra_editor_jni PRIVATE ${JNI_INCLUDE_DIRS})
    target_link_libraries(refra_editor_jni PRIVATE refra_editor)
    if (MSVC)
        target_compile_options(refra_editor_jni PRIVATE /W4 /WX)
    else ()
        target_compile_options(refra_editor_jni PRIVATE -Wall -Wextra -Werror)
    endif ()
    if (ANDROID)
        target_link_options(refra_editor_jni PRIVATE
            -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384)

        # Scanline JPEG encoder for the Android export: a lossless PNG of a
        # camera photo is an order of magnitude larger than the JPEG it came
        # from, so JPEG sources are re-encoded at the source's own quality
        # while still streaming one band at a time. The static libjpeg-turbo
        # is the prebuilt artifact :app:androidApp already links
        # (scripts/native/build-imgcodec.sh). Without it the source compiles
        # to stubs and the Kotlin side keeps the lossless PNG fallback.
        set(REFRA_EDITOR_IMGCODEC_PREBUILT_ROOT
            "${CMAKE_CURRENT_SOURCE_DIR}/../../app/androidApp/src/main/cpp/imgcodec"
            CACHE PATH "Prebuilt libjpeg-turbo root, with one subdirectory per ABI")
        set(REFRA_EDITOR_IMGCODEC_PREBUILT
            "${REFRA_EDITOR_IMGCODEC_PREBUILT_ROOT}/${ANDROID_ABI}")
        target_sources(refra_editor_jni PRIVATE src/jpeg_jni.c)
        if (EXISTS "${REFRA_EDITOR_IMGCODEC_PREBUILT}/lib/libjpeg.a")
            message(STATUS "refra_editor_jni: streaming JPEG encode enabled for ${ANDROID_ABI}")
            add_library(refra_editor_jpeg STATIC IMPORTED)
            set_target_properties(refra_editor_jpeg PROPERTIES
                IMPORTED_LOCATION "${REFRA_EDITOR_IMGCODEC_PREBUILT}/lib/libjpeg.a")
            target_include_directories(refra_editor_jni PRIVATE
                "${REFRA_EDITOR_IMGCODEC_PREBUILT}/include")
            target_compile_definitions(refra_editor_jni PRIVATE HAVE_JPEG_ENCODER=1)
            target_link_libraries(refra_editor_jni PRIVATE refra_editor_jpeg)
        else ()
            message(WARNING "refra_editor_jni: no prebuilt libjpeg-turbo for ${ANDROID_ABI}; "
                "JPEG export falls back to PNG. Run scripts/native/build-imgcodec.sh all.")
        endif ()

        # Tiled HEIF encode: libheif's grid API consumes 512×512 cells, so the
        # export buffers one tile row instead of holding the whole output.
        # Same prebuilt libheif + x265 + aom the app links
        # (scripts/native/build-heif-encode.sh). Without it HEIF is not offered.
        set(REFRA_EDITOR_HEIFENC_PREBUILT_ROOT
            "${CMAKE_CURRENT_SOURCE_DIR}/../../app/androidApp/src/main/cpp/heifenc"
            CACHE PATH "Prebuilt libheif encoder root, with one subdirectory per ABI")
        set(REFRA_EDITOR_HEIFENC_PREBUILT
            "${REFRA_EDITOR_HEIFENC_PREBUILT_ROOT}/${ANDROID_ABI}")
        target_sources(refra_editor_jni PRIVATE src/heif_jni.cpp)
        if (EXISTS "${REFRA_EDITOR_HEIFENC_PREBUILT}/lib/libheif.a"
            AND EXISTS "${REFRA_EDITOR_HEIFENC_PREBUILT}/lib/libx265.a"
            AND EXISTS "${REFRA_EDITOR_HEIFENC_PREBUILT}/lib/libaom.a"
            AND EXISTS "${REFRA_EDITOR_HEIFENC_PREBUILT}/lib/libde265.a")
            message(STATUS "refra_editor_jni: tiled HEIF encode enabled for ${ANDROID_ABI}")
            add_library(refra_editor_heif STATIC IMPORTED)
            set_target_properties(refra_editor_heif PROPERTIES
                IMPORTED_LOCATION "${REFRA_EDITOR_HEIFENC_PREBUILT}/lib/libheif.a")
            add_library(refra_editor_x265 STATIC IMPORTED)
            set_target_properties(refra_editor_x265 PROPERTIES
                IMPORTED_LOCATION "${REFRA_EDITOR_HEIFENC_PREBUILT}/lib/libx265.a")
            add_library(refra_editor_aom STATIC IMPORTED)
            set_target_properties(refra_editor_aom PROPERTIES
                IMPORTED_LOCATION "${REFRA_EDITOR_HEIFENC_PREBUILT}/lib/libaom.a")
            add_library(refra_editor_de265 STATIC IMPORTED)
            set_target_properties(refra_editor_de265 PROPERTIES
                IMPORTED_LOCATION "${REFRA_EDITOR_HEIFENC_PREBUILT}/lib/libde265.a")
            target_include_directories(refra_editor_jni PRIVATE
                "${REFRA_EDITOR_HEIFENC_PREBUILT}/include")
            target_compile_definitions(refra_editor_jni PRIVATE HAVE_HEIFENC=1)
            # libheif references x265/aom/de265 symbols, so it comes first.
            target_link_libraries(refra_editor_jni PRIVATE
                refra_editor_heif refra_editor_x265 refra_editor_aom refra_editor_de265
                ${refra_editor_log_lib})
        else ()
            message(WARNING "refra_editor_jni: no prebuilt libheif/x265/aom for ${ANDROID_ABI}; "
                "HEIF export is unavailable. Run scripts/native/build-heif-encode.sh all.")
        endif ()
    endif ()
endif ()

# Test-only JVM bridge: builds a shared library desktopTest loads with
# System.load for the Kotlin<->C conformance comparison. Never packaged or
# shipped; disabled by default.
option(REFRA_EDITOR_BUILD_JNI_TEST_BRIDGE "Build JVM conformance-test bridge" OFF)
if (REFRA_EDITOR_BUILD_JNI_TEST_BRIDGE)
    find_package(JNI REQUIRED)
    set_target_properties(refra_editor PROPERTIES POSITION_INDEPENDENT_CODE ON)
    add_library(refra_editor_test_jni SHARED tests/color_jni_test.c)
    # System.mapLibraryName has no "lib" on Windows, where MinGW would add one.
    if (WIN32)
        set_target_properties(refra_editor_test_jni PROPERTIES PREFIX "")
    endif ()
    target_include_directories(refra_editor_test_jni PRIVATE ${JNI_INCLUDE_DIRS})
    target_link_libraries(refra_editor_test_jni PRIVATE refra_editor)
    if (MSVC)
        target_compile_options(refra_editor_test_jni PRIVATE /W4 /WX)
    else ()
        target_compile_options(refra_editor_test_jni PRIVATE -Wall -Wextra -Werror)
    endif ()
endif ()
