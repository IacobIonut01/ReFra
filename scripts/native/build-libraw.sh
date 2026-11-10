#!/usr/bin/env bash
#
# SPDX-FileCopyrightText: 2023-2026 IacobIacob01
# SPDX-License-Identifier: Apache-2.0
#
# Cross-compiles LibRaw (+ libtiff, and the zlib/libjpeg-turbo they depend on) as static libraries
# for Android using the NDK CMake toolchain, installing the resulting static libs + headers into
#   app/androidApp/src/main/cpp/rawcodec/<abi>/{include,lib}
# so the app's CMakeLists can link them into the `rawcodec` JNI library. That JNI bridge gives the
# app true native RAW decoding (demosaic) for every LibRaw-supported camera format, plus a 16-bit
# TIFF export path via libtiff.
#
# Run once per machine (and again only when bumping the pinned versions):
#   scripts/native/build-libraw.sh            # builds arm64-v8a (device/dev ABI)
#   scripts/native/build-libraw.sh all        # builds all four ABIs for release
#
# Requires: internet access, the pinned NDK, and CMake (SDK 3.31.x preferred; 4.x works via
# NATIVE_CMAKE_POLICY_MIN).
set -euo pipefail

# --- Pinned versions -------------------------------------------------------------------------
ZLIB_VERSION="1.3.1"
LIBJPEGTURBO_VERSION="3.0.4"
LIBRAW_VERSION="0.21.3"
LIBTIFF_VERSION="4.6.0"
ANDROID_API=29                          # matches app minSdk
CMAKE_VERSION="3.31.6"

# --- Paths -----------------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
source "$SCRIPT_DIR/native-common.sh"
OUT_ROOT="${NATIVE_OUTPUT_BASE:-$REPO_ROOT/app/androidApp/src/main/cpp}/rawcodec"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ]; then
    SDK="$(native_read_property "$REPO_ROOT/local.properties" sdk.dir || true)"
fi
if [ -z "$SDK" ] || [ ! -d "$SDK" ]; then
    echo "ERROR: Android SDK not found. Set ANDROID_HOME or sdk.dir in local.properties." >&2
    exit 1
fi

NDK_VERSION="$(sed -n 's/^refra\.ndkVersion=//p' "$REPO_ROOT/gradle.properties" | head -n1)"
NDK_DIR="$SDK/ndk/$NDK_VERSION"
TOOLCHAIN="$NDK_DIR/build/cmake/android.toolchain.cmake"
if [ -z "$NDK_VERSION" ] || [ ! -f "$TOOLCHAIN" ] ||
    ! grep -Fqx "Pkg.Revision = $NDK_VERSION" "$NDK_DIR/source.properties"; then
    echo "ERROR: Pinned NDK $NDK_VERSION not found or invalid. Install ndk;$NDK_VERSION." >&2
    exit 1
fi

CMAKE_BIN="$SDK/cmake/$CMAKE_VERSION/bin/cmake"
NINJA_BIN="$SDK/cmake/$CMAKE_VERSION/bin/ninja"
if [ ! -x "$CMAKE_BIN" ]; then
    CMAKE_BIN="$(command -v cmake || true)"
    NINJA_BIN="$(command -v ninja || true)"
fi
if [ -z "$CMAKE_BIN" ]; then echo "ERROR: cmake $CMAKE_VERSION not found." >&2; exit 1; fi

echo "SDK      = $SDK"
echo "NDK      = $NDK_DIR"
echo "CMake    = $CMAKE_BIN"
echo "Out      = $OUT_ROOT"

# --- Source download -------------------------------------------------------------------------
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
ZLIB_SRC="$WORK/zlib"
JPEG_SRC="$WORK/libjpeg-turbo"
RAW_SRC="$WORK/LibRaw"
TIFF_SRC="$WORK/libtiff"
native_set_reproducible_env
native_prepare_source ZLIB_SOURCE_DIR zlib "$ZLIB_SRC" \
    "https://github.com/madler/zlib/releases/download/v$ZLIB_VERSION/zlib-$ZLIB_VERSION.tar.gz" \
    "9a93b2b7dfdac77ceba5a558a580e74667dd6fede4585b91eefb60f03b72df23" CMakeLists.txt
native_prepare_source LIBJPEG_TURBO_SOURCE_DIR libjpeg-turbo "$JPEG_SRC" \
    "https://github.com/libjpeg-turbo/libjpeg-turbo/releases/download/$LIBJPEGTURBO_VERSION/libjpeg-turbo-$LIBJPEGTURBO_VERSION.tar.gz" \
    "99130559e7d62e8d695f2c0eaeef912c5828d5b84a0537dcb24c9678c9d5b76b" CMakeLists.txt
native_prepare_source LIBRAW_SOURCE_DIR LibRaw "$RAW_SRC" \
    "https://www.libraw.org/data/LibRaw-$LIBRAW_VERSION.tar.gz" \
    "dba34b7fc1143503942fa32ad9db43e94f714e62a4a856e91617f8f3e1e0aa5c" Makefile.dist
