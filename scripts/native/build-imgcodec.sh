#!/usr/bin/env bash
#
# SPDX-FileCopyrightText: 2023-2026 IacobIacob01
# SPDX-License-Identifier: Apache-2.0
#
# Cross-compiles zlib + libpng + libjpeg-turbo (static) for Android using the NDK CMake toolchain
# and installs the resulting static libs + headers into
#   app/androidApp/src/main/cpp/imgcodec/<abi>/{include,lib}
# so the app's CMakeLists can link them into the `imgstream` JNI library for memory-bounded,
# scanline (row-streaming) JPEG/PNG encode — the editor bake writes the full-resolution result one
# strip at a time without ever holding the whole output bitmap in RAM.
#
# Run once per machine (and again only when bumping the pinned versions):
#   scripts/native/build-imgcodec.sh            # builds arm64-v8a (device/dev ABI)
#   scripts/native/build-imgcodec.sh all        # builds all four ABIs for release
#
# Requires: internet access, the pinned NDK, and CMake (SDK 3.31.x preferred; 4.x works via
# NATIVE_CMAKE_POLICY_MIN).
set -euo pipefail

# --- Pinned versions -------------------------------------------------------------------------
ZLIB_VERSION="1.3.1"
LIBPNG_VERSION="1.6.44"
LIBJPEGTURBO_VERSION="3.0.4"
ANDROID_API=29                          # matches app minSdk
CMAKE_VERSION="3.31.6"

# --- Paths -----------------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
source "$SCRIPT_DIR/native-common.sh"
OUT_ROOT="${NATIVE_OUTPUT_BASE:-$REPO_ROOT/app/androidApp/src/main/cpp}/imgcodec"

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
PNG_SRC="$WORK/libpng"
JPEG_SRC="$WORK/libjpeg-turbo"
native_set_reproducible_env
native_prepare_source ZLIB_SOURCE_DIR zlib "$ZLIB_SRC" \
    "https://github.com/madler/zlib/releases/download/v$ZLIB_VERSION/zlib-$ZLIB_VERSION.tar.gz" \
    "9a93b2b7dfdac77ceba5a558a580e74667dd6fede4585b91eefb60f03b72df23" CMakeLists.txt
native_prepare_source LIBPNG_SOURCE_DIR libpng "$PNG_SRC" \
    "https://download.sourceforge.net/libpng/libpng-$LIBPNG_VERSION.tar.gz" \
    "8c25a7792099a0089fa1cc76c94260d0bb3f1ec52b93671b572f8bb61577b732" CMakeLists.txt
native_prepare_source LIBJPEG_TURBO_SOURCE_DIR libjpeg-turbo "$JPEG_SRC" \
    "https://github.com/libjpeg-turbo/libjpeg-turbo/releases/download/$LIBJPEGTURBO_VERSION/libjpeg-turbo-$LIBJPEGTURBO_VERSION.tar.gz" \
    "99130559e7d62e8d695f2c0eaeef912c5828d5b84a0537dcb24c9678c9d5b76b" CMakeLists.txt

build_abi() {
    local ABI="$1"
    echo "================================================================"
    echo "==> Building imgcodec for $ABI"
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

    # 1) zlib (needed by libpng)
    "$CMAKE_BIN" -S "$ZLIB_SRC" -B "$WORK/zlib-$ABI" "${COMMON[@]}"
    "$CMAKE_BIN" --build "$WORK/zlib-$ABI" --target install
    # zlib installs libz.a on Unix; ensure the static archive name is discoverable.
    rm -f "$STAGE/lib/libz.so"* 2>/dev/null || true

    # 2) libjpeg-turbo (static, with the classic libjpeg API)
    "$CMAKE_BIN" -S "$JPEG_SRC" -B "$WORK/jpeg-$ABI" "${COMMON[@]}" \
        -DENABLE_SHARED=OFF -DENABLE_STATIC=ON \
        -DWITH_TURBOJPEG=OFF -DWITH_JPEG8=ON
    "$CMAKE_BIN" --build "$WORK/jpeg-$ABI" --target install

    # 3) libpng (static, pointed at the staged zlib)
    "$CMAKE_BIN" -S "$PNG_SRC" -B "$WORK/png-$ABI" "${COMMON[@]}" \
        -DPNG_SHARED=OFF -DPNG_STATIC=ON -DPNG_TESTS=OFF -DPNG_TOOLS=OFF \
        -DZLIB_ROOT="$STAGE" \
        -DZLIB_INCLUDE_DIR="$STAGE/include" \
        -DZLIB_LIBRARY="$STAGE/lib/libz.a"
    "$CMAKE_BIN" --build "$WORK/png-$ABI" --target install

    # 4) Collect the static libs + headers the JNI target links.
    cp -f "$STAGE/lib/libz.a" "$OUT/lib/"
    cp -f "$STAGE/lib/libjpeg.a" "$OUT/lib/"
    # libpng installs versioned (libpng16.a) + unversioned symlink; copy the real archive.
    if [ -f "$STAGE/lib/libpng16.a" ]; then cp -f "$STAGE/lib/libpng16.a" "$OUT/lib/libpng.a"; \
    elif [ -f "$STAGE/lib/libpng.a" ]; then cp -f "$STAGE/lib/libpng.a" "$OUT/lib/libpng.a"; fi
    cp -f "$STAGE/include/jpeglib.h" "$STAGE/include/jconfig.h" "$STAGE/include/jmorecfg.h" "$OUT/include/"
    cp -f "$STAGE"/include/png*.h "$OUT/include/" 2>/dev/null || true
    cp -f "$STAGE/include/zlib.h" "$STAGE/include/zconf.h" "$OUT/include/" 2>/dev/null || true
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
