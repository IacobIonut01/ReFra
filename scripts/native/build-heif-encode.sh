#!/usr/bin/env bash
#
# SPDX-FileCopyrightText: 2023-2026 IacobIacob01
# SPDX-License-Identifier: Apache-2.0
#
# Cross-compiles x265 (HEVC encoder) + aom (AV1 encoder) + libde265 (HEVC decoder) + libheif
# (WITH encoders) as static libs for Android, installing into
#   app/androidApp/src/main/cpp/heifenc/<abi>/{include,lib}
# so the app's CMakeLists can link the `heifenc` JNI library for memory-bounded TILED HEIC/AVIF
# encode (libheif grid images: each tile encoded separately, whole decoded image never in RAM).
#
# This is intentionally SEPARATE from build-heif.sh (decode-only, cpp/heif/) so the existing tiled
# DECODE path is never affected by the much heavier encoder build.
#
#   scripts/native/build-heif-encode.sh            # arm64-v8a
#   scripts/native/build-heif-encode.sh all        # all four ABIs
#
# Requires: internet access (curl + git), the pinned NDK, and SDK CMake 3.31.x.
set -euo pipefail

# --- Pinned versions -------------------------------------------------------------------------
LIBDE265_VERSION="1.0.15"
LIBHEIF_VERSION="1.19.8"
X265_VERSION="3.6"
AOM_VERSION="v3.9.1"
ANDROID_API=29
CMAKE_VERSION="3.31.6"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
source "$SCRIPT_DIR/native-common.sh"
OUT_ROOT="${NATIVE_OUTPUT_BASE:-$REPO_ROOT/app/androidApp/src/main/cpp}/heifenc"

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
NDK_CLANG="$(ls -d "$NDK_DIR"/toolchains/llvm/prebuilt/*/bin/clang 2>/dev/null | head -n1)"
# Cygwin resolves "clang" to "clang.exe", but native CMake needs the actual file name.
if [ -n "$NDK_CLANG" ] && [ -f "$NDK_CLANG.exe" ]; then
    NDK_CLANG="$NDK_CLANG.exe"
fi
if [ -z "$NDK_CLANG" ] || [ ! -x "$NDK_CLANG" ]; then
    echo "ERROR: NDK clang not found under $NDK_DIR" >&2
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

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
DE265_SRC="$WORK/libde265"
HEIF_SRC="$WORK/libheif"
X265_SRC="$WORK/x265"
AOM_SRC="$WORK/aom"
native_set_reproducible_env
native_prepare_source LIBDE265_SOURCE_DIR libde265 "$DE265_SRC" \
    "https://github.com/strukturag/libde265/releases/download/v$LIBDE265_VERSION/libde265-$LIBDE265_VERSION.tar.gz" \
    "00251986c29d34d3af7117ed05874950c875dd9292d016be29d3b3762666511d" CMakeLists.txt
native_prepare_source LIBHEIF_SOURCE_DIR libheif "$HEIF_SRC" \
    "https://github.com/strukturag/libheif/releases/download/v$LIBHEIF_VERSION/libheif-$LIBHEIF_VERSION.tar.gz" \
    "6c4a5b08e6eae66d199977468859dea3b5e059081db8928f7c7c16e53836c906" CMakeLists.txt
native_prepare_source X265_SOURCE_DIR x265 "$X265_SRC" \
    "https://download.videolan.org/pub/videolan/x265/x265_$X265_VERSION.tar.gz" \
    "663531f341c5389f460d730e62e10a4fcca3428ca2ca109693867bc5fe2e2807" source/CMakeLists.txt
native_prepare_source AOM_SOURCE_DIR aom "$AOM_SRC" \
    "https://storage.googleapis.com/aom-releases/libaom-${AOM_VERSION#v}.tar.gz" \
    "dba99fc1c28aaade28dda59821166b2fa91c06162d1bc99fde0ddaad7cecc50e" CMakeLists.txt