native_prepare_source LIBTIFF_SOURCE_DIR libtiff "$TIFF_SRC" \
    "https://download.osgeo.org/libtiff/tiff-$LIBTIFF_VERSION.tar.gz" \
    "88b3979e6d5c7e32b50d7ec72fb15af724f6ab2cbf7e10880c360a77e4b5d99a" CMakeLists.txt

build_abi() {
    local ABI="$1"
    echo "================================================================"
    echo "==> Building rawcodec deps for $ABI"
    echo "================================================================"
    local STAGE="$WORK/stage-$ABI"
    local OUT="$OUT_ROOT/$ABI"
    rm -rf "$OUT"
    mkdir -p "$OUT/lib" "$OUT/include"

    local COMMON=(
        -G Ninja
        -DCMAKE_MAKE_PROGRAM="$NINJA_BIN"
        -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN"
        -DANDROID_ABI="$ABI" -DANDROID_PLATFORM="android-$ANDROID_API"
        -DCMAKE_BUILD_TYPE=Release
        -DCMAKE_C_FLAGS="$NATIVE_REPRO_FLAGS"
        -DCMAKE_CXX_FLAGS="$NATIVE_REPRO_FLAGS"
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON
        -DCMAKE_SKIP_RPATH=ON
        -DBUILD_SHARED_LIBS=OFF
        -DCMAKE_INSTALL_PREFIX="$STAGE"
        -DCMAKE_FIND_ROOT_PATH="$STAGE"
        -DCMAKE_PREFIX_PATH="$STAGE"
        "$NATIVE_CMAKE_POLICY_MIN"
    )

    # 1) zlib (needed by libtiff deflate + LibRaw deflated DNG)
    "$CMAKE_BIN" -S "$ZLIB_SRC" -B "$WORK/zlib-$ABI" "${COMMON[@]}"
    "$CMAKE_BIN" --build "$WORK/zlib-$ABI" --target install
    rm -f "$STAGE/lib/libz.so"* 2>/dev/null || true

    # 2) libjpeg-turbo (classic libjpeg API; used by LibRaw lossy-DNG + JPEG-in-TIFF)
    "$CMAKE_BIN" -S "$JPEG_SRC" -B "$WORK/jpeg-$ABI" "${COMMON[@]}" \
        -DENABLE_SHARED=OFF -DENABLE_STATIC=ON \
        -DWITH_TURBOJPEG=OFF -DWITH_JPEG8=ON
    "$CMAKE_BIN" --build "$WORK/jpeg-$ABI" --target install

    # 3) libtiff (static; deflate via staged zlib, jpeg via staged libjpeg)
    "$CMAKE_BIN" -S "$TIFF_SRC" -B "$WORK/tiff-$ABI" "${COMMON[@]}" \
        -Dtiff-tools=OFF -Dtiff-tests=OFF -Dtiff-contrib=OFF -Dtiff-docs=OFF \
        -Dlzma=OFF -Dzstd=OFF -Dwebp=OFF -Djbig=OFF -Dlerc=OFF \
        -Dzlib=ON -Djpeg=ON \
        -DZLIB_INCLUDE_DIR="$STAGE/include" -DZLIB_LIBRARY="$STAGE/lib/libz.a" \
        -DJPEG_INCLUDE_DIR="$STAGE/include" -DJPEG_LIBRARY="$STAGE/lib/libjpeg.a"
    "$CMAKE_BIN" --build "$WORK/tiff-$ABI" --target install

    # 4) LibRaw (static). The libraw.org tarball has NO CMakeLists.txt, so use Makefile.dist's
    #    reentrant-object mapping while compiling and archiving directly with the pinned NDK tools.
    local NDK_BIN CCB
    NDK_BIN="$(ls -d "$NDK_DIR"/toolchains/llvm/prebuilt/*/bin 2>/dev/null | head -n1)"
    case "$ABI" in
        arm64-v8a)   CCB="aarch64-linux-android$ANDROID_API" ;;
        armeabi-v7a) CCB="armv7a-linux-androideabi$ANDROID_API" ;;
        x86_64)      CCB="x86_64-linux-android$ANDROID_API" ;;
        x86)         CCB="i686-linux-android$ANDROID_API" ;;
        *) echo "ERROR: unmapped ABI '$ABI' for LibRaw" >&2; return 1 ;;
    esac
    if [ -z "$NDK_BIN" ] || [ ! -x "$NDK_BIN/$CCB-clang++" ] || [ ! -x "$NDK_BIN/llvm-ar" ]; then
        echo "ERROR: Required NDK tools not found under $NDK_BIN" >&2; return 1
    fi

    local RAWB="$WORK/LibRaw-$ABI-src"
    rm -rf "$RAWB"; cp -R "$RAW_SRC" "$RAWB"
    mkdir -p "$RAWB/object" "$RAWB/lib"
    local JOBS
    JOBS="${NATIVE_JOBS:-$(getconf _NPROCESSORS_ONLN 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)}"
    local -a RAW_OBJECTS=()
    local -a PENDING_PIDS=()
    local SOURCE OBJECT PID
    while IFS= read -r SOURCE; do
        OBJECT="$RAWB/object/${SOURCE//\//_}"
        OBJECT="${OBJECT%.cpp}.o"
        (
            cd "$RAWB"
            "$NDK_BIN/$CCB-clang++" -c -O3 -I. -w -fPIC -DNDEBUG \
                -DUSE_ZLIB -DUSE_JPEG -DUSE_JPEG8 -I"$STAGE/include" \
                $NATIVE_REPRO_FLAGS -o "$OBJECT" "$SOURCE"
        ) &
        PENDING_PIDS+=("$!")
        RAW_OBJECTS+=("$OBJECT")
        if [ "${#PENDING_PIDS[@]}" -ge "$JOBS" ]; then
            for PID in "${PENDING_PIDS[@]}"; do wait "$PID"; done
            PENDING_PIDS=()
        fi
    done < <(awk '/^object\/.*\.mt\.o: .*\.cpp$/ {print $2}' "$RAWB/Makefile.dist")
    for PID in "${PENDING_PIDS[@]}"; do wait "$PID"; done
    if [ "${#RAW_OBJECTS[@]}" -eq 0 ]; then
        echo "ERROR: No LibRaw reentrant sources found in Makefile.dist" >&2; return 1
    fi
    "$NDK_BIN/llvm-ar" crsD "$RAWB/lib/libraw_r.a" "${RAW_OBJECTS[@]}"
    "$NDK_BIN/llvm-ranlib" "$RAWB/lib/libraw_r.a"

    # Stage LibRaw's headers + static libs where step 5 (and the app CMake) expect them.
    mkdir -p "$STAGE/include/libraw"
    cp -f "$RAWB"/libraw/*.h "$STAGE/include/libraw/"
    cp -f "$RAWB"/lib/libraw.a "$STAGE/lib/" 2>/dev/null || true
    cp -f "$RAWB"/lib/libraw_r.a "$STAGE/lib/" 2>/dev/null || true

    # 5) Collect the static libs + headers the JNI target links.
    cp -f "$STAGE/lib/libz.a" "$OUT/lib/"
    cp -f "$STAGE/lib/libjpeg.a" "$OUT/lib/"
    if [ -f "$STAGE/lib/libtiff.a" ]; then cp -f "$STAGE/lib/libtiff.a" "$OUT/lib/"; fi
    # LibRaw installs libraw.a (non-thread-safe) and libraw_r.a (reentrant); prefer libraw_r.
    if [ -f "$STAGE/lib/libraw_r.a" ]; then cp -f "$STAGE/lib/libraw_r.a" "$OUT/lib/libraw.a"; \
    elif [ -f "$STAGE/lib/libraw.a" ]; then cp -f "$STAGE/lib/libraw.a" "$OUT/lib/libraw.a"; fi

    cp -f "$STAGE/include/zlib.h" "$STAGE/include/zconf.h" "$OUT/include/" 2>/dev/null || true
    cp -f "$STAGE/include/jpeglib.h" "$STAGE/include/jconfig.h" "$STAGE/include/jmorecfg.h" "$OUT/include/" 2>/dev/null || true
    cp -f "$STAGE"/include/tiff*.h "$OUT/include/" 2>/dev/null || true
    mkdir -p "$OUT/include/libraw"
    cp -f "$STAGE"/include/libraw/*.h "$OUT/include/libraw/" 2>/dev/null || true
    native_normalize_archives "$OUT/lib/"*.a
    echo "==> Installed $ABI: $(ls -1 "$OUT/lib")"
}

declare -a ABIS=()
add_abi() {
    local a="$1"
    for existing in "${ABIS[@]:-}"; do [ "$existing" = "$a" ] && return; done
    ABIS+=("$a")
}
if [ "$#" -eq 0 ]; then
    add_abi "arm64-v8a"
else
    for arg in "$@"; do
        case "$(echo "$arg" | tr '[:upper:]' '[:lower:]')" in
            all|universal)   add_abi "arm64-v8a"; add_abi "armeabi-v7a"; add_abi "x86_64"; add_abi "x86" ;;
            arm64-v8a|arm64) add_abi "arm64-v8a" ;;
            armeabi-v7a|arm) add_abi "armeabi-v7a" ;;
            x86_64)          add_abi "x86_64" ;;
            x86)             add_abi "x86" ;;
            ci)              : ;;
            *) echo "WARNING: unknown ABI arg '$arg', skipping" >&2 ;;
        esac
    done
fi
if [ "${#ABIS[@]}" -eq 0 ]; then add_abi "arm64-v8a"; fi

for abi in "${ABIS[@]}"; do
    build_abi "$abi"
done

echo "DONE. Prebuilt libs under: $OUT_ROOT"
