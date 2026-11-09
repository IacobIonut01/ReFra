#!/usr/bin/env bash
# Builds a reader-only static zxing-cpp for the viewer's "codes in photos" scanner.
# Output: <NATIVE_OUTPUT_BASE>/zxingcpp/<ABI>/{lib/libZXing.a,include/ZXing/*.h}, linked into
# libcodescan.so by app/androidApp/src/main/cpp/CMakeLists.txt.
set -euo pipefail

ZXING_CPP_VERSION="2.3.0"
ZXING_CPP_SHA256="64e4139103fdbc57752698ee15b5f0b0f7af9a0331ecbdc492047e0772c417ba"
ANDROID_API=29
CMAKE_VERSION="3.31.6"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
source "$SCRIPT_DIR/native-common.sh"
OUT_ROOT="${NATIVE_OUTPUT_BASE:-$REPO_ROOT/app/androidApp/src/main/cpp}/zxingcpp"

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
if [ -z "$CMAKE_BIN" ]; then
    echo "ERROR: cmake $CMAKE_VERSION not found." >&2
    exit 1
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
ZXING_SRC="$WORK/zxing-cpp"
native_set_reproducible_env
# The writer backend (libzint) is shipped as symlinks into a submodule that the release archive
# does not contain; a reader-only build never touches it, so leave it out of the extraction.
native_prepare_source ZXING_CPP_SOURCE_DIR zxing-cpp "$ZXING_SRC" \
    "https://github.com/zxing-cpp/zxing-cpp/archive/refs/tags/v$ZXING_CPP_VERSION.tar.gz" \
    "$ZXING_CPP_SHA256" core/src/ReadBarcode.h '*/core/src/libzint/*'

build_abi() {
    local ABI="$1"
    local BUILD="$WORK/build-$ABI"
    local OUT="$OUT_ROOT/$ABI"
    rm -rf "$OUT"
    mkdir -p "$OUT/lib" "$OUT/include/ZXing"

    # -Os plus section GC keeps the reader small; the final .so links with --gc-sections.
    "$CMAKE_BIN" -S "$ZXING_SRC" -B "$BUILD" -G Ninja \
        -DCMAKE_MAKE_PROGRAM="$NINJA_BIN" \
        -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
        -DANDROID_ABI="$ABI" -DANDROID_PLATFORM="android-$ANDROID_API" \
        -DANDROID_STL=c++_static \
        -DCMAKE_BUILD_TYPE=MinSizeRel \
        -DCMAKE_C_FLAGS="$NATIVE_REPRO_FLAGS -ffunction-sections -fdata-sections" \
        -DCMAKE_CXX_FLAGS="$NATIVE_REPRO_FLAGS -ffunction-sections -fdata-sections" \
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
        -DBUILD_SHARED_LIBS=OFF \
        -DZXING_READERS=ON \
        -DZXING_WRITERS=OFF \
        -DZXING_C_API=OFF \
        -DZXING_EXAMPLES=OFF \
        -DZXING_BLACKBOX_TESTS=OFF \
        -DZXING_UNIT_TESTS=OFF \
        -DZXING_PYTHON_MODULE=OFF \
        -DZXING_DEPENDENCIES=LOCAL \
        -DCMAKE_ARCHIVE_OUTPUT_DIRECTORY="$OUT/lib"
    "$CMAKE_BIN" --build "$BUILD" --target ZXing

    local LIB
    LIB="$(find "$OUT/lib" "$BUILD" -name 'libZXing.a' -print -quit)"
    if [ -z "$LIB" ]; then
        echo "ERROR: libZXing.a was not produced for $ABI" >&2
        exit 1
    fi
    [ "$LIB" = "$OUT/lib/libZXing.a" ] || cp "$LIB" "$OUT/lib/libZXing.a"
    cp "$ZXING_SRC"/core/src/*.h "$OUT/include/ZXing/"
    # Version.h is generated from Version.h.in at configure time.
    if [ -f "$BUILD/core/Version.h" ]; then
        cp "$BUILD/core/Version.h" "$OUT/include/ZXing/"
    fi
    native_normalize_archives "$OUT/lib/libZXing.a"
}

declare -a ABIS=()
add_abi() {
    local ABI="$1"
    for EXISTING in "${ABIS[@]:-}"; do
        [ "$EXISTING" = "$ABI" ] && return
    done
    ABIS+=("$ABI")
}
if [ "$#" -eq 0 ]; then
    add_abi "arm64-v8a"
else
    for ARG in "$@"; do
        case "$(echo "$ARG" | tr '[:upper:]' '[:lower:]')" in
            all|universal) add_abi "arm64-v8a"; add_abi "armeabi-v7a"; add_abi "x86_64"; add_abi "x86" ;;
            arm64-v8a|arm64) add_abi "arm64-v8a" ;;
            armeabi-v7a|arm) add_abi "armeabi-v7a" ;;
            x86_64) add_abi "x86_64" ;;
            x86) add_abi "x86" ;;
            ci) : ;;
            *) echo "WARNING: unknown ABI arg '$ARG', skipping" >&2 ;;
        esac
    done
fi
if [ "${#ABIS[@]}" -eq 0 ]; then
    add_abi "arm64-v8a"
fi

for ABI in "${ABIS[@]}"; do
    build_abi "$ABI"
done