build_abi() {
    local ABI="$1"
    echo "================================================================"
    echo "==> Building HEIC/AVIF encoders for $ABI"
    echo "================================================================"
    local STAGE="$WORK/stage-$ABI"
    local OUT="$OUT_ROOT/$ABI"
    rm -rf "$OUT"; mkdir -p "$OUT/lib" "$OUT/include"

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
        -DCMAKE_PREFIX_PATH="$STAGE"
        -DCMAKE_FIND_ROOT_PATH="$STAGE"
    )

    # 1) libde265 (HEVC decoder — libheif needs a decoder present)
    "$CMAKE_BIN" -S "$DE265_SRC" -B "$WORK/de265-$ABI" "${COMMON[@]}" \
        -DENABLE_SDL=OFF -DENABLE_DECODER=OFF -DENABLE_ENCODER=OFF
    "$CMAKE_BIN" --build "$WORK/de265-$ABI" --target install

    # 2) x265 (HEVC encoder). Assembly OFF for portable, reliable cross-compile.
    "$CMAKE_BIN" -S "$X265_SRC/source" -B "$WORK/x265-$ABI" "${COMMON[@]}" \
        -DENABLE_SHARED=OFF -DENABLE_CLI=OFF -DENABLE_ASSEMBLY=OFF -DENABLE_PIC=ON -DHIGH_BIT_DEPTH=OFF
    "$CMAKE_BIN" --build "$WORK/x265-$ABI" --target install
    # x265 installs libx265.a; ensure the unversioned name exists.
    [ -f "$STAGE/lib/libx265.a" ] || { echo "ERROR: libx265.a not built" >&2; exit 1; }

    # 3) aom (AV1 encoder + decoder). AOM_TARGET_CPU must match the ABI, otherwise
    # aom compiles CPU-specific SIMD (e.g. AArch64-only NEON intrinsics like
    # vtrn1q_u64) with the wrong target and fails (breaks armeabi-v7a/x86/x86_64).
    local AOM_CPU
    case "$ABI" in
        arm64-v8a)   AOM_CPU="arm64" ;;
        armeabi-v7a) AOM_CPU="armv7" ;;
        x86_64)      AOM_CPU="x86_64" ;;
        x86)         AOM_CPU="x86" ;;
        *)           AOM_CPU="generic" ;;
    esac
    "$CMAKE_BIN" -S "$AOM_SRC" -B "$WORK/aom-$ABI" "${COMMON[@]}" \
        -DCMAKE_ASM_COMPILER="$NDK_CLANG" \
        -DENABLE_TESTS=OFF -DENABLE_EXAMPLES=OFF -DENABLE_DOCS=OFF -DENABLE_TOOLS=OFF \
        -DCONFIG_AV1_ENCODER=1 -DCONFIG_AV1_DECODER=1 -DAOM_TARGET_CPU="$AOM_CPU"
    "$CMAKE_BIN" --build "$WORK/aom-$ABI" --target install

    # 4) libheif WITH encoders, pointed at staged x265 + aom + libde265.
    "$CMAKE_BIN" -S "$HEIF_SRC" -B "$WORK/heif-$ABI" "${COMMON[@]}" \
        -DWITH_EXAMPLES=OFF -DWITH_GDK_PIXBUF=OFF -DBUILD_TESTING=OFF \
        -DWITH_LIBDE265=ON -DENABLE_PLUGIN_LOADING=OFF \
        -DWITH_X265=ON \
        -DWITH_AOM_ENCODER=ON -DWITH_AOM_DECODER=ON -DWITH_DAV1D=OFF -DWITH_RAV1E=OFF -DWITH_SvtEnc=OFF \
        -DWITH_JPEG_DECODER=OFF -DWITH_JPEG_ENCODER=OFF \
        -DWITH_OpenJPEG_DECODER=OFF -DWITH_OpenJPEG_ENCODER=OFF \
        -DWITH_UNCOMPRESSED_CODEC=OFF \
        -DLIBDE265_INCLUDE_DIR="$STAGE/include" -DLIBDE265_LIBRARY="$STAGE/lib/libde265.a" \
        -DX265_INCLUDE_DIR="$STAGE/include" -DX265_LIBRARY="$STAGE/lib/libx265.a" \
        -DAOM_INCLUDE_DIR="$STAGE/include" -DAOM_LIBRARY="$STAGE/lib/libaom.a" \
        -DPKG_CONFIG_EXECUTABLE=/usr/bin/pkg-config
    "$CMAKE_BIN" --build "$WORK/heif-$ABI" --target install

    # 5) Collect static libs + headers.
    cp -f "$STAGE/lib/libheif.a" "$OUT/lib/"
    cp -f "$STAGE/lib/libde265.a" "$OUT/lib/"
    cp -f "$STAGE/lib/libx265.a" "$OUT/lib/"
    cp -f "$STAGE/lib/libaom.a" "$OUT/lib/"
    cp -Rf "$STAGE/include/libheif" "$OUT/include/"
    native_normalize_archives "$OUT/lib/"*.a
    echo "==> Installed $ABI: $(ls -1 "$OUT/lib")"
}

declare -a ABIS=()
add_abi() { local a="$1"; for e in "${ABIS[@]:-}"; do [ "$e" = "$a" ] && return; done; ABIS+=("$a"); }
if [ "$#" -eq 0 ]; then add_abi "arm64-v8a"; else
    for arg in "$@"; do case "$(echo "$arg" | tr '[:upper:]' '[:lower:]')" in
        all|universal) add_abi "arm64-v8a"; add_abi "armeabi-v7a"; add_abi "x86_64"; add_abi "x86" ;;
        arm64-v8a|arm64) add_abi "arm64-v8a" ;;
        armeabi-v7a|arm) add_abi "armeabi-v7a" ;;
        x86_64) add_abi "x86_64" ;;
        x86) add_abi "x86" ;;
        ci) : ;;
        *) echo "WARNING: unknown ABI arg '$arg', skipping" >&2 ;;
    esac; done
fi
if [ "${#ABIS[@]}" -eq 0 ]; then add_abi "arm64-v8a"; fi

for abi in "${ABIS[@]}"; do build_abi "$abi"; done
echo "DONE. Prebuilt encoder libs under: $OUT_ROOT"
