#!/usr/bin/env bash
set -euo pipefail

JP2FORANDROID_REVISION="2cbc0f1dc3e71ac414aaa1fd65885c488dcfe256"
ANDROID_API=29
CMAKE_VERSION="3.31.6"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
source "$SCRIPT_DIR/native-common.sh"
OUT_ROOT="${NATIVE_OUTPUT_BASE:-$REPO_ROOT/app/src/main/cpp}/jp2codec"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ] && [ -f "$REPO_ROOT/local.properties" ]; then
    SDK="$(grep -E '^sdk\.dir=' "$REPO_ROOT/local.properties" | head -n1 | cut -d'=' -f2-)"
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
JP2_SRC="$WORK/JP2ForAndroid"
native_set_reproducible_env
native_prepare_source JP2FORANDROID_SOURCE_DIR JP2ForAndroid "$JP2_SRC" \
    "https://github.com/Tgo1014/JP2ForAndroid/archive/$JP2FORANDROID_REVISION.tar.gz" \
    "2abe83a246f927aaadce12d78b52fe81ad28d61cf19ff351679b5c8d074e50b9" library/CMakeLists.txt
sed 's/^[[:space:]]*SHARED[[:space:]]*$/             STATIC/' \
    "$JP2_SRC/library/CMakeLists.txt" > "$JP2_SRC/library/CMakeLists.txt.static"
mv "$JP2_SRC/library/CMakeLists.txt.static" "$JP2_SRC/library/CMakeLists.txt"

build_abi() {
    local ABI="$1"
    local BUILD="$WORK/build-$ABI"
    local OUT="$OUT_ROOT/$ABI"
    rm -rf "$OUT"
    mkdir -p "$OUT/lib"

    "$CMAKE_BIN" -S "$JP2_SRC/library" -B "$BUILD" -G Ninja \
        -DCMAKE_MAKE_PROGRAM="$NINJA_BIN" \
        -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
        -DANDROID_ABI="$ABI" -DANDROID_PLATFORM="android-$ANDROID_API" \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_C_FLAGS="$NATIVE_REPRO_FLAGS" \
        -DCMAKE_CXX_FLAGS="$NATIVE_REPRO_FLAGS" \
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
        -DCMAKE_ARCHIVE_OUTPUT_DIRECTORY="$OUT/lib"
    "$CMAKE_BIN" --build "$BUILD" --target openjpeg

    if [ ! -f "$OUT/lib/libopenjpeg.a" ]; then
        echo "ERROR: libopenjpeg.a was not produced for $ABI" >&2
        exit 1
    fi
    native_normalize_archives "$OUT/lib/libopenjpeg.a"
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
